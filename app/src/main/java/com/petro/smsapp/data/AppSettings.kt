package com.petro.smsapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** نوع تقویمی که برای نمایش تاریخ توی کل برنامه استفاده میشه */
enum class CalendarType { GREGORIAN, JALALI }

/** فرمت نمایش ساعت توی کل برنامه */
enum class ClockFormat { H24, H12 }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * تنظیمات ساده‌ی اپ - روی Preferences DataStore، دقیقاً همونی که خودِ گوگل برای
 * دیتای flat و key-value (نه رابطه‌ای) پیشنهاد می‌ده.
 *
 * API عمومیِ این object عیناً با نسخه‌ی قبلی (SharedPreferences-based) یکیه - چون
 * DateFormatter و چندین Composable مستقیم و synchronous از AppSettings.state.value
 * می‌خونن، تغییر این امضا یعنی دست زدن به کلی فایل دیگه که ربطی به مهاجرت storage
 * نداره. به‌جاش همون الگوی قبلی رو نگه داشتیم: یه StateFlow داخل حافظه که با
 * DataStore هم‌گام (in-sync) نگه داشته میشه؛ ولی برخلاف قبل، خودِ این StateFlow
 * دیگه «کش دستی» نیست - مستقیم و پیوسته از Flow خودِ DataStore پر میشه (init یه
 * collector راه می‌ندازه)، یعنی اگه از هر مسیر دیگه‌ای هم DataStore تغییر کنه
 * (مثلاً یه پروسس دیگه)، این State هم خودکار به‌روز میشه.
 */
object AppSettings {
    private const val KEY_TRASH_ENABLED_NAME = "trash_enabled"
    private const val KEY_CALENDAR_TYPE_NAME = "calendar_type"
    private const val KEY_CLOCK_FORMAT_NAME = "clock_format"
    private const val KEY_THEME_MODE_NAME = "theme_mode"
    private const val KEY_DELIVERY_NOTIFICATIONS_NAME = "delivery_notifications_enabled"
    private const val KEY_NOTIFICATION_ACTIONS_NAME = "notification_action_settings"
    private const val KEY_SHOW_BLOCKED_NOTIFICATIONS_NAME = "show_blocked_notifications_enabled"
    private const val KEY_SHOW_BLOCKED_IN_MESSAGE_LIST_NAME = "show_blocked_in_message_list_enabled"
    private const val KEY_BLOCK_NON_CONTACTS_NAME = "block_non_contacts_enabled"
    private const val KEY_MAX_PINNED_CONVERSATIONS_NAME = "max_pinned_conversations"
    private const val KEY_GROUP_MESSAGING_ENABLED_NAME = "group_messaging_enabled"
    // نمایشِ شماره‌ی مخاطبینِ ذخیره‌شده زیرِ اسمشون توی لیستِ اصلیِ مکالمات (با فونتِ کوچیک‌تر)
    private const val KEY_SHOW_CONTACT_NUMBER_IN_LIST_NAME = "show_contact_number_in_list"
    // جهتِ سویپ روی هر ردیفِ لیستِ مکالمات - هرکدوم یه SwipeAction.id ذخیره می‌کنه
    private const val KEY_SWIPE_RIGHT_TO_LEFT_ACTION_NAME = "swipe_right_to_left_action"
    private const val KEY_SWIPE_LEFT_TO_RIGHT_ACTION_NAME = "swipe_left_to_right_action"
    // قبل از اجرای واقعیِ عملیاتِ «حذف» با سویپ، از کاربر تأیید گرفته بشه یا نه
    private const val KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION_NAME = "swipe_delete_requires_confirmation"

    private val KEY_TRASH_ENABLED = booleanPreferencesKey(KEY_TRASH_ENABLED_NAME)
    private val KEY_CALENDAR_TYPE = stringPreferencesKey(KEY_CALENDAR_TYPE_NAME)
    private val KEY_CLOCK_FORMAT = stringPreferencesKey(KEY_CLOCK_FORMAT_NAME)
    private val KEY_THEME_MODE = stringPreferencesKey(KEY_THEME_MODE_NAME)
    private val KEY_DELIVERY_NOTIFICATIONS = booleanPreferencesKey(KEY_DELIVERY_NOTIFICATIONS_NAME)
    private val KEY_NOTIFICATION_ACTIONS = stringPreferencesKey(KEY_NOTIFICATION_ACTIONS_NAME)
    private val KEY_SHOW_BLOCKED_NOTIFICATIONS = booleanPreferencesKey(KEY_SHOW_BLOCKED_NOTIFICATIONS_NAME)
    private val KEY_SHOW_BLOCKED_IN_MESSAGE_LIST = booleanPreferencesKey(KEY_SHOW_BLOCKED_IN_MESSAGE_LIST_NAME)
    private val KEY_BLOCK_NON_CONTACTS = booleanPreferencesKey(KEY_BLOCK_NON_CONTACTS_NAME)
    private val KEY_MAX_PINNED_CONVERSATIONS = intPreferencesKey(KEY_MAX_PINNED_CONVERSATIONS_NAME)
    private val KEY_GROUP_MESSAGING_ENABLED = booleanPreferencesKey(KEY_GROUP_MESSAGING_ENABLED_NAME)
    private val KEY_SHOW_CONTACT_NUMBER_IN_LIST = booleanPreferencesKey(KEY_SHOW_CONTACT_NUMBER_IN_LIST_NAME)
    private val KEY_SWIPE_RIGHT_TO_LEFT_ACTION = stringPreferencesKey(KEY_SWIPE_RIGHT_TO_LEFT_ACTION_NAME)
    private val KEY_SWIPE_LEFT_TO_RIGHT_ACTION = stringPreferencesKey(KEY_SWIPE_LEFT_TO_RIGHT_ACTION_NAME)
    private val KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION = booleanPreferencesKey(KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION_NAME)

    /** پیش‌فرض حداکثر تعداد مکالمه‌ی قابل‌پین در لیست اصلی - کاربر می‌تونه از تنظیمات عوضش کنه */
    const val DEFAULT_MAX_PINNED_CONVERSATIONS = 3

    /** پیش‌فرض‌های عملیاتِ سویپ - کاربر می‌تونه هرکدوم رو از تنظیمات جدا عوض کنه */
    val DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION = SwipeAction.DELETE
    val DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION = SwipeAction.MARK_READ

    private fun defaultNotificationActionSettings(): List<NotificationActionSetting> = listOf(
        NotificationActionSetting(NotificationActionType.MARK_READ, true),
        NotificationActionSetting(NotificationActionType.DELETE, true),
        NotificationActionSetting(NotificationActionType.REPLY, false),
        NotificationActionSetting(NotificationActionType.BLOCK, false),
        NotificationActionSetting(NotificationActionType.CALL, false)
    )

    private fun parseNotificationActionSettings(raw: String?): List<NotificationActionSetting> {
        if (raw == null) return defaultNotificationActionSettings()
        val saved = raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val type = NotificationActionType.fromId(parts[0]) ?: return@mapNotNull null
            NotificationActionSetting(type, parts[1] == "1")
        }
        val missing = NotificationActionType.entries.filter { type -> saved.none { it.type == type } }
            .map { NotificationActionSetting(it, false) }
        return saved + missing
    }

    data class State(
        val trashEnabled: Boolean = false,
        val calendarType: CalendarType = CalendarType.GREGORIAN,
        val clockFormat: ClockFormat = ClockFormat.H24,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val deliveryNotificationsEnabled: Boolean = false,
        val notificationActions: List<NotificationActionSetting> = defaultNotificationActionSettings(),
        val showBlockedNotificationsEnabled: Boolean = false,
        val showBlockedInMessageListEnabled: Boolean = false,
        val blockNonContactsEnabled: Boolean = false,
        val maxPinnedConversations: Int = DEFAULT_MAX_PINNED_CONVERSATIONS,
        // اگه فعال باشه، توی «پیام جدید» امکان ذخیره‌ی چند مخاطبِ انتخاب‌شده به‌عنوان یه
        // «گروه» و بارگذاری دوباره‌شون در آینده فراهم میشه (بدونِ انتخابِ دوباره‌ی تک‌تکشون)
        val groupMessagingEnabled: Boolean = false,
        // اگه فعال باشه، زیرِ اسمِ مخاطبینِ ذخیره‌شده (تو لیستِ اصلیِ مکالمات) شماره‌شون هم
        // با فونتِ کوچیک‌تر نشون داده میشه
        val showContactNumberInListEnabled: Boolean = false,
        // عملیاتی که با کشیدنِ هر ردیفِ لیستِ مکالمات از راست به چپ اجرا میشه
        val swipeRightToLeftAction: SwipeAction = DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION,
        // عملیاتی که با کشیدنِ هر ردیفِ لیستِ مکالمات از چپ به راست اجرا میشه
        val swipeLeftToRightAction: SwipeAction = DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION,
        // قبل از اجرای واقعیِ حذف (وقتی یکی از دو جهتِ بالا روی «حذف» تنظیم شده باشه) دیالوگ تأیید نشون داده بشه
        val swipeDeleteRequiresConfirmation: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // اسکوپِ سطحِ اپلیکیشن - این object در طولِ عمرِ کل پروسس زنده‌ست، پس نیازی به
    // cancel کردن نداره (دقیقاً هم‌خانواده‌ی چیزی که SmsApplication خودش هم می‌بود)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    /**
     * باید یه بار توی SmsApplication.onCreate صدا زده بشه. برخلاف قبل (که یه خوندنِ
     * synchronous از SharedPreferences بود)، اینجا یه collector روی Flow خودِ
     * DataStore راه می‌افته که برای همیشه _state رو هم‌گام نگه می‌داره.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        scope.launch {
            context.settingsDataStore.data
                .map { prefs ->
                    State(
                        trashEnabled = prefs[KEY_TRASH_ENABLED] ?: false,
                        calendarType = if (prefs[KEY_CALENDAR_TYPE] == CalendarType.JALALI.name) CalendarType.JALALI else CalendarType.GREGORIAN,
                        clockFormat = if (prefs[KEY_CLOCK_FORMAT] == ClockFormat.H12.name) ClockFormat.H12 else ClockFormat.H24,
                        themeMode = when (prefs[KEY_THEME_MODE]) {
                            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                            ThemeMode.DARK.name -> ThemeMode.DARK
                            else -> ThemeMode.SYSTEM
                        },
                        deliveryNotificationsEnabled = prefs[KEY_DELIVERY_NOTIFICATIONS] ?: false,
                        notificationActions = parseNotificationActionSettings(prefs[KEY_NOTIFICATION_ACTIONS]),
                        showBlockedNotificationsEnabled = prefs[KEY_SHOW_BLOCKED_NOTIFICATIONS] ?: false,
                        showBlockedInMessageListEnabled = prefs[KEY_SHOW_BLOCKED_IN_MESSAGE_LIST] ?: false,
                        blockNonContactsEnabled = prefs[KEY_BLOCK_NON_CONTACTS] ?: false,
                        maxPinnedConversations = prefs[KEY_MAX_PINNED_CONVERSATIONS] ?: DEFAULT_MAX_PINNED_CONVERSATIONS,
                        groupMessagingEnabled = prefs[KEY_GROUP_MESSAGING_ENABLED] ?: false,
                        showContactNumberInListEnabled = prefs[KEY_SHOW_CONTACT_NUMBER_IN_LIST] ?: false,
                        swipeRightToLeftAction = SwipeAction.fromId(prefs[KEY_SWIPE_RIGHT_TO_LEFT_ACTION], DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION),
                        swipeLeftToRightAction = SwipeAction.fromId(prefs[KEY_SWIPE_LEFT_TO_RIGHT_ACTION], DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION),
                        swipeDeleteRequiresConfirmation = prefs[KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION] ?: true
                    )
                }
                .collect { newState -> _state.value = newState }
        }
    }

    fun isTrashEnabled(context: Context): Boolean = _state.value.trashEnabled

    fun setTrashEnabled(context: Context, enabled: Boolean) = write(context) { it[KEY_TRASH_ENABLED] = enabled }

    fun getCalendarType(context: Context): CalendarType = _state.value.calendarType

    fun setCalendarType(context: Context, type: CalendarType) = write(context) { it[KEY_CALENDAR_TYPE] = type.name }

    fun getClockFormat(context: Context): ClockFormat = _state.value.clockFormat

    fun setClockFormat(context: Context, format: ClockFormat) = write(context) { it[KEY_CLOCK_FORMAT] = format.name }

    fun getThemeMode(context: Context): ThemeMode = _state.value.themeMode

    fun setThemeMode(context: Context, mode: ThemeMode) = write(context) { it[KEY_THEME_MODE] = mode.name }

    fun isDeliveryNotificationsEnabled(context: Context): Boolean = _state.value.deliveryNotificationsEnabled

    fun setDeliveryNotificationsEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_DELIVERY_NOTIFICATIONS] = enabled }

    fun getNotificationActionSettings(context: Context): List<NotificationActionSetting> =
        _state.value.notificationActions

    fun setNotificationActionSettings(context: Context, settings: List<NotificationActionSetting>) {
        val raw = settings.joinToString(",") { "${it.type.id}:${if (it.enabled) "1" else "0"}" }
        write(context) { it[KEY_NOTIFICATION_ACTIONS] = raw }
    }

    fun isShowBlockedNotificationsEnabled(context: Context): Boolean = _state.value.showBlockedNotificationsEnabled

    fun setShowBlockedNotificationsEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_SHOW_BLOCKED_NOTIFICATIONS] = enabled }

    fun isShowBlockedInMessageListEnabled(context: Context): Boolean = _state.value.showBlockedInMessageListEnabled

    fun setShowBlockedInMessageListEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_SHOW_BLOCKED_IN_MESSAGE_LIST] = enabled }

    fun isBlockNonContactsEnabled(context: Context): Boolean = _state.value.blockNonContactsEnabled

    fun setBlockNonContactsEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_BLOCK_NON_CONTACTS] = enabled }

    fun getMaxPinnedConversations(context: Context): Int = _state.value.maxPinnedConversations

    fun setMaxPinnedConversations(context: Context, count: Int) {
        val clamped = count.coerceIn(1, 20)
        write(context) { it[KEY_MAX_PINNED_CONVERSATIONS] = clamped }
    }

    fun isGroupMessagingEnabled(context: Context): Boolean = _state.value.groupMessagingEnabled

    fun setGroupMessagingEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_GROUP_MESSAGING_ENABLED] = enabled }

    fun isShowContactNumberInListEnabled(context: Context): Boolean = _state.value.showContactNumberInListEnabled

    fun setShowContactNumberInListEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_SHOW_CONTACT_NUMBER_IN_LIST] = enabled }

    fun getSwipeRightToLeftAction(context: Context): SwipeAction = _state.value.swipeRightToLeftAction

    fun setSwipeRightToLeftAction(context: Context, action: SwipeAction) =
        write(context) { it[KEY_SWIPE_RIGHT_TO_LEFT_ACTION] = action.id }

    fun getSwipeLeftToRightAction(context: Context): SwipeAction = _state.value.swipeLeftToRightAction

    fun setSwipeLeftToRightAction(context: Context, action: SwipeAction) =
        write(context) { it[KEY_SWIPE_LEFT_TO_RIGHT_ACTION] = action.id }

    fun isSwipeDeleteRequiresConfirmation(context: Context): Boolean = _state.value.swipeDeleteRequiresConfirmation

    fun setSwipeDeleteRequiresConfirmation(context: Context, enabled: Boolean) =
        write(context) { it[KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION] = enabled }

    private fun write(context: Context, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch {
            context.applicationContext.settingsDataStore.edit { prefs -> block(prefs) }
        }
    }
}