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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
import com.petro.smsapp.util.SettingsDropdown
import androidx.compose.ui.draw.scale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenNotificationActions: () -> Unit,
    onOpenPopupSettings: () -> Unit = {},
    onOpenAlphabetIndexSettings: () -> Unit = {},
    onMenuClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()
    val scope = rememberCoroutineScope()

    var showSwipeRightToLeftDialog by remember { mutableStateOf(false) }
    var showSwipeLeftToRightDialog by remember { mutableStateOf(false) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    var expandedAppearance by remember { mutableStateOf(false) }
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
                    Toast.makeText(context, "بک‌آپ با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "خطا در ذخیره فایل", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, "خطا در بازیابی داده‌ها", Toast.LENGTH_SHORT).show()
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
            title = { Text("انتخاب بخش‌های بک‌آپ") },
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
                                Text(cat.title, fontWeight = FontWeight.SemiBold)
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
                Button(
                    onClick = {
                        if (selectedCategories.isEmpty()) {
                            Toast.makeText(context, "حداقل یک بخش انتخاب کن", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showBackupDialog = false
                        exportLauncher.launch(BackupModule.suggestedFileName())
                    }
                ) { Text("ذخیره بک‌آپ") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("انصراف") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // بخش ظاهر
            AccordionSection(
                title = "ظاهر و پوسته",
                icon = Icons.Outlined.Palette,
                expanded = expandedAppearance,
                onToggle = { expandedAppearance = !expandedAppearance }
            ) {
                SettingsDropdown(
                    title = "تم برنامه",

                    current = settings.themeMode,

                    items = listOf(
                        ThemeMode.SYSTEM to "سیستم",
                        ThemeMode.LIGHT to "روشن",
                        ThemeMode.DARK to "تاریک"
                    ),

                    onChange = {
                        AppSettings.setThemeMode(context, it)
                    }
                )
                ThinDivider()
                SettingsDropdown(
                    title = "نمایش تاریخ",

                    current = settings.calendarType,

                    items = listOf(
                        CalendarType.GREGORIAN to "میلادی",
                        CalendarType.JALALI to "شمسی"
                    ),

                    onChange = {
                        AppSettings.setCalendarType(context, it)
                    }
                )

                ThinDivider()

                SettingsDropdown(
                    title = "نمایش ساعت",

                    current = settings.clockFormat,

                    items = listOf(
                        ClockFormat.H24 to "۲۴ ساعته",
                        ClockFormat.H12 to "۱۲ ساعته"
                    ),

                    onChange = {
                        AppSettings.setClockFormat(context, it)
                    }
                )
            }

            // لیست مکالمات
            AccordionSection(
                title = "لیست مکالمات",
                icon = Icons.Outlined.Forum,
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
                SettingRow(
                    title = "نوار حروف الفبا",
                    subtitle = if (settings.alphabetIndexBarEnabled) "فعال" else "غیرفعال",
                    onClick = onOpenAlphabetIndexSettings
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

            // پیام‌رسانی
            AccordionSection(
                title = "پیام‌رسانی",
                icon = Icons.Outlined.MarkChatUnread,
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
                ThinDivider()
                ListItem(
                    headlineContent = { Text("تأخیر ارسال") },
                    supportingContent = {
                        Text(
                            if (settings.sendDelaySeconds == 0) "فوری (بدون تأخیر)"
                            else "${settings.sendDelaySeconds} ثانیه فرصت لغو"
                        )
                    },
                    trailingContent = {
                        SendDelayStepper(
                            value = settings.sendDelaySeconds,
                            onValueChange = { AppSettings.setSendDelaySeconds(context, it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            // اعلان‌ها
            AccordionSection(
                title = "اعلان‌ها",
                icon = Icons.Outlined.Notifications,
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
                SettingRow(
                    title = "پاپ‌آپ پیامک",
                    subtitle = if (settings.popupInsteadOfNotificationEnabled) "فعال" else "غیرفعال — تنظیمات پاپ‌آپ",
                    onClick = onOpenPopupSettings
                )
                SwitchRow(
                    title = "خوانده‌شدن با بیرون‌انداختنِ نوتیف",
                    info = "اگه نوتیفِ پیامکِ تازه‌رسیده رو با دست (سوایپ) بیرون بندازی، همون پیام خودکار خوانده‌شده علامت می‌خوره",
                    checked = settings.markReadOnNotificationDismissEnabled,
                    onChecked = { AppSettings.setMarkReadOnNotificationDismissEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                ThinDivider()
                SwitchRow(
                    title = "یادآوری پیام‌های خوانده‌نشده",
                    info = "اگر پیامک خوانده‌نشده داشته باشی، بعد از فاصله‌ی مشخص دوباره نوتیف یادآوری نشون داده می‌شه (تا تعداد دفعات تنظیم‌شده)",
                    checked = settings.unreadReminderEnabled,
                    onChecked = { AppSettings.setUnreadReminderEnabled(context, it) },
                    onInfo = { infoDialogText = it }
                )
                val reminderOn = settings.unreadReminderEnabled
                SettingsDropdown(
                    title = "تعداد یادآوری",
                    current = settings.unreadReminderCount,
                    items = listOf(
                        1 to "۱ بار",
                        2 to "۲ بار",
                        3 to "۳ بار"
                    ),
                    onChange = { AppSettings.setUnreadReminderCount(context, it) },
                    enabled = reminderOn
                )
                SettingsDropdown(
                    title = "فاصله بین یادآوری‌ها",
                    current = settings.unreadReminderIntervalMinutes,
                    items = listOf(
                        5 to "۵ دقیقه",
                        10 to "۱۰ دقیقه",
                        30 to "۳۰ دقیقه"
                    ),
                    onChange = { AppSettings.setUnreadReminderIntervalMinutes(context, it) },
                    enabled = reminderOn
                )
                }


            // عمومی
            AccordionSection(
                title = "عمومی",
                icon = Icons.Outlined.Tune,
                expanded = expandedGeneral,
                onToggle = { expandedGeneral = !expandedGeneral }
            ) {
                ListItem(
                    headlineContent = { Text("زبان برنامه") },
                    supportingContent = { Text("فارسی (به‌زودی: انگلیسی)") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            // پشتیبان‌گیری
            AccordionSection(
                title = "پشتیبان‌گیری و بازیابی",
                icon = Icons.Outlined.Backup,
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بک‌آپ")
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بازیابی")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(bottom = 8.dp)
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
        trailingContent = info?.let {
            {
                IconButton(onClick = { onInfo?.invoke(it) }) {
                    Icon(
                        Icons.Outlined.Info,
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
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onInfo(info) }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "توضیحات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onChecked,
                    modifier = Modifier.scale(0.65f)
                )
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
                            .padding(vertical = 8.dp),
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
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = { if (value < 20) onValueChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "زیاد کردن")
        }
    }
}

@Composable
private fun SendDelayStepper(value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (value > AppSettings.MIN_SEND_DELAY_SECONDS) onValueChange(value - 1)
        }) {
            Icon(Icons.Filled.Remove, contentDescription = "کم کردن")
        }
        Text(
            text = if (value == 0) "۰" else value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = {
            if (value < AppSettings.MAX_SEND_DELAY_SECONDS) onValueChange(value + 1)
        }) {
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
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