package com.petro.smsapp.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun <T> SettingsDropdown(
    title: String,
    current: T,
    items: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    info: String? = null,
    onInfo: ((String) -> Unit)? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val currentText = items.firstOrNull { it.first == current }?.second ?: ""
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val valueColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) Modifier.clickable { expanded = true }
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor
                    )
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = valueColor
                    )
                }
                if (info != null) {
                    IconButton(
                        onClick = { onInfo?.invoke(info) },
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "توضیحات",
                            tint = iconTint
                        )
                    }
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = iconTint
            )
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(220.dp)
                .padding(4.dp)
        ) {
            items.forEach { item ->
                val selected = item.first == current
                DropdownMenuItem(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .then(
                            if (selected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp)
                                )
                            } else {
                                Modifier
                            }
                        ),
                    text = {
                        Text(
                            text = item.second,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        onChange(item.first)
                        expanded = false
                    }
                )
            }
        }
    }
}
