package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.autoDirection
import kotlin.math.roundToInt

/**
 * یک دکمه‌ی اکشنِ پاپ‌آپ - عیناً هم‌خانواده‌ی NotificationActionSetting، فقط اینجا
 * برچسبِ کوتاه (برای جا شدن کنارِ هم، شبیه Done/Open/Reply تصویرِ نمونه) و آیکنش
 * جدا مشخص میشه. type=null یعنی این یه اکشنِ ثابتِ خودِ پاپ‌آپه (نه از تنظیماتِ
 * دکمه‌های نوتیف)، مثلِ «باز کردن» که همیشه باید باشه.
 */
data class QuickReplyPopupAction(
    val type: NotificationActionType?,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * پاپ‌آپِ روی صفحه‌ی پیامکِ تازه‌رسیده - جایگزینِ نوتیفِ معمولی وقتی کاربر از تنظیمات
 * فعالش کرده باشه. کاملاً مستقل از Activity/Context میزبانه؛ فقط دیتا و کال‌بک می‌گیره.
 *
 * دکمه‌های اصلی (حداکثر ۳ تا، طبقِ همون ترتیب/فعال‌بودنِ تنظیماتِ دکمه‌های نوتیف)
 * مستقیم کنارِ هم نشون داده میشن؛ بقیه پشتِ دکمه‌ی سه‌نقطه (⋮) میرن، دقیقاً هم‌قاعده‌ی
 * منطقِ فعلیِ SmsDeliverReceiver.showNotification (enabledActions.take(3)).
 *
 * زدنِ «پاسخ» به‌جای بازکردنِ RemoteInput سیستمی، همینجا یه فیلدِ متنیِ inline باز
 * می‌کنه (چون خودمون UI کامل داریم و نیازی به RemoteInput نیست).
 */
@Composable
fun QuickReplyPopupScreen(
    senderDisplayName: String,
    senderAddress: String,
    isKnownContact: Boolean,
    photoUri: String?,
    messageBody: String,
    receivedAtMillis: Long,
    primaryActions: List<QuickReplyPopupAction>,
    overflowActions: List<QuickReplyPopupAction>,
    onOpenThread: () -> Unit,
    onSendReply: (text: String) -> Unit,
    onClose: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var overflowExpanded by remember { mutableStateOf(false) }
    val replyFocusRequester = remember { FocusRequester() }

    // آفستِ عمودیِ دستی (با درگ‌کردنِ هدر) - جدا از جابه‌جاییِ خودکارِ بالای کیبورد
    var dragOffsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            // وقتی کیبورد باز میشه، کارت خودش خودکار میره بالای کیبورد تا زیرش گم نشه
            .imePadding()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffsetY.roundToInt()) },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ---- دستگیره‌ی درگ - با کشیدنش می‌تونی کارت رو بالاتر/پایین‌تر ببری
                // (مثلاً وقتی کیبورد بازه و می‌خوای دکمه‌های پایینی رو ببینی) ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY = (dragOffsetY + dragAmount.y)
                                        .coerceIn(-1600f, 400f)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                }

                // ---- هدر: آواتار + اسم/شماره + ساعت + بستن ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoUri != null) {
                        ContactAvatar(
                            name = senderDisplayName,
                            address = senderAddress,
                            size = 44.dp,
                            modifier = Modifier.clickable(onClick = onOpenThread)
                        )
                    } else {
                        InitialAvatar(name = senderDisplayName, onClick = onOpenThread)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenThread)
                    ) {
                        Text(
                            text = senderDisplayName,
                            style = MaterialTheme.typography.titleMedium.autoDirection(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // اگه این آدرس واقعاً یه مخاطبِ ذخیره‌شده باشه، شماره‌ی خودش هم
                        // زیرِ اسم نشون داده بشه - همیشه چپ‌به‌راست
                        if (isKnownContact && senderAddress.isNotBlank() && senderAddress != senderDisplayName) {
                            Text(
                                text = senderAddress,
                                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }

                    Text(
                        text = DateFormatter.formatSmart(receivedAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- بدنه‌ی پیام ----
                Text(
                    text = messageBody,
                    style = MaterialTheme.typography.bodyLarge.autoDirection(),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ---- ردیفِ ورودیِ پاسخِ سریع - همیشه نمایش داده میشه (دیگه نیازی به
                // زدنِ دکمه‌ی «پاسخ» برای بازکردنش نیست) ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .focusRequester(replyFocusRequester),
                        placeholder = { Text("پاسخ سریع...") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (replyText.isNotBlank()) {
                                onSendReply(replyText)
                                replyText = ""
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onSendReply(replyText)
                                replyText = ""
                            }
                        },
                        enabled = replyText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "ارسال پاسخ")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ---- ردیفِ دکمه‌های اکشن ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        primaryActions.forEach { action ->
                            val isReplyAction = action.type == NotificationActionType.REPLY
                            PopupActionButton(
                                label = action.label,
                                icon = action.icon,
                                onClick = {
                                    if (isReplyAction) {
                                        replyFocusRequester.requestFocus()
                                    } else {
                                        action.onClick()
                                    }
                                }
                            )
                        }
                    }

                    if (overflowActions.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "عملیات بیشتر")
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false }
                            ) {
                                overflowActions.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        leadingIcon = { Icon(action.icon, contentDescription = null) },
                                        onClick = {
                                            overflowExpanded = false
                                            action.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InitialAvatar(name: String, onClick: () -> Unit) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), shape = androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}