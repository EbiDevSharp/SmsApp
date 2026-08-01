package com.petro.smsapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PushPin
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

/**
 * آکاردئونِ فیلترِ لیستِ چت‌ها - بالای همه‌ی آیتم‌های درآور قرار می‌گیره. با کلیک روی
 * هدر رو به پایین باز میشه و آیتم‌ها به شکلِ چیپ‌های کنارِ‌هم (نه چک‌باکسِ عمودی) نشون
 * داده میشن - با FlowRow خودکار به خطِ بعدی wrap میشن، پس اضافه‌کردنِ آیتم‌های بیشتر
 * در آینده فقط باعثِ بلندتر شدنِ آکاردئون میشه، نه به‌هم‌ریختنِ چیدمان.
 *
 * این کامپوننت یه ماژولِ کاملاً مستقله - فقط items/selectedIds/onToggle رو از بیرون
 * می‌گیره و خودش هیچ منطق ذخیره‌سازی/فیلترِ واقعی نداره. بعداً که لیستِ آیتم‌ها از
 * تنظیمات داینامیک بشه (کاربر بتونه آیتم‌ها رو کم/زیاد یا مرتب کنه)، کافیه پارامترِ
 * items از یه StateFlow/DataStore پر بشه - خودِ این فایل نیازی به تغییر نداره.
 *
 * انتخاب چندتایی: هر چیپ مستقل انتخاب/عدم‌انتخاب میشه، نه رادیویی.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawerFilterAccordion(
    items: List<ConversationFilterType> = ConversationFilterType.entries,
    selectedIds: Set<String>,
    onToggle: (ConversationFilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "drawer_filter_chevron")

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
                if (selectedIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedIds.size.toString(),
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
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
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
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

/** آیکنِ هر نوع فیلتر - آیتم‌های بعدی که اضافه بشن، فقط یه شاخه‌ی when جدید اینجا لازم دارن */
private fun iconForFilter(type: ConversationFilterType): ImageVector = when (type) {
    ConversationFilterType.UNREAD -> Icons.Filled.MarkChatUnread
    ConversationFilterType.PINNED -> Icons.Filled.PushPin
    ConversationFilterType.NON_CONTACT -> Icons.Filled.PersonOff
    // آیکنِ توخالی برای تمایز از فیلترِ PINNED (سنجاق‌شدنِ خودِ چت) که آیکنِ توپر داره
    ConversationFilterType.HAS_PINNED_MESSAGE -> Icons.Outlined.PushPin
    ConversationFilterType.HAS_FAVORITE_MESSAGE -> Icons.Filled.Star
}
