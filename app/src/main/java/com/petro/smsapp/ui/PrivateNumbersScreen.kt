package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.PrivateNumber
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی «شماره‌های خصوصی» - عیناً هم‌خانواده‌ی BlockedNumbersScreen: جستجو + دکمه‌ی
 * افزودن شماره‌ی جدید (بالای صفحه) + دکمه‌ی «خارج کردن» روی هر ردیف.
 */
@Composable
fun PrivateNumbersScreen(
    privateNumbers: List<PrivateNumber>,
    onBack: () -> Unit,
    onRemovePrivate: (threadId: Long) -> Unit,
    onAddNumberClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredNumbers = if (searchQuery.isBlank()) {
        privateNumbers
    } else {
        privateNumbers.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) || it.address.contains(searchQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("شماره‌های خصوصی") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = onAddNumberClick) {
                        Icon(Icons.Filled.Add, contentDescription = "افزودن شماره‌ی خصوصی")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (privateNumbers.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("جستجوی نام یا شماره") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
                )
            }

            if (privateNumbers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هیچ شماره‌ای خصوصی نیست", color = Color.Gray)
                }
            } else if (filteredNumbers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("چیزی پیدا نشد", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(filteredNumbers, key = { it.threadId }) { private ->
                        PrivateNumberRow(private = private, onRemove = { onRemovePrivate(private.threadId) })
                        Divider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateNumberRow(private: PrivateNumber, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name = private.displayName)
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = private.displayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "خصوصی‌شده در ${DateFormatter.formatFull(private.madePrivateAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        TextButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("خارج کردن")
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
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
