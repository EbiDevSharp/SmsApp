package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.SimInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSelector(
    sims: List<SimInfo>,
    selectedSubscriptionId: Int?,
    onSelect: (Int) -> Unit
) {
    // اگه گوشی تک‌سیم باشه اصلاً چیزی نشون نمی‌دیم
    if (sims.size < 2) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sims.forEach { sim ->
            FilterChip(
                selected = sim.subscriptionId == selectedSubscriptionId,
                onClick = { onSelect(sim.subscriptionId) },
                label = { Text(sim.displayName) }
            )
        }
    }
}
