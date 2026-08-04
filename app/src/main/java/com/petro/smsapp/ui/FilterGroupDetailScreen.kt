package com.petro.smsapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.util.autoDirection

/**
 * صفحه‌ی «داخلِ یه گروه» - جایگزینِ عمومیِ BlockScreen/BlockSettingsScreen قبلی، ولی
 * برای یه گروهِ مشخص. تنظیمات داخل آکاردئون جمع می‌شن تا جا نگیرن؛ پایینش چهار
 * کارتِ مربعی هم‌اندازه در دو ردیف (شماره / کلمه / الگو / پیام‌ها).
 *
 * سوییچِ «هدفِ افزودنِ سریع» جدا از بقیه‌ی تنظیمات (که با دکمه‌ی «ذخیره» یه‌جا ذخیره
 * میشن) بلافاصله با تغییرش اعمال میشه - چون رفتارش رادیوییه (فقط یه گروه می‌تونه
 * هم‌زمان هدف باشه) و باید فوراً روی بقیه‌ی گروه‌ها هم اثر بذاره (خاموش کردنشون).
 */
@Composable
fun FilterGroupDetailScreen(
    summary: FilterGroupSummary,
    onBack: () -> Unit,
    onOpenNumbers: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenPatterns: () -> Unit,
    onOpenMessages: () -> Unit,
    onSave: (name: String, hideFromMainList: Boolean, showNotifications: Boolean, blockNonContacts: Boolean, showInNotificationPicker: Boolean) -> Unit,
    onSetQuickAddTarget: (Boolean) -> Unit = {}
) {
    var name by remember(summary.group.id) { mutableStateOf(summary.group.name) }
    var hideFromMainList by remember(summary.group.id) { mutableStateOf(summary.group.hideFromMainList) }
    var showNotifications by remember(summary.group.id) { mutableStateOf(summary.group.showNotifications) }
    var blockNonContacts by remember(summary.group.id) { mutableStateOf(summary.group.blockNonContacts) }
    var showInNotificationPicker by remember(summary.group.id) { mutableStateOf(summary.group.showInNotificationPicker) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var showQuickAddHelp by remember { mutableStateOf(false) }

    val hasChanges = name.trim() != summary.group.name ||
            hideFromMainList != summary.group.hideFromMainList ||
            showNotifications != summary.group.showNotifications ||
            blockNonContacts != summary.group.blockNonContacts ||
            showInNotificationPicker != summary.group.showInNotificationPicker

    if (showQuickAddHelp) {
        AlertDialog(
            onDismissRequest = { showQuickAddHelp = false },
            title = { Text("افزودن سریع به گروه") },
            text = {
                Text(
                    "با فعال‌کردنش، دکمه‌ی «افزودن سریع به گروه» روی نوتیف پیامک مستقیم فرستنده رو به همین گروه اضافه می‌کنه. فقط یه گروه می‌تونه هم‌زمان این باشه."
                )
            },
            confirmButton = {
                TextButton(onClick = { showQuickAddHelp = false }) {
                    Text("باشه")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary.group.name, style = LocalTextStyle.current.autoDirection()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    if (hasChanges) {
                        TextButton(onClick = {
                            onSave(name.trim(), hideFromMainList, showNotifications, blockNonContacts, showInNotificationPicker)
                        }) { Text("ذخیره") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ── آکاردئون تنظیمات ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // هدر آکاردئون
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsExpanded = !settingsExpanded }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تنظیمات گروه",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasChanges) {
                            Text(
                                "تغییرات ذخیره نشده",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Icon(
                            imageVector = if (settingsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (settingsExpanded) "بستن" else "باز کردن"
                        )
                    }

                    AnimatedVisibility(
                        visible = settingsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("اسمِ گروه") },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingSwitchRow("از لیستِ اصلی مخفی بشه", hideFromMainList) { hideFromMainList = it }
                            SettingSwitchRow("با اینکه افتاد تو این گروه، بازم نوتیف بده", showNotifications) { showNotifications = it }
                            SettingSwitchRow("فرستنده‌های خارج از مخاطبین خودکار بیان اینجا", blockNonContacts) { blockNonContacts = it }
                            SettingSwitchRow("تو انتخابگرِ شیتِ «افزودن به گروه»ِ نوتیف هم نشون داده بشه", showInNotificationPicker) { showInNotificationPicker = it }

                            Spacer(modifier = Modifier.height(8.dp))
                            // هدف افزودن سریع
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (summary.group.isQuickAddTarget) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = if (summary.group.isQuickAddTarget) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "هدفِ دکمه‌ی «افزودن سریع به گروه» در نوتیف",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { showQuickAddHelp = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = "راهنما",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Switch(
                                        checked = summary.group.isQuickAddTarget,
                                        onCheckedChange = { checked -> onSetQuickAddTarget(checked) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── گرید ۲×۲ کارت‌های مربعی ───────────────────────────────────
            val gap = 10.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    FilterGroupSectionCard(
                        icon = Icons.Filled.Sms,
                        label = "شماره‌ها",
                        count = summary.numberCount,
                        onClick = onOpenNumbers,
                        modifier = Modifier.weight(1f)
                    )
                    FilterGroupSectionCard(
                        icon = Icons.Filled.TextFields,
                        label = "کلمات کلیدی",
                        count = summary.keywordCount,
                        onClick = onOpenKeywords,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    FilterGroupSectionCard(
                        icon = Icons.Filled.Rule,
                        label = "الگوهای شماره",
                        count = summary.patternCount,
                        onClick = onOpenPatterns,
                        modifier = Modifier.weight(1f)
                    )
                    FilterGroupSectionCard(
                        icon = Icons.Filled.Sms,
                        label = "پیام‌های این گروه",
                        count = summary.messageCount,
                        onClick = onOpenMessages,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGroupSectionCard(
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}