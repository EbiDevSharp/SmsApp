@file:OptIn(ExperimentalMaterial3Api::class)

package com.petro.smsapp.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.petro.smsapp.data.AppSettings

/**
 * صفحه جدا برای همه تنظیمات پاپ‌آپ پیامک (زیر بخش اعلان‌ها).
 */
@Composable
fun PopupSettingsScreen(
    onOpenPopupActions: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()

    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var infoDialogText by remember { mutableStateOf<String?>(null) }
    val masterOn = settings.popupInsteadOfNotificationEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پاپ‌آپ پیامک") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            PopupSwitchRow(
                title = "فعال‌سازی پاپ‌آپ",
                info = "به‌جای نوتیف معمولی، پیامک تازه‌رسیده را به‌صورت پاپ‌آپ نشان بده (بر اساس تنظیمات زیر)",
                checked = masterOn,
                onChecked = { AppSettings.setPopupInsteadOfNotificationEnabled(context, it) },
                onInfo = { infoDialogText = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "زمان نمایش",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            PopupSwitchRow(
                title = "روی صفحه‌قفل",
                info = "اگر خاموش باشد، وقتی گوشی قفل است به‌جای پاپ‌آپ همان نوتیف معمولی می‌آید",
                checked = settings.popupOnLockEnabled,
                onChecked = { AppSettings.setPopupOnLockEnabled(context, it) },
                onInfo = { infoDialogText = it },
                enabled = masterOn
            )

            PopupSwitchRow(
                title = "وقتی برنامه‌ها بازند",
                info = "وقتی صفحه باز است (اپ پیامک یا برنامه‌های دیگر). اگر خاموش باشد در این حالت نوتیف معمولی می‌آید تا مزاحمت کمتر شود",
                checked = settings.popupWhenUnlockedEnabled,
                onChecked = { AppSettings.setPopupWhenUnlockedEnabled(context, it) },
                onInfo = { infoDialogText = it },
                enabled = masterOn
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            ListItem(
                headlineContent = {
                    Text(
                        "دکمه‌های پاپ‌آپ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (masterOn) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                },
                supportingContent = {
                    Text(
                        "عملیات، ترتیب و نمایش آیکن/متن",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (masterOn) 1f else 0.38f)
                    )
                },
                modifier = if (masterOn) {
                    Modifier.clickable(onClick = onOpenPopupActions)
                } else Modifier,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )

            if (masterOn && settings.popupWhenUnlockedEnabled && !overlayPermissionGranted) {
                ListItem(
                    headlineContent = {
                        Text(
                            "پرمیشن «نمایش روی برنامه‌های دیگر» لازمه",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = {
                        Text("بدون این پرمیشن پاپ‌آپ در حالت صفحه باز کار نمی‌کند و نوتیف معمولی می‌آید.")
                    },
                    trailingContent = {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        ) {
                            Text("اجازه")
                        }
                    }
                )
            }
        }
    }

    if (infoDialogText != null) {
        AlertDialog(
            onDismissRequest = { infoDialogText = null },
            confirmButton = {
                TextButton(onClick = { infoDialogText = null }) { Text("باشه") }
            },
            text = { Text(infoDialogText!!) }
        )
    }
}

@Composable
private fun PopupSwitchRow(
    title: String,
    info: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onInfo: (String) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onInfo(info) }, enabled = enabled) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "توضیحات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f)
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onChecked,
                    enabled = enabled,
                    modifier = Modifier.scale(0.65f)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    )
}
