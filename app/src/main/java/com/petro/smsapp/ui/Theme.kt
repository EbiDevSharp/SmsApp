package com.petro.smsapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF2F7BFF)
private val BlueDark = Color(0xFF0B5CFF)
private val Background = Color(0xFFF7F8FA)
private val SurfaceLight = Color(0xFFFFFFFF)

private val AppLightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = BlueDark,
    background = Background,
    surface = SurfaceLight,
)

private val AppDarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = BlueDark,
)

@Composable
fun SmsAppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColors else AppLightColors,
        content = content
    )
}
