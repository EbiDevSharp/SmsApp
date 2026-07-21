package com.petro.smsapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.util.DateFormatter

@Composable
fun ThreadScreen(
    displayName: String,
    messages: List<SmsMessage>,
    sims: List<SimInfo>,
    onSend: (body: String, subscriptionId: Int?) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }
    var selectedMessage by remember { mutableStateOf<SmsMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(sims) {
        if (selectedSimId == null && sims.isNotEmpty()) {
            selectedSimId = sims.first().subscriptionId
        }
    }

    // اسکرول خودکار به آخرین پیام داخل BoxWithConstraints هندل میشه (هم برای پیام جدید، هم برای تغییر فضا با کیبورد)

    if (selectedMessage != null) {
        MessageActionsSheet(
            message = selectedMessage!!,
            contactDisplayName = displayName,
            onDismiss = { selectedMessage = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                SimSelector(
                    sims = sims,
                    selectedSubscriptionId = selectedSimId,
                    onSelect = { selectedSimId = it }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // دکمه ارسال اول میاد تا توی چیدمان راست‌به‌چپ سمت راست کادر بشینه
                    Button(onClick = {
                        if (input.isNotBlank()) {
                            onSend(input, selectedSimId)
                            input = ""
                        }
                    }) {
                        Text("ارسال")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("پیام...") },
                        maxLines = 5,
                        // متن انگلیسی/اعداد از چپ نوشته بشن، حتی داخل کانتینر راست‌به‌چپ
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // هر بار فضای واقعی در دسترس عوض بشه (باز/بسته شدن کیبورد، چرخش صفحه و ...)
            // دوباره برو آخرین پیام تا زیر کادر ارسال قایم نمونه
            val availableHeight = maxHeight
            LaunchedEffect(availableHeight, messages.size) {
                if (messages.isNotEmpty()) {
                    // چون reverseLayout=true هست، ایندکس ۰ = پایین‌ترین/جدیدترین پیام
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(messages.reversed(), key = { it.id }) { message ->
                    MessageBubble(message = message, onClick = { selectedMessage = message })
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SmsMessage, onClick: () -> Unit) {
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isOutgoing) MaterialTheme.colorScheme.primary else Color(0xFFE5E5EA)
    val textColor = if (message.isOutgoing) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box(contentAlignment = alignment, modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = bubbleColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(4.dp)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    text = message.body,
                    color = textColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Text(
            text = DateFormatter.formatSmart(message.date),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
