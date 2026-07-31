package com.petro.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petro.smsapp.data.ScheduledMessage
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.PhoneNumberUtils
import com.petro.smsapp.util.autoDirection
import kotlinx.coroutines.launch

private sealed class ThreadListItem {
    abstract val sortDate: Long
    data class Real(val message: SmsMessage) : ThreadListItem() {
        override val sortDate get() = message.date
    }
    data class Pending(val scheduled: ScheduledMessage) : ThreadListItem() {
        override val sortDate get() = scheduled.scheduledAt
    }
}

@Composable
fun ThreadScreen(
    displayName: String,
    address: String,
    messages: List<SmsMessage>,
    scheduledMessages: List<ScheduledMessage>,
    sims: List<SimInfo>,
    favoriteIds: Set<Long>,
    pinnedMessageIds: Set<Long> = emptySet(),
    initialDraft: String = "",
    // اینکه این آدرس همین الان تو مخاطبینِ گوشی ذخیره‌ست یا نه - برای انتخابِ آیکن/برچسبِ
    // درستِ دکمه‌ی بالای صفحه (مشاهده‌ی مخاطب در برابرِ افزودنِ مخاطبِ جدید)
    isKnownContact: Boolean = false,
    // با کلیک روی دکمه‌ی مخاطبِ بالای صفحه صدا زده میشه - صداکننده (MainActivity) تصمیم
    // می‌گیره که Intent.ACTION_VIEW (مشاهده) بزنه یا Intent.ACTION_INSERT (افزودن)
    onOpenContactInfo: () -> Unit = {},
    onSend: (body: String, subscriptionId: Int?) -> Unit,
    onScheduleSend: (body: String, subscriptionId: Int?, scheduledAt: Long) -> Unit,
    onDeleteMessage: (messageId: Long) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onOpenNote: (text: String) -> Unit,
    onToggleFavorite: (message: SmsMessage) -> Unit,
    onTogglePinMessage: (message: SmsMessage) -> Unit = {},
    onResend: (message: SmsMessage) -> Unit,
    onUpdateScheduledTime: (id: Long, newTime: Long) -> Unit,
    onSendScheduledNow: (id: Long) -> Unit,
    onCancelScheduledMessage: (id: Long) -> Unit,
    onLeaveWithDraft: (text: String) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var draftApplied by remember { mutableStateOf(false) }
    LaunchedEffect(initialDraft) {
        if (!draftApplied && initialDraft.isNotBlank()) {
            draftApplied = true
            if (input.isBlank()) {
                input = initialDraft
            }
        }
    }
    val latestInput = rememberUpdatedState(input)
    val latestOnLeave = rememberUpdatedState(onLeaveWithDraft)
    DisposableEffect(Unit) {
        onDispose { latestOnLeave.value(latestInput.value) }
    }
    val canSend = remember(address) { PhoneNumberUtils.isSendableAddress(address) }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }
    var selectedMessage by remember { mutableStateOf<SmsMessage?>(null) }
    var selectedScheduledMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    var editingScheduledMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var fontScale by remember { mutableStateOf(1f) }
    var scheduledAt by remember { mutableStateOf<Long?>(null) }
    val selectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val combinedItems = remember(messages, scheduledMessages) {
        (messages.map { ThreadListItem.Real(it) } + scheduledMessages.map { ThreadListItem.Pending(it) })
            .sortedBy { it.sortDate }
    }

    val pinnedInThread = remember(messages, pinnedMessageIds) {
        messages.filter { pinnedMessageIds.contains(it.id) }.sortedBy { it.date }
    }
    var pinnedBannerIndex by remember(pinnedInThread) { mutableStateOf(0) }
    val currentPinnedMessage = pinnedInThread.getOrNull(pinnedBannerIndex)

    LaunchedEffect(sims) {
        if (selectedSimId == null && sims.isNotEmpty()) {
            selectedSimId = sims.first().subscriptionId
        }
    }

    LaunchedEffect(messages) {
        val stillExisting = messages.map { it.id }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    BackHandler(enabled = true) {
        if (selectionMode) {
            selectedIds = emptySet()
        } else {
            onBack()
        }
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
            onToggleFavorite = { onToggleFavorite(currentSelectedMessage) },
            onResend = { onResend(currentSelectedMessage) },
            isPinned = pinnedMessageIds.contains(currentSelectedMessage.id),
            onTogglePin = { onTogglePinMessage(currentSelectedMessage) }
        )
    }

    val currentSelectedScheduled = selectedScheduledMessage
    if (currentSelectedScheduled != null) {
        ScheduledMessageActionsSheet(
            onDismiss = { selectedScheduledMessage = null },
            onEditTime = { editingScheduledMessage = currentSelectedScheduled },
            onSendNow = {
                onSendScheduledNow(currentSelectedScheduled.id)
                selectedScheduledMessage = null
            },
            onCancelSchedule = {
                onCancelScheduledMessage(currentSelectedScheduled.id)
                selectedScheduledMessage = null
            }
        )
    }

    val currentEditingScheduled = editingScheduledMessage
    if (currentEditingScheduled != null) {
        DateTimePickerDialog(
            initialMillis = currentEditingScheduled.scheduledAt,
            onConfirm = {
                onUpdateScheduledTime(currentEditingScheduled.id, it)
                editingScheduledMessage = null
            },
            onDismiss = { editingScheduledMessage = null }
        )
    }

    Scaffold(
        topBar = {
            Column {
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
                        title = {
                            Column {
                                Text(displayName, style = LocalTextStyle.current.autoDirection())
                                // فقط وقتی این آدرس واقعاً تو مخاطبینِ گوشی ذخیره‌ست، شماره‌ش
                                // رو هم زیرِ اسم نشون بده - همیشه چپ‌به‌راست
                                if (isKnownContact) {
                                    Text(
                                        text = address,
                                        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                        color = Color.Gray
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Text("←") }
                        },
                        actions = {
                            // فقط برای آدرس‌های واقعاً قابل‌ارسال (شماره) معنی داره - برای
                            // Sender ID های حروفی (اسمِ اپراتور و ...) نه مشاهده معنی داره نه افزودن
                            if (canSend) {
                                IconButton(onClick = onOpenContactInfo) {
                                    Icon(
                                        imageVector = if (isKnownContact) Icons.Filled.Person else Icons.Filled.PersonAdd,
                                        contentDescription = if (isKnownContact) "مشاهده اطلاعات مخاطب" else "افزودن به مخاطبین"
                                    )
                                }
                            }
                        }
                    )
                    if (currentPinnedMessage != null) {
                        PinnedMessageBanner(
                            message = currentPinnedMessage,
                            currentIndex = pinnedBannerIndex,
                            totalPinnedCount = pinnedInThread.size,
                            onClick = {
                                val index = combinedItems.reversed().indexOfFirst {
                                    it is ThreadListItem.Real && it.message.id == currentPinnedMessage.id
                                }
                                if (index >= 0) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                                pinnedBannerIndex = (pinnedBannerIndex + 1) % pinnedInThread.size
                            },
                            onUnpin = { onTogglePinMessage(currentPinnedMessage) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!selectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    if (canSend) {
                        SimSelector(
                            sims = sims,
                            selectedSubscriptionId = selectedSimId,
                            onSelect = { selectedSimId = it }
                        )
                        MessageInputBar(
                            value = input,
                            onValueChange = { input = it },
                            onSendClick = {
                                if (input.isNotBlank()) {
                                    val at = scheduledAt
                                    if (at != null) {
                                        onScheduleSend(input, selectedSimId, at)
                                    } else {
                                        onSend(input, selectedSimId)
                                    }
                                    input = ""
                                    scheduledAt = null
                                }
                            },
                            scheduledAt = scheduledAt,
                            onScheduledAtChange = { scheduledAt = it }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "این مخاطب شماره ندارد و امکان ارسال پیام به آن وجود ندارد",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontScale = (fontScale * zoom).coerceIn(1f, 1.75f)
                    }
                }
        ) {
            val availableHeight = maxHeight
            LaunchedEffect(availableHeight, combinedItems.size) {
                if (combinedItems.isNotEmpty()) {
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
                items(
                    combinedItems.reversed(),
                    key = { item ->
                        when (item) {
                            is ThreadListItem.Real -> "m_${item.message.id}"
                            is ThreadListItem.Pending -> "s_${item.scheduled.id}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is ThreadListItem.Real -> {
                            val message = item.message
                            MessageBubble(
                                message = message,
                                isFavorite = favoriteIds.contains(message.id),
                                isPinned = pinnedMessageIds.contains(message.id),
                                selectionMode = selectionMode,
                                isSelected = selectedIds.contains(message.id),
                                fontScale = fontScale,
                                onResend = { onResend(message) },
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
                        is ThreadListItem.Pending -> {
                            PendingScheduledBubble(
                                scheduled = item.scheduled,
                                onClick = { if (!selectionMode) selectedScheduledMessage = item.scheduled }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedMessageBanner(
    message: SmsMessage,
    currentIndex: Int,
    totalPinnedCount: Int,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    Surface(
        color = Color(0xFFFFF3E0),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (totalPinnedCount > 1) {
                Row(modifier = Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(totalPinnedCount) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .width(3.dp)
                                .height(if (i == currentIndex) 18.dp else 10.dp)
                                .background(
                                    color = if (i == currentIndex) Color(0xFFFFA000) else Color(0xFFFFCC80),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = Color(0xFFFFA000),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (totalPinnedCount > 1) "پیام پین‌شده (${currentIndex + 1} از $totalPinnedCount)" else "پیام پین‌شده",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(Icons.Filled.Close, contentDescription = "برداشتن پین", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: SmsMessage,
    isFavorite: Boolean,
    isPinned: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    fontScale: Float,
    onResend: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = when {
        message.isFailed -> Color(0xFFFFCDD2)
        message.isOutgoing -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFE5E5EA)
    }
    val textColor = when {
        message.isFailed -> Color(0xFFB71C1C)
        message.isOutgoing -> Color.White
        else -> Color.Black
    }
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
                    border = if (isPinned) BorderStroke(1.5.dp, Color(0xFFFFA000)) else null,
                    modifier = Modifier
                        .padding(4.dp)
                        .combinedClickable(
                            onClick = if (message.isFailed && !selectionMode) onResend else onClick,
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
                if (message.isOutgoing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when {
                        message.isFailed -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "ارسال نشد - برای ارسال دوباره بزن",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(14.dp)
                                    .combinedClickable(onClick = onResend, onLongClick = {})
                            )
                        }
                        message.isSending || message.isQueued -> {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = if (message.isQueued) "در صف ارسال" else "در حال ارسال",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        message.isDelivered -> {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "تحویل داده شد",
                                tint = Color(0xFF34A853),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "ارسال شد",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "فیوریت‌شده",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (isPinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "پین‌شده",
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

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

@Composable
private fun PendingScheduledBubble(scheduled: ScheduledMessage, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable(onClick = onClick)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Alarm,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ارسال در ${DateFormatter.formatFull(scheduled.scheduledAt)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = Color.White.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = scheduled.body, color = Color.White)
                    }
                }
            }
        }
    }
}