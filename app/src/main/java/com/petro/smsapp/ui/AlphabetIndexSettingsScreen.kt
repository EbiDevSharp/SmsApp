@file:OptIn(ExperimentalMaterial3Api::class)

package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.AppSettings

/**
 * صفحه جدا برای تنظیمات نوار حروف الفبا (Alphabet Index Bar):
 * فعال/غیرفعال، اندازه بادکنک، فاصله افقی (محور X) و فاصله عمودی (محور Y).
 */
@Composable
fun AlphabetIndexSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by AppSettings.state.collectAsState()
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    val masterOn = settings.alphabetIndexBarEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نوار حروف الفبا") },
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
            ListItem(
                headlineContent = { Text("فعال‌سازی نوار حروف") },
                supportingContent = {
                    Text("نوار کناری سمت چپ برای پرش سریع به مخاطبین بر اساس حرف اول اسم")
                },
                trailingContent = {
                    Switch(
                        checked = masterOn,
                        onCheckedChange = { AppSettings.setAlphabetIndexBarEnabled(context, it) }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "ظاهر و فاصله",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // اندازه بادکنک
            AlphabetSliderRow(
                title = "اندازه بادکنک",
                subtitle = "اندازه دایره بزرگ نمایش حرف حین لمس (${AppSettings.MIN_ALPHABET_BUBBLE_SIZE_DP}–${AppSettings.MAX_ALPHABET_BUBBLE_SIZE_DP})",
                value = settings.alphabetBubbleSizeDp,
                valueRange = AppSettings.MIN_ALPHABET_BUBBLE_SIZE_DP.toFloat()..AppSettings.MAX_ALPHABET_BUBBLE_SIZE_DP.toFloat(),
                enabled = masterOn,
                onValueChange = { AppSettings.setAlphabetBubbleSizeDp(context, it) },
                onInfo = {
                    infoDialogText = "بادکنک فقط موقع لمس یا کشیدن انگشت روی نوار حروف ظاهر می‌شود. اندازه خیلی بزرگ ممکن است مزاحم محتوای لیست شود."
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // محور X
            AlphabetSliderRow(
                title = "فاصله افقی (محور X)",
                subtitle = "فاصله نوار از لبه چپ صفحه و فاصله از ردیف پیام‌ها (${AppSettings.MIN_ALPHABET_OFFSET_X_DP}–${AppSettings.MAX_ALPHABET_OFFSET_X_DP})",
                value = settings.alphabetOffsetXDp,
                valueRange = AppSettings.MIN_ALPHABET_OFFSET_X_DP.toFloat()..AppSettings.MAX_ALPHABET_OFFSET_X_DP.toFloat(),
                enabled = masterOn,
                onValueChange = { AppSettings.setAlphabetOffsetXDp(context, it) },
                onInfo = {
                    infoDialogText = "مقدار بیشتر = نوار از لبه دورتر و فضای بیشتری بین حروف و ردیف‌های مکالمه. مقدار ۰ ممکن است حروف را به لبه یا ردیف‌ها بچسباند."
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // محور Y
            AlphabetSliderRow(
                title = "فاصله عمودی (محور Y)",
                subtitle = "فاصله حروف از بالا و پایین نوار (${AppSettings.MIN_ALPHABET_OFFSET_Y_DP}–${AppSettings.MAX_ALPHABET_OFFSET_Y_DP})",
                value = settings.alphabetOffsetYDp,
                valueRange = AppSettings.MIN_ALPHABET_OFFSET_Y_DP.toFloat()..AppSettings.MAX_ALPHABET_OFFSET_Y_DP.toFloat(),
                enabled = masterOn,
                onValueChange = { AppSettings.setAlphabetOffsetYDp(context, it) },
                onInfo = {
                    infoDialogText = "فاصله عمودی بین اولین/آخرین حرف و لبه‌های بالا/پایین ناحیه نوار. برای تعداد زیاد حروف مقدار کمتر مناسب‌تر است."
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "پیشنهاد پیش‌فرض: اندازه بادکنک ۶۴، فاصله افقی ۶، فاصله عمودی ۸ — این مقادیر تعادل خوبی بین خوانایی و فاصله از لبه/ردیف‌ها می‌دهند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (infoDialogText != null) {
        AlertDialog(
            onDismissRequest = { infoDialogText = null },
            confirmButton = {
                TextButton(onClick = { infoDialogText = null }) { Text("باشه") }
            },
            title = { Text("راهنما") },
            text = { Text(infoDialogText!!) }
        )
    }
}

@Composable
private fun AlphabetSliderRow(
    title: String,
    subtitle: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onInfo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onInfo, enabled = true) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "راهنما",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val next = (value - 1).coerceAtLeast(valueRange.start.toInt())
                    if (next != value) onValueChange(next)
                },
                enabled = enabled && value > valueRange.start.toInt()
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "کم کردن")
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange,
                steps = ((valueRange.endInclusive - valueRange.start).toInt() - 1).coerceAtLeast(0),
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val next = (value + 1).coerceAtMost(valueRange.endInclusive.toInt())
                    if (next != value) onValueChange(next)
                },
                enabled = enabled && value < valueRange.endInclusive.toInt()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "زیاد کردن")
            }
        }
    }
}
