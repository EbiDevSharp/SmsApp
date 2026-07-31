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
import androidx.compose.material3.Checkbox
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
 * آکاردئونِ فیلترِ لیست چت‌ها - بالای همه‌ی آیتم‌های درآور قرار می‌گیره. با کلیک روی
 * هدر رو به پایین باز میشه و آیتم‌های چک‌باکسی (چندتایی‌قابل‌انتخاب) رو نشون میده.
 *
 * این کامپوننت یه ماژولِ کاملاً مستقله - فقط items/selectedIds/onToggle رو از بیرون
 * می‌گیره و خودش هیچ منطق ذخیره‌سازی/فیلترِ واقعی نداره. بعداً که لیستِ آیتم‌ها از
 * تنظیمات داینامیک بشه (کاربر بتونه آیتم‌ها رو کم/زیاد یا مرتب کنه)، کافیه پارامترِ
 * items از یه StateFlow/DataStore پر بشه - خودِ این فایل نیازی به تغییر نداره.
 *
 * انتخاب چندتایی: هر آیتم مستقل تیک می‌خوره/برمی‌داره (Checkbox)، نه رادیویی.
 */
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
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    items.forEach { item ->
                        val isChecked = selectedIds.contains(item.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = { onToggle(item) })
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = iconForFilter(item),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(item.label, style = MaterialTheme.typography.bodyMedium)
                        }
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
}