package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * هاب اصلی بخش «خصوصی» - دقیقاً شبیه BlockScreen: دو ورودی، پیامک‌های خصوصی و
 * شماره‌های خصوصی، هرکدوم با یه بج شمارنده. فرق اصلی با بلاک اینه که قبل از رسیدن
 * به این صفحه، از پشتِ یه رمز ۴ رقمی (PrivatePinScreen) رد شدیم.
 */
@Composable
fun PrivateScreen(
    privateMessageCount: Int,
    privateNumberCount: Int,
    onBack: () -> Unit,
    onOpenPrivateMessages: () -> Unit,
    onOpenPrivateNumbers: () -> Unit,
    onOpenPinSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خصوصی") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = onOpenPinSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "تنظیمات رمز")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PrivateHubCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Sms,
                label = "پیامک‌های خصوصی",
                count = privateMessageCount,
                onClick = onOpenPrivateMessages
            )
            PrivateHubCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Lock,
                label = "شماره‌های خصوصی",
                count = privateNumberCount,
                onClick = onOpenPrivateNumbers
            )
        }
    }
}

@Composable
private fun PrivateHubCard(
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
