package com.petro.smsapp.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * فرمت هوشمند تاریخ برای لیست مکالمات و زیر هر پیام.
 *
 * فعلاً میلادی با اسم ماه فارسی‌شده‌ست. وقتی برنامه دوزبانه و تقویم شمسی اضافه بشه،
 * فقط کافیه پیاده‌سازی این آبجکت عوض بشه - همه‌جای UI از همین یه نقطه صدا زده میشه.
 */
object DateFormatter {

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val gregorianMonthNamesFa = arrayOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن",
        "جولای", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )

    fun formatSmart(millis: Long): String {
        if (millis == 0L) return ""

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }

        return when {
            isSameDay(now, target) -> timeFormatter.format(Date(millis))
            isYesterday(now, target) -> "دیروز"
            else -> {
                val day = target.get(Calendar.DAY_OF_MONTH)
                val month = gregorianMonthNamesFa[target.get(Calendar.MONTH)]
                "$day $month"
            }
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, target)
    }
}
