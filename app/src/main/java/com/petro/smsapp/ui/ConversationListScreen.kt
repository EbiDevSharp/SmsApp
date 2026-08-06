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
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.SwipeAction
import com.petro.smsapp.util.AlphabetIndexHelper
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.PhoneNumberUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.rememberCoroutineScope
import com.petro.smsapp.util.autoDirection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * صفحه‌ی اصلی لیست مکالمات. عملیاتِ قبلیِ «بلاک کردن» (چه از سویپ چه از منو) دیگه
 * مستقیم به یه مقصدِ ثابت نمی‌ره - onAddToGroupClick صدا زده میشه و صداکننده (AppNavigation)
 * یه شیتِ کوچیک برای انتخابِ گروه نشون میده.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit,
    onComposeClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    hasActiveFilter: Boolean = false,
    onClearFilters: () -> Unit = {},
    onDeleteConversations: (Set<Long>) -> Unit,
    onAddToGroupClick: (List<Conversation>) -> Unit,
    onMakeConversationsPrivate: (List<Conversation>) -> Unit,
    onPinConversations: (List<Conversation>) -> Unit = {},
    swipeRightToLeftAction: SwipeAction = SwipeAction.NONE,
    swipeLeftToRightAction: SwipeAction = SwipeAction.NONE,
    swipeDeleteRequiresConfirmation: Boolean = true,
    showContactNumberEnabled: Boolean = false,
    alphabetIndexBarEnabled: Boolean = true,
    onMarkThreadRead: (threadId: Long) -> Unit = {},
    onMarkThreadUnread: (threadId: Long) -> Unit = {}
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var swipeDeleteTarget by remember { mutableStateOf<Conversation?>(null) }
    val selectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(conversations) {
        val stillExisting = conversations.map { it.threadId }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

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

    val letterToFirstIndex = remember(conversations) {
        val map = LinkedHashMap<String, Int>()
        conversations.forEachIndexed { index, conversation ->
            val letter = AlphabetIndexHelper.groupFor(conversation.displayName, conversation.address)
            if (!map.containsKey(letter)) map[letter] = index
        }
        map
    }
    val sortedIndexLetters = remember(letterToFirstIndex) {
        AlphabetIndexHelper.sortPresentLetters(letterToFirstIndex.keys)
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
                                run {
                                    val selectedConversations = conversations.filter { it.threadId in selectedIds }
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
                                DropdownMenuItem(
                                    text = { Text("خواندن") },
                                    leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        selectedIds.forEach { threadId -> onMarkThreadRead(threadId) }
                                        selectedIds = emptySet()
                                    }
                                )
                                ComingSoonMenuItem(Icons.Filled.ContentCopy, "کپی کردن") { showMoreMenu = false }
                                ComingSoonMenuItem(Icons.Filled.Share, "اشتراک‌گذاری") { showMoreMenu = false }
                                DropdownMenuItem(
                                    text = { Text("افزودن به گروه") },
                                    leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        val selectedConversations = conversations.filter { it.threadId in selectedIds }
                                        onAddToGroupClick(selectedConversations)
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
                        Text(
                            "پیام‌ها",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = "منو")
                        }
                    },
                    actions = {
                        if (hasActiveFilter) {
                            IconButton(onClick = onClearFilters) {
                                Box {
                                    Icon(Icons.Filled.FilterAlt, contentDescription = "لیست فیلتر شده - برای خارج شدن از فیلتر بزنید")
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Filled.Search, contentDescription = "جستجو")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هنوز مکالمه‌ای نداری", color = Color.Gray)
                }
            } else {
                LazyColumn(state = listState) {
                    items(conversations, key = { it.threadId }) { conversation ->
                        SwipeableConversationRow(
                            conversation = conversation,
                            selectionMode = selectionMode,
                            isSelected = selectedIds.contains(conversation.threadId),
                            rightToLeftAction = swipeRightToLeftAction,
                            leftToRightAction = swipeLeftToRightAction,
                            showContactNumberEnabled = showContactNumberEnabled,
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
                            onAddToGroup = { onAddToGroupClick(listOf(conversation)) },
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

            if (alphabetIndexBarEnabled && sortedIndexLetters.isNotEmpty() && !selectionMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AlphabetIndexBar(
                        letters = sortedIndexLetters,
                        onLetterChange = { letter ->
                            val targetIndex = letterToFirstIndex[letter]
                            if (targetIndex != null) {
                                scope.launch { listState.scrollToItem(targetIndex) }
                            }
                        },
                        onDragEnd = {},
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingSoonMenuItem(
    icon: ImageVector,
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

@Composable
private fun SwipeableConversationRow(
    conversation: Conversation,
    selectionMode: Boolean,
    isSelected: Boolean,
    rightToLeftAction: SwipeAction,
    leftToRightAction: SwipeAction,
    showContactNumberEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRequestDelete: () -> Unit,
    onAddToGroup: () -> Unit,
    onCall: () -> Unit
) {
    val density = LocalDensity.current
    val actionThresholdPx = with(density) { 88.dp.toPx() }
    val maxDragPx = with(density) { 132.dp.toPx() }
    var offsetX by remember { mutableStateOf(0f) }

    LaunchedEffect(selectionMode) {
        if (selectionMode) offsetX = 0f
    }

    fun runAction(action: SwipeAction) {
        when (action) {
            SwipeAction.MARK_READ -> onMarkRead()
            SwipeAction.MARK_UNREAD -> onMarkUnread()
            SwipeAction.DELETE -> onRequestDelete()
            SwipeAction.BLOCK -> onAddToGroup()
            SwipeAction.CALL -> onCall()
            SwipeAction.NONE -> Unit
        }
    }

    val draggableState = rememberDraggableState { delta ->
        val minBound = if (leftToRightAction != SwipeAction.NONE) -maxDragPx else 0f
        val maxBound = if (rightToLeftAction != SwipeAction.NONE) maxDragPx else 0f
        // با offsetX - delta حرکت ردیف از نظر شما درست است
        offsetX = (offsetX - delta).coerceIn(minBound, maxBound)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // پنل عملیات هنگام کشیدن به چپ (Right -> Left)
        // پنل عملیات هنگام کشیدن از راست به چپ
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    when {
                        offsetX > 0 -> swipeActionColor(rightToLeftAction)
                        offsetX < 0 -> swipeActionColor(leftToRightAction)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = when {
                offsetX > 0 -> Alignment.CenterStart
                offsetX < 0 -> Alignment.CenterEnd
                else -> Alignment.Center
            }
        ) {
            when {
                offsetX > 0 && rightToLeftAction != SwipeAction.NONE -> {
                    SwipeActionLabel(
                        action = rightToLeftAction,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }
                offsetX < 0 && leftToRightAction != SwipeAction.NONE -> {
                    SwipeActionLabel(
                        action = leftToRightAction,
                        modifier = Modifier.padding(end = 32.dp)
                    )
                }
            }
        }

        // محتوای ردیف (روی پنل‌ها می‌لغزد)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !selectionMode &&
                            (rightToLeftAction != SwipeAction.NONE || leftToRightAction != SwipeAction.NONE),
                    onDragStopped = {
                        val triggeredAction = when {
                            // offsetX منفی → لیبل leftToRight دیده می‌شود → همان را اجرا کن
                            offsetX <= -actionThresholdPx -> leftToRightAction
                            // offsetX مثبت → لیبل rightToLeft دیده می‌شود → همان را اجرا کن
                            offsetX >= actionThresholdPx -> rightToLeftAction
                            else -> null
                        }
                        animate(
                            initialValue = offsetX,
                            targetValue = 0f,
                            animationSpec = tween(220)
                        ) { value, _ ->
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
                showContactNumberEnabled = showContactNumberEnabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
        }
    }
}

@Composable
private fun SwipeActionLabel(
    action: SwipeAction,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = swipeActionIcon(action),
            contentDescription = action.label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = action.label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun swipeActionIcon(action: SwipeAction): ImageVector = when (action) {
    SwipeAction.MARK_READ -> Icons.Filled.Done
    SwipeAction.MARK_UNREAD -> Icons.Filled.Circle
    SwipeAction.DELETE -> Icons.Filled.Delete
    SwipeAction.CALL -> Icons.Filled.Call
    SwipeAction.BLOCK -> Icons.Filled.Folder
    SwipeAction.NONE -> Icons.Filled.Close
}

private fun swipeActionColor(action: SwipeAction): Color = when (action) {
    SwipeAction.MARK_READ -> Color(0xFF2196F3)
    SwipeAction.MARK_UNREAD -> Color(0xFF757575)
    SwipeAction.DELETE -> Color(0xFFE53935)
    SwipeAction.CALL -> Color(0xFF43A047)
    SwipeAction.BLOCK -> Color(0xFF8E24AA)
    SwipeAction.NONE -> Color.Transparent
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selectionMode: Boolean,
    isSelected: Boolean,
    showContactNumberEnabled: Boolean,
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
            Box {
                ContactAvatar(name = conversation.displayName, address = conversation.address)
                // چتی که حداقل یه پیامش عضوِ یه گروهِ فیلتره - یه نشونِ کوچیکِ پوشه گوشه‌ی آواتار
                if (conversation.isGrouped) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = "این مکالمه عضوِ یه گروهِ فیلتره",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
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
            if (showContactNumberEnabled && conversation.address.isNotBlank() && conversation.address != conversation.displayName) {
                Text(
                    text = conversation.address,
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.snippet,
                maxLines = 1,
                color = when {
                    conversation.isDraft -> MaterialTheme.colorScheme.error
                    conversation.unreadCount > 0 -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
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