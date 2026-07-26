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
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

private val AppDarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = BlueDark,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White
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