package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import com.petro.smsapp.data.FilterGroupNumber
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.autoDirection

@Composable
fun FilterGroupNumbersScreen(
    groupName: String,
    numbers: List<FilterGroupNumber>,
    onBack: () -> Unit,
    onRemove: (address: String) -> Unit,
    onAddNumberClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredNumbers = if (searchQuery.isBlank()) {
        numbers
    } else {
        numbers.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) || it.address.contains(searchQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("شماره‌های «$groupName»", style = LocalTextStyle.current.autoDirection()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = onAddNumberClick) {
                        Icon(Icons.Filled.Add, contentDescription = "افزودن شماره")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (numbers.isNotEmpty()) {
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

            if (numbers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هیچ شماره‌ای تو این گروه نیست", color = Color.Gray)
                }
            } else if (filteredNumbers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("چیزی پیدا نشد", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(filteredNumbers, key = { it.address }) { number ->
                        FilterGroupNumberRow(number = number, onRemove = { onRemove(number.address) })
                        Divider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterGroupNumberRow(number: FilterGroupNumber, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initial = number.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(number.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge.autoDirection())
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "اضافه‌شده در ${DateFormatter.formatFull(number.addedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "حذف از گروه")
        }
    }
}
