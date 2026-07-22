package com.petro.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی چت یک مخاطب. دو تا قابلیت مهم علاوه بر ارسال/دریافت معمولی:
 *
 * ۱) حالت «انتخاب چندتایی» - دقیقاً همون الگوی لیست مکالمات: لانگ‌کلیک روی یه پیام وارد
 *    حالت انتخاب میشه، تک‌کلیک بعدی روی هر پیام دیگه فقط انتخاب/عدم‌انتخابش می‌کنه (دیگه
 *    منوی اکشن پیام باز نمیشه)، نوار بالا با تعداد انتخاب‌شده + انتخاب‌همه + حذف عوض میشه.
 *
 * ۲) زوم فونت با دو انگشت (Pinch) - این یه Scale واقعی روی کل صفحه نیست؛ فقط اندازه‌ی فونت
 *    متن پیام‌ها (که حباب‌ها هم چون دور همون متن رو می‌گیرن، خودشون بزرگ/کوچیک میشن) بین
 *    16sp تا 28sp تغییر می‌کنه. کاربر حس می‌کنه صفحه رو زوم کرده، ولی فقط متنه که عوض میشه.
 */
@Composable
fun ThreadScreen(
    displayName: String,
    messages: List<SmsMessage>,
    sims: List<SimInfo>,
    favoriteIds: Set<Long>,
    onSend: (body: String, subscriptionId: Int?) -> Unit,
    onDeleteMessage: (messageId: Long) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onOpenNote: (text: String) -> Unit,
    onToggleFavorite: (message: SmsMessage) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }
    var selectedMessage by remember { mutableStateOf<SmsMessage?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 1f = فونت پایه (16sp)، 1.75f = حداکثر زوم (28sp)
    var fontScale by remember { mutableStateOf(1f) }
    val selectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(sims) {
        if (selectedSimId == null && sims.isNotEmpty()) {
            selectedSimId = sims.first().subscriptionId
        }
    }

    // اگه بعد از حذف/تغییر لیست، بعضی id های انتخاب‌شده دیگه وجود نداشته باشن، از انتخاب پاک بشن
    LaunchedEffect(messages) {
        val stillExisting = messages.map { it.id }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    // دکمه‌ی برگشت سیستم: اگه توی حالت انتخابیم، فقط از انتخاب خارج شو، از چت خارج نشو
    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف پیام‌ها") },
            text = { Text("${selectedIds.size} پیام حذف بشه؟") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteMessages(selectedIds)
                    selectedIds = emptySet()
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

    val currentSelectedMessage = selectedMessage
    if (currentSelectedMessage != null) {
        MessageActionsSheet(
            message = currentSelectedMessage,
            contactDisplayName = displayName,
            isFavorite = favoriteIds.contains(currentSelectedMessage.id),
            onDismiss = { selectedMessage = null },
            onOpenNote = { onOpenNote(currentSelectedMessage.body) },
            onDeleteConfirmed = {
                onDeleteMessage(currentSelectedMessage.id)
                selectedMessage = null
            },
            onToggleFavorite = { onToggleFavorite(currentSelectedMessage) }
        )
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} انتخاب شده") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "لغو انتخاب")
                        }
                    },
                    actions = {
                        val allSelected = selectedIds.size == messages.size && messages.isNotEmpty()
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else messages.map { it.id }.toSet()
                        }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = if (allSelected) "از انتخاب دراوردن همه" else "انتخاب همه"
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(displayName) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Text("←") }
                    }
                )
            }
        },
        bottomBar = {
            if (!selectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    SimSelector(
                        sims = sims,
                        selectedSubscriptionId = selectedSimId,
                        onSelect = { selectedSimId = it }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // دکمه ارسال اول میاد تا توی چیدمان راست‌به‌چپ سمت راست کادر بشینه
                        Button(onClick = {
                            if (input.isNotBlank()) {
                                onSend(input, selectedSimId)
                                input = ""
                            }
                        }) {
                            Text("ارسال")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("پیام...") },
                            maxLines = 5,
                            // متن انگلیسی/اعداد از چپ نوشته بشن، حتی داخل کانتینر راست‌به‌چپ
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
                        )
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // زوم فونت با دو انگشت - فقط اندازه‌ی متن عوض میشه، نه واقعاً اسکیل کل صفحه
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontScale = (fontScale * zoom).coerceIn(1f, 1.75f)
                    }
                }
        ) {
            // هر بار فضای واقعی در دسترس عوض بشه (باز/بسته شدن کیبورد، چرخش صفحه و ...)
            // دوباره برو آخرین پیام تا زیر کادر ارسال قایم نمونه
            val availableHeight = maxHeight
            LaunchedEffect(availableHeight, messages.size) {
                if (messages.isNotEmpty()) {
                    // چون reverseLayout=true هست، ایندکس ۰ = پایین‌ترین/جدیدترین پیام
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(messages.reversed(), key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isFavorite = favoriteIds.contains(message.id),
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(message.id),
                        fontScale = fontScale,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (selectedIds.contains(message.id)) {
                                    selectedIds - message.id
                                } else {
                                    selectedIds + message.id
                                }
                            } else {
                                selectedMessage = message
                            }
                        },
                        onDoubleClick = {
                            if (!selectionMode) onOpenNote(message.body)
                        },
                        onLongClick = {
                            if (!selectionMode) selectedIds = setOf(message.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: SmsMessage,
    isFavorite: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    fontScale: Float,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isOutgoing) MaterialTheme.colorScheme.primary else Color(0xFFE5E5EA)
    val textColor = if (message.isOutgoing) Color.White else Color.Black
    val fontSize = (16 * fontScale).sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(isSelected = isSelected)
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
        ) {
            Box(contentAlignment = alignment, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(4.dp)
                        // تک‌کلیک: منوی اکشن پیام (یا انتخاب، توی حالت انتخاب) - دابل‌کلیک: نوت - لانگ‌کلیک: ورود به حالت انتخاب
                        .combinedClickable(
                            onClick = onClick,
                            onDoubleClick = onDoubleClick,
                            onLongClick = onLongClick
                        )
                ) {
                    Text(
                        text = message.body,
                        color = textColor,
                        fontSize = fontSize,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = DateFormatter.formatSmart(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                // تیک دلیوری: فقط برای پیام‌های ارسالی که گزارش تحویل موفق براشون برگشته
                if (message.isDelivered) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = "تحویل داده شد",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // نشان ستاره: پیام فیوریت‌شده (قفل‌شده در برابر حذف)
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "فیوریت‌شده",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/** آواتار جایگزین توی حالت انتخاب: دایره‌ی خالی وقتی انتخاب نشده، دایره‌ی رنگی با تیک وقتی انتخاب شده */
@Composable
private fun SelectionCheck(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "انتخاب‌شده",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
