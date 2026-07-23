package com.petro.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.NotificationActionSetting

/**
 * صفحه‌ی مدیریت دکمه‌های نوتیف پیامک: می‌شه هر دکمه رو روشن/خاموش کرد و با پیکان‌های
 * بالا/پایین جاش رو عوض کرد. همون لیست (به همون ترتیب) عیناً تو AppSettings ذخیره میشه؛
 * از داخلش هر بار حداکثر ۳ تای فعالِ اول برای ساختن نوتیف واقعی استفاده میشه.
 */
@Composable
fun NotificationActionsSettingsScreen(
    actions: List<NotificationActionSetting>,
    onSave: (List<NotificationActionSetting>) -> Unit,
    onBack: () -> Unit
) {
    var localActions by remember(actions) { mutableStateOf(actions) }

    fun moveUp(index: Int) {
        if (index <= 0) return
        localActions = localActions.toMutableList().apply {
            val tmp = this[index - 1]
            this[index - 1] = this[index]
            this[index] = tmp
        }
        onSave(localActions)
    }

    fun moveDown(index: Int) {
        if (index >= localActions.size - 1) return
        localActions = localActions.toMutableList().apply {
            val tmp = this[index + 1]
            this[index + 1] = this[index]
            this[index] = tmp
        }
        onSave(localActions)
    }

    fun toggle(index: Int, enabled: Boolean) {
        localActions = localActions.toMutableList().apply {
            this[index] = this[index].copy(enabled = enabled)
        }
        onSave(localActions)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دکمه‌های نوتیفیکیشن") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "حداکثر ۳ تا از دکمه‌های فعال (به همین ترتیب از بالا) روی نوتیف پیامک نشون داده میشن",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            LazyColumn {
                itemsIndexed(localActions, key = { _, item -> item.type.id }) { index, setting ->
                    NotificationActionRow(
                        setting = setting,
                        canMoveUp = index > 0,
                        canMoveDown = index < localActions.size - 1,
                        onToggle = { enabled -> toggle(index, enabled) },
                        onMoveUp = { moveUp(index) },
                        onMoveDown = { moveDown(index) }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun NotificationActionRow(
    setting: NotificationActionSetting,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "جابه‌جایی به بالا")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "جابه‌جایی به پایین")
            }
        }
        Text(
            text = setting.type.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Switch(checked = setting.enabled, onCheckedChange = onToggle)
    }
}
