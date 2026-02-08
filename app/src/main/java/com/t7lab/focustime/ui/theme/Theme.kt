package com.t7lab.focustime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val FocusGreen = Color(0xFF1B6D3D)
private val FocusGreenLight = Color(0xFF4CAF50)
private val FocusGreenDark = Color(0xFF0D3B1F)

private val LightColorScheme = lightColorScheme(
    primary = FocusGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0C8),
    onPrimaryContainer = FocusGreenDark,
    secondary = Color(0xFF4E6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D5),
    onSecondaryContainer = Color(0xFF0C1F13),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color.White,
    background = Color(0xFFFCFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFCFDF7),
    onSurface = Color(0xFF1A1C19),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = FocusGreenLight,
    onPrimary = FocusGreenDark,
    primaryContainer = FocusGreen,
    onPrimaryContainer = Color(0xFFB8F0C8),
    secondary = Color(0xFFB5CCB9),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD0E8D5),
    tertiary = Color(0xFFA2CED9),
    onTertiary = Color(0xFF01363F),
    background = Color(0xFF1A1C19),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFE2E3DD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun FocusTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color (Material You) available on Android 12+
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusTimeTypography,
        content = content
    )
}
