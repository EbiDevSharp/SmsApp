package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.Conversation
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit,
    onComposeClick: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("پیام‌ها") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onComposeClick) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(conversations, key = { it.threadId }) { conversation ->
                ConversationRow(conversation, onClick = { onConversationClick(conversation) })
                Divider()
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.displayName,
                fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.snippet,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = formatDate(conversation.date),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatDate(millis: Long): String {
    if (millis == 0L) return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
