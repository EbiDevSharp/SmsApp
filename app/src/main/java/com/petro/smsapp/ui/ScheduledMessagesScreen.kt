package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ScheduledMessage
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی «زمان‌بندی‌شده» - همه‌ی پیام‌های هنوز-ارسال‌نشده‌ی زمان‌بندی‌شده، از همه‌ی
 * مکالمه‌ها با هم (نه فقط یک چت)، مرتب‌شده بر اساس نزدیک‌ترین زمانِ ارسال. کلیک روی
 * هرکدوم همون منوی «ویرایش زمان / اکنون ارسال شود / لغو زمان‌بندی» که توی چت هم هست رو باز می‌کنه.
 */
@Composable
fun ScheduledMessagesScreen(
    scheduledMessages: List<ScheduledMessage>,
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onUpdateTime: (id: Long, newTime: Long) -> Unit,
    onSendNow: (id: Long) -> Unit,
    onCancel: (id: Long) -> Unit
) {
    var selected by remember { mutableStateOf<ScheduledMessage?>(null) }
    var editing by remember { mutableStateOf<ScheduledMessage?>(null) }

    val currentSelected = selected
    if (currentSelected != null) {
        ScheduledMessageActionsSheet(
            onDismiss = { selected = null },
            onEditTime = { editing = currentSelected },
            onSendNow = {
                onSendNow(currentSelected.id)
                selected = null
            },
            onCancelSchedule = {
                onCancel(currentSelected.id)
                selected = null
            }
        )
    }

    val currentEditing = editing
    if (currentEditing != null) {
        DateTimePickerDialog(
            initialMillis = currentEditing.scheduledAt,
            onConfirm = {
                onUpdateTime(currentEditing.id, it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("زمان‌بندی‌شده") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                }
            )
        }
    ) { padding ->
        if (scheduledMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هیچ پیام زمان‌بندی‌شده‌ای نیست", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(scheduledMessages.sortedBy { it.scheduledAt }, key = { it.id }) { scheduled ->
                    ScheduledRow(scheduled = scheduled, onClick = { selected = scheduled })
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun ScheduledRow(scheduled: ScheduledMessage, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = scheduled.displayName)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(scheduled.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(scheduled.body, maxLines = 2, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "ارسال در ${DateFormatter.formatFull(scheduled.scheduledAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}