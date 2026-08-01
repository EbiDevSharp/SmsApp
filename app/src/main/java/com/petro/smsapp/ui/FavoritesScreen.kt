package com.petro.smsapp.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.autoDirection

/**
 * صفحه‌ی «علاقه‌مندی‌ها».
 *
 * قبلاً تک‌کلیک روی هر ردیف مستقیم می‌رفت تو مکالمه‌ی همون پیام. الان مثلِ بقیه‌ی
 * صفحات مشابه (بلاک‌شده‌ها/خصوصی/سطل‌زباله) شده:
 * ۱) تک‌کلیک -> یه منوی اکشنِ کوچیک (FavoriteMessageActionsSheet) باز میشه: «رفتن به
 *    پیام» (همون رفتارِ قبلی، حالا به‌عنوانِ یه گزینه‌ی داخلِ منو)، «باز کردن در نوت»،
 *    «کپی»، «اشتراک‌گذاری» و «برداشتن از علاقه‌مندی‌ها».
 * ۲) لانگ‌کلیک -> وارد حالت «انتخاب چندتایی» میشه (تک‌کلیکِ بعدی فقط انتخاب/عدمِ‌انتخاب
 *    می‌کنه)، نوار بالا با تعداد انتخاب‌شده + انتخاب‌همه + برداشتنِ گروهی از
 *    علاقه‌مندی‌ها (با تائید) عوض میشه.
 *
 * چون مدلِ FavoriteMessage سبک‌تر از SmsMessage کامله (فیلدهای وضعیتِ ارسال/دلیوری
 * رو نداره)، این صفحه به‌جای MessageActionsSheetِ مشترک، یه شیتِ اختصاصیِ خودش داره.
 */
@Composable
fun FavoritesScreen(
    favorites: List<FavoriteMessage>,
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onItemClick: (FavoriteMessage) -> Unit,
    onRemoveFavorite: (messageId: Long) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var actionSheetEntry by remember { mutableStateOf<FavoriteMessage?>(null) }
    var pendingRemove by remember { mutableStateOf<FavoriteMessage?>(null) }
    var showBulkRemoveConfirm by remember { mutableStateOf(false) }
    val selectionMode = selectedIds.isNotEmpty()

    // اگه بعد از برداشتن از علاقه‌مندی‌ها بعضی id های انتخاب‌شده دیگه وجود نداشته باشن، از انتخاب پاک بشن
    LaunchedEffect(favorites) {
        val stillExisting = favorites.map { it.messageId }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    val toRemove = pendingRemove
    if (toRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("برداشتن از علاقه‌مندی‌ها") },
            text = { Text("این پیام از علاقه‌مندی‌ها برداشته بشه؟ خود پیامک حذف نمیشه، فقط قفلش باز میشه.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFavorite(toRemove.messageId)
                    pendingRemove = null
                }) {
                    Text("بردار")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showBulkRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkRemoveConfirm = false },
            title = { Text("برداشتن از علاقه‌مندی‌ها") },
            text = { Text("${selectedIds.size} پیام از علاقه‌مندی‌ها برداشته بشن؟ خودِ پیامک‌ها حذف نمیشن، فقط قفلشون باز میشه.") },
            confirmButton = {
                TextButton(onClick = {
                    showBulkRemoveConfirm = false
                    selectedIds.forEach { onRemoveFavorite(it) }
                    selectedIds = emptySet()
                }) {
                    Text("بردار")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkRemoveConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    val currentActionSheetEntry = actionSheetEntry
    if (currentActionSheetEntry != null) {
        FavoriteMessageActionsSheet(
            favorite = currentActionSheetEntry,
            onDismiss = { actionSheetEntry = null },
            onGoToMessage = {
                actionSheetEntry = null
                onItemClick(currentActionSheetEntry)
            },
            onOpenNote = { onItemClick(currentActionSheetEntry) },
            onRequestRemove = {
                actionSheetEntry = null
                pendingRemove = currentActionSheetEntry
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
                        val allSelected = selectedIds.size == favorites.size && favorites.isNotEmpty()
                        IconButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else favorites.map { it.messageId }.toSet()
                        }) {
                            Icon(
                                Icons.Filled.SelectAll,
                                contentDescription = if (allSelected) "از انتخاب دراوردن همه" else "انتخاب همه"
                            )
                        }
                        IconButton(onClick = { showBulkRemoveConfirm = true }) {
                            Icon(Icons.Filled.Star, contentDescription = "برداشتن از علاقه‌مندی‌ها")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("علاقه‌مندی‌ها") },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                    }
                )
            }
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز پیامی رو فیوریت نکردی", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(favorites, key = { it.messageId }) { favorite ->
                    FavoriteRow(
                        favorite = favorite,
                        selectionMode = selectionMode,
                        isSelected = selectedIds.contains(favorite.messageId),
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (selectedIds.contains(favorite.messageId)) {
                                    selectedIds - favorite.messageId
                                } else {
                                    selectedIds + favorite.messageId
                                }
                            } else {
                                actionSheetEntry = favorite
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) selectedIds = setOf(favorite.messageId)
                        }
                    )
                    Divider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

/** منوی اکشنِ تک‌کلیکِ روی یه پیامِ داخلِ علاقه‌مندی‌ها */
@Composable
private fun FavoriteMessageActionsSheet(
    favorite: FavoriteMessage,
    onDismiss: () -> Unit,
    onGoToMessage: () -> Unit,
    onOpenNote: () -> Unit,
    onRequestRemove: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            FavoriteMenuRow(
                icon = Icons.Filled.NorthEast,
                label = "رفتن به پیام",
                onClick = onGoToMessage
            )
            FavoriteMenuRow(
                icon = Icons.Filled.Notes,
                label = "باز کردن در نوت",
                onClick = {
                    onDismiss()
                    onOpenNote()
                }
            )
            FavoriteMenuRow(
                icon = Icons.Filled.ContentCopy,
                label = "کپی",
                onClick = {
                    clipboardManager.setText(AnnotatedString(favorite.body))
                    onDismiss()
                }
            )
            FavoriteMenuRow(
                icon = Icons.Filled.Share,
                label = "اشتراک‌گذاری",
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, favorite.body)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                    onDismiss()
                }
            )
            FavoriteMenuRow(
                icon = Icons.Filled.Star,
                label = "برداشتن از علاقه‌مندی‌ها",
                onClick = onRequestRemove
            )
        }
    }
}

@Composable
private fun FavoriteMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(
    favorite: FavoriteMessage,
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
            SelectionCheck(isSelected = isSelected)
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Avatar(name = favorite.displayName)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = favorite.displayName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge.autoDirection()
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "قفل‌شده - قابل حذف نیست",
                    tint = Color.Gray,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = favorite.body,
                maxLines = 2,
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = DateFormatter.formatFull(favorite.date),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
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
