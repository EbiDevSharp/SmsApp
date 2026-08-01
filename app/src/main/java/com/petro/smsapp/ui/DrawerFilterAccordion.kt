package com.petro.smsapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ConversationFilterType
import com.petro.smsapp.data.ConversationSortType
import com.petro.smsapp.data.CustomTimeRange
import com.petro.smsapp.data.TimeFilterSelection
import com.petro.smsapp.data.TimeRangePreset
import com.petro.smsapp.util.DateFormatter

/**
 * آکاردئونِ فیلترِ لیستِ چت‌ها - بالای همه‌ی آیتم‌های درآور قرار می‌گیره. سه بخشِ
 * مستقل داره، همه به‌شکلِ چیپ‌های کنارِ‌هم و wrap-شونده (FlowRow):
 *
 * ۱) «وضعیت پیام» - چندتایی‌انتخاب (خوانده‌نشده، سنجاق‌شده و ...).
 * ۲) «زمان» - تک‌انتخابی: چهارتا بازه‌ی آماده + یه چیپِ «بازه‌ی دلخواه» که دیالوگِ
 *    از‌تاریخ/تا‌تاریخ رو باز می‌کنه.
 * ۳) «مرتب‌سازی» - تک‌انتخابی: وقتی هیچی انتخاب نشده باشه، ترتیبِ پیش‌فرض (پین بالا)
 *    دست‌نخورده می‌مونه؛ با انتخابِ هرکدوم، پین‌بودن دیگه اولویتی نداره.
 *
 * این کامپوننت یه ماژولِ کاملاً مستقله - فقط state/callback ها رو از بیرون می‌گیره،
 * خودش هیچ منطق ذخیره‌سازی/فیلترِ واقعی نداره.
 */
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
    var showCustomTimeDialog by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "drawer_filter_chevron")

    val activeCount = selectedIds.size +
        (if (timeSelection != TimeFilterSelection.None) 1 else 0) +
        (if (sortType != null) 1 else 0)

    if (showCustomTimeDialog) {
        val existingCustom = (timeSelection as? TimeFilterSelection.Custom)?.range
        val defaultFrom = System.currentTimeMillis() - 6L * 24 * 60 * 60 * 1000
        CustomTimeRangeDialog(
            initialFromMillis = existingCustom?.fromMillis ?: defaultFrom,
            initialToMillis = existingCustom?.toMillis ?: System.currentTimeMillis(),
            onConfirm = { from, to ->
                onTimeSelectionChange(TimeFilterSelection.Custom(CustomTimeRange(from, to)))
                showCustomTimeDialog = false
            },
            onDismiss = { showCustomTimeDialog = false }
        )
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
                            val isChecked = selectedIds.contains(item.id)
                            FilterChip(
                                selected = isChecked,
                                onClick = { onToggle(item) },
                                label = { Text(item.label, style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = iconForFilter(item),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = chipColors()
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
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onTimeSelectionChange(
                                        if (isSelected) TimeFilterSelection.None else TimeFilterSelection.Preset(preset)
                                    )
                                },
                                label = { Text(preset.label, style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                colors = chipColors()
                            )
                        }

                        val customSelection = timeSelection as? TimeFilterSelection.Custom
                        FilterChip(
                            selected = customSelection != null,
                            onClick = { showCustomTimeDialog = true },
                            label = {
                                Text(
                                    text = if (customSelection != null) {
                                        "${DateFormatter.formatDayMonth(customSelection.range.fromMillis)} تا ${DateFormatter.formatDayMonth(customSelection.range.toMillis)}"
                                    } else {
                                        "بازه‌ی دلخواه"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = if (customSelection != null) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "پاک کردنِ بازه‌ی دلخواه",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onTimeSelectionChange(TimeFilterSelection.None) }
                                    )
                                }
                            } else null,
                            colors = chipColors()
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
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSortTypeChange(if (isSelected) null else type) },
                                label = { Text(type.label, style = MaterialTheme.typography.bodyMedium) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                colors = chipColors()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** رنگ‌بندیِ مشترکِ همه‌ی چیپ‌های آکاردئون - یه‌جا تعریف شده تا هر سه بخش دقیقاً یکی باشن */
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

/** برچسبِ کوچیکِ بالای هر بخش (وضعیتِ پیام / زمان / مرتب‌سازی) */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
    )
}

/** آیکنِ هر نوع فیلترِ وضعیتِ پیام - آیتم‌های بعدی که اضافه بشن، فقط یه شاخه‌ی when جدید اینجا لازم دارن */
private fun iconForFilter(type: ConversationFilterType): ImageVector = when (type) {
    ConversationFilterType.UNREAD -> Icons.Filled.MarkChatUnread
    ConversationFilterType.PINNED -> Icons.Filled.PushPin
    ConversationFilterType.NON_CONTACT -> Icons.Filled.PersonOff
    // آیکنِ توخالی برای تمایز از فیلترِ PINNED (سنجاق‌شدنِ خودِ چت) که آیکنِ توپر داره
    ConversationFilterType.HAS_PINNED_MESSAGE -> Icons.Outlined.PushPin
    ConversationFilterType.HAS_FAVORITE_MESSAGE -> Icons.Filled.Star
}
