package com.petro.smsapp.data

import com.petro.smsapp.util.TimeRangeHelper

/** بازه‌های آماده‌ی ماژولِ «زمان» توی آکاردئونِ فیلترِ درآور */
enum class TimeRangePreset(val id: String, val label: String) {
    TODAY("today", "امروز"),
    YESTERDAY("yesterday", "دیروز"),
    THIS_WEEK("this_week", "این هفته"),
    THIS_MONTH("this_month", "این ماه");

    companion object {
        fun fromId(id: String?): TimeRangePreset? = entries.find { it.id == id }
    }
}

/** بازه‌ی دلخواهِ کاربر (میلی‌ثانیه، هردو سرِ بازه شاملن) */
data class CustomTimeRange(val fromMillis: Long, val toMillis: Long)

/**
 * انتخابِ فعلیِ ماژولِ «زمان» - یا هیچی انتخاب نشده (None)، یا یکی از بازه‌های آماده
 * (Preset)، یا یه بازه‌ی دلخواهِ کاربر (Custom). همیشه تک‌انتخابیه (نه چندتایی مثلِ
 * فیلترهای وضعیتِ پیام) چون همپوشانیِ چندتا بازه‌ی زمانیِ هم‌زمان معنی نداره.
 */
sealed class TimeFilterSelection {
    object None : TimeFilterSelection()
    data class Preset(val preset: TimeRangePreset) : TimeFilterSelection()
    data class Custom(val range: CustomTimeRange) : TimeFilterSelection()
}

/**
 * فقط مکالماتی که تاریخِ آخرین پیامشون توی بازه‌ی انتخابی باشه رو نگه می‌داره -
 * اگه هیچ بازه‌ای انتخاب نشده باشه (None)، لیست بدونِ تغییر برمی‌گرده.
 */
fun List<Conversation>.applyTimeFilter(selection: TimeFilterSelection): List<Conversation> {
    val range: LongRange = when (selection) {
        is TimeFilterSelection.None -> return this
        is TimeFilterSelection.Preset -> TimeRangeHelper.rangeFor(selection.preset)
        is TimeFilterSelection.Custom -> selection.range.fromMillis..selection.range.toMillis
    }
    return filter { it.date in range }
}
