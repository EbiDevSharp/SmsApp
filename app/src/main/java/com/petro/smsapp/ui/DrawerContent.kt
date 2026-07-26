package com.petro.smsapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.R
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SettingsSuggest

data class DrawerItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: androidx.compose.ui.graphics.Color? = null
)

val drawerItems = listOf(
    //DrawerItem("list", "پیام‌ها", Icons.Filled.Message),

    DrawerItem("favorites", "علاقه‌مندی‌ها", Icons.Filled.Star,androidx.compose.ui.graphics.Color(0xFFFFC107)),
    DrawerItem("trash", "سطل زباله", Icons.Filled.Delete,androidx.compose.ui.graphics.Color(0xFFC62828)),
    DrawerItem("scheduled", "زمان‌بندی‌شده", Icons.Filled.Schedule,androidx.compose.ui.graphics.Color(0xFF2196F3)),
    DrawerItem("blocked", "مسدودشده‌ها", Icons.Filled.Block,androidx.compose.ui.graphics.Color.Red),
    DrawerItem("private", "خصوصی", Icons.Filled.Lock,androidx.compose.ui.graphics.Color(0xFF7E57C2)),
    DrawerItem("settings", "تنظیمات",  Icons.Filled.Build,androidx.compose.ui.graphics.Color(0xFF7E57C2)),
)

@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {

    // ===== Header =====
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {

            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "هما پیامک",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    drawerItems.forEach { item ->
        NavigationDrawerItem(
            label = { Text(item.label) },
            icon = { Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.iconColor ?: MaterialTheme.colorScheme.onSurfaceVariant
            ) },
            selected = item.route == currentRoute,
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { onItemClick(item.route) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}