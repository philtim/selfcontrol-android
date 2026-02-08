package com.t7lab.focustime.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.t7lab.focustime.MainActivity
import com.t7lab.focustime.R
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.data.db.FocusTimeDatabase
import com.t7lab.focustime.data.preferences.PreferencesManager
import com.t7lab.focustime.util.formatDuration
import com.t7lab.focustime.util.isHostBlocked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

class FocusVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timerJob: Job? = null
    private var blockedUrls: List<BlockedItem> = emptyList()

    companion object {
        const val CHANNEL_ID = "focus_session_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.t7lab.focustime.START_VPN"
        const val ACTION_STOP = "com.t7lab.focustime.STOP_VPN"

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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        val db = androidx.room.Room.databaseBuilder(
            applicationContext,
            FocusTimeDatabase::class.java,
            "focustime.db"
        ).build()
        blockedUrls = db.sessionDao().getActiveBlockedItems()
            .filter { it.type == BlockedItemType.URL }
        db.close()
    }

    private fun startVpn() {
        if (blockedUrls.isEmpty()) return

        val builder = Builder()
            .setSession("FocusTime")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            .addRoute("10.0.0.1", 32) // Only route DNS through VPN

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
        val buffer = ByteBuffer.allocate(32767)

        while (serviceScope.isActive) {
            try {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length <= 0) {
                    delay(10)
                    continue
                }
                buffer.limit(length)

                // Parse IP packet to find DNS queries
                val packet = buffer.array().copyOf(length)
                val dnsResponse = processDnsPacket(packet)
                if (dnsResponse != null) {
                    outputStream.write(dnsResponse)
                    outputStream.flush()
                }
            } catch (e: Exception) {
                if (!serviceScope.isActive) break
                delay(100)
            }
        }
    }

    private fun processDnsPacket(packet: ByteArray): ByteArray? {
        // Minimum IP header (20) + UDP header (8) + DNS header (12)
        if (packet.size < 40) return null

        // Check if IPv4
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return null

        val ipHeaderLen = (packet[0].toInt() and 0xF) * 4
        if (packet.size < ipHeaderLen + 8) return null

        // Check if UDP (protocol 17)
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null

        // Check if destination port is 53 (DNS)
        val destPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 1].toInt() and 0xFF)
        if (destPort != 53) return null

        // Parse DNS query
        val dnsOffset = ipHeaderLen + 8
        if (packet.size < dnsOffset + 12) return null

        val hostname = parseDnsHostname(packet, dnsOffset + 12)
        if (hostname != null && isHostBlocked(hostname, blockedUrls)) {
            return buildDnsBlockResponse(packet, ipHeaderLen, dnsOffset)
        }

        // Forward non-blocked queries to real DNS
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

        // Set DNS response flags (QR=1, RCODE=0 - no error, but answer with 0.0.0.0)
        response[dnsOffset + 2] = 0x81.toByte() // QR=1, RD=1
        response[dnsOffset + 3] = 0x80.toByte() // RA=1

        // Set answer count to 1
        response[dnsOffset + 6] = 0
        response[dnsOffset + 7] = 1

        // Build answer section: pointer to query name + A record pointing to 0.0.0.0
        val queryEnd = findQueryEnd(response, dnsOffset + 12)
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, // Pointer to name in query
            0, 1,                // Type A
            0, 1,                // Class IN
            0, 0, 0, 30,        // TTL 30 seconds
            0, 4,                // Data length
            0, 0, 0, 0          // 0.0.0.0
        )

        val result = ByteArray(queryEnd + answer.size)
        System.arraycopy(response, 0, result, 0, queryEnd)
        System.arraycopy(answer, 0, result, queryEnd, answer.size)

        // Update IP total length
        val totalLen = result.size
        result[2] = (totalLen shr 8).toByte()
        result[3] = (totalLen and 0xFF).toByte()

        // Update UDP length
        val udpLen = totalLen - ipHeaderLen
        result[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        result[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()

        // Zero out checksums (optional for UDP over IPv4)
        result[10] = 0; result[11] = 0 // IP checksum
        result[ipHeaderLen + 6] = 0; result[ipHeaderLen + 7] = 0 // UDP checksum

        // Recalculate IP checksum
        val checksum = calculateIpChecksum(result, ipHeaderLen)
        result[10] = (checksum shr 8).toByte()
        result[11] = (checksum and 0xFF).toByte()

        return result
    }

    private fun findQueryEnd(packet: ByteArray, offset: Int): Int {
        var pos = offset
        // Skip name
        while (pos < packet.size && packet[pos].toInt() != 0) {
            pos += (packet[pos].toInt() and 0xFF) + 1
        }
        pos += 1 // null terminator
        pos += 4 // type + class
        return pos
    }

    private fun forwardDnsQuery(packet: ByteArray, ipHeaderLen: Int, dnsOffset: Int): ByteArray? {
        try {
            val dnsData = packet.copyOfRange(dnsOffset, packet.size)
            val socket = java.net.DatagramSocket()
            protect(socket) // Prevent VPN loop

            val dnsServer = InetAddress.getByName("8.8.8.8")
            val outPacket = java.net.DatagramPacket(dnsData, dnsData.size, dnsServer, 53)
            socket.soTimeout = 5000
            socket.send(outPacket)

            val responseBuffer = ByteArray(1024)
            val inPacket = java.net.DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(inPacket)
            socket.close()

            // Build IP+UDP response packet
            val response = packet.copyOf()
            // Swap src/dst IP
            for (i in 0 until 4) {
                val temp = response[12 + i]
                response[12 + i] = response[16 + i]
                response[16 + i] = temp
            }
            // Swap ports
            val srcPort0 = response[ipHeaderLen]
            val srcPort1 = response[ipHeaderLen + 1]
            response[ipHeaderLen] = response[ipHeaderLen + 2]
            response[ipHeaderLen + 1] = response[ipHeaderLen + 3]
            response[ipHeaderLen + 2] = srcPort0
            response[ipHeaderLen + 3] = srcPort1

            val dnsResponseData = inPacket.data.copyOf(inPacket.length)
            val result = ByteArray(ipHeaderLen + 8 + dnsResponseData.size)
            System.arraycopy(response, 0, result, 0, ipHeaderLen + 8)
            System.arraycopy(dnsResponseData, 0, result, ipHeaderLen + 8, dnsResponseData.size)

            // Update lengths
            val totalLen = result.size
            result[2] = (totalLen shr 8).toByte()
            result[3] = (totalLen and 0xFF).toByte()
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
            val prefs = PreferencesManager(applicationContext)
            while (isActive) {
                val endTime = prefs.getSessionEndTimeOnce()
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    // Session ended
                    prefs.endSession()
                    showSessionEndedNotification()
                    stopVpn()
                    stopSelf()
                    break
                }
                updateNotification(formatDuration(remaining))
                delay(1000)
            }
        }
    }

    private fun stopVpn() {
        timerJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.focus_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.focus_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(timeText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
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
            this,
            0,
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
        // VPN was revoked by system - try to restart
        serviceScope.launch {
            val prefs = PreferencesManager(applicationContext)
            if (prefs.isSessionActiveOnce()) {
                delay(1000)
                startVpn()
            }
        }
    }

    override fun onDestroy() {
        stopVpn()
        serviceScope.cancel()
        super.onDestroy()
    }
}
