package com.petro.smsapp.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.data.ClockFormat
import com.petro.smsapp.data.SwipeAction
import com.petro.smsapp.data.ThemeMode
import com.petro.smsapp.data.backup.BackupCategory
import com.petro.smsapp.data.backup.BackupModule
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenNotificationActions: () -> Unit,
    onMenuClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()
    val scope = rememberCoroutineScope()

    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showSwipeRightToLeftDialog by remember { mutableStateOf(false) }
    var showSwipeLeftToRightDialog by remember { mutableStateOf(false) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    var expandedAppearance by remember { mutableStateOf(true) }
    var expandedConversations by remember { mutableStateOf(false) }
    var expandedMessaging by remember { mutableStateOf(false) }
    var expandedNotifications by remember { mutableStateOf(false) }
    var expandedGeneral by remember { mutableStateOf(false) }
    var expandedBackup by remember { mutableStateOf(false) }

    var showBackupDialog by remember { mutableStateOf(false) }
    var selectedCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            BackupModule.export(context, selectedCategories, uri)
                .onSuccess {
                    Toast.makeText(context, "بک‌آپ ذخیره شد", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "خطا در ذخیره", Toast.LENGTH_SHORT).show()
                }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            BackupModule.import(context, uri)
                .onSuccess { cats ->
                    val names = cats.joinToString("، ") { it.title }
                    Toast.makeText(context, "بازیابی شد: $names", Toast.LENGTH_LONG).show()
                }
                .onFailure {
                    Toast.makeText(context, "خطا در بازیابی", Toast.LENGTH_SHORT).show()
                }
        }
    }

    if (showSwipeRightToLeftDialog) {
        SwipeActionPickerDialog(
            title = "عملیات سویپ راست‌به‌چپ",
            current = settings.swipeRightToLeftAction,
            onSelect = { AppSettings.setSwipeRightToLeftAction(context, it) },
            onDismiss = { showSwipeRightToLeftDialog = false }
        )
    }
    if (showSwipeLeftToRightDialog) {
        SwipeActionPickerDialog(
            title = "عملیات سویپ چپ‌به‌راست",
            current = settings.swipeLeftToRightAction,
            onSelect = { AppSettings.setSwipeLeftToRightAction(context, it) },
            onDismiss = { showSwipeLeftToRightDialog = false }
        )
    }
    infoDialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { infoDialogText = null },
            title = { Text("توضیحات") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { infoDialogText = null }) { Text("باشه") }
            }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("انتخاب بخش‌ها") },
            text = {
                Column {
                    BackupCategory.entries.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategories = if (cat in selectedCategories)
                                        selectedCategories - cat
                                    else
                                        selectedCategories + cat
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = cat in selectedCategories,
                                onCheckedChange = { checked ->
                                    selectedCategories = if (checked)
                                        selectedCategories + cat
                                    else
                                        selectedCategories - cat
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(cat.title)
                                Text(
                                    cat.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedCategories.isEmpty()) {
                            Toast.makeText(context, "حداقل یک بخش انتخاب کن", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        showBackupDialog = false
                        exportLauncher.launch(BackupModule.suggestedFileName())
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("انصراف") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "منو")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccordionSection(
                title = "ظاهر",
                expanded = expandedConversations,
                onToggle = { expandedConversations = !expandedConversations }
            ) {
                SettingRow(
                    title = "تم برنامه",
                    info = "بر اساس تنظیمات گوشی یا دستی",
                    onInfo = { infoDialogText = it }
                )
                CalendarOptionRow("سیستم", settings.themeMode == ThemeMode.SYSTEM) {
                    AppSettings.setThemeMode(context, ThemeMode.SYSTEM)
                }
                CalendarOptionRow("روشن", settings.themeMode == ThemeMode.LIGHT) {
                    AppSettings.setThemeMode(context, ThemeMode.LIGHT)
                }
                CalendarOptionRow("تاریک", settings.themeMode == ThemeMode.DARK) {
                    AppSettings.setThemeMode(context, ThemeMode.DARK)
                }
                ThinDivider()
                SettingRow(
                    title = "نمایش تاریخ",
                    info = "تاریخ‌های داخل برنامه بر همین اساس نشون داده میشن",
                    onInfo = { infoDialogText = it }
                )
                CalendarOptionRow("میلادی", settings.calendarType == CalendarType.GREGORIAN) {
                    AppSettings.setCalendarType(context, CalendarType.GREGORIAN)
                }
                CalendarOptionRow("شمسی", settings.calendarType == CalendarType.JALALI) {
                    AppSettings.setCalendarType(context, CalendarType.JALALI)
                }
                ThinDivider()
                SettingRow(
                    title = "نمایش ساعت",
                    info = "ساعت‌های داخل برنامه بر همین اساس نشون داده میشن",
                    onInfo = { infoDialogText = it }
                )
                CalendarOptionRow("۲۴ ساعته", settings.clockFormat == ClockFormat.H24) {
                    AppSettings.setClockFormat(context, ClockFormat.H24)
                }
                CalendarOptionRow("۱۲ ساعته", settings.clockFormat == ClockFormat.H12) {
                    AppSettings.setClockFormat(context, ClockFormat.H12)
                }
            }

            AccordionSection(
                title = "لیست مکالمات",
                expanded = expandedConversations,
                onToggle = { expandedConversations = !expandedConversations }
            ) {
                SwitchRow(
                    title = "نمایش شماره مخاطب",
                    info = "زیر اسم مخاطبین ذخیره‌شده، شماره با فونت کوچیک‌تر نشون داده بشه",
                    checked = settings.showContactNumberInListEnabled,
                    onChecked = { AppSettings.setShowContactNumberInListEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                SwitchRow(
                    title = "نوار حروف الفبا",
                    info = "نوار کناری سمت چپ برای پرش سریع به مخاطبین بر اساس حرف اول اسم",
                    checked = settings.alphabetIndexBarEnabled,
                    onChecked = { AppSettings.setAlphabetIndexBarEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                ThinDivider()
                SettingRow(
                    title = "سویپ راست‌به‌چپ",
                    subtitle = settings.swipeRightToLeftAction.label,
                    onClick = { showSwipeRightToLeftDialog = true }
                )
                SettingRow(
                    title = "سویپ چپ‌به‌راست",
                    subtitle = settings.swipeLeftToRightAction.label,
                    onClick = { showSwipeLeftToRightDialog = true }
                )
                SwitchRow(
                    title = "تأیید حذف با سویپ",
                    info = "قبل از حذف واقعی (وقتی یکی از جهت‌ها روی حذف باشه) دیالوگ تأیید نشون بده",
                    checked = settings.swipeDeleteRequiresConfirmation,
                    onChecked = { AppSettings.setSwipeDeleteRequiresConfirmation(context, it) },
                    onInfo = { infoDialogText = it }
                )
                ThinDivider()
                ListItem(
                    headlineContent = { Text("حداکثر تعداد پین") },
                    supportingContent = { Text("سقف مکالمات پین‌شده در لیست اصلی") },
                    trailingContent = {
                        PinCountStepper(
                            value = settings.maxPinnedConversations,
                            onValueChange = { AppSettings.setMaxPinnedConversations(context, it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            AccordionSection(
                title = "پیام‌رسانی",
                expanded = expandedMessaging,
                onToggle = { expandedMessaging = !expandedMessaging }
            ) {
                SwitchRow(
                    title = "سطل زباله",
                    info = "پیام‌های حذف‌شده به‌جای حذف کامل، اول بیان سطل زباله",
                    checked = settings.trashEnabled,
                    onChecked = { AppSettings.setTrashEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                SwitchRow(
                    title = "گروه‌های پیامکی",
                    info = "توی «پیام جدید» چند مخاطب رو به‌عنوان گروه ذخیره کن و بعداً براشون بفرست",
                    checked = settings.groupMessagingEnabled,
                    onChecked = { AppSettings.setGroupMessagingEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
            }

            AccordionSection(
                title = "اعلان‌ها",
                expanded = expandedNotifications,
                onToggle = { expandedNotifications = !expandedNotifications }
            ) {
                SwitchRow(
                    title = "اعلان دلیوری",
                    info = "وقتی پیام به گیرنده رسید، نوتیف جدا نشون بده",
                    checked = settings.deliveryNotificationsEnabled,
                    onChecked = { AppSettings.setDeliveryNotificationsEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                SettingRow(
                    title = "دکمه‌های نوتیفیکیشن",
                    subtitle = "انتخاب و ترتیب دکمه‌های روی نوتیف پیامک",
                    onClick = onOpenNotificationActions
                )
                SwitchRow(
                    title = "پاپ‌آپ پیامک روی صفحه",
                    info = "به‌جای نوتیف معمولی، پیامک تازه‌رسیده پاپ‌آپ روی صفحه (حتی صفحه‌قفل) نشون بده",
                    checked = settings.popupInsteadOfNotificationEnabled,
                    onChecked = { AppSettings.setPopupInsteadOfNotificationEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                if (settings.popupInsteadOfNotificationEnabled && !overlayPermissionGranted) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "پرمیشن «نمایش روی برنامه‌های دیگر» لازمه",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        supportingContent = {
                            Text("بدون این پرمیشن پاپ‌آپ فقط روی صفحه‌قفل میاد. برای نمایش همیشه این پرمیشن رو بده.")
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }) { Text("دادن پرمیشن") }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }

            AccordionSection(
                title = "عمومی",
                expanded = expandedGeneral,
                onToggle = { expandedGeneral = !expandedGeneral }
            ) {
                ListItem(
                    headlineContent = { Text("زبان برنامه") },
                    supportingContent = { Text("فارسی (به‌زودی: انگلیسی)") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            AccordionSection(
                title = "پشتیبان‌گیری",
                expanded = expandedBackup,
                onToggle = { expandedBackup = !expandedBackup }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            selectedCategories = BackupCategory.entries.toSet()
                            showBackupDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("بک‌آپ") }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("بازیابی") }
                }
            }
        }
    }
}

@Composable
private fun AccordionSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = 4.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    info: String? = null,
    onInfo: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = info?.let {
            {
                IconButton(onClick = { onInfo?.invoke(it) }) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "توضیحات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    info: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onInfo: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onInfo(info) }) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "توضیحات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = checked, onCheckedChange = onChecked)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

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