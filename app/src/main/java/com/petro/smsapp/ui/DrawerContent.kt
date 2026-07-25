package com.petro.smsapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DrawerItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

// «پیام‌ها» عمداً اولین آیتمه - چون صفحه‌ی لیست مکالمات (startDestination) هیچ‌وقت یه آیتم
// جدا تو دراور نداشت، یعنی وقتی کاربر رو صفحه‌ی اصلی بود هیچ‌کدوم از آیتم‌های دراور
// انتخاب‌شده نشون داده نمی‌شد. با اضافه‌شدنش، دقیقاً مثل تلگرام همیشه یه آیتم انتخابه.
val drawerItems = listOf(
    DrawerItem("list", "پیام‌ها", Icons.Filled.Message),
    DrawerItem("settings", "تنظیمات", Icons.Filled.Settings),
    DrawerItem("favorites", "علاقه‌مندی‌ها", Icons.Filled.Star),
    DrawerItem("trash", "سطل زباله", Icons.Filled.Delete),
    DrawerItem("scheduled", "زمان‌بندی‌شده", Icons.Filled.Schedule),
    DrawerItem("blocked", "مسدودشده‌ها", Icons.Filled.Block),
    DrawerItem("private", "خصوصی", Icons.Filled.Lock),
)

@Composable
fun AppDrawerContent(currentRoute: String?, onItemClick: (String) -> Unit) {
    drawerItems.forEach { item ->
        NavigationDrawerItem(
            label = { Text(item.label) },
            icon = { Icon(item.icon, contentDescription = null) },
            // فقط یه آیتم می‌تونه selected باشه - همونی که route ش دقیقاً با مقصد فعلی یکی باشه
            selected = item.route == currentRoute,
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            onClick = { onItemClick(item.route) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}
