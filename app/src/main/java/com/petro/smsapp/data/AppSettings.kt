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
    private const val KEY_DELIVERY_NOTIFICATIONS = "delivery_notifications_enabled"
    private const val KEY_NOTIFICATION_ACTIONS = "notification_action_settings"

    /**
     * پیش‌فرض: فقط «خوانده شد» و «حذف» فعالن (همونایی که از قبل بودن)، بقیه (پاسخ سریع/
     * بلاک/تماس) غیرفعالن چون تازه اضافه شدن و کاربر باید خودش از تنظیمات فعالشون کنه.
     */
    private fun defaultNotificationActionSettings(): List<NotificationActionSetting> = listOf(
        NotificationActionSetting(NotificationActionType.MARK_READ, true),
        NotificationActionSetting(NotificationActionType.DELETE, true),
        NotificationActionSetting(NotificationActionType.REPLY, false),
        NotificationActionSetting(NotificationActionType.BLOCK, false),
        NotificationActionSetting(NotificationActionType.CALL, false)
    )

    private fun loadNotificationActionSettings(p: android.content.SharedPreferences): List<NotificationActionSetting> {
        val raw = p.getString(KEY_NOTIFICATION_ACTIONS, null) ?: return defaultNotificationActionSettings()
        val saved = raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val type = NotificationActionType.fromId(parts[0]) ?: return@mapNotNull null
            NotificationActionSetting(type, parts[1] == "1")
        }
        // اگه بعداً یه نوع اکشن جدید به enum اضافه شد که هنوز تو تنظیمات ذخیره‌شده‌ی کاربر
        // نیست، انتهای لیست اضافه‌ش کن (غیرفعال، تا کاربر خودش تصمیم بگیره فعالش کنه)
        val missing = NotificationActionType.entries.filter { type -> saved.none { it.type == type } }
            .map { NotificationActionSetting(it, false) }
        return saved + missing
    }

    data class State(
        val trashEnabled: Boolean = false,
        val calendarType: CalendarType = CalendarType.GREGORIAN,
        val clockFormat: ClockFormat = ClockFormat.H24,
        // پیش‌فرض خاموش: اگه کاربر ۲۰ تا پیام بفرسته و همه دلیور بشن، ۲۰ تا نوتیف جدا
        // اسپم حساب میشه. تیک دلیوری زیر خود پیام (توی چت) کافیه؛ نوتیف اختیاریه.
        val deliveryNotificationsEnabled: Boolean = false,
        // ترتیب و روشن/خاموش بودن دکمه‌های نوتیف پیامک - قابل تنظیم از صفحه‌ی تنظیمات
        val notificationActions: List<NotificationActionSetting> = defaultNotificationActionSettings()
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
            },
            deliveryNotificationsEnabled = p.getBoolean(KEY_DELIVERY_NOTIFICATIONS, false),
            notificationActions = loadNotificationActionSettings(p)
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

    fun isDeliveryNotificationsEnabled(context: Context): Boolean = _state.value.deliveryNotificationsEnabled

    fun setDeliveryNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DELIVERY_NOTIFICATIONS, enabled).apply()
        _state.value = _state.value.copy(deliveryNotificationsEnabled = enabled)
    }

    fun getNotificationActionSettings(context: Context): List<NotificationActionSetting> =
        _state.value.notificationActions

    fun setNotificationActionSettings(context: Context, settings: List<NotificationActionSetting>) {
        val raw = settings.joinToString(",") { "${it.type.id}:${if (it.enabled) "1" else "0"}" }
        prefs(context).edit().putString(KEY_NOTIFICATION_ACTIONS, raw).apply()
        _state.value = _state.value.copy(notificationActions = settings)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
