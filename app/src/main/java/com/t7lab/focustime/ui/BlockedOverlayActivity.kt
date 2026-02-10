package com.t7lab.focustime.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.t7lab.focustime.data.preferences.PreferencesManager
import com.t7lab.focustime.service.SessionManager
import com.t7lab.focustime.ui.components.FocusTimerRing
import com.t7lab.focustime.ui.theme.FocusTimeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BlockedOverlayActivity : ComponentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var sessionManager: SessionManager

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val blockedAppName = blockedPackage?.let { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

        setContent {
            FocusTimeTheme(isSessionActive = true) {
                BlockedScreen(
                    preferencesManager = preferencesManager,
                    sessionManager = sessionManager,
                    blockedAppName = blockedAppName,
                    onGoBack = { goHome() },
                    onUnlocked = { finish() }
                )
            }
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

}

@Composable
private fun BlockedScreen(
    preferencesManager: PreferencesManager,
    sessionManager: SessionManager,
    blockedAppName: String?,
    onGoBack: () -> Unit,
    onUnlocked: () -> Unit
) {
    var remainingTimeMs by remember { mutableLongStateOf(0L) }
    var endTimeMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var password by remember { mutableStateOf("") }
    var showPasswordField by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var hasPassword by remember { mutableStateOf(false) }
    var unlockDelayComplete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        hasPassword = preferencesManager.hasPasswordSet()
        var initialized = false
        while (isActive) {
            val endTime = preferencesManager.getSessionEndTimeOnce()
            val remaining = endTime - System.currentTimeMillis()
            if (remaining <= 0) {
                onUnlocked()
                break
            }
            remainingTimeMs = remaining
            endTimeMs = endTime

            if (!initialized) {
                totalDurationMs = remaining
                initialized = true
            }

            delay(1000)
        }
    }

    // 5-second delay before showing password field
    LaunchedEffect(showPasswordField) {
        if (showPasswordField) {
            unlockDelayComplete = false
            delay(5000)
            unlockDelayComplete = true
        }
    }

    // Dark scrim background
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Scrim
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.85f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // (1) "[AppName] is blocked"
            Text(
                text = if (blockedAppName != null) {
                    "$blockedAppName is blocked"
                } else {
                    "This app is blocked"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // (2) Circular timer ring (shared composable)
            FocusTimerRing(
                remainingTimeMs = remainingTimeMs,
                endTimeMs = endTimeMs,
                durationMs = totalDurationMs,
                ringSize = 220.dp,
                strokeWidth = 10.dp,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // (3) "Return Home" button
            Button(
                onClick = onGoBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                )
            ) {
                Text("Return Home")
            }

            // Emergency override — subtle, delayed
            if (hasPassword) {
                Spacer(modifier = Modifier.height(24.dp))

                if (!showPasswordField) {
                    TextButton(
                        onClick = { showPasswordField = true }
                    ) {
                        Text(
                            text = "Emergency override",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showPasswordField,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!unlockDelayComplete) {
                            Text(
                                text = "Please wait...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        } else {
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    passwordError = null
                                },
                                label = { Text("Master Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = passwordError != null,
                                supportingText = passwordError?.let { err -> { Text(err) } },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (preferencesManager.verifyPassword(password)) {
                                            sessionManager.endSession()
                                            onUnlocked()
                                        } else {
                                            passwordError = "Incorrect password"
                                        }
                                    }
                                },
                                enabled = password.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White.copy(alpha = 0.7f)
                                )
                            ) {
                                Text("Unlock")
                            }
                        }
                    }
                }
            }
        }
    }
}
