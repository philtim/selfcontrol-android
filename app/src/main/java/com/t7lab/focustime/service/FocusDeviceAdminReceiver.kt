package com.t7lab.focustime.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.t7lab.focustime.di.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // Device admin enabled
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val isActive = runBlocking {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext, ServiceEntryPoint::class.java
            )
            entryPoint.preferencesManager().isSessionActiveOnce()
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
