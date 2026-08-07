@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.petro.smsapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.NotificationActionSetting
import com.petro.smsapp.data.PopupActionDisplayMode
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * تنظیمات جدا برای دکمه‌های پاپ‌آپ پیامک:
 * - ترتیب و فعال/غیرفعال (همان عملیات نوتیف، لیست مستقل)
 * - نحوه نمایش: آیکن+متن / فقط آیکن / فقط متن
 */
@Composable
fun PopupActionsSettingsScreen(
    actions: List<NotificationActionSetting>,
    displayMode: PopupActionDisplayMode,
    onSaveActions: (List<NotificationActionSetting>) -> Unit,
    onSaveDisplayMode: (PopupActionDisplayMode) -> Unit,
    onBack: () -> Unit
) {
    var localActions by remember(actions) { mutableStateOf(actions) }
    var localDisplayMode by remember(displayMode) { mutableStateOf(displayMode) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localActions = localActions.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onSaveActions(localActions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دکمه‌های پاپ‌آپ") },
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
                text = "حداکثر ۳ دکمه فعال اول روی پاپ‌آپ پیامک نمایش داده می‌شوند. بقیه پشت منوی ⋮ می‌روند.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Text(
                text = "نحوه نمایش دکمه",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            PopupActionDisplayMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = localDisplayMode == mode,
                            onClick = {
                                localDisplayMode = mode
                                onSaveDisplayMode(mode)
                            },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = localDisplayMode == mode,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "عملیات و ترتیب",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                        PopupActionSettingRow(
                            setting = setting,
                            isDragging = isDragging,
                            onToggle = { enabled ->
                                localActions = localActions.toMutableList().apply {
                                    this[index] = this[index].copy(enabled = enabled)
                                }
                                onSaveActions(localActions)
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
private fun PopupActionSettingRow(
    setting: NotificationActionSetting,
    isDragging: Boolean,
    onToggle: (Boolean) -> Unit,
    dragModifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier)
            .let { mod ->
                if (isDragging) {
                    mod
                        .scale(1.03f)
                        .shadow(8.dp, shape = RoundedCornerShape(8.dp))
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
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
