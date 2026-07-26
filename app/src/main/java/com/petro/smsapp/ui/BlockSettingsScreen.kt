package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * صفحه‌ی «تنظیماتِ بلاک» - دو گزینه‌ی مستقل از هم، هرکدوم پیش‌فرض خاموش (رفتار همیشگیِ
 * قبلیِ بخش بلاک: بی‌صدا و مخفی):
 *
 * ۱) نمایش نوتیف پیام‌های بلاک‌شده: اگه فعال بشه، پیام‌هایی که به‌خاطر بلاک‌بودنِ شماره،
 *    یه کلمه‌ی کلیدی، یا یه الگوی شماره بلاک شدن، به‌جای کاملاً بی‌صدا موندن، نوتیف/صدای
 *    عادی هم میدن (ولی همچنان از لیست اصلیِ مکالمات مخفی می‌مونن، مگر گزینه‌ی دوم هم فعال باشه).
 * ۲) نمایش توی لیست پیام‌ها: اگه فعال بشه، خودِ این پیام‌ها و شماره‌های بلاک‌شده، به‌جای مخفی
 *    بودن، توی لیست اصلیِ مکالمات و داخل چت هم عادی (کنارِ بقیه‌ی پیام‌ها) نشون داده میشن.
 *
 * توی هر دو حالت، پیام‌ها همچنان توی صفحه‌ی «پیامک‌های بلاک‌شده» هم قابل دیدنن؛ این دو گزینه
 * فقط رفتار «مخفی/بی‌صدا بودنِ پیش‌فرض» رو کنترل می‌کنن، نه خودِ منطق بلاک‌شدن رو.
 */
@Composable
fun BlockSettingsScreen(
    showBlockedNotificationsEnabled: Boolean,
    showBlockedInMessageListEnabled: Boolean,
    onBack: () -> Unit,
    onShowBlockedNotificationsChange: (Boolean) -> Unit,
    onShowBlockedInMessageListChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات بلاک") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("نمایش نوتیف پیام‌های بلاک‌شده") },
                supportingContent = { Text("وقتی پیامی به‌خاطر بلاک‌بودنِ شماره، کلمه‌ی کلیدی، یا الگوی شماره بلاک میشه، همچنان نوتیف و صدا هم بده") },
                trailingContent = {
                    Switch(
                        checked = showBlockedNotificationsEnabled,
                        onCheckedChange = onShowBlockedNotificationsChange
                    )
                }
            )
            Divider()

            ListItem(
                headlineContent = { Text("نمایش در لیست پیام‌ها") },
                supportingContent = { Text("این پیام‌ها و شماره‌های بلاک‌شده، توی لیست اصلیِ مکالمات و داخل چت هم نشون داده بشن") },
                trailingContent = {
                    Switch(
                        checked = showBlockedInMessageListEnabled,
                        onCheckedChange = onShowBlockedInMessageListChange
                    )
                }
            )
            Divider()
        }
    }
}
