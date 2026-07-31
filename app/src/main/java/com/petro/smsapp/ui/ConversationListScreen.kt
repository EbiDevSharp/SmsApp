package com.petro.smsapp.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.SwipeAction
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.PhoneNumberUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import com.petro.smsapp.util.autoDirection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * صفحه‌ی اصلی لیست مکالمات (پیام‌ها). علاوه بر رفتار عادی (کلیک -> باز کردن مکالمه)،
 * حالا حالت «انتخاب چندتایی» و «سویپِ داینامیک» هم داره:
 * - لانگ‌کلیک روی یه ردیف -> وارد حالت انتخاب میشه و همون ردیف انتخاب میشه.
 * - توی حالت انتخاب، کلیک ساده روی هر ردیف فقط انتخاب/عدم‌انتخابش می‌کنه (دیگه مکالمه باز نمیشه).
 * - نوار بالای صفحه با تعداد انتخاب‌شده‌ها عوض میشه: دکمه‌ی بستن (خروج از حالت انتخاب)،
 *   دکمه‌ی «انتخاب همه/هیچ‌کدام»، دکمه‌ی حذف (فعلاً تنها عملیات واقعی)، و یه منوی سه‌نقطه
 *   برای عملیات‌های بعدی (پین، کپی، اشتراک‌گذاری، مسدودکردن - که هنوز پیاده نشدن).
 * - دکمه‌ی برگشتِ گوشی هم توی حالت انتخاب، فقط از حالت انتخاب خارج می‌کنه نه از کل صفحه.
 * - هر ردیف قابل‌سویپه: جهتِ راست‌به‌چپ و چپ‌به‌راست هرکدوم یه عملیاتِ داینامیک و
 *   قابل‌تنظیم از صفحه‌ی تنظیمات دارن (خوانده/ناخوانده‌شدن، حذف، تماس، بلاک). حین
 *   کشیدن، پس‌زمینه‌ی ردیف رنگِ همون عملیات رو می‌گیره و آیکنش نزدیکِ لبه‌ی
 *   بازشده (جایی که کشیدن از اونجا شروع شده) نشون داده میشه.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit,
    onComposeClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDeleteConversations: (Set<Long>) -> Unit,
    onBlockConversations: (List<Conversation>) -> Unit,
    onMakeConversationsPrivate: (List<Conversation>) -> Unit,
    onPinConversations: (List<Conversation>) -> Unit = {},
    swipeRightToLeftAction: SwipeAction = SwipeAction.NONE,
    swipeLeftToRightAction: SwipeAction = SwipeAction.NONE,
    swipeDeleteRequiresConfirmation: Boolean = true,
    onMarkThreadRead: (threadId: Long) -> Unit = {},
    onMarkThreadUnread: (threadId: Long) -> Unit = {}
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // هدفِ حذفِ تک‌موردیِ ناشی از سویپ (جدا از showDeleteConfirm که مالِ حذفِ چندتاییه)
    var swipeDeleteTarget by remember { mutableStateOf<Conversation?>(null) }
    val selectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // اگه بعد از حذف/تغییر لیست، بعضی id های انتخاب‌شده دیگه وجود نداشته باشن، از انتخاب پاک بشن
    LaunchedEffect(conversations) {
        val stillExisting = conversations.map { it.threadId }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    // دکمه‌ی برگشت سیستم: اگه توی حالت انتخابیم، فقط از انتخاب خارج شو، از صفحه خارج نشو
    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف مکالمه‌ها") },
            text = { Text("${selectedIds.size} مکالمه حذف بشه؟") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteConversations(selectedIds)
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

    val currentSwipeDeleteTarget = swipeDeleteTarget
    if (currentSwipeDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { swipeDeleteTarget = null },
            title = { Text("حذف مکالمه") },
            text = { Text("مکالمه‌ی «${currentSwipeDeleteTarget.displayName}» حذف بشه؟") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConversations(setOf(currentSwipeDeleteTarget.threadId))
                    swipeDeleteTarget = null
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { swipeDeleteTarget = null }) {
                    Text("انصراف")
                }
            }
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
                        val allSelected = selectedIds.size == conversations.size && conversations.isNotEmpty()
                        IconButton(onClick = {
                            selectedIds = if (allSelected) {
                                emptySet()
                            } else {
                                conversations.map { it.threadId }.toSet()
                            }
                        }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = if (allSelected) "از انتخاب دراوردن همه" else "انتخاب همه"
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "عملیات بیشتر")
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                // این دوتا فعلاً فقط اسکلت‌بندی‌شدن - قابلیت واقعیشون بعداً اضافه میشه
                                run {
                                    val selectedConversations = conversations.filter { it.threadId in selectedIds }
                                    // اگه همه‌ی انتخاب‌شده‌ها از قبل پین بودن، این دکمه اونا رو آنپین می‌کنه؛
                                    // وگرنه (حتی اگه بعضی‌هاشون پین بودن) بقیه رو هم پین می‌کنه.
                                    val allPinned = selectedConversations.isNotEmpty() && selectedConversations.all { it.isPinned }
                                    DropdownMenuItem(
                                        text = { Text(if (allPinned) "برداشتن پین" else "پین کردن") },
                                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onPinConversations(selectedConversations)
                                            selectedIds = emptySet()
                                        }
                                    )
                                }
                                ComingSoonMenuItem(Icons.Filled.ContentCopy, "کپی کردن") { showMoreMenu = false }
                                ComingSoonMenuItem(Icons.Filled.Share, "اشتراک‌گذاری") { showMoreMenu = false }
                                DropdownMenuItem(
                                    text = { Text("بلاک کردن") },
                                    leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        val selectedConversations = conversations.filter { it.threadId in selectedIds }
                                        onBlockConversations(selectedConversations)
                                        selectedIds = emptySet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("خصوصی کردن") },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        val selectedConversations = conversations.filter { it.threadId in selectedIds }
                                        onMakeConversationsPrivate(selectedConversations)
                                        selectedIds = emptySet()
                                    }
                                )
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text("پیام‌ها", fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            scope.launch { listState.animateScrollToItem(0) }
                        })


                            },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = "منو")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(onClick = onComposeClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "پیام جدید")
                }
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز مکالمه‌ای نداری", color = Color.Gray)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding)) {
                items(conversations, key = { it.threadId }) { conversation ->
                    SwipeableConversationRow(
                        conversation = conversation,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(conversation.threadId),
                        rightToLeftAction = swipeRightToLeftAction,
                        leftToRightAction = swipeLeftToRightAction,
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (selectedIds.contains(conversation.threadId)) {
                                    selectedIds - conversation.threadId
                                } else {
                                    selectedIds + conversation.threadId
                                }
                            } else {
                                onConversationClick(conversation)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectedIds = setOf(conversation.threadId)
                            }
                        },
                        onMarkRead = { onMarkThreadRead(conversation.threadId) },
                        onMarkUnread = { onMarkThreadUnread(conversation.threadId) },
                        onRequestDelete = {
                            if (swipeDeleteRequiresConfirmation) {
                                swipeDeleteTarget = conversation
                            } else {
                                onDeleteConversations(setOf(conversation.threadId))
                            }
                        },
                        onBlock = { onBlockConversations(listOf(conversation)) },
                        onCall = {
                            if (PhoneNumberUtils.isSendableAddress(conversation.address)) {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${conversation.address}")))
                            } else {
                                Toast.makeText(context, "این آدرس قابلِ تماس نیست", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun ComingSoonMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            onDismiss()
            Toast.makeText(context, "$label: به‌زودی اضافه میشه", Toast.LENGTH_SHORT).show()
        }
    )
}

/**
 * لایه‌ی سویپ‌پذیرِ دورِ هر ردیفِ لیستِ مکالمات. از Modifier.draggable (نه پویینترانپوت
 * دستی) استفاده می‌کنه چون این ماژول به‌خوبی کنارِ combinedClickable ردیفِ داخلی
 * (ConversationRow) کار می‌کنه: تا وقتی حرکتِ انگشت از آستانه‌ی لمسِ سیستم رد نشه،
 * تپ‌های ساده/لانگ‌کلیک دست‌نخورده به همون ردیف می‌رسن.
 *
 * جهتِ افقیِ آفست همیشه فیزیکیه (مستقل از راست‌چین/چپ‌چینِ کلِ اپ) - یعنی کشیدنِ
 * انگشت به‌سمتِ چپ همیشه «راست‌به‌چپ» حساب میشه، چه اپ RTL باشه چه LTR؛ برای همین
 * پس‌زمینه‌ی آیکن هم عمداً با LayoutDirection.Ltr اجباری کشیده میشه تا Start/End
 * همیشه معنیِ فیزیکیِ چپ/راست بدن، نه معنیِ جهتِ متنِ برنامه.
 */
@Composable
private fun SwipeableConversationRow(
    conversation: Conversation,
    selectionMode: Boolean,
    isSelected: Boolean,
    rightToLeftAction: SwipeAction,
    leftToRightAction: SwipeAction,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRequestDelete: () -> Unit,
    onBlock: () -> Unit,
    onCall: () -> Unit
) {
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 88.dp.toPx() }
    val maxDragPx = with(density) { 132.dp.toPx() }
    var offsetX by remember { mutableStateOf(0f) }

    // اگه وسطِ کشیدن وارد حالتِ انتخاب چندتایی بشیم، ردیف رو فوراً به حالتِ عادی برگردون
    LaunchedEffect(selectionMode) {
        if (selectionMode) offsetX = 0f
    }

    fun runAction(action: SwipeAction) {
        when (action) {
            SwipeAction.MARK_READ -> onMarkRead()
            SwipeAction.MARK_UNREAD -> onMarkUnread()
            SwipeAction.DELETE -> onRequestDelete()
            SwipeAction.BLOCK -> onBlock()
            SwipeAction.CALL -> onCall()
            SwipeAction.NONE -> Unit
        }
    }

    val draggableState = rememberDraggableState { delta ->
        // اگه برای یه جهت هیچ عملیاتی تنظیم نشده باشه (NONE)، اصلاً اجازه‌ی کشیدن به همون سمت رو نده
        val minBound = if (rightToLeftAction != SwipeAction.NONE) -maxDragPx else 0f
        val maxBound = if (leftToRightAction != SwipeAction.NONE) maxDragPx else 0f
        offsetX = (offsetX + delta).coerceIn(minBound, maxBound)
    }

    val revealedAction: SwipeAction? = when {
        offsetX <= -1f -> rightToLeftAction
        offsetX >= 1f -> leftToRightAction
        else -> null
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // پس‌زمینه‌ی رنگی + آیکنِ عملیات - فقط وقتی واقعاً چیزی کشیده شده نشون داده میشه
        if (revealedAction != null && revealedAction != SwipeAction.NONE) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .background(swipeActionColor(revealedAction)),
                    horizontalArrangement = if (offsetX < 0) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = swipeActionIcon(revealedAction),
                            contentDescription = revealedAction.label,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = revealedAction.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !selectionMode && (rightToLeftAction != SwipeAction.NONE || leftToRightAction != SwipeAction.NONE),
                    onDragStopped = { _ ->
                        val current = offsetX
                        val triggeredAction = when {
                            current <= -actionThresholdPx -> rightToLeftAction
                            current >= actionThresholdPx -> leftToRightAction
                            else -> null
                        }
                        // همیشه برمی‌گرده سرِ جاش - عملیات (اگه بود) بعد از شروعِ برگشتن اجرا میشه
                        animate(initialValue = offsetX, targetValue = 0f, animationSpec = tween(220)) { value, _ ->
                            offsetX = value
                        }
                        if (triggeredAction != null && triggeredAction != SwipeAction.NONE) {
                            runAction(triggeredAction)
                        }
                    }
                )
        ) {
            ConversationRow(
                conversation = conversation,
                selectionMode = selectionMode,
                isSelected = isSelected,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
    }
}

private fun swipeActionIcon(action: SwipeAction): ImageVector = when (action) {
    SwipeAction.MARK_READ -> Icons.Filled.Done
    SwipeAction.MARK_UNREAD -> Icons.Filled.Circle
    SwipeAction.DELETE -> Icons.Filled.Delete
    SwipeAction.CALL -> Icons.Filled.Call
    SwipeAction.BLOCK -> Icons.Filled.Block
    SwipeAction.NONE -> Icons.Filled.Close
}

private fun swipeActionColor(action: SwipeAction): Color = when (action) {
    SwipeAction.MARK_READ -> Color(0xFF2196F3)
    SwipeAction.MARK_UNREAD -> Color(0xFF757575)
    SwipeAction.DELETE -> Color(0xFFE53935)
    SwipeAction.CALL -> Color(0xFF43A047)
    SwipeAction.BLOCK -> Color(0xFFB71C1C)
    SwipeAction.NONE -> Color.Transparent
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionAvatar(isSelected = isSelected)
        } else {
            Avatar(name = conversation.displayName)
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "پین‌شده",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = conversation.displayName,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge.autoDirection()
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.snippet,
                maxLines = 1,
                color = when {
                    conversation.isDraft -> MaterialTheme.colorScheme.error
                    conversation.unreadCount > 0 -> Color.Black
                    else -> Color.Gray
                },
                fontWeight = if (conversation.isDraft) FontWeight.Medium else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = DateFormatter.formatSmart(conversation.date),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (conversation.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

/** آواتار جایگزین توی حالت انتخاب: دایره‌ی خالی وقتی انتخاب نشده، دایره‌ی رنگی با تیک وقتی انتخاب شده */
@Composable
private fun SelectionAvatar(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = "انتخاب‌شده", tint = Color.White)
        }
    }
}
