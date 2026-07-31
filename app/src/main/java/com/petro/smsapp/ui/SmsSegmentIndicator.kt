package com.petro.smsapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.petro.smsapp.util.SmsSegmentCalculator

/**
 * نمایش کوچیکِ «تعداد کاراکتر باقی‌مونده / تعداد پیامک» (مثلاً 160/1) - قابل استفاده
 * هم توی نوار ارسال (کنار دکمه‌ی ارسال) هم زیر هر حباب پیام (ارسالی/دریافتی).
 *
 * همیشه چپ‌به‌راست نمایش داده میشه (خودِ اعداد/اسلش، مستقل از جهتِ کلیِ صفحه)، دقیقاً
 * هم‌قاعده‌ی بقیه‌ی جاهایی که عدد/شماره تو برنامه نشون داده میشن.
 */
@Composable
fun SmsSegmentIndicator(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    showRemaining: Boolean = true   // ← جدید: true = باقی‌مونده (نوار ارسال)، false = کلِ کاراکتر (حباب پیام)
) {
    val info = remember(text) { SmsSegmentCalculator.calculate(text) }
    val count = if (showRemaining) info.remainingChars else info.totalLength
    Text(
        text = "$count/${info.segmentCount}",
        color = color,
        fontSize = fontSize,
        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
        modifier = modifier
    )
}
