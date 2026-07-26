package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Rule
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
    onOpenBlockPatterns: () -> Unit
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
