package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * قالب مشترک برای بخش‌هایی که هنوز پیاده‌سازی نشدن (علاقه‌مندی‌ها، سطل زباله، زمان‌بندی‌شده، بلاک، خصوصی).
 * وقتی هرکدوم رو واقعی پیاده‌سازی کردیم، به‌جای صدا زدن این تابع، یه فایل/Composable مخصوص خودش می‌سازیم.
 */
@Composable
fun PlaceholderScreen(title: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("این بخش به‌زودی اضافه میشه", color = Color.Gray)
        }
    }
}
