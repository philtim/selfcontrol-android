package com.t7lab.focustime.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.t7lab.focustime.di.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val PREFS_READ_TIMEOUT_MS = 2000L
    }

    override fun onEnabled(context: Context, intent: Intent) {
        // Device admin enabled
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // This runs on the main thread; use a short timeout to avoid ANR.
        val isActive = runBlocking {
            withTimeoutOrNull(PREFS_READ_TIMEOUT_MS) {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext, ServiceEntryPoint::class.java
                )
                entryPoint.preferencesManager().isSessionActiveOnce()
            } ?: true // Default to true (show warning) if timeout occurs
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
