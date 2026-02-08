package com.t7lab.focustime.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.t7lab.focustime.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = PreferencesManager(context)
                    if (prefs.isSessionActiveOnce()) {
                        val endTime = prefs.getSessionEndTimeOnce()
                        if (endTime > System.currentTimeMillis()) {
                            // Session is still active, restart services
                            restartServices(context)
                        } else {
                            // Session has expired while phone was off
                            prefs.endSession()
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun restartServices(context: Context) {
        // Restart app monitor service
        val monitorIntent = AppMonitorService.createStartIntent(context)
        context.startForegroundService(monitorIntent)

        // VPN requires user interaction to start, but the app monitor
        // will at least block apps. The VPN will reconnect when the user
        // opens the app.
    }
}
