@file:OptIn(ExperimentalFoundationApi::class)

package com.petro.smsapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.util.autoDirection
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * هابِ اصلیِ «گروه‌ها» - جایگزینِ عمومیِ صفحه‌ی قدیمیِ «بلاک». ترتیبِ لیست همون ترتیبِ
 * اولویتِ چک‌شدنه (بالاتر = زودتر چک میشه). به‌جای دکمه‌های بالا/پایین، حالا درست مثلِ
 * صفحه‌ی «دکمه‌های نوتیفیکیشن» کاملاً با درگ‌اند‌دراپ جابه‌جا میشه؛ کل ردیف قابل کشیدنه
 * و هر جابه‌جایی بلافاصله ذخیره میشه.
 *
 * اگه یه گروه هدفِ دکمه‌ی «افزودن سریع به گروه»ِ نوتیف باشه (isQuickAddTarget)، کنارِ
 * اسمش یه آیکنِ رعد (⚡) کوچیک نشون داده میشه تا کاربر بدونِ باز کردنِ تک‌تکِ گروه‌ها
 * بفهمه الان کدوم گروه هدفه.
 */
@Composable
fun FilterGroupsScreen(
    groups: List<FilterGroupSummary>,
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onOpenGroup: (groupId: Long) -> Unit,
    onCreateGroup: (name: String, hideFromMainList: Boolean, showNotifications: Boolean, blockNonContacts: Boolean, showInNotificationPicker: Boolean) -> Unit,
    onDeleteGroup: (groupId: Long) -> Unit,
    onReorder: (orderedGroupIds: List<Long>) -> Unit,
    onGlobalShowNotificationsChange: (Boolean) -> Unit,
    onGlobalShowInMainListChange: (Boolean) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FilterGroupSummary?>(null) }

    var localOrder by remember(groups) {
        mutableStateOf(groups.sortedBy { it.group.priority })
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localOrder = localOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onReorder(localOrder.map { it.group.id })
    }

    if (showCreateDialog) {
        CreateFilterGroupDialog(
            onConfirm = { name, hide, notify, nonContacts, notifPicker ->
                onCreateGroup(name, hide, notify, nonContacts, notifPicker)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذفِ گروه") },
            text = { Text("گروهِ «${toDelete.group.name}» حذف بشه؟ همه‌ی شماره/کلمه/الگوهاش هم پاک میشن. خودِ پیامک‌ها حذف نمیشن.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(toDelete.group.id)
                    pendingDelete = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("انصراف") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گروه‌ها") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "گروهِ جدید")
                    }
                }
            )
        }
    ) { padding ->
        if (localOrder.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("هنوز هیچ گروهی نساختی", color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "مثلاً یه گروهِ «تبلیغاتی» یا «بانک» بساز و شماره/کلمه/الگوی خودش رو بهش اضافه کن",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ساختِ اولین گروه")
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                GlobalGroupSettingsCard(
                    groups = localOrder,
                    onShowNotificationsChange = onGlobalShowNotificationsChange,
                    onShowInMainListChange = onGlobalShowInMainListChange
                )
                Text(
                    "ترتیبِ زیر همون ترتیبِ اولویتِ چک‌کردنه - با نگه‌داشتن و کشیدنِ ردیف جابه‌جا کن",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(localOrder, key = { _, item -> item.group.id }) { _, summary ->
                        ReorderableItem(reorderableState, key = summary.group.id) { isDragging ->
                            FilterGroupRow(
                                summary = summary,
                                isDragging = isDragging,
                                dragModifier = Modifier.draggableHandle(),
                                onClick = { onOpenGroup(summary.group.id) },
                                onDelete = { pendingDelete = summary }
                            )
                            Divider(modifier = Modifier.padding(start = 72.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * کارتِ تنظیماتِ گلوبالِ بالای هابِ گروه‌ها - دو سوییچی که رویِ *همه*ی گروه‌ها یه‌جا
 * اثر می‌ذارن (به‌جای اینکه کاربر مجبور باشه تک‌تکِ گروه‌ها رو باز کنه و دستی عوض کنه).
 * وضعیتِ هر سوییچ از رویِ خودِ لیستِ گروه‌ها مشتق میشه: روشنه فقط اگه همه‌ی گروه‌ها
 * همون مقدار رو داشته باشن؛ اگه لیست ترکیبی باشه (بعضی روشن بعضی خاموش) سوییچ خاموش
 * نشون داده میشه و اولین لمس همه رو یکجا روشن می‌کنه.
 */
@Composable
private fun GlobalGroupSettingsCard(
    groups: List<FilterGroupSummary>,
    onShowNotificationsChange: (Boolean) -> Unit,
    onShowInMainListChange: (Boolean) -> Unit
) {
    val allShowNotifications = groups.isNotEmpty() && groups.all { it.group.showNotifications }
    val allShowInMainList = groups.isNotEmpty() && groups.all { !it.group.hideFromMainList }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                "تنظیماتِ گلوبالِ همه‌ی گروه‌ها",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
            SettingSwitchRow(
                label = "دریافتِ نوتیف برای همه‌ی گروه‌ها",
                checked = allShowNotifications,
                onCheckedChange = onShowNotificationsChange
            )
            SettingSwitchRow(
                label = "نمایش در لیستِ پیام‌ها برای همه‌ی گروه‌ها",
                checked = allShowInMainList,
                onCheckedChange = onShowInMainListChange
            )
        }
    }
}

@Composable
private fun FilterGroupRow(
    summary: FilterGroupSummary,
    isDragging: Boolean,
    dragModifier: Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)          // ← کل ردیف دستگیرهٔ درگ شد
            .clickable(onClick = onClick)
            .let { mod ->
                if (isDragging) {
                    mod
                        .scale(1.02f)
                        .shadow(6.dp, shape = RoundedCornerShape(8.dp))
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                } else {
                    mod
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary.group.name, style = MaterialTheme.typography.bodyLarge.autoDirection())
                if (summary.group.isQuickAddTarget) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "هدفِ افزودنِ سریع",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                "${summary.numberCount} شماره · ${summary.keywordCount} کلمه · ${summary.patternCount} الگو · ${summary.messageCount} پیام",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (summary.group.hideFromMainList) {
                Text("از لیستِ اصلی مخفی میشه", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "حذفِ گروه")
        }
        // آیکن فقط برای راهنمایی بصری؛ دیگه خودش handle نیست
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "جابجایی",
            modifier = Modifier
                .size(32.dp)
                .padding(start = 4.dp)
        )
    }
}

/** دیالوگِ ساختِ گروهِ جدید - اسم + چهار سوییچِ تنظیماتِ اولیه (بعداً هم از صفحه‌ی تنظیماتِ خودِ گروه قابلِ‌تغییره) */
@Composable
private fun CreateFilterGroupDialog(
    onConfirm: (name: String, hideFromMainList: Boolean, showNotifications: Boolean, blockNonContacts: Boolean, showInNotificationPicker: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hideFromMainList by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(true) }
    var blockNonContacts by remember { mutableStateOf(false) }
    var showInNotificationPicker by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساختِ گروهِ جدید") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسمِ گروه") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingSwitchRow(
                    label = "از لیستِ اصلی مخفی بشه",
                    checked = hideFromMainList,
                    onCheckedChange = { hideFromMainList = it }
                )
                SettingSwitchRow(
                    label = "با اینکه افتاد تو این گروه، بازم نوتیف بده",
                    checked = showNotifications,
                    onCheckedChange = { showNotifications = it }
                )
                SettingSwitchRow(
                    label = "فرستنده‌های خارج از مخاطبین خودکار بیان اینجا",
                    checked = blockNonContacts,
                    onCheckedChange = { blockNonContacts = it }
                )
                SettingSwitchRow(
                    label = "تو انتخابگرِ سریعِ دکمه‌ی نوتیفیکیشن هم نشون داده بشه",
                    checked = showInNotificationPicker,
                    onCheckedChange = { showInNotificationPicker = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), hideFromMainList, showNotifications, blockNonContacts, showInNotificationPicker) },
                enabled = name.isNotBlank()
            ) { Text("ساخت") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
internal fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}