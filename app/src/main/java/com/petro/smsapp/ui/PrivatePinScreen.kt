package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class PinStage { LOADING, VERIFY, SETUP_ENTER, SETUP_CONFIRM }

/**
 * صفحه‌ی ورود به بخش «خصوصی» با یه رمز ۴ رقمی.
 *
 * چون رمز الان روی DataStore ئه (نه SharedPreferences synchronous قبلی)، چک‌کردن
 * وجودِ رمز و تائید/ساختش همگی suspend شدن؛ برای همین این صفحه خودش (با
 * rememberCoroutineScope) مسئولِ صدا زدنِ suspend لامبداهاست، نه صداکننده‌ش.
 */
@Composable
fun PrivatePinScreen(
    checkHasExistingPin: suspend () -> Boolean,
    onVerifyPin: suspend (pin: String) -> Boolean,
    onSetPin: suspend (pin: String) -> Unit,
    onUnlocked: () -> Unit,
    onMenuClick: () -> Unit,
    onBack: () -> Unit
) {
    var stage by remember { mutableStateOf(PinStage.LOADING) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        stage = if (checkHasExistingPin()) PinStage.VERIFY else PinStage.SETUP_ENTER
    }

    fun onDigit(d: String) {
        if (input.length >= 4) return
        errorMessage = null
        input += d
        if (input.length == 4) {
            when (stage) {
                PinStage.VERIFY -> {
                    val enteredPin = input
                    scope.launch {
                        if (onVerifyPin(enteredPin)) {
                            onUnlocked()
                        } else {
                            errorMessage = "رمز اشتباهه"
                            input = ""
                        }
                    }
                }
                PinStage.SETUP_ENTER -> {
                    firstPin = input
                    input = ""
                    stage = PinStage.SETUP_CONFIRM
                }
                PinStage.SETUP_CONFIRM -> {
                    if (input == firstPin) {
                        val newPin = input
                        scope.launch {
                            onSetPin(newPin)
                            onUnlocked()
                        }
                    } else {
                        errorMessage = "رمزها یکسان نبودن، دوباره تلاش کن"
                        firstPin = ""
                        input = ""
                        stage = PinStage.SETUP_ENTER
                    }
                }
                PinStage.LOADING -> Unit
            }
        }
    }

    fun onBackspace() {
        if (input.isNotEmpty()) input = input.dropLast(1)
    }

    val title = when (stage) {
        PinStage.LOADING -> ""
        PinStage.VERIFY -> "رمز بخش خصوصی رو وارد کن"
        PinStage.SETUP_ENTER -> "یه رمز ۴ رقمی برای بخش خصوصی بساز"
        PinStage.SETUP_CONFIRM -> "رمز رو دوباره وارد کن"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خصوصی") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "منو") }
                }
            )
        }
    ) { padding ->
        if (stage == PinStage.LOADING) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { i ->
                        val filled = i < input.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.height(20.dp)) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(64.dp))
                        } else {
                            PinKey(label = key) {
                                if (key == "⌫") onBackspace() else onDigit(key)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
