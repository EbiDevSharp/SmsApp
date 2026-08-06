package com.petro.smsapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petro.smsapp.data.ConversationFilterType
import com.petro.smsapp.data.ConversationSortType
import com.petro.smsapp.data.CustomTimeRange
import com.petro.smsapp.data.TimeFilterSelection
import com.petro.smsapp.data.TimeRangePreset
import com.petro.smsapp.util.DateFormatter

/** مراحلِ انتخابِ بازه‌ی دلخواه با DateTimePickerSheet: اول «از تاریخ»، بعد «تا تاریخ» */
private enum class RangePickerStage { FROM, TO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerFilterAccordion(
    items: List<ConversationFilterType> = ConversationFilterType.entries,
    selectedIds: Set<String>,
    onToggle: (ConversationFilterType) -> Unit,
    timeSelection: TimeFilterSelection = TimeFilterSelection.None,
    onTimeSelectionChange: (TimeFilterSelection) -> Unit = {},
    sortType: ConversationSortType? = null,
    onSortTypeChange: (ConversationSortType?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var rangePickerStage by remember { mutableStateOf<RangePickerStage?>(null) }
    var pendingFromMillis by remember { mutableStateOf<Long?>(null) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "drawer_filter_chevron")

    val activeCount = selectedIds.size +
            (if (timeSelection != TimeFilterSelection.None) 1 else 0) +
            (if (sortType != null) 1 else 0)

    val existingCustom = (timeSelection as? TimeFilterSelection.Custom)?.range
    val defaultFrom = System.currentTimeMillis() - 6L * 24 * 60 * 60 * 1000

    when (rangePickerStage) {
        RangePickerStage.FROM -> {
            DateTimePickerSheet(
                title = "از تاریخ",
                initialMillis = existingCustom?.fromMillis ?: defaultFrom,
                restrictPast = false,
                onConfirm = { from ->
                    pendingFromMillis = from
                    rangePickerStage = RangePickerStage.TO
                },
                onDismiss = { rangePickerStage = null }
            )
        }
        RangePickerStage.TO -> {
            DateTimePickerSheet(
                title = "تا تاریخ",
                initialMillis = existingCustom?.toMillis ?: System.currentTimeMillis(),
                restrictPast = false,
                onConfirm = { to ->
                    val from = pendingFromMillis
                    if (from != null) {
                        // اگه کاربر «تا» رو قبل از «از» انتخاب کرد، جابه‌جاشون می‌کنیم تا بازه معتبر بمونه
                        val range = if (from <= to) CustomTimeRange(from, to) else CustomTimeRange(to, from)
                        onTimeSelectionChange(TimeFilterSelection.Custom(range))
                    }
                    rangePickerStage = null
                    pendingFromMillis = null
                },
                onDismiss = {
                    rangePickerStage = null
                    pendingFromMillis = null
                }
            )
        }
        null -> {}
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "فیلتر لیست چت‌ها",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (activeCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = activeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "بستن فیلترها" else "بازکردن فیلترها",
                    modifier = Modifier.rotate(chevronRotation)
                )
            }

            if (expanded) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    SectionLabel("وضعیتِ پیام")
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach { item ->
                            IconFilterChip(
                                icon = iconForFilter(item),
                                contentDescription = item.label,
                                selected = selectedIds.contains(item.id),
                                onClick = { onToggle(item) }
                            )
                        }
                    }

                    SectionLabel("زمان")
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimeRangePreset.entries.forEach { preset ->
                            val isSelected = (timeSelection as? TimeFilterSelection.Preset)?.preset == preset
                            IconFilterChip(
                                icon = iconForTimePreset(preset),
                                contentDescription = preset.label,
                                selected = isSelected,
                                onClick = {
                                    onTimeSelectionChange(
                                        if (isSelected) TimeFilterSelection.None else TimeFilterSelection.Preset(preset)
                                    )
                                }
                            )
                        }

                        val customSelection = timeSelection as? TimeFilterSelection.Custom
                        IconFilterChip(
                            icon = Icons.Filled.DateRange,
                            contentDescription = if (customSelection != null) {
                                "${DateFormatter.formatDayMonth(customSelection.range.fromMillis)} تا ${DateFormatter.formatDayMonth(customSelection.range.toMillis)}"
                            } else {
                                "بازه‌ی دلخواه"
                            },
                            selected = customSelection != null,
                            onClick = { rangePickerStage = RangePickerStage.FROM },
                            trailingIcon = if (customSelection != null) Icons.Filled.Close else null,
                            onTrailingClick = if (customSelection != null) {
                                { onTimeSelectionChange(TimeFilterSelection.None) }
                            } else null
                        )
                    }

                    SectionLabel("مرتب‌سازی")
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConversationSortType.entries.forEach { type ->
                            val isSelected = sortType == type
                            IconFilterChip(
                                icon = iconForSort(type),
                                contentDescription = type.label,
                                selected = isSelected,
                                onClick = { onSortTypeChange(if (isSelected) null else type) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private val FILTER_CHIP_WIDTH = 60.dp // کوچیک‌تر از چیپِ منوی «+» (که ۷۴dp بود)
private val FILTER_CHIP_MIN_HEIGHT = 60.dp
private val FILTER_CHIP_CLEAR_BADGE_SIZE = 16.dp

@Composable
private fun IconFilterChip(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.width(FILTER_CHIP_WIDTH)) {
        // آیکن بالا، لیبل درست زیرش، هردو وسط‌چین - دقیقاً همون الگوی چیپِ
        // زمان‌بندیِ منوی «+» (AttachMenuChip تو AttachMenu.kt)، فقط کوچیک‌تر؛ اگه
        // لیبل جا نشه تا ۲ خط می‌شکنه و بعدش سه‌نقطه می‌خوره
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = FILTER_CHIP_MIN_HEIGHT)
                .clip(shape)
                .background(bgColor)
                .border(1.dp, borderColor, shape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true, color = MaterialTheme.colorScheme.primary),
                    onClick = onClick
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = contentDescription,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = contentTint,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingIcon != null && onTrailingClick != null) {
            // بجِ کوچیکِ گوشه‌ی چیپ - همون ظاهرِ دکمه‌ی لغوِ چیپِ زمان‌بندی
            // (دایره‌ی قرمزِ پر با آیکنِ سفید)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(FILTER_CHIP_CLEAR_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTrailingClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = "پاک کردن",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // خط سمت چپ
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        // متن در وسط
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // خط سمت راست
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )
    }
}
private fun iconForFilter(type: ConversationFilterType): ImageVector = when (type) {
    ConversationFilterType.UNREAD -> Icons.Filled.MarkChatUnread
    ConversationFilterType.PINNED -> Icons.Filled.PushPin
    ConversationFilterType.NON_CONTACT -> Icons.Filled.PersonOff
    ConversationFilterType.HAS_PINNED_MESSAGE -> Icons.Outlined.PushPin
    ConversationFilterType.HAS_FAVORITE_MESSAGE -> Icons.Filled.Star
    ConversationFilterType.DRAFT -> Icons.Filled.Edit
    ConversationFilterType.GROUPED -> Icons.Filled.Folder
}

private fun iconForTimePreset(preset: TimeRangePreset): ImageVector = when (preset.ordinal) {
    0 -> Icons.Filled.Filter1
    1 -> Icons.Filled.History
    2 -> Icons.Filled.DateRange
    3 -> Icons.Filled.CalendarMonth
    else -> Icons.Filled.CalendarToday
}

private fun iconForSort(type: ConversationSortType): ImageVector = when (type.ordinal) {
    0 -> Icons.Filled.ArrowDownward
    1 -> Icons.Filled.ArrowUpward
    2 -> Icons.Filled.MarkChatUnread
    else -> Icons.Filled.Sort
}