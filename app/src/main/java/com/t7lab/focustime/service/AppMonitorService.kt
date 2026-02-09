package com.t7lab.focustime.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.t7lab.focustime.MainActivity
import com.t7lab.focustime.R
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.di.ServiceEntryPoint
import com.t7lab.focustime.ui.BlockedOverlayActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var blockedPackages: Set<String> = emptySet()

    companion object {
        const val NOTIFICATION_ID = 2
        const val ACTION_START = "com.t7lab.focustime.START_MONITOR"
        const val ACTION_STOP = "com.t7lab.focustime.STOP_MONITOR"
        private const val FOREGROUND_APP_POLL_INTERVAL_MS = 500L

        fun createStartIntent(context: Context): Intent {
            return Intent(context, AppMonitorService::class.java).apply {
                action = ACTION_START
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, AppMonitorService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                serviceScope.launch {
                    loadBlockedApps()
                    startMonitoring()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun loadBlockedApps() {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, ServiceEntryPoint::class.java
        )
        blockedPackages = entryPoint.sessionDao().getActiveBlockedItems()
            .filter { it.type == BlockedItemType.APP }
            .map { it.value }
            .toSet()
    }

    private fun startMonitoring() {
        if (blockedPackages.isEmpty()) {
            stopSelf()
            return
        }

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, ServiceEntryPoint::class.java
            )
            val prefs = entryPoint.preferencesManager()
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            while (isActive) {
                val endTime = prefs.getSessionEndTimeOnce()
                if (endTime > 0 && System.currentTimeMillis() >= endTime) {
                    prefs.endSession()
                    stopSelf()
                    break
                }

                val foregroundPackage = getForegroundPackage(usageStatsManager)
                if (foregroundPackage != null && foregroundPackage in blockedPackages) {
                    launchBlockedOverlay(foregroundPackage)
                }

                delay(FOREGROUND_APP_POLL_INTERVAL_MS)
            }
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager): String? {
        val now = System.currentTimeMillis()
        val usageEvents = usageStatsManager.queryEvents(now - 5000, now)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null
        var lastForegroundTime = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp >= lastForegroundTime) {
                    lastForegroundTime = event.timeStamp
                    lastForegroundPackage = event.packageName
                }
            }
        }

        return lastForegroundPackage
    }

    private fun launchBlockedOverlay(packageName: String) {
        val intent = Intent(this, BlockedOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
        }
        startActivity(intent)
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, FocusVpnService.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_monitoring_apps))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
}
