package com.t7lab.focustime.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.t7lab.focustime.data.preferences.PreferencesManager
import kotlinx.coroutines.runBlocking

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // Device admin enabled
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // Check if session is active - warn user
        val isActive = runBlocking {
            PreferencesManager(context).isSessionActiveOnce()
        }
        return if (isActive) {
            "FocusTime has an active session. Disabling device admin will not end the session."
        } else {
            null
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Device admin disabled
    }
}
