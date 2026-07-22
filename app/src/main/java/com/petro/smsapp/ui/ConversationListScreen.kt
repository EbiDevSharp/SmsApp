package com.petro.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.util.DateFormatter

/**
 * صفحه‌ی اصلی لیست مکالمات (پیام‌ها). علاوه بر رفتار عادی (کلیک -> باز کردن مکالمه)،
 * حالا حالت «انتخاب چندتایی» هم داره:
 * - لانگ‌کلیک روی یه ردیف -> وارد حالت انتخاب میشه و همون ردیف انتخاب میشه.
 * - توی حالت انتخاب، کلیک ساده روی هر ردیف فقط انتخاب/عدم‌انتخابش می‌کنه (دیگه مکالمه باز نمیشه).
 * - نوار بالای صفحه با تعداد انتخاب‌شده‌ها عوض میشه: دکمه‌ی بستن (خروج از حالت انتخاب)،
 *   دکمه‌ی «انتخاب همه/هیچ‌کدام»، دکمه‌ی حذف (فعلاً تنها عملیات واقعی)، و یه منوی سه‌نقطه
 *   برای عملیات‌های بعدی (پین، کپی، اشتراک‌گذاری، مسدودکردن - که هنوز پیاده نشدن).
 * - دکمه‌ی برگشتِ گوشی هم توی حالت انتخاب، فقط از حالت انتخاب خارج می‌کنه نه از کل صفحه.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit,
    onComposeClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDeleteConversations: (Set<Long>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

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
                                // این چهارتا فعلاً فقط اسکلت‌بندی‌شدن - قابلیت واقعیشون بعداً اضافه میشه
                                ComingSoonMenuItem(Icons.Filled.PushPin, "پین کردن") { showMoreMenu = false }
                                ComingSoonMenuItem(Icons.Filled.ContentCopy, "کپی کردن") { showMoreMenu = false }
                                ComingSoonMenuItem(Icons.Filled.Share, "اشتراک‌گذاری") { showMoreMenu = false }
                                ComingSoonMenuItem(Icons.Filled.Block, "مسدود کردن") { showMoreMenu = false }
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("پیام‌ها", fontWeight = FontWeight.Bold) },
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
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(conversations, key = { it.threadId }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(conversation.threadId),
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
            Text(
                text = conversation.displayName,
                fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.snippet,
                maxLines = 1,
                color = if (conversation.unreadCount > 0) Color.Black else Color.Gray,
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
