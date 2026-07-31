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

/**
 * صفحه‌ی «افزودن فرستنده‌ی غیرشماره» - برای بلاک‌کردن پیامک‌هایی که فرستنده‌شون یه
 * شماره‌ی واقعی نیست، بلکه یه Sender ID حروفی‌ه (مثلاً اسم اپراتور «ایرانسل»/«همراه‌اول»
 * یا یه Sender ID انگلیسیِ سرویس‌ها مثل «GOOGLE»). قبلاً هیچ راهی برای بلاک‌کردنِ
 * صریح/دستیِ همچین فرستنده‌ای وجود نداشت.
 *
 * عمداً جدا از AddBlockedNumberScreen نگه داشته شده: اونجا برای شماره‌های واقعیه و
 * جستجو/انتخاب از مخاطبین گوشی معنی داره؛ اینجا چون Sender ID اصلاً تو مخاطبین گوشی
 * ذخیره نمیشه، فقط یه فیلدِ متنیِ ساده برای وارد کردنِ دقیقِ همون متنی که به‌عنوانِ
 * فرستنده تو لیستِ مکالمات/پیامک دیده میشه لازمه.
 */
@Composable
fun AddBlockedSenderScreen(
    onBlockSender: (sender: String) -> Unit,
    onBack: () -> Unit
) {
    var senderInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افزودن فرستنده‌ی غیرشماره") },
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
                text = "برای فرستنده‌هایی که شماره نیستن (مثل اسمِ اپراتور یا Sender ID های انگلیسیِ سرویس‌ها)، دقیقاً همون متنی که به‌عنوانِ فرستنده نشون داده میشه رو وارد کن.",
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
                        onBlockSender(senderInput.trim())
                        onBack()
                    }
                },
                enabled = senderInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("بلاک کردن این فرستنده")
            }
        }
    }
}
