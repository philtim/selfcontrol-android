package com.t7lab.focustime

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.t7lab.focustime.service.FocusVpnService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FocusTimeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            FocusVpnService.CHANNEL_ID,
            getString(R.string.focus_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.focus_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
