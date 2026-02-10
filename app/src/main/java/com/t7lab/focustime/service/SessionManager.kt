package com.t7lab.focustime.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.data.repository.BlocklistRepository
import com.t7lab.focustime.data.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val blocklistRepository: BlocklistRepository
) {
    suspend fun startSession(durationMs: Long): StartSessionResult {
        val items = blocklistRepository.getAllItemsList()
        if (items.isEmpty()) return StartSessionResult.NoItemsToBlock

        val itemIds = items.map { it.id }
        val sessionId = sessionRepository.startSession(durationMs, itemIds)

        val hasApps = items.any { it.type == BlockedItemType.APP }
        val hasUrls = items.any { it.type == BlockedItemType.URL }

        // Start app monitor service if there are apps to block
        if (hasApps) {
            val monitorIntent = AppMonitorService.createStartIntent(context)
            context.startForegroundService(monitorIntent)
        }

        // Start VPN if there are URLs to block
        if (hasUrls) {
            val vpnIntent = FocusVpnService.createStartIntent(context)
            context.startForegroundService(vpnIntent)
        }

        return StartSessionResult.Success(sessionId)
    }

    suspend fun endSession() {
        sessionRepository.endSession()

        // Stop services
        context.startService(AppMonitorService.createStopIntent(context))
        context.startService(FocusVpnService.createStopIntent(context))
    }

    fun isVpnPermissionGranted(): Boolean {
        return VpnService.prepare(context) == null
    }

    fun getVpnPermissionIntent(): Intent? {
        return VpnService.prepare(context)
    }

    sealed class StartSessionResult {
        data class Success(val sessionId: Long) : StartSessionResult()
        data object NoItemsToBlock : StartSessionResult()
    }
}
