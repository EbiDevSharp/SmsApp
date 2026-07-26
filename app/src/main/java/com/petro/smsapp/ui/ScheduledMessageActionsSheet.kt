package com.petro.smsapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * منوی کلیک روی حباب یه پیام زمان‌بندی‌شده (هنوز ارسال نشده): «ویرایش زمان»، «اکنون
 * ارسال شود» و «لغو زمان‌بندی» (با تائید کاربر). جدا از MessageActionsSheet چون این
 * پیام‌ها هنوز SmsMessage واقعی نیستن (هنوز ارسال نشدن)، دیتای متفاوتی دارن.
 */
@Composable
fun ScheduledMessageActionsSheet(
    onDismiss: () -> Unit,
    onEditTime: () -> Unit,
    onSendNow: () -> Unit,
    onCancelSchedule: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showCancelConfirm by remember { mutableStateOf(false) }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("لغو زمان‌بندی") },
            text = { Text("زمان‌بندی این پیام لغو بشه؟ دیگه اصلاً ارسال نمیشه.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancelSchedule()
                }) {
                    Text("لغو زمان‌بندی", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("بی‌خیال")
                }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ScheduledMenuRow(
                icon = Icons.Filled.Schedule,
                label = "ویرایش زمان",
                onClick = {
                    onDismiss()
                    onEditTime()
                }
            )
            ScheduledMenuRow(
                icon = Icons.Filled.Send,
                label = "اکنون ارسال شود",
                onClick = {
                    onDismiss()
                    onSendNow()
                }
            )
            ScheduledMenuRow(
                icon = Icons.Filled.Delete,
                label = "لغو زمان‌بندی",
                onClick = { showCancelConfirm = true }
            )
        }
    }
}

@Composable
private fun ScheduledMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
