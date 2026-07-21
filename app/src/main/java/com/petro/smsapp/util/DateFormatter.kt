package com.petro.smsapp.util

import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.data.ClockFormat
import java.util.Calendar

/**
 * فرمت هوشمند تاریخ برای لیست مکالمات و زیر هر پیام.
 *
 * بر اساس تنظیمات کاربر (صفحه‌ی تنظیمات -> AppSettings) خروجی می‌تونه میلادی یا شمسی
 * باشه و ساعت هم می‌تونه ۱۲ یا ۲۴ ساعته نشون داده بشه. چون AppSettings.state همیشه
 * (بعد از AppSettings.init توی SmsApplication.onCreate) مقدار به‌روز داره، اینجا نیازی
 * به گرفتن Context نیست و همه‌جای UI فقط با صدا زدن همین توابع، خودکار هماهنگ میشن.
 */
object DateFormatter {

    private val gregorianMonthNamesFa = arrayOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن",
        "جولای", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )

    fun formatSmart(millis: Long): String {
        if (millis == 0L) return ""

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }

        return when {
            isSameDay(now, target) -> formatTime(millis)
            isYesterday(now, target) -> "دیروز"
            else -> {
                val (day, monthName) = dayAndMonthName(target)
                "$day $monthName"
            }
        }
    }

    /** برای صفحه جزئیات پیام و صفحه علاقه‌مندی‌ها - تاریخ کامل به‌همراه ساعت */
    fun formatFull(millis: Long): String {
        if (millis <= 0L) return "-"
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val (day, monthName, year) = dayMonthNameYear(cal)
        return "$day $monthName $year - ${formatTime(millis)}"
    }

    /** ساعت به‌تنهایی، با توجه به تنظیم ۱۲/۲۴ ساعته */
    fun formatTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        return if (AppSettings.state.value.clockFormat == ClockFormat.H12) {
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            val suffix = if (hour24 < 12) "ق.ظ" else "ب.ظ"
            "%d:%02d %s".format(hour12, minute, suffix)
        } else {
            "%02d:%02d".format(hour24, minute)
        }
    }

    private fun dayAndMonthName(cal: Calendar): Pair<Int, String> {
        return if (AppSettings.state.value.calendarType == CalendarType.JALALI) {
            val j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            j.day to JalaliCalendar.monthNames[j.month - 1]
        } else {
            cal.get(Calendar.DAY_OF_MONTH) to gregorianMonthNamesFa[cal.get(Calendar.MONTH)]
        }
    }

    private fun dayMonthNameYear(cal: Calendar): Triple<Int, String, Int> {
        return if (AppSettings.state.value.calendarType == CalendarType.JALALI) {
            val j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            Triple(j.day, JalaliCalendar.monthNames[j.month - 1], j.year)
        } else {
            Triple(
                cal.get(Calendar.DAY_OF_MONTH),
                gregorianMonthNamesFa[cal.get(Calendar.MONTH)],
                cal.get(Calendar.YEAR)
            )
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, target)
    }
}
