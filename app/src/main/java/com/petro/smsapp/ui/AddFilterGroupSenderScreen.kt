package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petro.smsapp.util.autoDirection

@Composable
fun AddFilterGroupSenderScreen(
    groupName: String,
    onAddSender: (sender: String) -> Unit,
    onBack: () -> Unit
) {
    var senderInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افزودنِ فرستنده‌ی غیرشماره", style = LocalTextStyle.current.autoDirection()) },
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "برای فرستنده‌هایی که شماره نیستن (مثل اسمِ اپراتور یا Sender ID های انگلیسیِ سرویس‌ها)، دقیقاً همون متنی که به‌عنوانِ فرستنده نشون داده میشه رو وارد کن. به گروهِ «$groupName» اضافه میشه.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = senderInput,
                onValueChange = { senderInput = it },
                label = { Text("مثلاً ایرانسل یا GOOGLE") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.autoDirection()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (senderInput.isNotBlank()) {
                        onAddSender(senderInput.trim())
                        onBack()
                    }
                },
                enabled = senderInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("افزودن این فرستنده")
            }
        }
    }
}
