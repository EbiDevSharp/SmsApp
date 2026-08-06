package com.petro.smsapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ThemeMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue



private val Blue = Color(0xFF2F7BFF)
private val BlueDark = Color(0xFF0B5CFF)

private val AppLightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,

    secondary = BlueDark,
    onSecondary = Color.White,

    background = Color(0xFFF7F8FA),
    onBackground = Color.Black,

    surface = Color.White,
    onSurface = Color.Black,

    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = Color(0xFF424242),

    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color.Black,

    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color.Black,

    error = Color(0xFFB3261E),
    onError = Color.White
)

private val AppDarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,

    secondary = BlueDark,
    onSecondary = Color.White,

    background = Color(0xFF121212),
    onBackground = Color.White,

    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,

    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color.White,

    primaryContainer = Color(0xFF1D3F73),
    onPrimaryContainer = Color.White,

    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color.White,

    error = Color(0xFFFFB4AB),
    onError = Color.Black
)

@Composable
fun SmsAppTheme(
    content: @Composable () -> Unit
) {
    val settings by AppSettings.state.collectAsState()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColors else AppLightColors,
        content = content
    )
}