package com.petro.smsapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.util.JalaliCalendar
import java.util.Calendar

private val gregorianMonthNamesFa = arrayOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن",
    "جولای", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
)

/**
 * دیالوگ انتخاب تاریخ+ساعت برای پیام زمان‌بندی‌شده. بر اساس تقویمی که کاربر تو تنظیمات
 * انتخاب کرده (شمسی یا میلادی - AppSettings.state.calendarType) نمایش داده میشه، ولی
 * همیشه یه epoch millis برمی‌گردونه چون AlarmManager/ذخیره‌سازی فقط با میلادی کار می‌کنن.
 */
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val isJalali = AppSettings.state.value.calendarType == CalendarType.JALALI
    val initialCal = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    val initialJalali = remember {
        JalaliCalendar.toJalali(
            initialCal.get(Calendar.YEAR),
            initialCal.get(Calendar.MONTH) + 1,
            initialCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    var year by remember { mutableStateOf(if (isJalali) initialJalali.year else initialCal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(if (isJalali) initialJalali.month else initialCal.get(Calendar.MONTH) + 1) }
    var day by remember { mutableStateOf(if (isJalali) initialJalali.day else initialCal.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initialCal.get(Calendar.MINUTE)) }

    val monthNames = if (isJalali) JalaliCalendar.monthNames else gregorianMonthNamesFa
    val maxDay = remember(year, month, isJalali) {
        if (isJalali) {
            when {
                month <= 6 -> 31
                month <= 11 -> 30
                // اسفند: سال کبیسه‌ی شمسی گاهی ۳۰ روزه، ساده‌سازی محافظه‌کارانه: ۲۹ در نظر می‌گیریم
                else -> 29
            }
        } else {
            val tmp = Calendar.getInstance()
            tmp.clear()
            tmp.set(year, month - 1, 1)
            tmp.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }
    // اگه با عوض شدن ماه، روز فعلی از حداکثر روزهای اون ماه بیشتر شد، بکشش پایین
    LaunchedEffect(maxDay) {
        if (day > maxDay) day = maxDay
    }

    val resultMillis = remember(year, month, day, hour, minute, isJalali) {
        val gymd = if (isJalali) JalaliCalendar.toGregorian(year, month, day) else JalaliCalendar.YMD(year, month, day)
        Calendar.getInstance().apply {
            clear()
            set(gymd.year, gymd.month - 1, gymd.day, hour, minute, 0)
        }.timeInMillis
    }
    val isPast = resultMillis <= System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("زمان ارسال") },
        text = {
            Column {
                Stepper(label = "سال", displayText = year.toString(),
                    onDecrease = { year-- }, onIncrease = { year++ })
                Stepper(label = "ماه", displayText = monthNames[month - 1],
                    onDecrease = { month = if (month <= 1) 12 else month - 1 },
                    onIncrease = { month = if (month >= 12) 1 else month + 1 })
                Stepper(label = "روز", displayText = day.toString(),
                    onDecrease = { day = if (day <= 1) maxDay else day - 1 },
                    onIncrease = { day = if (day >= maxDay) 1 else day + 1 })
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Stepper(label = "ساعت", displayText = "%02d".format(hour),
                    onDecrease = { hour = if (hour <= 0) 23 else hour - 1 },
                    onIncrease = { hour = if (hour >= 23) 0 else hour + 1 })
                Stepper(label = "دقیقه", displayText = "%02d".format(minute),
                    onDecrease = { minute = if (minute <= 0) 59 else minute - 1 },
                    onIncrease = { minute = if (minute >= 59) 0 else minute + 1 })

                if (isPast) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "این زمان گذشته! یه زمان تو آینده انتخاب کن",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(resultMillis) }, enabled = !isPast) {
                Text("تائید")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
private fun Stepper(label: String, displayText: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(56.dp), color = Color.Gray)
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "کم کردن $label")
        }
        Text(
            text = displayText,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "زیاد کردن $label")
        }
    }
}
