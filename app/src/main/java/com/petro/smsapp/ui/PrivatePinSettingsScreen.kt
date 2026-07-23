package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
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

private enum class Stage { MENU, VERIFY_CURRENT, NEW_ENTER, NEW_CONFIRM, CONFIRM_REMOVE }
private enum class PendingAction { CHANGE, REMOVE }

/**
 * تنظیمات رمز بخش خصوصی - از داخل خودِ PrivateScreen (آیکون تنظیمات) باز میشه.
 * دو تا کار می‌تونه بکنه:
 * - «تغییر رمز»: اول رمز فعلی رو تائید می‌کنه، بعد یه رمز جدید (با تکرار) می‌گیره.
 * - «حذف رمز»: اول رمز فعلی رو تائید می‌کنه، بعد با یه دیالوگ تائید نهایی، رمز کاملاً پاک میشه
 *   (دفعه‌ی بعد که وارد بخش خصوصی بشه، دوباره باید یه رمز جدید بسازه).
 */
@Composable
fun PrivatePinSettingsScreen(
    onVerifyPin: (pin: String) -> Boolean,
    onChangePin: (newPin: String) -> Unit,
    onRemovePin: () -> Unit,
    onBack: () -> Unit
) {
    var stage by remember { mutableStateOf(Stage.MENU) }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var firstNewPin by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }

    fun startFlow(action: PendingAction) {
        pendingAction = action
        input = ""
        errorMessage = null
        stage = Stage.VERIFY_CURRENT
    }

    fun onDigit(d: String) {
        if (input.length >= 4) return
        errorMessage = null
        input += d
        if (input.length == 4) {
            when (stage) {
                Stage.VERIFY_CURRENT -> {
                    if (onVerifyPin(input)) {
                        input = ""
                        stage = when (pendingAction) {
                            PendingAction.CHANGE -> Stage.NEW_ENTER
                            PendingAction.REMOVE -> {
                                showRemoveConfirmDialog = true
                                Stage.MENU // تا وقتی دیالوگ تائید بسته نشده برنمی‌گردیم منو، فقط دیالوگ رو نشون می‌دیم
                            }
                            null -> Stage.MENU
                        }
                    } else {
                        errorMessage = "رمز فعلی اشتباهه"
                        input = ""
                    }
                }
                Stage.NEW_ENTER -> {
                    firstNewPin = input
                    input = ""
                    stage = Stage.NEW_CONFIRM
                }
                Stage.NEW_CONFIRM -> {
                    if (input == firstNewPin) {
                        onChangePin(input)
                        onBack()
                    } else {
                        errorMessage = "رمزها یکسان نبودن، دوباره تلاش کن"
                        firstNewPin = ""
                        input = ""
                        stage = Stage.NEW_ENTER
                    }
                }
                else -> Unit
            }
        }
    }

    fun onBackspace() {
        if (input.isNotEmpty()) input = input.dropLast(1)
    }

    if (showRemoveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmDialog = false; stage = Stage.MENU },
            title = { Text("حذف رمز بخش خصوصی") },
            text = { Text("رمز کاملاً حذف میشه. دفعه‌ی بعد که وارد بخش خصوصی بشی، باید یه رمز جدید بسازی. ادامه بدیم؟") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirmDialog = false
                    onRemovePin()
                    onBack()
                }) {
                    Text("حذف رمز", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmDialog = false; stage = Stage.MENU }) {
                    Text("انصراف")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات رمز خصوصی") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (stage == Stage.MENU) onBack() else stage = Stage.MENU
                    }) { Text("←") }
                }
            )
        }
    ) { padding ->
        when (stage) {
            Stage.MENU -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    ListItem(
                        headlineContent = { Text("تغییر رمز") },
                        supportingContent = { Text("رمز ۴ رقمی فعلی رو با یه رمز جدید عوض کن") },
                        leadingContent = { Icon(Icons.Filled.Key, contentDescription = null) },
                        modifier = Modifier.clickable { startFlow(PendingAction.CHANGE) }
                    )
                    Divider()
                    ListItem(
                        headlineContent = { Text("حذف رمز", color = MaterialTheme.colorScheme.error) },
                        supportingContent = { Text("قفل بخش خصوصی کاملاً برداشته میشه") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable { startFlow(PendingAction.REMOVE) }
                    )
                    Divider()
                }
            }
            else -> {
                PinEntryBody(
                    padding = padding,
                    title = when (stage) {
                        Stage.VERIFY_CURRENT -> "رمز فعلی رو وارد کن"
                        Stage.NEW_ENTER -> "رمز جدید رو وارد کن"
                        Stage.NEW_CONFIRM -> "رمز جدید رو دوباره وارد کن"
                        else -> ""
                    },
                    input = input,
                    errorMessage = errorMessage,
                    onDigit = ::onDigit,
                    onBackspace = ::onBackspace
                )
            }
        }
    }
}

@Composable
private fun PinEntryBody(
    padding: PaddingValues,
    title: String,
    input: String,
    errorMessage: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
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
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))
        // LTR اجباری - دقیقاً همون دلیل PrivatePinScreen: پرشدن باید از چپ به راست باشه
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
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable { if (key == "⌫") onBackspace() else onDigit(key) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(key, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
