package com.petro.smsapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * صفحه تنظیمات. فعلاً چند گزینه‌ی نمایشی داره؛ هر گزینه‌ی جدید (زبان، تم، اعلان‌ها و ...)
 * به همین لیست اضافه میشه.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("زبان برنامه") },
                supportingContent = { Text("فارسی (به‌زودی: انگلیسی هم اضافه میشه)") }
            )
            Divider()
            ListItem(
                headlineContent = { Text("نمایش تاریخ") },
                supportingContent = { Text("میلادی (به‌زودی: شمسی)") }
            )
            Divider()
            ListItem(
                headlineContent = { Text("اعلان‌ها") },
                supportingContent = { Text("فعال") }
            )
            Divider()
        }
    }
}
