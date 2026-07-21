package com.petro.smsapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.util.DateFormatter

/**
 * منوی کلیک روی پیام: «جزئیات پیام»، «باز کردن در نوت» و «حذف پیام» (با تائید کاربر).
 * آیتم‌های بعدی (کپی، پاسخ، فوروارد و ...) به لیست‌آیتم‌های داخل بخش منو اضافه میشن.
 */
@Composable
fun MessageActionsSheet(
    message: SmsMessage,
    contactDisplayName: String,
    onDismiss: () -> Unit,
    onOpenNote: () -> Unit,
    onDeleteConfirmed: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showDetails by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف پیام") },
            text = { Text("این پیام حذف بشه؟ این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteConfirmed()
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (!showDetails) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                MenuRow(
                    icon = Icons.Filled.Info,
                    label = "جزئیات پیام",
                    onClick = { showDetails = true }
                )
                MenuRow(
                    icon = Icons.Filled.Notes,
                    label = "باز کردن در نوت",
                    onClick = {
                        onDismiss()
                        onOpenNote()
                    }
                )
                MenuRow(
                    icon = Icons.Filled.Delete,
                    label = "حذف پیام",
                    onClick = { showDeleteConfirm = true }
                )
                // آیتم‌های بعدی (کپی، پاسخ، فوروارد و ...) اینجا اضافه میشن
            }
        } else {
            MessageDetailsContent(message = message, contactDisplayName = contactDisplayName)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
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

@Composable
private fun MessageDetailsContent(message: SmsMessage, contactDisplayName: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
        Text("جزئیات پیام", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        if (message.isOutgoing) {
            DetailRow(Icons.Filled.Send, "زمان ارسال", DateFormatter.formatFull(message.date))
            // زمان دلیوری (تحویل به گیرنده) فقط وقتی گزارش دلیوری موفق برگشته باشه معنی داره
            if (message.isDelivered && message.deliveredAt > 0L) {
                DetailRow(Icons.Filled.Done, "زمان دریافت (دلیوری)", DateFormatter.formatFull(message.deliveredAt))
            } else {
                DetailRow(Icons.Filled.Done, "وضعیت دلیوری", "هنوز تحویل داده نشده")
            }
        } else {
            // برای پیام دریافتی این دو تا می‌تونن واقعاً فرق کنن (مثلاً گوشی خاموش بوده).
            // زمان ارسال از timestamp خود PDU میاد (گزارش شبکه/فرستنده) و منطقاً باید زودتر
            // یا هم‌زمان با زمان دریافت باشه. اگه به هر دلیلی (مثل بعضی امولاتورها که موقع
            // تزریق پیامک تستی timestamp درستی نمی‌سازن) این ترتیب برعکس بود، به‌جای نمایش
            // یه چیز غیرمنطقی، فقط زمان دریافت رو نشون میدیم.
            val sentTime = message.dateSent
            if (sentTime in 1..message.date) {
                DetailRow(Icons.Filled.Send, "زمان ارسال", DateFormatter.formatFull(sentTime))
            }
            DetailRow(Icons.Filled.Done, "زمان دریافت", DateFormatter.formatFull(message.date))
        }
        DetailRow(Icons.Filled.Info, "نوع", typeLabel(message))
        DetailRow(Icons.Filled.Person, "فرستنده", if (message.isOutgoing) "شما" else contactDisplayName)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun typeLabel(message: SmsMessage): String = when (message.type) {
    1 -> "دریافتی"       // MESSAGE_TYPE_INBOX
    2 -> "ارسالی"         // MESSAGE_TYPE_SENT
    3 -> "پیش‌نویس"       // MESSAGE_TYPE_DRAFT
    4 -> "در حال ارسال"   // MESSAGE_TYPE_OUTBOX
    5 -> "ناموفق"         // MESSAGE_TYPE_FAILED
    6 -> "در صف ارسال"    // MESSAGE_TYPE_QUEUED
    else -> if (message.isOutgoing) "ارسالی" else "دریافتی"
}
