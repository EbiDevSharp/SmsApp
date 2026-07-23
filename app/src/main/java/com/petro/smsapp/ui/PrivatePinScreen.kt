package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private enum class PinStage { VERIFY, SETUP_ENTER, SETUP_CONFIRM }

/**
 * صفحه‌ی ورود به بخش «خصوصی» با یه رمز ۴ رقمی.
 * - اگه هنوز رمزی ساخته نشده (hasExistingPin=false): اول یه رمز جدید می‌گیره، بعد برای
 *   اطمینان دوباره همون رمز رو می‌خواد؛ اگه یکی بودن، ذخیره میشه و وارد میشیم.
 * - اگه رمز از قبل هست: فقط یه‌بار می‌خواد و با رمز ذخیره‌شده مقایسه می‌کنه.
 *
 * onVerifyPin/onSetPin عملیات واقعی (هش‌کردن و مقایسه با PrivateStore) رو از بیرون
 * (ViewModel) می‌گیرن - این کامپوننت فقط UI و جریان صفحات رو مدیریت می‌کنه.
 */
@Composable
fun PrivatePinScreen(
    hasExistingPin: Boolean,
    onVerifyPin: (pin: String) -> Boolean,
    onSetPin: (pin: String) -> Unit,
    onUnlocked: () -> Unit,
    onBack: () -> Unit
) {
    var stage by remember { mutableStateOf(if (hasExistingPin) PinStage.VERIFY else PinStage.SETUP_ENTER) }
    var firstPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun onDigit(d: String) {
        if (input.length >= 4) return
        errorMessage = null
        input += d
        if (input.length == 4) {
            when (stage) {
                PinStage.VERIFY -> {
                    if (onVerifyPin(input)) {
                        onUnlocked()
                    } else {
                        errorMessage = "رمز اشتباهه"
                        input = ""
                    }
                }
                PinStage.SETUP_ENTER -> {
                    firstPin = input
                    input = ""
                    stage = PinStage.SETUP_CONFIRM
                }
                PinStage.SETUP_CONFIRM -> {
                    if (input == firstPin) {
                        onSetPin(input)
                        onUnlocked()
                    } else {
                        errorMessage = "رمزها یکسان نبودن، دوباره تلاش کن"
                        firstPin = ""
                        input = ""
                        stage = PinStage.SETUP_ENTER
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (input.isNotEmpty()) input = input.dropLast(1)
    }

    val title = when (stage) {
        PinStage.VERIFY -> "رمز بخش خصوصی رو وارد کن"
        PinStage.SETUP_ENTER -> "یه رمز ۴ رقمی برای بخش خصوصی بساز"
        PinStage.SETUP_CONFIRM -> "رمز رو دوباره وارد کن"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خصوصی") },
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

            Spacer(modifier = Modifier.height(16.dp))
            // ارتفاع ثابت برای پیام خطا، تا با ظاهرشدن/ناپدیدشدنش کیبورد اعداد جابه‌جا نشه
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
