package com.petro.smsapp.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.TrashedMessage
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.autoDirection

/**
 * صفحه‌ی «سطل زباله» - عیناً هم‌خانواده‌ی BlockedMessagesScreen/PrivateMessagesScreen:
 * ۱) تک‌کلیک روی یه پیام -> منوی اکشنِ مخصوصِ سطل زباله (TrashMessageActionsSheet):
 *    باز کردن در نوت، کپی، اشتراک‌گذاری، بازگردانی، حذف همیشگی (با تائید).
 * ۲) لانگ‌کلیک -> حالت «انتخاب چندتایی» با نوار بالا: بستن انتخاب، انتخاب‌همه/هیچ‌کدام،
 *    بازگردانیِ گروهی، حذف همیشگیِ گروهی (با تائید).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    trashedMessages: List<TrashedMessage>,
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onOpenNote: (text: String) -> Unit,
    onRestore: (messageId: Long) -> Unit,
    onRestoreMultiple: (Set<Long>) -> Unit,
    onPermanentDelete: (messageId: Long) -> Unit,
    onPermanentDeleteMultiple: (Set<Long>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var actionSheetEntry by remember { mutableStateOf<TrashedMessage?>(null) }
    var pendingPermanentDelete by remember { mutableStateOf<TrashedMessage?>(null) }
    val selectionMode = selectedIds.isNotEmpty()

    // اگه بعد از بازگردانی/حذف، بعضی id های انتخاب‌شده دیگه وجود نداشته باشن، از انتخاب پاک بشن
    LaunchedEffect(trashedMessages) {
        val stillExisting = trashedMessages.map { it.message.id }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    val currentActionSheetEntry = actionSheetEntry
    if (currentActionSheetEntry != null) {
        TrashMessageActionsSheet(
            trashed = currentActionSheetEntry,
            onDismiss = { actionSheetEntry = null },
            onOpenNote = { onOpenNote(currentActionSheetEntry.message.body) },
            onRestore = {
                onRestore(currentActionSheetEntry.message.id)
                actionSheetEntry = null
            },
            onRequestPermanentDelete = {
                actionSheetEntry = null
                pendingPermanentDelete = currentActionSheetEntry
            }
        )
    }

    val toDelete = pendingPermanentDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingPermanentDelete = null },
            title = { Text("حذف همیشگی") },
            text = { Text("این پیام برای همیشه پاک بشه؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    onPermanentDelete(toDelete.message.id)
                    pendingPermanentDelete = null
                }) {
                    Text("حذف همیشگی", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPermanentDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("حذف همیشگی") },
            text = { Text("${selectedIds.size} پیام برای همیشه پاک بشن؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkDeleteConfirm = false
                    onPermanentDeleteMultiple(selectedIds)
                    selectedIds = emptySet()
                }) {
                    Text("حذف همیشگی", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
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
                        val allSelected = selectedIds.size == trashedMessages.size && trashedMessages.isNotEmpty()
                        IconButton(onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                trashedMessages.map { it.message.id }.toSet()
                            }
                        }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = if (allSelected) "از انتخاب دراوردن همه" else "انتخاب همه"
                            )
                        }
                        IconButton(onClick = {
                            onRestoreMultiple(selectedIds)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Restore, contentDescription = "بازگردانی")
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف همیشگی")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("سطل زباله") },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                    }
                )
            }
        }
    ) { padding ->
        if (trashedMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("سطل زباله خالیه", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(trashedMessages, key = { it.message.id }) { trashed ->
                    TrashRow(
                        trashed = trashed,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(trashed.message.id),
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (selectedIds.contains(trashed.message.id)) {
                                    selectedIds - trashed.message.id
                                } else {
                                    selectedIds + trashed.message.id
                                }
                            } else {
                                actionSheetEntry = trashed
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) selectedIds = setOf(trashed.message.id)
                        }
                    )
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

/** منوی اکشنِ تک‌کلیکِ روی یه پیامِ داخل سطل زباله */
@Composable
private fun TrashMessageActionsSheet(
    trashed: TrashedMessage,
    onDismiss: () -> Unit,
    onOpenNote: () -> Unit,
    onRestore: () -> Unit,
    onRequestPermanentDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            TrashMenuRow(
                icon = Icons.Filled.Notes,
                label = "باز کردن در نوت",
                onClick = {
                    onDismiss()
                    onOpenNote()
                }
            )
            TrashMenuRow(
                icon = Icons.Filled.ContentCopy,
                label = "کپی",
                onClick = {
                    clipboardManager.setText(AnnotatedString(trashed.message.body))
                    onDismiss()
                }
            )
            TrashMenuRow(
                icon = Icons.Filled.Share,
                label = "اشتراک‌گذاری",
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, trashed.message.body)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                    onDismiss()
                }
            )
            TrashMenuRow(
                icon = Icons.Filled.Restore,
                label = "بازگردانی",
                onClick = onRestore
            )
            TrashMenuRow(
                icon = Icons.Filled.Delete,
                label = "حذف همیشگی",
                onClick = onRequestPermanentDelete
            )
        }
    }
}

@Composable
private fun TrashMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    trashed: TrashedMessage,
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
            Avatar(name = trashed.contactDisplayName)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trashed.contactDisplayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge.autoDirection()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = trashed.message.body,
                maxLines = 2,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateFormatter.formatFull(trashed.message.date),
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
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
