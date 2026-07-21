package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی «علاقه‌مندی‌ها» - لیست پیام‌هایی که با یک‌کلیک از منوی اکشن پیام فیوریت شدن.
 * هر آیتم اسم شخص، متن پیام و تاریخ/ساعت کامل رو نشون میده. تا وقتی پیامی اینجاست،
 * توی SmsRepository.deleteMessage قفله و قابل حذف نیست؛ تنها کاری که میشه کرد
 * برداشتن فیوریت (باز کردن قفل) از همینجاست - که خود پیامک رو حذف نمی‌کنه.
 */
@Composable
fun FavoritesScreen(
    favorites: List<FavoriteMessage>,
    onBack: () -> Unit,
    onItemClick: (FavoriteMessage) -> Unit,
    onRemoveFavorite: (messageId: Long) -> Unit
) {
    var pendingRemove by remember { mutableStateOf<FavoriteMessage?>(null) }

    val toRemove = pendingRemove
    if (toRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("برداشتن از علاقه‌مندی‌ها") },
            text = { Text("این پیام از علاقه‌مندی‌ها برداشته بشه؟ خود پیامک حذف نمیشه، فقط قفلش باز میشه.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFavorite(toRemove.messageId)
                    pendingRemove = null
                }) {
                    Text("بردار")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("علاقه‌مندی‌ها") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز پیامی رو فیوریت نکردی", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(favorites, key = { it.messageId }) { favorite ->
                    FavoriteRow(
                        favorite = favorite,
                        onClick = { onItemClick(favorite) },
                        onRemoveClick = { pendingRemove = favorite }
                    )
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteMessage,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = favorite.displayName)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = favorite.displayName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "قفل‌شده - قابل حذف نیست",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = favorite.body,
                maxLines = 2,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateFormatter.formatFull(favorite.date),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onRemoveClick) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "برداشتن از علاقه‌مندی‌ها",
                tint = Color(0xFFFFC107)
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
