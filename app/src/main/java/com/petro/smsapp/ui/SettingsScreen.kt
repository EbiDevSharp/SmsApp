package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Divider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.data.ClockFormat

/**
 * صفحه تنظیمات. هر گزینه‌ی جدید (زبان، تم، اعلان‌ها و ...) به همین لیست اضافه میشه.
 *
 * مقادیر فعلی از AppSettings.state خونده میشن (که همون مقداریه که DateFormatter و
 * SmsRepository هم ازش استفاده می‌کنن)، پس تغییر هر کدوم اینجا فوراً روی کل برنامه اثر می‌ذاره.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()

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
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("زبان برنامه") },
                supportingContent = { Text("فارسی (به‌زودی: انگلیسی هم اضافه میشه)") }
            )
            Divider()

            // سطل زباله: اگه فعال باشه، پیام‌های حذف‌شده به‌جای حذف کامل میرن سطل زباله
            ListItem(
                headlineContent = { Text("سطل زباله") },
                supportingContent = { Text("پیام‌های حذف‌شده به‌جای حذف کامل، اول بیان اینجا") },
                trailingContent = {
                    Switch(
                        checked = settings.trashEnabled,
                        onCheckedChange = { enabled -> AppSettings.setTrashEnabled(context, enabled) }
                    )
                }
            )
            Divider()

            // نوع تقویم برای نمایش تاریخ توی کل برنامه
            ListItem(
                headlineContent = { Text("نمایش تاریخ") },
                supportingContent = { Text("تاریخ‌های داخل برنامه بر همین اساس نشون داده میشن") }
            )
            CalendarOptionRow(
                label = "میلادی",
                selected = settings.calendarType == CalendarType.GREGORIAN,
                onSelect = { AppSettings.setCalendarType(context, CalendarType.GREGORIAN) }
            )
            CalendarOptionRow(
                label = "شمسی",
                selected = settings.calendarType == CalendarType.JALALI,
                onSelect = { AppSettings.setCalendarType(context, CalendarType.JALALI) }
            )
            Divider()

            // فرمت نمایش ساعت برای کل برنامه
            ListItem(
                headlineContent = { Text("نمایش ساعت") },
                supportingContent = { Text("ساعت‌های داخل برنامه بر همین اساس نشون داده میشن") }
            )
            CalendarOptionRow(
                label = "۲۴ ساعته",
                selected = settings.clockFormat == ClockFormat.H24,
                onSelect = { AppSettings.setClockFormat(context, ClockFormat.H24) }
            )
            CalendarOptionRow(
                label = "۱۲ ساعته",
                selected = settings.clockFormat == ClockFormat.H12,
                onSelect = { AppSettings.setClockFormat(context, ClockFormat.H12) }
            )
            Divider()

            // نوتیف جدا برای دلیوری هر پیام - پیش‌فرض خاموش چون برای ارسال چندتا پیام
            // پشت‌سرهم می‌تونه اسپم بشه؛ تیک دلیوری زیر خود پیام همیشه هست
            ListItem(
                headlineContent = { Text("اعلان دلیوری پیام‌ها") },
                supportingContent = { Text("وقتی پیامت به گیرنده رسید، یه نوتیف جدا هم نشون بده") },
                trailingContent = {
                    Switch(
                        checked = settings.deliveryNotificationsEnabled,
                        onCheckedChange = { enabled -> AppSettings.setDeliveryNotificationsEnabled(context, enabled) }
                    )
                }
            )
            Divider()
        }
    }
}

/** یه ردیف رادیویی ساده برای انتخاب بین گزینه‌های تقویم/ساعت */
@Composable
private fun CalendarOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
