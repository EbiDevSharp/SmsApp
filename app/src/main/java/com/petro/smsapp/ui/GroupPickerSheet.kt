package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.util.autoDirection

/**
 * شیتِ کوچیکِ «به کدوم گروه اضافه بشه؟» - از سه جا صدا زده میشه: سویپ/منویِ لیستِ
 * مکالمات، دکمه‌ی روی نوتیف. اگه گروهی وجود نداشته باشه یا کاربر بخواد گروهِ تازه
 * بسازه، یه فرمِ ساختِ سریعِ گروه (فقط اسم، با تنظیماتِ پیش‌فرض) همینجا باز میشه.
 */
@Composable
fun GroupPickerSheet(
    targetLabel: String,
    groups: List<FilterGroupSummary>,
    onPick: (groupId: Long) -> Unit,
    onCreateAndPick: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showCreateForm by remember { mutableStateOf(groups.isEmpty()) }
    var newGroupName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "افزودنِ «$targetLabel» به کدوم گروه؟",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (!showCreateForm) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(groups, key = { it.group.id }) { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(summary.group.id) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(summary.group.name, style = MaterialTheme.typography.bodyLarge.autoDirection())
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateForm = true }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("گروهِ جدید", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    if (groups.isEmpty()) {
                        Text(
                            "هنوز هیچ گروهی نساختی - یه اسم بده تا هم گروه ساخته بشه هم همین شماره بره توش",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("اسمِ گروهِ جدید") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        if (groups.isNotEmpty()) {
                            TextButton(onClick = { showCreateForm = false }) { Text("انصراف") }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = { onCreateAndPick(newGroupName.trim()) },
                            enabled = newGroupName.isNotBlank()
                        ) { Text("ساخت و افزودن") }
                    }
                }
            }
        }
    }
}
