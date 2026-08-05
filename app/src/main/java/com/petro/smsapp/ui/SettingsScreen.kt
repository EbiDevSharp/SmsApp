package com.petro.smsapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.data.ClockFormat
import com.petro.smsapp.data.SwipeAction
import com.petro.smsapp.data.ThemeMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * صفحه تنظیمات. هر گزینه‌ی جدید (زبان، تم، اعلان‌ها و ...) به همین لیست اضافه میشه.
 *
 * مقادیر فعلی از AppSettings.state خونده میشن (که همون مقداریه که DateFormatter و
 * SmsRepository هم ازش استفاده می‌کنن)، پس تغییر هر کدوم اینجا فوراً روی کل برنامه اثر می‌ذاره.
 */
@Composable
fun SettingsScreen(onOpenNotificationActions: () -> Unit, onMenuClick: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()

    var showSwipeRightToLeftDialog by remember { mutableStateOf(false) }
    var showSwipeLeftToRightDialog by remember { mutableStateOf(false) }

    if (showSwipeRightToLeftDialog) {
        SwipeActionPickerDialog(
            title = "عملیاتِ سویپِ راست‌به‌چپ",
            current = settings.swipeRightToLeftAction,
            onSelect = { action -> AppSettings.setSwipeRightToLeftAction(context, action) },
            onDismiss = { showSwipeRightToLeftDialog = false }
        )
    }
    if (showSwipeLeftToRightDialog) {
        SwipeActionPickerDialog(
            title = "عملیاتِ سویپِ چپ‌به‌راست",
            current = settings.swipeLeftToRightAction,
            onSelect = { action -> AppSettings.setSwipeLeftToRightAction(context, action) },
            onDismiss = { showSwipeLeftToRightDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("زبان برنامه") },
                supportingContent = { Text("فارسی (به‌زودی: انگلیسی هم اضافه میشه)") }
            )
            Divider()
            ListItem(
                headlineContent = { Text("تم برنامه") },
                supportingContent = { Text("بر اساس تنظیمات گوشی یا دستی") }
            )
            CalendarOptionRow(
                label = "سیستم",
                selected = settings.themeMode == ThemeMode.SYSTEM,
                onSelect = { AppSettings.setThemeMode(context, ThemeMode.SYSTEM) }
            )
            CalendarOptionRow(
                label = "روشن",
                selected = settings.themeMode == ThemeMode.LIGHT,
                onSelect = { AppSettings.setThemeMode(context, ThemeMode.LIGHT) }
            )
            CalendarOptionRow(
                label = "تاریک",
                selected = settings.themeMode == ThemeMode.DARK,
                onSelect = { AppSettings.setThemeMode(context, ThemeMode.DARK) }
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

            // امکان ارسال گروهی + ذخیره‌سازیِ گروه‌های پیامکی توی صفحه‌ی «پیام جدید»
            ListItem(
                headlineContent = { Text("گروه‌های پیامکی") },
                supportingContent = {
                    Text("توی «پیام جدید» بشه چند مخاطبِ انتخاب‌شده رو به‌عنوان یه گروه ذخیره کرد و بعداً دوباره براشون فرستاد")
                },
                trailingContent = {
                    Switch(
                        checked = settings.groupMessagingEnabled,
                        onCheckedChange = { enabled -> AppSettings.setGroupMessagingEnabled(context, enabled) }
                    )
                }
            )
            Divider()

            // نمایشِ شماره‌ی مخاطبینِ ذخیره‌شده زیرِ اسمشون توی لیستِ اصلیِ مکالمات
            ListItem(
                headlineContent = { Text("نمایش شماره‌ی مخاطب در لیست چت‌ها") },
                supportingContent = {
                    Text("زیرِ اسمِ مخاطبینِ ذخیره‌شده، توی لیستِ اصلیِ مکالمات، شماره‌شون هم با فونتِ کوچیک‌تر نشون داده بشه")
                },
                trailingContent = {
                    Switch(
                        checked = settings.showContactNumberInListEnabled,
                        onCheckedChange = { enabled -> AppSettings.setShowContactNumberInListEnabled(context, enabled) }
                    )
                }
            )
            Divider()

            // نوارِ کناریِ پرشِ سریعِ الفبا (Alphabet Index Bar) روی لیستِ اصلیِ مکالمات
            ListItem(
                headlineContent = { Text("نوار حروف الفبا در لیست چت‌ها") },
                supportingContent = {
                    Text("یه نوار کناری سمتِ چپِ صفحه برای پرشِ سریع به مخاطبین بر اساسِ حرفِ اولِ اسمشون")
                },
                trailingContent = {
                    Switch(
                        checked = settings.alphabetIndexBarEnabled,
                        onCheckedChange = { enabled -> AppSettings.setAlphabetIndexBarEnabled(context, enabled) }
                    )
                }
            )
            Divider()

            // سویپِ ردیف‌های لیستِ مکالمات - جهتِ راست‌به‌چپ و چپ‌به‌راست هرکدوم جدا قابل‌تنظیمن
            ListItem(
                headlineContent = { Text("سویپِ لیستِ مکالمات") },
                supportingContent = { Text("با کشیدنِ هر ردیفِ لیستِ اصلی، عملیاتِ زیر اجرا میشه") }
            )
            ListItem(
                headlineContent = { Text("سویپِ راست‌به‌چپ") },
                supportingContent = { Text(settings.swipeRightToLeftAction.label) },
                modifier = Modifier.clickable { showSwipeRightToLeftDialog = true }
            )
            ListItem(
                headlineContent = { Text("سویپِ چپ‌به‌راست") },
                supportingContent = { Text(settings.swipeLeftToRightAction.label) },
                modifier = Modifier.clickable { showSwipeLeftToRightDialog = true }
            )
            ListItem(
                headlineContent = { Text("تأییدِ حذف با سویپ") },
                supportingContent = { Text("قبل از حذفِ واقعی (وقتی یکی از دو جهتِ بالا روی «حذف» باشه) یه دیالوگِ تأیید نشون بده") },
                trailingContent = {
                    Switch(
                        checked = settings.swipeDeleteRequiresConfirmation,
                        onCheckedChange = { enabled -> AppSettings.setSwipeDeleteRequiresConfirmation(context, enabled) }
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

            // دکمه‌های نوتیف پیامک (خوانده‌شد/حذف/پاسخ‌سریع/بلاک/تماس) - ترتیب و روشن/خاموش
            // بودنشون از یه صفحه‌ی جدا قابل تنظیمه
            ListItem(
                headlineContent = { Text("دکمه‌های نوتیفیکیشن") },
                supportingContent = { Text("انتخاب و ترتیب دکمه‌های روی نوتیف پیامک") },
                modifier = Modifier.clickable(onClick = onOpenNotificationActions)
            )
            Divider()

            // پیامکِ تازه‌رسیده به‌جای نوتیفِ معمولی، با یه پاپ‌آپِ روی صفحه (شبیهِ
            // نوتیفِ تماسِ ورودی) نشون داده بشه - از همون دکمه‌های بالا استفاده می‌کنه
            ListItem(
                headlineContent = { Text("پاپ‌آپِ پیامک روی صفحه") },
                supportingContent = {
                    Text("به‌جای نوتیفِ معمولی، پیامکِ تازه‌رسیده یه پاپ‌آپ روی صفحه (حتی صفحه‌قفل) نشون بده - دکمه‌هاش همون دکمه‌های نوتیفیکیشن بالان")
                },
                trailingContent = {
                    Switch(
                        checked = settings.popupInsteadOfNotificationEnabled,
                        onCheckedChange = { enabled -> AppSettings.setPopupInsteadOfNotificationEnabled(context, enabled) }
                    )
                }
            )
            Divider()

            // حداکثر تعداد مکالمه‌ای که میشه هم‌زمان توی لیست اصلی پین کرد (پین‌کردنِ خودِ
            // مکالمه از منوی «انتخاب چندتایی» توی لیست اصلی انجام میشه، اینجا فقط سقفشه)
            ListItem(
                headlineContent = { Text("حداکثر تعداد پین در لیست اصلی") },
                supportingContent = { Text("حداکثر چند مکالمه هم‌زمان می‌تونه بالای لیست پیام‌ها پین بشه") },
                trailingContent = {
                    PinCountStepper(
                        value = settings.maxPinnedConversations,
                        onValueChange = { newValue -> AppSettings.setMaxPinnedConversations(context, newValue) }
                    )
                }
            )
            Divider()
        }
    }
}

/** دیالوگِ رادیویی برای انتخاب اینکه یه جهتِ سویپ خاص، کدوم عملیات رو اجرا کنه */
@Composable
private fun SwipeActionPickerDialog(
    title: String,
    current: SwipeAction,
    onSelect: (SwipeAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SwipeAction.entries.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = current == action,
                                onClick = {
                                    onSelect(action)
                                    onDismiss()
                                }
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = current == action,
                            onClick = {
                                onSelect(action)
                                onDismiss()
                            }
                        )
                        Text(action.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

/** استپرِ ساده‌ی +/- برای تنظیمِ سقفِ تعداد پین - بین ۱ تا ۲۰ */
@Composable
private fun PinCountStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (value > 1) onValueChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "کم کردن")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = { if (value < 20) onValueChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "زیاد کردن")
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
