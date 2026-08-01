package com.petro.smsapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.util.JalaliCalendar
import java.util.Calendar

private val gregorianMonthNamesFaCustomRange = arrayOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن",
    "جولای", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
)

/**
 * دیالوگِ انتخابِ بازه‌ی دلخواهِ زمانی (از تاریخ - تا تاریخ) برای ماژولِ «زمان» توی
 * آکاردئونِ فیلترِ درآور. برخلافِ DateTimePickerDialog (که مالِ زمان‌بندیِ ارسالِ پیامه
 * و ساعت/دقیقه هم داره)، این یکی فقط روز/ماه/سال می‌گیره - چون فیلترِ زمانی روی
 * «روزی که آخرین پیام اومده» معنی داره، نه ساعتِ دقیق. بازه‌ی نهایی خودکار از نیمه‌شبِ
 * «از» تا آخرِ شبِ «تا» محاسبه میشه تا کلِ اون دو روز رو هم پوشش بده.
 */
@Composable
fun CustomTimeRangeDialog(
    initialFromMillis: Long,
    initialToMillis: Long,
    onConfirm: (fromMillis: Long, toMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val isJalali = AppSettings.state.value.calendarType == CalendarType.JALALI
    val monthNames = if (isJalali) JalaliCalendar.monthNames else gregorianMonthNamesFaCustomRange

    var fromDate by remember { mutableStateOf(toDatePart(initialFromMillis, isJalali)) }
    var toDate by remember { mutableStateOf(toDatePart(initialToMillis, isJalali)) }

    fun maxDayFor(datePart: DatePart): Int {
        return if (isJalali) {
            when {
                datePart.month <= 6 -> 31
                datePart.month <= 11 -> 30
                // اسفند: سال کبیسه‌ی شمسی گاهی ۳۰ روزه، ساده‌سازی محافظه‌کارانه: ۲۹ در نظر می‌گیریم
                else -> 29
            }
        } else {
            val tmp = Calendar.getInstance()
            tmp.clear()
            tmp.set(datePart.year, datePart.month - 1, 1)
            tmp.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
    }

    val fromMillisResult = remember(fromDate, isJalali) { startOfDayMillis(fromDate, isJalali) }
    val toMillisResult = remember(toDate, isJalali) { endOfDayMillis(toDate, isJalali) }
    val isInvalidRange = fromMillisResult > toMillisResult

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازه‌ی دلخواهِ زمانی") },
        text = {
            Column {
                Text("از تاریخ", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                DatePartPicker(
                    datePart = fromDate,
                    monthNames = monthNames,
                    maxDay = maxDayFor(fromDate),
                    onChange = { fromDate = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text("تا تاریخ", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                DatePartPicker(
                    datePart = toDate,
                    monthNames = monthNames,
                    maxDay = maxDayFor(toDate),
                    onChange = { toDate = it }
                )
                if (isInvalidRange) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "تاریخِ «تا» نباید قبل از تاریخِ «از» باشه",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fromMillisResult, toMillisResult) },
                enabled = !isInvalidRange
            ) { Text("تائید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

private data class DatePart(val year: Int, val month: Int, val day: Int)

private fun toDatePart(millis: Long, isJalali: Boolean): DatePart {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return if (isJalali) {
        val j = JalaliCalendar.toJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        DatePart(j.year, j.month, j.day)
    } else {
        DatePart(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
}

private fun startOfDayMillis(datePart: DatePart, isJalali: Boolean): Long {
    val gymd = if (isJalali) JalaliCalendar.toGregorian(datePart.year, datePart.month, datePart.day) else JalaliCalendar.YMD(datePart.year, datePart.month, datePart.day)
    return Calendar.getInstance().apply {
        clear()
        set(gymd.year, gymd.month - 1, gymd.day, 0, 0, 0)
    }.timeInMillis
}

private fun endOfDayMillis(datePart: DatePart, isJalali: Boolean): Long {
    val gymd = if (isJalali) JalaliCalendar.toGregorian(datePart.year, datePart.month, datePart.day) else JalaliCalendar.YMD(datePart.year, datePart.month, datePart.day)
    return Calendar.getInstance().apply {
        clear()
        set(gymd.year, gymd.month - 1, gymd.day, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

@Composable
private fun DatePartPicker(
    datePart: DatePart,
    monthNames: Array<String>,
    maxDay: Int,
    onChange: (DatePart) -> Unit
) {
    LaunchedEffect(maxDay) {
        if (datePart.day > maxDay) onChange(datePart.copy(day = maxDay))
    }
    Column {
        RangeStepper(
            label = "سال",
            displayText = datePart.year.toString(),
            onDecrease = { onChange(datePart.copy(year = datePart.year - 1)) },
            onIncrease = { onChange(datePart.copy(year = datePart.year + 1)) }
        )
        RangeStepper(
            label = "ماه",
            displayText = monthNames[datePart.month - 1],
            onDecrease = { onChange(datePart.copy(month = if (datePart.month <= 1) 12 else datePart.month - 1)) },
            onIncrease = { onChange(datePart.copy(month = if (datePart.month >= 12) 1 else datePart.month + 1)) }
        )
        RangeStepper(
            label = "روز",
            displayText = datePart.day.toString(),
            onDecrease = { onChange(datePart.copy(day = if (datePart.day <= 1) maxDay else datePart.day - 1)) },
            onIncrease = { onChange(datePart.copy(day = if (datePart.day >= maxDay) 1 else datePart.day + 1)) }
        )
    }
}

@Composable
private fun RangeStepper(label: String, displayText: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(48.dp), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = onDecrease) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "کم کردن $label", modifier = Modifier.size(20.dp))
        }
        Text(
            text = displayText,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = onIncrease) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "زیاد کردن $label", modifier = Modifier.size(20.dp))
        }
    }
}
