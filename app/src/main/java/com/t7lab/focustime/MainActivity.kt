package com.t7lab.focustime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.t7lab.focustime.data.preferences.PreferencesManager
import com.t7lab.focustime.ui.apppicker.AppPickerScreen
import com.t7lab.focustime.ui.home.HomeScreen
import com.t7lab.focustime.ui.navigation.Routes
import com.t7lab.focustime.ui.onboarding.OnboardingScreen
import com.t7lab.focustime.ui.settings.SettingsScreen
import com.t7lab.focustime.ui.theme.FocusTimeTheme
import com.t7lab.focustime.ui.urlmanager.UrlManagerScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* VPN permission result handled */ }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Notification permission result handled */ }

    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var onboardingComplete by mutableStateOf<Boolean?>(null)

    companion object {
        private const val NAV_ANIM_DURATION_MS = 300
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load onboarding status asynchronously
        activityScope.launch {
            onboardingComplete = preferencesManager.isOnboardingComplete()
        }

        setContent {
            FocusTimeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isComplete = onboardingComplete ?: return@Surface
                    val navController = rememberNavController()
                    val startDest = if (isComplete) Routes.HOME else Routes.ONBOARDING

                    NavHost(
                        navController = navController,
                        startDestination = startDest,
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(NAV_ANIM_DURATION_MS)
                            ) + fadeIn(tween(NAV_ANIM_DURATION_MS))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(NAV_ANIM_DURATION_MS)
                            ) + fadeOut(tween(NAV_ANIM_DURATION_MS))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(NAV_ANIM_DURATION_MS)
                            ) + fadeIn(tween(NAV_ANIM_DURATION_MS))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(NAV_ANIM_DURATION_MS)
                            ) + fadeOut(tween(NAV_ANIM_DURATION_MS))
                        }
                    ) {
                        composable(
                            Routes.ONBOARDING,
                            enterTransition = { fadeIn(tween(NAV_ANIM_DURATION_MS)) },
                            exitTransition = { fadeOut(tween(NAV_ANIM_DURATION_MS)) }
                        ) {
                            OnboardingScreen(
                                onComplete = {
                                    activityScope.launch {
                                        preferencesManager.completeOnboarding()
                                        onboardingComplete = true
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable(Routes.HOME) {
                            // Request permissions contextually on first arriving at home
                            LaunchedEffect(Unit) {
                                requestContextualPermissions()
                            }
                            HomeScreen(
                                onNavigateToAppPicker = {
                                    navController.navigate(Routes.APP_PICKER)
                                },
                                onNavigateToUrlManager = {
                                    navController.navigate(Routes.URL_MANAGER)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Routes.SETTINGS)
                                },
                                onVpnPermissionNeeded = {
                                    requestVpnPermission()
                                },
                                onUsageStatsPermissionNeeded = {
                                    requestUsageStatsPermission()
                                },
                                onOverlayPermissionNeeded = {
                                    requestOverlayPermission()
                                }
                            )
                        }
                        composable(Routes.APP_PICKER) {
                            AppPickerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.URL_MANAGER) {
                            UrlManagerScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestContextualPermissions() {
        // Only request notification permission on first visit — less intrusive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
}
