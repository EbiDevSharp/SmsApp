@file:OptIn(ExperimentalFoundationApi::class)

package com.petro.smsapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.NotificationActionSetting
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun NotificationActionsSettingsScreen(
    actions: List<NotificationActionSetting>,
    onSave: (List<NotificationActionSetting>) -> Unit,
    onBack: () -> Unit
) {
    var localActions by remember(actions) {
        mutableStateOf(actions)
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localActions = localActions.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onSave(localActions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دکمه‌های نوتیفیکیشن") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "حداکثر ۳ دکمه فعال اول، در نوتیفیکیشن پیامک نمایش داده می‌شوند",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = localActions,
                    key = { _, item -> item.type.id }
                ) { index, setting ->
                    ReorderableItem(
                        reorderableState,
                        key = setting.type.id
                    ) { isDragging ->
                        NotificationActionRow(
                            setting = setting,
                            isDragging = isDragging,
                            onToggle = { enabled ->
                                localActions = localActions.toMutableList().apply {
                                    this[index] = this[index].copy(enabled = enabled)
                                }
                                onSave(localActions)
                            },
                            dragModifier = Modifier.draggableHandle()
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationActionRow(
    setting: NotificationActionSetting,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    dragModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier) // کل ردیف قابل درگ
            .let { mod ->
                if (isDragging) {
                    mod
                        .scale(1.03f)                                // بزرگ‌تر
                        .shadow(8.dp, shape = RoundedCornerShape(8.dp)) // سایه همراستا با گوشه‌ها
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))               // برش محتوا برای گوشه‌های گرد
                } else {
                    mod
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = "جابجایی",
            modifier = Modifier.size(32.dp).padding(end = 8.dp)
        )

        Text(
            text = setting.type.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = setting.enabled,
            onCheckedChange = onToggle
        )
    }
}