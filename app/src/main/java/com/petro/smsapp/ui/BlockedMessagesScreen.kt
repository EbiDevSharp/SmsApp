package com.petro.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.BlockedMessageEntry
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی «پیامک‌های بلاک‌شده» - همه‌ی پیام‌های (قدیم + جدید) شماره‌های بلاک‌شده.
 * برای برداشتن یه شماره از حالت بلاک، باید از صفحه‌ی «شماره‌های بلاک‌شده» اقدام کرد.
 *
 * دو تا قابلیت، دقیقاً به همون الگوی صفحه‌ی چت اصلی:
 * ۱) تک‌کلیک روی یه پیام → همون منوی اکشن پیام (MessageActionsSheet) که تو چت داریم باز
 *    میشه: جزئیات، باز کردن در نوت (برای متن‌های طولانی)، کپی، اشتراک‌گذاری، فیوریت،
 *    و حذف. (قبلاً تک‌کلیک فقط متن رو ۲خطی/کامل می‌کرد؛ چون الان «باز کردن در نوت» همون
 *    کارو بهتر انجام میده، دیگه لازم نبود و با کلیکِ منو تداخل داشت.)
 * ۲) لانگ‌کلیک روی یه پیام وارد حالت «انتخاب چندتایی» میشه (تک‌کلیک بعدی فقط انتخاب/
 *    عدم‌انتخاب می‌کنه)، نوار بالا با تعداد انتخاب‌شده + انتخاب‌همه + حذف (با تائید) عوض میشه.
 *    چون پیام‌های این صفحه از چند مکالمه‌ی مختلف جمع شدن (نه فقط یکی)، حذف از اینجا با
 *    همون منطق مشترک حذف (سطل زباله/قفل فیوریت) میره، نه وابسته به یه thread خاص.
 */
@Composable
fun BlockedMessagesScreen(
    blockedMessages: List<BlockedMessageEntry>,
    favoriteIds: Set<Long>,
    onBack: () -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onOpenNote: (text: String) -> Unit,
    onToggleFavorite: (entry: BlockedMessageEntry) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var actionSheetEntry by remember { mutableStateOf<BlockedMessageEntry?>(null) }
    val selectionMode = selectedIds.isNotEmpty()

    // اگه بعد از حذف/تغییر لیست، بعضی id های انتخاب‌شده دیگه وجود نداشته باشن، از انتخاب پاک بشن
    LaunchedEffect(blockedMessages) {
        val stillExisting = blockedMessages.map { it.message.id }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    val currentActionSheetEntry = actionSheetEntry
    if (currentActionSheetEntry != null) {
        MessageActionsSheet(
            message = currentActionSheetEntry.message,
            contactDisplayName = currentActionSheetEntry.contactDisplayName,
            isFavorite = favoriteIds.contains(currentActionSheetEntry.message.id),
            onDismiss = { actionSheetEntry = null },
            onOpenNote = { onOpenNote(currentActionSheetEntry.message.body) },
            onDeleteConfirmed = {
                onDeleteMessages(setOf(currentActionSheetEntry.message.id))
                actionSheetEntry = null
            },
            onToggleFavorite = { onToggleFavorite(currentActionSheetEntry) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف پیام‌ها") },
            text = { Text("${selectedIds.size} پیام حذف بشه؟") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteMessages(selectedIds)
                    selectedIds = emptySet()
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} انتخاب شده") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "لغو انتخاب")
                        }
                    },
                    actions = {
                        val allSelected = selectedIds.size == blockedMessages.size && blockedMessages.isNotEmpty()
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else blockedMessages.map { it.message.id }.toSet()
                        }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = if (allSelected) "از انتخاب دراوردن همه" else "انتخاب همه"
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("پیامک‌های بلاک‌شده") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Text("←") }
                    }
                )
            }
        }
    ) { padding ->
        if (blockedMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هیچ پیامک بلاک‌شده‌ای نیست", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(blockedMessages, key = { it.message.id }) { entry ->
                    BlockedMessageRow(
                        entry = entry,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(entry.message.id),
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (selectedIds.contains(entry.message.id)) {
                                    selectedIds - entry.message.id
                                } else {
                                    selectedIds + entry.message.id
                                }
                            } else {
                                actionSheetEntry = entry
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) selectedIds = setOf(entry.message.id)
                        }
                    )
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BlockedMessageRow(
    entry: BlockedMessageEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(isSelected = isSelected)
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Avatar(name = entry.contactDisplayName)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.contactDisplayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.message.body,
                maxLines = 2,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateFormatter.formatFull(entry.message.date),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun SelectionCheck(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "انتخاب‌شده",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
