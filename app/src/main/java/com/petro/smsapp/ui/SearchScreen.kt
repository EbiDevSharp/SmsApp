package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.ConversationFilterContext
import com.petro.smsapp.data.ConversationFilterType
import com.petro.smsapp.data.applyConversationFilters
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.autoDirection

/**
 * صفحه‌ی جستجو - از طریق آیکنِ جستجو توی هدرِ صفحه‌ی اصلی باز میشه.
 *
 * دو راهِ فیلتر کردن داره که با هم AND میشن: متنِ جستجو (رو اسم/شماره/متنِ آخرین
 * پیامِ هر مکالمه) و چیپ‌های فیلتر که دقیقاً همون ConversationFilterType ای هستن که
 * توی آکاردئونِ درآور استفاده می‌شن (پس منطقِ OR بینِ چندتا چیپِ انتخاب‌شده هم دقیقاً
 * همون applyConversationFilters موجوده). اگه نه متنی تایپ شده نه چیپی انتخاب شده،
 * لیست خالی می‌مونه و یه پیامِ راهنما نشون داده میشه - نه کلِ لیستِ مکالمات.
 */
@Composable
fun SearchScreen(
    conversations: List<Conversation>,
    pinnedMessageThreadIds: Set<Long> = emptySet(),
    favoriteThreadIds: Set<Long> = emptySet(),
    showContactNumberEnabled: Boolean = false,
    onBack: () -> Unit,
    onConversationClick: (Conversation) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedFilters by remember { mutableStateOf(setOf<ConversationFilterType>()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val trimmedQuery = query.trim()
    val hasAnyCriteria = trimmedQuery.isNotEmpty() || selectedFilters.isNotEmpty()

    val results = remember(conversations, trimmedQuery, selectedFilters, pinnedMessageThreadIds, favoriteThreadIds) {
        if (trimmedQuery.isEmpty() && selectedFilters.isEmpty()) {
            emptyList()
        } else {
            conversations
                .applyConversationFilters(
                    selectedFilters,
                    ConversationFilterContext(
                        pinnedMessageThreadIds = pinnedMessageThreadIds,
                        favoriteThreadIds = favoriteThreadIds
                    )
                )
                .filter { conversation ->
                    trimmedQuery.isEmpty() ||
                            conversation.displayName.contains(trimmedQuery, ignoreCase = true) ||
                            conversation.address.contains(trimmedQuery, ignoreCase = true) ||
                            conversation.snippet.contains(trimmedQuery, ignoreCase = true)
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        focusRequester = focusRequester
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FilterChipsRow(
                selectedFilters = selectedFilters,
                onToggle = { filter ->
                    selectedFilters = if (selectedFilters.contains(filter)) {
                        selectedFilters - filter
                    } else {
                        selectedFilters + filter
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            when {
                !hasAnyCriteria -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "برای جستجو تایپ کن یا یه فیلتر انتخاب کن",
                            color = Color.Gray
                        )
                    }
                }
                results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("نتیجه‌ای پیدا نشد", color = Color.Gray)
                    }
                }
                else -> {
                    LazyColumn {
                        items(results, key = { it.threadId }) { conversation ->
                            SearchResultRow(
                                conversation = conversation,
                                showContactNumberEnabled = showContactNumberEnabled,
                                onClick = { onConversationClick(conversation) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "جستجو در مکالمات...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDirection = TextDirection.Content
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "پاک کردن",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    selectedFilters: Set<ConversationFilterType>,
    onToggle: (ConversationFilterType) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConversationFilterType.entries.forEach { filter ->
            val selected = selectedFilters.contains(filter)
            // دقیقاً همون چیپِ آیکن‌دار+نوشته‌ی منوی آکاردئونی (IconFilterChip تو
            // DrawerFilterAccordion.kt) - برای یکسان بودنِ ظاهرِ چیپ‌ها تو کل اپ
            IconFilterChip(
                icon = iconForFilter(filter),
                contentDescription = filter.label,
                selected = selected,
                onClick = { onToggle(filter) }
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    conversation: Conversation,
    showContactNumberEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(name = conversation.displayName, address = conversation.address)
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
                color = if (conversation.unreadCount > 0) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
