package com.t7lab.focustime.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.t7lab.focustime.MainActivity
import com.t7lab.focustime.R
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.di.ServiceEntryPoint
import com.t7lab.focustime.util.formatDuration
import com.t7lab.focustime.util.isHostBlocked
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class FocusVpnService : VpnService() {

    companion object {
        private const val TAG = "FocusVpnService"
        const val CHANNEL_ID = "focus_session_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.t7lab.focustime.START_VPN"
        const val ACTION_STOP = "com.t7lab.focustime.STOP_VPN"
        private const val DNS_READ_DELAY_MS = 10L
        private const val DNS_ERROR_DELAY_MS = 100L
        private const val TIMER_UPDATE_INTERVAL_MS = 1000L

        fun createStartIntent(context: Context): Intent {
            return Intent(context, FocusVpnService::class.java).apply {
                action = ACTION_START
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, FocusVpnService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var blockedUrls: List<BlockedItem> = emptyList()
    private var dnsChannel: DatagramChannel? = null

    override fun onCreate() {
        super.onCreate()
        // Notification channel is created in FocusTimeApp.onCreate()

        // Create DNS forwarding channel on main thread (avoids coroutine context restrictions)
        try {
            dnsChannel = DatagramChannel.open()
            dnsChannel?.let { channel ->
                protect(channel.socket())
                channel.socket().soTimeout = 5000
                channel.configureBlocking(true)
                // Don't connect() - we'll use send() with address instead
                Log.d(TAG, "DNS forwarding channel created and protected in onCreate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DNS channel in onCreate: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
                serviceScope.launch {
                    loadBlockedUrls()
                    startVpn()
                    startTimer()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun loadBlockedUrls() {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, ServiceEntryPoint::class.java
        )
        blockedUrls = entryPoint.sessionDao().getActiveBlockedItems()
            .filter { it.type == BlockedItemType.URL }
    }

    private fun startVpn() {
        if (blockedUrls.isEmpty()) {
            Log.d(TAG, "No URLs to block, skipping VPN setup")
            return
        }

        Log.d(TAG, "Starting VPN with ${blockedUrls.size} blocked URLs: ${blockedUrls.map { it.value }}")
        Log.d(TAG, "DNS channel available: ${dnsChannel != null}")

        val builder = Builder()
            .setSession("FocusTime")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            // Only route our fake DNS through VPN - real DNS forwarding uses protected socket
            .addRoute("10.0.0.1", 32)

        vpnInterface = builder.establish()
        Log.d(TAG, "VPN interface established: ${vpnInterface != null}")

        vpnInterface?.let { pfd ->
            serviceScope.launch {
                handleDnsRequests(pfd)
            }
        }
    }

    private suspend fun handleDnsRequests(pfd: ParcelFileDescriptor) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteBuffer.allocate(32767)

        while (serviceScope.isActive) {
            try {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length <= 0) {
                    delay(DNS_READ_DELAY_MS)
                    continue
                }
                buffer.limit(length)

                val packet = buffer.array().copyOf(length)
                val dnsResponse = processDnsPacket(packet)
                if (dnsResponse != null) {
                    outputStream.write(dnsResponse)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                if (!serviceScope.isActive) break
                delay(DNS_ERROR_DELAY_MS)
            }
        }
    }

    private fun processDnsPacket(packet: ByteArray): ByteArray? {
        if (packet.size < 40) return null

        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return null

        val ipHeaderLen = (packet[0].toInt() and 0xF) * 4
        if (packet.size < ipHeaderLen + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null

        // UDP header: source port (2 bytes), dest port (2 bytes), length (2), checksum (2)
        val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (destPort != 53) return null

        val dnsOffset = ipHeaderLen + 8
        if (packet.size < dnsOffset + 12) return null

        val hostname = parseDnsHostname(packet, dnsOffset + 12)
        if (hostname != null && isHostBlocked(hostname, blockedUrls)) {
            Log.d(TAG, "BLOCKING DNS query for: $hostname")
            return buildDnsBlockResponse(packet, ipHeaderLen, dnsOffset)
        }

        Log.d(TAG, "FORWARDING DNS query for: $hostname")
        return forwardDnsQuery(packet, ipHeaderLen, dnsOffset)
    }

    private fun parseDnsHostname(packet: ByteArray, offset: Int): String? {
        val parts = mutableListOf<String>()
        var pos = offset

        while (pos < packet.size) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) break
            if (pos + 1 + labelLen > packet.size) return null

            parts.add(String(packet, pos + 1, labelLen))
            pos += 1 + labelLen
        }

        return if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else null
    }

    private fun buildDnsBlockResponse(packet: ByteArray, ipHeaderLen: Int, dnsOffset: Int): ByteArray {
        val response = packet.copyOf()

        for (i in 0 until 4) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }

        val srcPort0 = response[ipHeaderLen]
        val srcPort1 = response[ipHeaderLen + 1]
        response[ipHeaderLen] = response[ipHeaderLen + 2]
        response[ipHeaderLen + 1] = response[ipHeaderLen + 3]
        response[ipHeaderLen + 2] = srcPort0
        response[ipHeaderLen + 3] = srcPort1

        response[dnsOffset + 2] = 0x81.toByte()
        response[dnsOffset + 3] = 0x80.toByte()
        response[dnsOffset + 6] = 0
        response[dnsOffset + 7] = 1

        val queryEnd = findQueryEnd(response, dnsOffset + 12)
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,
            0, 1,
            0, 1,
            0, 0, 0, 30,
            0, 4,
            0, 0, 0, 0
        )

        val result = ByteArray(queryEnd + answer.size)
        System.arraycopy(response, 0, result, 0, queryEnd)
        System.arraycopy(answer, 0, result, queryEnd, answer.size)

        val totalLen = result.size
        result[2] = (totalLen shr 8).toByte()
        result[3] = (totalLen and 0xFF).toByte()

        val udpLen = totalLen - ipHeaderLen
        result[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        result[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()

        result[10] = 0; result[11] = 0
        result[ipHeaderLen + 6] = 0; result[ipHeaderLen + 7] = 0
        val checksum = calculateIpChecksum(result, ipHeaderLen)
        result[10] = (checksum shr 8).toByte()
        result[11] = (checksum and 0xFF).toByte()

        return result
    }

    private fun findQueryEnd(packet: ByteArray, offset: Int): Int {
        var pos = offset
        while (pos < packet.size && packet[pos].toInt() != 0) {
            pos += (packet[pos].toInt() and 0xFF) + 1
        }
        pos += 1
        pos += 4
        return pos
    }

    @Synchronized
    private fun forwardDnsQuery(packet: ByteArray, ipHeaderLen: Int, dnsOffset: Int): ByteArray? {
        val channel = dnsChannel ?: run {
            Log.e(TAG, "DNS channel not available")
            return null
        }

        try {
            val dnsData = packet.copyOfRange(dnsOffset, packet.size)
            val dnsServer = InetSocketAddress("8.8.8.8", 53)

            // Send DNS query using pre-created channel (use send() instead of write())
            channel.send(ByteBuffer.wrap(dnsData), dnsServer)

            // Receive response
            val responseBuffer = ByteBuffer.allocate(4096)
            channel.receive(responseBuffer)
            responseBuffer.flip()

            val responseLength = responseBuffer.remaining()
            val dnsResponseData = ByteArray(responseLength)
            responseBuffer.get(dnsResponseData)

            Log.d(TAG, "DNS forward success, received $responseLength bytes")

            // Build IP response packet
            val response = packet.copyOf()
            // Swap source and destination IP
            for (i in 0 until 4) {
                val temp = response[12 + i]
                response[12 + i] = response[16 + i]
                response[16 + i] = temp
            }
            // Swap source and destination ports
            val srcPort0 = response[ipHeaderLen]
            val srcPort1 = response[ipHeaderLen + 1]
            response[ipHeaderLen] = response[ipHeaderLen + 2]
            response[ipHeaderLen + 1] = response[ipHeaderLen + 3]
            response[ipHeaderLen + 2] = srcPort0
            response[ipHeaderLen + 3] = srcPort1

            val result = ByteArray(ipHeaderLen + 8 + dnsResponseData.size)
            System.arraycopy(response, 0, result, 0, ipHeaderLen + 8)
            System.arraycopy(dnsResponseData, 0, result, ipHeaderLen + 8, dnsResponseData.size)

            // Update IP total length
            val totalLen = result.size
            result[2] = (totalLen shr 8).toByte()
            result[3] = (totalLen and 0xFF).toByte()

            // Update UDP length
            val udpLen = totalLen - ipHeaderLen
            result[ipHeaderLen + 4] = (udpLen shr 8).toByte()
            result[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()

            // Recalculate checksums
            result[10] = 0; result[11] = 0
            result[ipHeaderLen + 6] = 0; result[ipHeaderLen + 7] = 0
            val checksum = calculateIpChecksum(result, ipHeaderLen)
            result[10] = (checksum shr 8).toByte()
            result[11] = (checksum and 0xFF).toByte()

            return result
        } catch (e: Exception) {
            Log.e(TAG, "DNS forward failed: ${e.message}", e)
            return null
        }
    }

    private fun calculateIpChecksum(packet: ByteArray, headerLen: Int): Int {
        var sum = 0
        for (i in 0 until headerLen step 2) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, ServiceEntryPoint::class.java
            )
            val prefs = entryPoint.preferencesManager()
            while (isActive) {
                val endTime = prefs.getSessionEndTimeOnce()
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    prefs.endSession()
                    showSessionEndedNotification()
                    stopVpn()
                    stopSelf()
                    break
                }
                updateNotification(formatDuration(remaining))
                delay(TIMER_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopVpn() {
        timerJob?.cancel()
        try {
            dnsChannel?.close()
        } catch (_: Exception) {}
        dnsChannel = null
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun buildNotification(timeText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, timeText))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(timeText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(timeText))
    }

    private fun showSessionEndedNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.session_ended))
            .setContentText(getString(R.string.session_ended_body))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onRevoke() {
        // Respect the user's decision to revoke the VPN — do not auto-reconnect.
        // The app monitor service continues to block apps independently.
        Log.d(TAG, "VPN revoked by user, stopping VPN service")
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
