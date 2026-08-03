package com.petro.smsapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.R
import com.petro.smsapp.data.ConversationFilterType
import com.petro.smsapp.data.ConversationSortType
import com.petro.smsapp.data.TimeFilterSelection

data class DrawerItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: androidx.compose.ui.graphics.Color? = null
)

val drawerItems = listOf(
    DrawerItem("favorites", "علاقه‌مندی‌ها", Icons.Filled.Star, androidx.compose.ui.graphics.Color(0xFFFFC107)),
    DrawerItem("trash", "سطل زباله", Icons.Filled.Delete, androidx.compose.ui.graphics.Color(0xFFC62828)),
    DrawerItem("scheduled", "زمان‌بندی‌شده", Icons.Filled.Schedule, androidx.compose.ui.graphics.Color(0xFF2196F3)),
    // قبلاً «مسدودشده‌ها» با یه مقصدِ ثابت بود؛ الان کاربر خودش N تا گروهِ دلخواه می‌سازه
    DrawerItem("filter_groups", "گروه‌ها", Icons.Filled.Folder, androidx.compose.ui.graphics.Color(0xFF6D4C41)),
    DrawerItem("private", "خصوصی", Icons.Filled.Lock, androidx.compose.ui.graphics.Color(0xFF7E57C2)),
    DrawerItem("settings", "تنظیمات", Icons.Filled.Build, androidx.compose.ui.graphics.Color(0xFFA9A6A6)),
)

@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onItemClick: (String) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    selectedFilterIds: Set<String> = emptySet(),
    onToggleFilter: (ConversationFilterType) -> Unit = {},
    timeSelection: TimeFilterSelection = TimeFilterSelection.None,
    onTimeSelectionChange: (TimeFilterSelection) -> Unit = {},
    sortType: ConversationSortType? = null,
    onSortTypeChange: (ConversationSortType?) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 24.dp),
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
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (isDarkTheme) "حالت روز" else "حالت شب",
                tint = if (isDarkTheme) {
                    Color(0xFFFFD700)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }

    DrawerFilterAccordion(
        selectedIds = selectedFilterIds,
        onToggle = onToggleFilter,
        timeSelection = timeSelection,
        onTimeSelectionChange = onTimeSelectionChange,
        sortType = sortType,
        onSortTypeChange = onSortTypeChange
    )

    Spacer(modifier = Modifier.height(4.dp))

    drawerItems.forEach { item ->
        NavigationDrawerItem(
            label = { Text(item.label) },
            icon = {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.iconColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
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
