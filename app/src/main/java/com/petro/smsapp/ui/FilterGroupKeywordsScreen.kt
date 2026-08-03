package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FilterGroupKeyword
import com.petro.smsapp.util.DateFormatter

@Composable
fun FilterGroupKeywordsScreen(
    groupName: String,
    keywords: List<FilterGroupKeyword>,
    onBack: () -> Unit,
    onAddKeyword: (text: String) -> Unit,
    onRemoveKeyword: (id: String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کلمات کلیدیِ «$groupName»") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text("متن یا کلمه‌ی مورد نظر") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newKeyword.isNotBlank()) {
                            onAddKeyword(newKeyword)
                            newKeyword = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "افزودن کلمه")
                }
            }

            Text(
                text = "هر پیامِ ورودی‌ای که شاملِ یکی از این عبارت‌ها باشه، میره تو گروهِ «$groupName».",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (keywords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هیچ کلمه‌ی کلیدی‌ای اضافه نشده", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(keywords, key = { it.id }) { keyword ->
                        FilterGroupKeywordRow(keyword = keyword, onRemove = { onRemoveKeyword(keyword.id) })
                        Divider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGroupKeywordRow(keyword: FilterGroupKeyword, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Sms, contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keyword.text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "اضافه‌شده در ${DateFormatter.formatFull(keyword.addedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف کلمه")
        }
    }
}
