package com.petro.smsapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** نوع تقویمی که برای نمایش تاریخ توی کل برنامه استفاده میشه */
enum class CalendarType { GREGORIAN, JALALI }

/** فرمت نمایش ساعت توی کل برنامه */
enum class ClockFormat { H24, H12 }

/**
 * تنظیمات ساده‌ی اپ که نیاز به دیتابیس ندارن، با SharedPreferences ذخیره میشن.
 *
 * علاوه بر پرچم «سطل زباله»، الان تنظیمات نوع تقویم (میلادی/شمسی) و فرمت ساعت (۱۲/۲۴)
 * هم اینجا نگه‌داری میشن. چون AppSettings یه object سراسریه و جاهای زیادی از کد (مثل
 * DateFormatter) بدون داشتن Context بهش نیاز دارن، مقادیر علاوه بر SharedPreferences
 * توی یه StateFlow داخل حافظه هم نگه داشته میشن؛ کافیه AppSettings.init(context) یه بار
 * توی SmsApplication.onCreate صدا زده بشه تا این StateFlow از روی مقادیر ذخیره‌شده پر بشه.
 * هر setter هم SharedPreferences و هم StateFlow رو با هم آپدیت می‌کنه، پس UI (با collectAsState)
 * فوراً از تغییرات باخبر میشه.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_TRASH_ENABLED = "trash_enabled"
    private const val KEY_CALENDAR_TYPE = "calendar_type"
    private const val KEY_CLOCK_FORMAT = "clock_format"

    data class State(
        val trashEnabled: Boolean = false,
        val calendarType: CalendarType = CalendarType.GREGORIAN,
        val clockFormat: ClockFormat = ClockFormat.H24
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** باید یه بار توی SmsApplication.onCreate صدا زده بشه تا مقادیر ذخیره‌شده لود بشن */
    fun init(context: Context) {
        val p = prefs(context)
        _state.value = State(
            trashEnabled = p.getBoolean(KEY_TRASH_ENABLED, false),
            calendarType = if (p.getString(KEY_CALENDAR_TYPE, null) == CalendarType.JALALI.name) {
                CalendarType.JALALI
            } else {
                CalendarType.GREGORIAN
            },
            clockFormat = if (p.getString(KEY_CLOCK_FORMAT, null) == ClockFormat.H12.name) {
                ClockFormat.H12
            } else {
                ClockFormat.H24
            }
        )
    }

    fun isTrashEnabled(context: Context): Boolean = _state.value.trashEnabled

    fun setTrashEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRASH_ENABLED, enabled).apply()
        _state.value = _state.value.copy(trashEnabled = enabled)
    }

    fun getCalendarType(context: Context): CalendarType = _state.value.calendarType

    fun setCalendarType(context: Context, type: CalendarType) {
        prefs(context).edit().putString(KEY_CALENDAR_TYPE, type.name).apply()
        _state.value = _state.value.copy(calendarType = type)
    }

    fun getClockFormat(context: Context): ClockFormat = _state.value.clockFormat

    fun setClockFormat(context: Context, format: ClockFormat) {
        prefs(context).edit().putString(KEY_CLOCK_FORMAT, format.name).apply()
        _state.value = _state.value.copy(clockFormat = format)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
