package com.t7lab.focustime.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.t7lab.focustime.BuildConfig
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
        private const val VPN_RECONNECT_DELAY_MS = 1000L

        // IP protocol constants
        private const val IP_VERSION_4 = 4
        private const val IP_PROTOCOL_UDP = 17
        private const val IP_SRC_ADDR_OFFSET = 12
        private const val IP_DST_ADDR_OFFSET = 16
        private const val IP_ADDR_LENGTH = 4
        private const val IP_TOTAL_LENGTH_OFFSET = 2
        private const val IP_CHECKSUM_OFFSET = 10
        private const val IP_PROTOCOL_OFFSET = 9
        private const val MIN_IP_PACKET_SIZE = 40

        // UDP constants
        private const val UDP_HEADER_SIZE = 8
        private const val UDP_LENGTH_OFFSET = 4
        private const val UDP_CHECKSUM_OFFSET = 6
        private const val DNS_PORT = 53

        // DNS constants
        private const val DNS_HEADER_SIZE = 12
        private const val DNS_FLAGS_RESPONSE = 0x81.toByte()
        private const val DNS_FLAGS_NO_ERROR = 0x80.toByte()
        private const val DNS_ANSWER_COUNT_OFFSET = 6
        private const val DNS_QUESTION_OFFSET = 12
        private const val MAX_DNS_LABEL_LENGTH = 63
        private const val MAX_DNS_NAME_LENGTH = 253

        // VPN buffer and upstream DNS
        private const val VPN_BUFFER_SIZE = 32767
        private const val DNS_RESPONSE_BUFFER_SIZE = 4096
        private const val UPSTREAM_DNS_SERVER = "8.8.8.8"
        private const val DNS_SOCKET_TIMEOUT_MS = 5000

        private const val PENDING_INTENT_VPN_REQUEST_CODE = 100
        private const val PENDING_INTENT_VPN_END_REQUEST_CODE = 101

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
    @Volatile
    private var dnsChannel: DatagramChannel? = null
    private val channelLock = Any()

    override fun onCreate() {
        super.onCreate()
        try {
            val channel = DatagramChannel.open()
            protect(channel.socket())
            channel.socket().soTimeout = DNS_SOCKET_TIMEOUT_MS
            channel.configureBlocking(true)
            synchronized(channelLock) {
                dnsChannel = channel
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DNS channel: ${e.message}")
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
            if (BuildConfig.DEBUG) Log.d(TAG, "No URLs to block, skipping VPN setup")
            return
        }

        if (BuildConfig.DEBUG) Log.d(TAG, "Starting VPN with ${blockedUrls.size} blocked URLs")

        val builder = Builder()
            .setSession("FocusTime")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            .addRoute("10.0.0.1", 32)

        vpnInterface = builder.establish()

        vpnInterface?.let { pfd ->
            serviceScope.launch {
                handleDnsRequests(pfd)
            }
        }
    }

    private suspend fun handleDnsRequests(pfd: ParcelFileDescriptor) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteBuffer.allocate(VPN_BUFFER_SIZE)

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
        if (packet.size < MIN_IP_PACKET_SIZE) return null

        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != IP_VERSION_4) return null

        val ipHeaderLen = (packet[0].toInt() and 0xF) * 4
        if (ipHeaderLen < 20 || packet.size < ipHeaderLen + UDP_HEADER_SIZE) return null

        val protocol = packet[IP_PROTOCOL_OFFSET].toInt() and 0xFF
        if (protocol != IP_PROTOCOL_UDP) return null

        val destPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (destPort != DNS_PORT) return null

        val dnsOffset = ipHeaderLen + UDP_HEADER_SIZE
        if (packet.size < dnsOffset + DNS_HEADER_SIZE) return null

        val hostname = parseDnsHostname(packet, dnsOffset + DNS_QUESTION_OFFSET)
        if (hostname != null && isHostBlocked(hostname, blockedUrls)) {
            return buildDnsBlockResponse(packet, ipHeaderLen, dnsOffset)
        }

        return forwardDnsQuery(packet, ipHeaderLen, dnsOffset)
    }

    private fun parseDnsHostname(packet: ByteArray, offset: Int): String? {
        val parts = mutableListOf<String>()
        var pos = offset
        var totalLength = 0

        while (pos < packet.size) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) break

            // DNS compression pointer (starts with 0xC0) - not expected in queries but handle gracefully
            if (labelLen and 0xC0 == 0xC0) return null

            // Validate label length per RFC 1035
            if (labelLen > MAX_DNS_LABEL_LENGTH) return null
            if (pos + 1 + labelLen > packet.size) return null

            totalLength += labelLen + 1
            if (totalLength > MAX_DNS_NAME_LENGTH) return null

            parts.add(String(packet, pos + 1, labelLen, Charsets.US_ASCII))
            pos += 1 + labelLen
        }

        return if (parts.isNotEmpty()) parts.joinToString(".").lowercase() else null
    }

    private fun swapIpAddresses(packet: ByteArray) {
        for (i in 0 until IP_ADDR_LENGTH) {
            val temp = packet[IP_SRC_ADDR_OFFSET + i]
            packet[IP_SRC_ADDR_OFFSET + i] = packet[IP_DST_ADDR_OFFSET + i]
            packet[IP_DST_ADDR_OFFSET + i] = temp
        }
    }

    private fun swapUdpPorts(packet: ByteArray, ipHeaderLen: Int) {
        val srcPort0 = packet[ipHeaderLen]
        val srcPort1 = packet[ipHeaderLen + 1]
        packet[ipHeaderLen] = packet[ipHeaderLen + 2]
        packet[ipHeaderLen + 1] = packet[ipHeaderLen + 3]
        packet[ipHeaderLen + 2] = srcPort0
        packet[ipHeaderLen + 3] = srcPort1
    }

    private fun updatePacketLengths(packet: ByteArray, ipHeaderLen: Int) {
        val totalLen = packet.size
        packet[IP_TOTAL_LENGTH_OFFSET] = (totalLen shr 8).toByte()
        packet[IP_TOTAL_LENGTH_OFFSET + 1] = (totalLen and 0xFF).toByte()

        val udpLen = totalLen - ipHeaderLen
        packet[ipHeaderLen + UDP_LENGTH_OFFSET] = (udpLen shr 8).toByte()
        packet[ipHeaderLen + UDP_LENGTH_OFFSET + 1] = (udpLen and 0xFF).toByte()
    }

    private fun recalculateChecksums(packet: ByteArray, ipHeaderLen: Int) {
        // Zero out existing checksums before recalculation
        packet[IP_CHECKSUM_OFFSET] = 0
        packet[IP_CHECKSUM_OFFSET + 1] = 0
        packet[ipHeaderLen + UDP_CHECKSUM_OFFSET] = 0
        packet[ipHeaderLen + UDP_CHECKSUM_OFFSET + 1] = 0

        val checksum = calculateIpChecksum(packet, ipHeaderLen)
        packet[IP_CHECKSUM_OFFSET] = (checksum shr 8).toByte()
        packet[IP_CHECKSUM_OFFSET + 1] = (checksum and 0xFF).toByte()
    }

    private fun buildDnsBlockResponse(packet: ByteArray, ipHeaderLen: Int, dnsOffset: Int): ByteArray {
        val response = packet.copyOf()

        swapIpAddresses(response)
        swapUdpPorts(response, ipHeaderLen)

        // Set DNS response flags
        response[dnsOffset + 2] = DNS_FLAGS_RESPONSE
        response[dnsOffset + 3] = DNS_FLAGS_NO_ERROR
        response[dnsOffset + DNS_ANSWER_COUNT_OFFSET] = 0
        response[dnsOffset + DNS_ANSWER_COUNT_OFFSET + 1] = 1

        val queryEnd = findQueryEnd(response, dnsOffset + DNS_QUESTION_OFFSET)
        if (queryEnd > response.size) return packet // Bail on malformed packet

        // DNS answer: pointer to name, type A, class IN, TTL 30s, 4 bytes 0.0.0.0
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, // Name pointer
            0, 1,               // Type A
            0, 1,               // Class IN
            0, 0, 0, 30,       // TTL 30 seconds
            0, 4,               // Data length
            0, 0, 0, 0         // 0.0.0.0
        )

        val result = ByteArray(queryEnd + answer.size)
        System.arraycopy(response, 0, result, 0, queryEnd)
        System.arraycopy(answer, 0, result, queryEnd, answer.size)

        updatePacketLengths(result, ipHeaderLen)
        recalculateChecksums(result, ipHeaderLen)

        return result
    }

    private fun findQueryEnd(packet: ByteArray, offset: Int): Int {
        var pos = offset
        while (pos < packet.size) {
            val labelLen = packet[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos += 1 // null terminator
                break
            }
            if (labelLen > MAX_DNS_LABEL_LENGTH) break // Malformed
            pos += labelLen + 1
        }
        // Skip QTYPE (2 bytes) and QCLASS (2 bytes)
        pos += 4
        return pos.coerceAtMost(packet.size)
    }

    private fun forwardDnsQuery(packet: ByteArray, ipHeaderLen: Int, dnsOffset: Int): ByteArray? {
        synchronized(channelLock) {
            val channel = dnsChannel ?: return null

            try {
                val dnsData = packet.copyOfRange(dnsOffset, packet.size)
                val dnsServer = InetSocketAddress(UPSTREAM_DNS_SERVER, DNS_PORT)

                channel.send(ByteBuffer.wrap(dnsData), dnsServer)

                val responseBuffer = ByteBuffer.allocate(DNS_RESPONSE_BUFFER_SIZE)
                channel.receive(responseBuffer)
                responseBuffer.flip()

                val responseLength = responseBuffer.remaining()
                val dnsResponseData = ByteArray(responseLength)
                responseBuffer.get(dnsResponseData)

                // Build IP response packet
                val response = packet.copyOf()
                swapIpAddresses(response)
                swapUdpPorts(response, ipHeaderLen)

                val result = ByteArray(ipHeaderLen + UDP_HEADER_SIZE + dnsResponseData.size)
                System.arraycopy(response, 0, result, 0, ipHeaderLen + UDP_HEADER_SIZE)
                System.arraycopy(dnsResponseData, 0, result, ipHeaderLen + UDP_HEADER_SIZE, dnsResponseData.size)

                updatePacketLengths(result, ipHeaderLen)
                recalculateChecksums(result, ipHeaderLen)

                return result
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "DNS forward failed: ${e.message}")
                return null
            }
        }
    }

    private fun calculateIpChecksum(packet: ByteArray, headerLen: Int): Int {
        var sum = 0
        for (i in 0 until headerLen step 2) {
            if (i + 1 < packet.size) {
                val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
                sum += word
            } else if (i < packet.size) {
                sum += (packet[i].toInt() and 0xFF) shl 8
            }
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
        synchronized(channelLock) {
            try {
                dnsChannel?.close()
            } catch (_: Exception) {}
            dnsChannel = null
        }
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    private fun buildNotification(timeText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, PENDING_INTENT_VPN_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, timeText))
            .setSmallIcon(R.drawable.ic_notification)
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
            this, PENDING_INTENT_VPN_END_REQUEST_CODE,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.session_ended))
            .setContentText(getString(R.string.session_ended_body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onRevoke() {
        serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, ServiceEntryPoint::class.java
            )
            if (entryPoint.preferencesManager().isSessionActiveOnce()) {
                delay(VPN_RECONNECT_DELAY_MS)
                try {
                    startVpn()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reconnect VPN after revoke: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
