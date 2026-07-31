package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * هاب اصلی بخش «بلاک» - دو ورودی: پیامک‌های بلاک‌شده (همه‌ی پیام‌های شماره‌های بلاک‌شده)
 * و شماره‌های بلاک‌شده (خودِ لیست شماره‌ها، با امکان آنبلاک). هر کدوم یه بج با تعداد داره.
 *
 * ردیفِ «افزودن فرستنده‌ی غیرشماره» جدا از دو کارتِ بالا اضافه شده: برای فرستنده‌هایی
 * که شماره‌ی واقعی نیستن (اسمِ اپراتور یا Sender ID های انگلیسی)، چون این‌جور فرستنده‌ها
 * تو مخاطبینِ گوشی معنی ندارن و AddBlockedNumberScreen (که مبتنی بر جستجوی مخاطبینه)
 * براشون مناسب نیست.
 */
@Composable
fun BlockScreen(
    blockedMessageCount: Int,
    blockedNumberCount: Int,
    blockKeywordCount: Int,
    blockPatternCount: Int,
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onOpenBlockedMessages: () -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onOpenBlockKeywords: () -> Unit,
    onOpenBlockPatterns: () -> Unit,
    onOpenAddSender: () -> Unit,
    onOpenBlockSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بلاک") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BlockHubCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Sms,
                    label = "پیامک‌های بلاک‌شده",
                    count = blockedMessageCount,
                    onClick = onOpenBlockedMessages
                )
                BlockHubCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Block,
                    label = "شماره‌های بلاک‌شده",
                    count = blockedNumberCount,
                    onClick = onOpenBlockedNumbers
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BlockHubCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.TextFields,
                    label = "کلمات کلیدی بلاک",
                    count = blockKeywordCount,
                    onClick = onOpenBlockKeywords
                )
                BlockHubCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Rule,
                    label = "الگوهای بلاکِ شماره",
                    count = blockPatternCount,
                    onClick = onOpenBlockPatterns
                )
            }

            AddSenderRow(onClick = onOpenAddSender)
            BlockSettingsRow(onClick = onOpenBlockSettings)
        }
    }
}

/** ردیفِ افزودنِ فرستنده‌ی غیرشماره (اسمِ اپراتور یا Sender ID انگلیسی) - بدونِ شمارنده، چون خودش صرفاً یه اکشنه نه یه لیست */
@Composable
private fun AddSenderRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "افزودن فرستنده‌ی غیرشماره",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "برای اسمِ اپراتور یا Sender ID های انگلیسی (مثل GOOGLE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

@Composable
private fun BlockSettingsRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "تنظیمات بلاک",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

@Composable
private fun BlockHubCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (count > 99) "99+" else count.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}