package com.petro.smsapp.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.data.SimInfo
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
 * یه پیامِ نمایش‌داده‌شده تو تاریخچه‌ی کوچیکِ داخلِ پاپ‌آپ - یا همون پیامِ اصلیِ
 * تازه‌رسیده (isOutgoing=false)، یا یه پاسخی که خودِ کاربر همینجا فرستاده
 * (isOutgoing=true). چون دیگه با فرستادنِ پاسخ پنجره بسته نمیشه، این لیست نگه‌داشته
 * میشه تا کاربر ببینه چی رفت و بیاد و لازم شد دوباره جواب بده.
 *
 * status/type/messageId برای تیک‌های ارسال و دلیوری (هم‌قاعده‌ی حباب‌های ThreadScreen):
 * - status == 0 → تحویل داده شد (DoneAll سبز)
 * - STATUS_FAILED / MESSAGE_TYPE_FAILED → خطا
 * - MESSAGE_TYPE_OUTBOX / QUEUED → در حال ارسال
 * - در غیر این صورت برای outgoing → ارسال شد (Done خاکستری)
 */
data class MessageEntry(
    val text: String,
    val isOutgoing: Boolean,
    val timestampMillis: Long,
    val messageId: Long = -1L,
    val status: Int = -1,
    val type: Int = -1
) {
    val isDelivered: Boolean get() = isOutgoing && status == 0
    val isFailed: Boolean
        get() = isOutgoing && (
                status == android.provider.Telephony.Sms.STATUS_FAILED ||
                        type == android.provider.Telephony.Sms.MESSAGE_TYPE_FAILED
                )
    val isSending: Boolean
        get() = isOutgoing && type == android.provider.Telephony.Sms.MESSAGE_TYPE_OUTBOX
    val isQueued: Boolean
        get() = isOutgoing && type == android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
}

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickReplyPopupScreen(
    senderDisplayName: String,
    senderAddress: String,
    isKnownContact: Boolean,
    photoUri: String?,
    messages: List<MessageEntry>,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    receivedAtMillis: Long,
    primaryActions: List<QuickReplyPopupAction>,
    overflowActions: List<QuickReplyPopupAction>,
    // ناوبریِ صف - وقتی همزمان چند مکالمه تو صفِ پاپ‌آپ منتظرن. totalSessions<=۱
    // یعنی صف خالیه و کلِ ناوبری مخفی میشه.
    totalSessions: Int = 1,
    currentSessionPosition: Int = 1,
    onSwitchToPrevious: () -> Unit = {},
    onSwitchToNext: () -> Unit = {},
    // دکمه‌ی «نمایشِ پیام‌های قبلی» - فقط وقتی showHistoryButton=true نشون داده
    // میشه (یعنی هنوز برای این مکالمه لود نشده)؛ زدنش onLoadHistory رو صدا می‌زنه
    // و خودش بعدِ لود (چه نتیجه خالی چه پر) از طرفِ caller مخفی میشه.
    showHistoryButton: Boolean = false,
    onLoadHistory: () -> Unit = {},
    onOpenThread: () -> Unit,
    onCallSender: () -> Unit,
    onSendReply: (text: String) -> Unit,
    onClose: () -> Unit,
    // انتخاب سیم‌کارت - فقط وقتی حداقل ۲ سیم فعال باشه چیپ نشون داده میشه
    sims: List<SimInfo> = emptyList(),
    selectedSubscriptionId: Int? = null,
    onSimSelect: (Int) -> Unit = {}
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val replyFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current
    val settings by AppSettings.state.collectAsState()
    var isPending by remember { mutableStateOf(false) }
    var pendingSnapshot by remember { mutableStateOf("") }

    val messagesScrollState = rememberScrollState()
    LaunchedEffect(messages.size) {
        messagesScrollState.animateScrollTo(messagesScrollState.maxValue)
    }

    fun sendCurrentReply() {
        val text = replyText
        if (text.isBlank() || isPending) return
        val delay = settings.sendDelaySeconds
        if (delay > 0) {
            pendingSnapshot = text
            onReplyTextChange("")
            isPending = true
        } else {
            onSendReply(text)
            onReplyTextChange("")
        }
    }

    // آفستِ عمودیِ دستی (با درگ‌کردنِ کارت) - جدا از جابه‌جاییِ خودکارِ بالای کیبورد
    var dragOffsetY by remember { mutableStateOf(0f) }
    // ارتفاعِ واقعیِ کارت (پیکسل) - برای اینکه بدونیم تا کجا میشه درگش کرد بدون
    // اینکه از بالای صفحه بیرون بزنه
    var cardHeightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            // وقتی کیبورد باز میشه، این پدینگ خودش فضای در دسترس رو کم می‌کنه، پس
            // BoxWithConstraintsِ زیرش از همون اول ارتفاعِ درستِ بالای کیبورد رو می‌بینه
            .imePadding()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxHeightPx = with(density) { maxHeight.toPx() }

            // هر بار که فضای در دسترس عوض بشه (مثلاً کیبورد باز/بسته بشه) یا ارتفاعِ
            // کارت مشخص/عوض بشه، آفستِ فعلی رو با محدوده‌ی جدید تنظیم می‌کنیم تا کارت
            // هیچ‌وقت از بالای صفحه (یا زیرِ کیبورد) بیرون نزنه
            LaunchedEffect(maxHeightPx, cardHeightPx) {
                val minOffset = -(maxHeightPx - cardHeightPx).coerceAtLeast(0f)
                dragOffsetY = dragOffsetY.coerceIn(minOffset, 400f)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                    .onSizeChanged { cardHeightPx = it.height.toFloat() }
                    // کلِ کارت درگ‌پذیره، نه فقط دستگیره‌ی بالاش - چون move فقط بعد از
                    // consume شدنِ حرکت اعمال میشه، تپ‌های معمولی رو (روی دکمه‌ها، فیلدِ
                    // متن و ...) دست‌نخورده می‌ذاره
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val minOffset = -(maxHeightPx - cardHeightPx).coerceAtLeast(0f)
                                dragOffsetY = (dragOffsetY + dragAmount.y)
                                    .coerceIn(minOffset, 400f)
                            }
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // ---- دستگیره‌ی درگ - صرفاً نشونه‌ی بصریه؛ خودِ درگ رویِ کلِ کارت
                    // فعاله (بالاتر، رویِ Surface) ----
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )
                    }

                    // ---- هدر: آواتار + اسم/شماره + ساعت + بستن - زدنِ آواتار/اسم/شماره
                    // میره برایِ تماس ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (photoUri != null) {
                            ContactAvatar(
                                name = senderDisplayName,
                                address = senderAddress,
                                size = 44.dp,
                                modifier = Modifier.clickable(onClick = onCallSender)
                            )
                        } else {
                            InitialAvatar(name = senderDisplayName, onClick = onCallSender)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onCallSender)
                        ) {
                            Text(
                                text = senderDisplayName,
                                style = MaterialTheme.typography.titleMedium.autoDirection(),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // اگه این آدرس واقعاً یه مخاطبِ ذخیره‌شده باشه، شماره‌ی خودش هم
                            // زیرِ اسم نشون داده بشه - همیشه چپ‌به‌راست. لانگ‌کلیک روش
                            // شماره رو کپی می‌کنه (تپِ معمولیش هم مثلِ بقیه‌ی هدر میره تماس)
                            if (isKnownContact && senderAddress.isNotBlank() && senderAddress != senderDisplayName) {
                                Text(
                                    text = senderAddress,
                                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                    color = Color.Gray,
                                    maxLines = 1,
                                    modifier = Modifier.combinedClickable(
                                        onClick = onCallSender,
                                        onLongClick = {
                                            clipboardManager.setText(AnnotatedString(senderAddress))
                                            Toast.makeText(context, "شماره کپی شد", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                )
                            }
                        }

                        Text(
                            text = DateFormatter.formatSmart(receivedAtMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // ---- ناوبریِ صف - فقط وقتی بیشتر از یه مکالمه تو صفه نشون داده
                        // میشه. فلش‌ها بینِ مکالمه‌های صف می‌چرخونن، بدونِ اینکه چیزی از
                        // هیچ‌کدوم (تاریخچه/پاسخِ درحالِ تایپ) از دست بره ----
                        if (totalSessions > 1) {
                            IconButton(onClick = onSwitchToPrevious, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "مکالمه‌ی قبلی")
                            }
                            Text(
                                text = "$currentSessionPosition/$totalSessions",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            IconButton(onClick = onSwitchToNext, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "مکالمه‌ی بعدی")
                            }
                        }

                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "بستن")
                        }
                    }

                    // ---- دکمه‌ی «نمایشِ پیام‌های قبلی» - عمداً به‌جای لودِ خودکار، فقط
                    // با تپِ کاربر تاریخچه‌ی این گفتگو از دیتابیس خونده میشه (تا پاپ‌آپ
                    // بدونِ تاخیر/کوئریِ اضافه‌ی همیشگی ظاهر بشه) ----
                    if (showHistoryButton) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = onLoadHistory, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("نمایشِ پیام‌های قبلی", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ---- بدنه‌ی پیام‌ها - تاریخچه‌ی کوچیکِ پیامِ اصلی + پاسخ‌هایی که همینجا
                    // فرستاده شدن، قابلِ اسکرول (سقفِ ارتفاع تا کارت زیادی بزرگ نشه). تپِ
                    // هرکدوم میره داخلِ برنامه رویِ همون پیام، لانگ‌کلیک متنش رو کپی می‌کنه ----
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(messagesScrollState),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        messages.forEach { entry ->
                            MessageBubbleRow(
                                entry = entry,
                                onClick = onOpenThread,
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(entry.text))
                                    Toast.makeText(context, "متن پیام کپی شد", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isPending && settings.sendDelaySeconds > 0) {
                        PendingMessageBubble(
                            text = pendingSnapshot,
                            delaySeconds = settings.sendDelaySeconds,
                            onCancel = {
                                onReplyTextChange(pendingSnapshot)
                                isPending = false
                                pendingSnapshot = ""
                            },
                            onSendTimeout = {
                                onSendReply(pendingSnapshot)
                                isPending = false
                                pendingSnapshot = ""
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ---- ردیفِ ورودیِ پاسخِ سریع - همیشه نمایش داده میشه (دیگه نیازی به
                    // زدنِ دکمه‌ی «پاسخ» برای بازکردنش نیست). انتخاب سیم مثل MessageInputBar
                    // داخلِ خودِ کادر (trailingIcon) نشون داده میشه. ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = onReplyTextChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .focusRequester(replyFocusRequester),
                            placeholder = { Text("پاسخ سریع...") },
                            singleLine = true,
                            enabled = !isPending,
                            shape = RoundedCornerShape(20.dp),
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendCurrentReply() }),
                            trailingIcon = {
                                if (sims.size >= 2) {
                                    PopupSimSelectChip(
                                        sims = sims,
                                        selectedSubscriptionId = selectedSubscriptionId,
                                        onSelect = onSimSelect
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { sendCurrentReply() },
                            enabled = replyText.isNotBlank() && !isPending
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
}

@Composable
private fun PopupActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * یه ردیفِ پیام تو تاریخچه‌ی کوچیکِ داخلِ پاپ‌آپ - پیامِ رسیده سمتِ شروع (رنگِ خنثی)،
 * پاسخِ فرستاده‌شده سمتِ پایان (رنگِ اصلیِ تم)، شبیهِ حباب‌های خودِ صفحه‌ی چت ولی
 * ساده‌تر چون فضای پاپ‌آپ محدوده.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleRow(
    entry: MessageEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = if (entry.isOutgoing) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (entry.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                color = when {
                    entry.isFailed -> MaterialTheme.colorScheme.errorContainer
                    entry.isOutgoing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium.autoDirection(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        // ---- تاریخ+ساعت + تیک ارسال/دلیوری (هم‌قاعده‌ی ThreadScreen) ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, end = 4.dp)
        ) {
            Text(
                text = DateFormatter.formatDateTimeShort(entry.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (entry.isOutgoing) {
                Spacer(modifier = Modifier.width(4.dp))
                when {
                    entry.isFailed -> Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = "ارسال نشد",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    entry.isSending || entry.isQueued -> Icon(
                        Icons.Filled.Schedule,
                        contentDescription = if (entry.isQueued) "در صف ارسال" else "در حال ارسال",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    entry.isDelivered -> Icon(
                        Icons.Filled.DoneAll,
                        contentDescription = "تحویل داده شد",
                        tint = Color(0xFF34A853),
                        modifier = Modifier.size(14.dp)
                    )
                    else -> Icon(
                        Icons.Filled.Done,
                        contentDescription = "ارسال شد",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * چیپِ انتخاب سیم داخل پاپ‌آپ - هم‌شکلِ MessageInputBar.SimQuickSelectChip
 * (فقط اینجا public/reusable برای خودِ پاپ‌آپ).
 */
@Composable
private fun PopupSimSelectChip(
    sims: List<SimInfo>,
    selectedSubscriptionId: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSim = sims.find { it.subscriptionId == selectedSubscriptionId } ?: sims.firstOrNull()
    val label = selectedSim?.let { (it.slotIndex + 1).toString() } ?: "?"

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .padding(end = 4.dp)
                .size(width = 26.dp, height = 22.dp)
                .clickable { expanded = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text(sim.displayName) },
                    onClick = {
                        onSelect(sim.subscriptionId)
                        expanded = false
                    }
                )
            }
        }
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