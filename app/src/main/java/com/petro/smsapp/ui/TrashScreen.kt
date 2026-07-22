package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.TrashedMessage
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی «سطل زباله» - پیام‌هایی که با تیک «سطل زباله»ی فعال توی تنظیمات حذف شدن.
 * خودِ پیام‌ها فیزیکی پاک نشدن (TrashStore فقط مخفی‌شون کرده)، پس اینجا هم قابل
 * ری‌استور هستن، هم قابل حذف همیشگی (که دیگه واقعاً برگشت نداره).
 */
@Composable
fun TrashScreen(
    trashedMessages: List<TrashedMessage>,
    onBack: () -> Unit,
    onRestore: (messageId: Long) -> Unit,
    onPermanentDelete: (messageId: Long) -> Unit
) {
    var pendingPermanentDelete by remember { mutableStateOf<TrashedMessage?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سطل زباله") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
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
                        onRestoreClick = { onRestore(trashed.message.id) },
                        onDeleteClick = { pendingPermanentDelete = trashed }
                    )
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun TrashRow(
    trashed: TrashedMessage,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = trashed.contactDisplayName)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trashed.contactDisplayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
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

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(onClick = onRestoreClick) {
            Icon(
                imageVector = Icons.Filled.Restore,
                contentDescription = "بازگردانی",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "حذف همیشگی",
                tint = MaterialTheme.colorScheme.error
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
