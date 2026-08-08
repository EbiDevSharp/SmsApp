package com.petro.smsapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petro.smsapp.R
import com.petro.smsapp.data.ConversationFilterType
import com.petro.smsapp.data.ConversationSortType
import com.petro.smsapp.data.ThemeMode
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
    themeMode: ThemeMode,
    onCycleTheme: (centerInRoot: Offset) -> Unit,
    selectedFilterIds: Set<String> = emptySet(),
    onToggleFilter: (ConversationFilterType) -> Unit = {},
    timeSelection: TimeFilterSelection = TimeFilterSelection.None,
    onTimeSelectionChange: (TimeFilterSelection) -> Unit = {},
    sortType: ConversationSortType? = null,
    onSortTypeChange: (ConversationSortType?) -> Unit = {},
    sims: List<com.petro.smsapp.data.SimInfo> = emptyList()
) {
    // مرکز آیکن تم در مختصات root (برای شروع انیمیشن دایره‌ای از همان نقطه)
    var iconCenter by remember { mutableStateOf(Offset.Zero) }

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

        IconButton(
            onClick = { onCycleTheme(iconCenter) },
            modifier = Modifier.onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                iconCenter = Offset(
                    pos.x + coords.size.width / 2f,
                    pos.y + coords.size.height / 2f
                )
            }
        ) {
            val (icon, description, tint) = when (themeMode) {
                ThemeMode.LIGHT -> Triple(
                    Icons.Filled.LightMode,
                    "حالت روز — برای تغییر به شب بزن",
                    Color(0xFFFFA000)
                )
                ThemeMode.DARK -> Triple(
                    Icons.Filled.DarkMode,
                    "حالت شب — برای تغییر به سیستم بزن",
                    Color(0xFF90CAF9)
                )
                ThemeMode.SYSTEM -> Triple(
                    Icons.Filled.BrightnessAuto,
                    "حالت سیستم — برای تغییر به روز بزن",
                    MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = tint
            )
        }
    }

    DrawerFilterAccordion(
        selectedIds = selectedFilterIds,
        onToggle = onToggleFilter,
        timeSelection = timeSelection,
        onTimeSelectionChange = onTimeSelectionChange,
        sortType = sortType,
        onSortTypeChange = onSortTypeChange,
        sims = sims
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
