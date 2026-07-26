package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
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
import com.petro.smsapp.data.BlockPattern
import com.petro.smsapp.data.BlockPatternType
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی «الگوهای بلاکِ شماره» - کاربر اینجا الگوهایی برای **شماره‌ی فرستنده** تعریف می‌کنه
 * (نه متنِ پیام): «شروع با ...» (مثلاً +98 یا 0930) یا «پایان با ...» (مثلاً 9325). هر پیامِ
 * ورودی‌ای که شماره‌ی فرستنده‌ش با یکی از این الگوها مچ بشه، خودکار بلاک میشه (بدون نوتیف/صدا) -
 * دقیقاً هم‌خانواده‌ی «کلمات کلیدی بلاک» ولی روی شماره به‌جای متن.
 */
@Composable
fun BlockPatternsScreen(
    patterns: List<BlockPattern>,
    onBack: () -> Unit,
    onAddPattern: (type: BlockPatternType, value: String) -> Unit,
    onRemovePattern: (id: String) -> Unit
) {
    var newValue by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(BlockPatternType.STARTS_WITH) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الگوهای بلاکِ شماره") },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == BlockPatternType.STARTS_WITH,
                    onClick = { selectedType = BlockPatternType.STARTS_WITH },
                    label = { Text("شروع شماره با...") }
                )
                FilterChip(
                    selected = selectedType == BlockPatternType.ENDS_WITH,
                    onClick = { selectedType = BlockPatternType.ENDS_WITH },
                    label = { Text("پایان شماره با...") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    label = {
                        Text(
                            if (selectedType == BlockPatternType.STARTS_WITH) "مثلاً +98 یا 0930"
                            else "مثلاً 9325"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (newValue.isNotBlank()) {
                            onAddPattern(selectedType, newValue)
                            newValue = ""
                        }
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "افزودن الگو")
                }
            }

            Text(
                text = "هر پیامِ ورودی‌ای که شماره‌ی فرستنده‌ش با یکی از این الگوها مطابقت داشته باشه، خودکار بلاک میشه (بدون نوتیف/صدا)، حتی اگه خودِ شماره جداگانه بلاک نشده باشه.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (patterns.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("هیچ الگویی اضافه نشده", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(patterns, key = { it.id }) { pattern ->
                        BlockPatternRow(pattern = pattern, onRemove = { onRemovePattern(pattern.id) })
                        Divider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockPatternRow(pattern: BlockPattern, onRemove: () -> Unit) {
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
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (pattern.type == BlockPatternType.STARTS_WITH) Icons.Filled.ArrowForward else Icons.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pattern.value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (pattern.type == BlockPatternType.STARTS_WITH) "شروع شماره با این عبارت" else "پایان شماره با این عبارت",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "اضافه‌شده در ${DateFormatter.formatFull(pattern.addedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "حذف الگو")
        }
    }
}
