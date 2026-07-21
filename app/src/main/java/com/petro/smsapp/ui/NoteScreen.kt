package com.petro.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

/**
 * صفحه‌ی «متن پیام» - فعلاً شبیه یه برگه‌ی نوت‌پد ساده‌ست: فقط متن پیام رو نشون میده و
 * امکان انتخاب/کپی/کات/انتخاب‌همه داره (از طریق فیلد متنی قابل‌ویرایش و منوی سیستمی انتخاب متن).
 * قابلیت‌های بعدی (ذخیره‌ی یادداشت جدا، ویرایش دائمی، اشتراک‌گذاری و ...) به همین صفحه اضافه میشن.
 *
 * توجه: این فیلد مستقل از دیتابیس پیامک‌هاست - تغییر یا کات‌کردن متن اینجا روی خود پیامک ذخیره‌شده
 * توی گوشی تاثیری نداره، فقط یه کپی موقت برای کار کردن با متنه.
 */
@Composable
fun NoteScreen(text: String, onBack: () -> Unit) {
    var fieldValue by remember(text) { mutableStateOf(TextFieldValue(text)) }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("متن پیام") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = {
                        fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                    }) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "انتخاب همه")
                    }
                    IconButton(onClick = {
                        val toCopy = if (fieldValue.selection.length > 0) {
                            fieldValue.text.substring(fieldValue.selection.min, fieldValue.selection.max)
                        } else {
                            fieldValue.text
                        }
                        clipboardManager.setText(AnnotatedString(toCopy))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // فیلد متنی معمولی خودش منوی سیستمی «کپی/کات/چسباندن/انتخاب همه» رو موقع
            // انتخاب متن (لانگ‌پرس یا درگ) نشون میده؛ دکمه‌های بالای صفحه فقط میان‌بر سریع‌ترن.
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
            )
        }
    }
}
