package com.t7lab.focustime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

// Session-specific color schemes — branded green, no dynamic color
private val SessionLightColorScheme = lightColorScheme(
    primary = FocusGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0C8),
    onPrimaryContainer = FocusGreenDark,
    secondary = Color(0xFF4E6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D5),
    onSecondaryContainer = Color(0xFF0C1F13),
    background = Color(0xFFF0F7F1),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFF0F7F1),
    onSurface = Color(0xFF1A1C19),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val SessionDarkColorScheme = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = FocusGreenDark,
    primaryContainer = FocusGreen,
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFB5CCB9),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD0E8D5),
    background = Color(0xFF0F1A12),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF0F1A12),
    onSurface = Color(0xFFE2E3DD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

data class SessionColors(
    val timerRingFill: Color,
    val timerRingTrack: Color,
    val timerText: Color,
    val quoteText: Color,
    val ambientGlow: Color,
)

val LightSessionColors = SessionColors(
    timerRingFill = Color(0xFF1B6D3D),
    timerRingTrack = Color(0xFFE0E8E2),
    timerText = Color(0xFF0D3B1F),
    quoteText = Color(0xFF4E6354),
    ambientGlow = Color(0xFFB8F0C8),
)

val DarkSessionColors = SessionColors(
    timerRingFill = Color(0xFF66BB6A),
    timerRingTrack = Color(0xFF1B3A22),
    timerText = Color(0xFFC8E6C9),
    quoteText = Color(0xFFA5B4AB),
    ambientGlow = Color(0xFF1B6D3D),
)

val LocalSessionColors = staticCompositionLocalOf { LightSessionColors }

@Composable
fun FocusTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isSessionActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme
    val sessionColors: SessionColors

    if (isSessionActive) {
        colorScheme = if (darkTheme) SessionDarkColorScheme else SessionLightColorScheme
        sessionColors = if (darkTheme) DarkSessionColors else LightSessionColors
    } else {
        colorScheme = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
        sessionColors = if (darkTheme) DarkSessionColors else LightSessionColors
    }

    CompositionLocalProvider(LocalSessionColors provides sessionColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FocusTimeTypography,
            content = content
        )
    }
}
