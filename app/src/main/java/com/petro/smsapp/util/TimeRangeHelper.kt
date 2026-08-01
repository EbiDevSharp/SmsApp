package com.petro.smsapp.util

import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.data.TimeRangePreset
import java.util.Calendar

/**
 * محاسبه‌ی بازه‌ی میلی‌ثانیه‌ایِ هر بازه‌ی آماده‌ی زمانیِ ماژولِ «زمان» (توی آکاردئونِ
 * فیلترِ درآور). کاملاً درحافظه‌ست، هیچ کوئری‌ای نمی‌زنه.
 *
 * - «امروز» و «دیروز» بر اساسِ مرزِ نیمه‌شبِ محلیِ گوشی محاسبه میشن.
 * - «این هفته» یه پنجره‌ی غلتانِ ۷ روزه‌ست (نه لزوماً از شنبه/دوشنبه‌ی تقویم) تا
 *   وابسته به قراردادِ اول‌هفته‌ی فرهنگی نباشه.
 * - «این ماه» بر اساسِ اولِ ماهِ تقویمی‌ای که کاربر تو تنظیمات انتخاب کرده (شمسی یا
 *   میلادی - AppSettings.state.calendarType) حساب میشه، دقیقاً هم‌قاعده‌ی DateFormatter.
 */
object TimeRangeHelper {

    fun rangeFor(preset: TimeRangePreset): LongRange {
        val now = System.currentTimeMillis()
        return when (preset) {
            TimeRangePreset.TODAY -> startOfToday()..now
            TimeRangePreset.YESTERDAY -> startOfDaysAgo(1) until startOfToday()
            TimeRangePreset.THIS_WEEK -> startOfDaysAgo(6)..now
            TimeRangePreset.THIS_MONTH -> startOfCurrentCalendarMonth()..now
        }
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfDaysAgo(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfCurrentCalendarMonth(): Long {
        val now = Calendar.getInstance()
        return if (AppSettings.state.value.calendarType == CalendarType.JALALI) {
            val j = JalaliCalendar.toJalali(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)
            )
            val firstOfMonth = JalaliCalendar.toGregorian(j.year, j.month, 1)
            Calendar.getInstance().apply {
                clear()
                set(firstOfMonth.year, firstOfMonth.month - 1, firstOfMonth.day, 0, 0, 0)
            }.timeInMillis
        } else {
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}
