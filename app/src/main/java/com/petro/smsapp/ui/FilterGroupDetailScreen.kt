package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.util.autoDirection

/**
 * صفحه‌ی «داخلِ یه گروه» - جایگزینِ عمومیِ BlockScreen/BlockSettingsScreen قبلی، ولی
 * برای یه گروهِ مشخص. بالای صفحه اسمِ گروه (با امکانِ ویرایش) + سه سوییچِ تنظیمات، پایینش
 * سه کارتِ ورودی (شماره/کلمه/الگو) و یه کارتِ نمایشِ پیام‌ها.
 */
@Composable
fun FilterGroupDetailScreen(
    summary: FilterGroupSummary,
    onBack: () -> Unit,
    onOpenNumbers: () -> Unit,
    onOpenKeywords: () -> Unit,
    onOpenPatterns: () -> Unit,
    onOpenMessages: () -> Unit,
    onSave: (name: String, hideFromMainList: Boolean, showNotifications: Boolean, blockNonContacts: Boolean) -> Unit
) {
    var name by remember(summary.group.id) { mutableStateOf(summary.group.name) }
    var hideFromMainList by remember(summary.group.id) { mutableStateOf(summary.group.hideFromMainList) }
    var showNotifications by remember(summary.group.id) { mutableStateOf(summary.group.showNotifications) }
    var blockNonContacts by remember(summary.group.id) { mutableStateOf(summary.group.blockNonContacts) }

    val hasChanges = name.trim() != summary.group.name ||
        hideFromMainList != summary.group.hideFromMainList ||
        showNotifications != summary.group.showNotifications ||
        blockNonContacts != summary.group.blockNonContacts

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
                            onSave(name.trim(), hideFromMainList, showNotifications, blockNonContacts)
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

            Spacer(modifier = Modifier.height(20.dp))

            FilterGroupSectionRow(
                icon = Icons.Filled.Sms,
                label = "شماره‌ها",
                count = summary.numberCount,
                onClick = onOpenNumbers
            )
            FilterGroupSectionRow(
                icon = Icons.Filled.TextFields,
                label = "کلمات کلیدی",
                count = summary.keywordCount,
                onClick = onOpenKeywords
            )
            FilterGroupSectionRow(
                icon = Icons.Filled.Rule,
                label = "الگوهای شماره",
                count = summary.patternCount,
                onClick = onOpenPatterns
            )
            FilterGroupSectionRow(
                icon = Icons.Filled.Sms,
                label = "پیام‌های این گروه",
                count = summary.messageCount,
                onClick = onOpenMessages
            )
        }
    }
}

@Composable
private fun FilterGroupSectionRow(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(count.toString(), color = androidx.compose.ui.graphics.Color.Gray)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Gray)
        }
    }
}
