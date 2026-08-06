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
 * تنظیمات ساده‌ی اپ - روی Preferences DataStore.
 *
 * تنظیماتِ مربوط به «بلاک» (نمایشِ نوتیف/نمایش‌درلیست/بلاکِ‌غیرمخاطبین) از اینجا حذف
 * شدن چون دیگه سراسری نیستن - هرکدوم الان تویِ خودِ FilterGroupEntity، مخصوصِ هر
 * گروهه (AppContainer.filterGroupRepository).
 */
object AppSettings {
    private const val KEY_TRASH_ENABLED_NAME = "trash_enabled"
    private const val KEY_CALENDAR_TYPE_NAME = "calendar_type"
    private const val KEY_CLOCK_FORMAT_NAME = "clock_format"
    private const val KEY_THEME_MODE_NAME = "theme_mode"
    private const val KEY_DELIVERY_NOTIFICATIONS_NAME = "delivery_notifications_enabled"
    private const val KEY_NOTIFICATION_ACTIONS_NAME = "notification_action_settings"
    private const val KEY_MAX_PINNED_CONVERSATIONS_NAME = "max_pinned_conversations"
    private const val KEY_GROUP_MESSAGING_ENABLED_NAME = "group_messaging_enabled"
    private const val KEY_SHOW_CONTACT_NUMBER_IN_LIST_NAME = "show_contact_number_in_list"
    private const val KEY_SWIPE_RIGHT_TO_LEFT_ACTION_NAME = "swipe_right_to_left_action"
    private const val KEY_SWIPE_LEFT_TO_RIGHT_ACTION_NAME = "swipe_left_to_right_action"
    private const val KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION_NAME = "swipe_delete_requires_confirmation"
    private const val KEY_ALPHABET_INDEX_BAR_ENABLED_NAME = "alphabet_index_bar_enabled"
    // جدید: نمایشِ پاپ‌آپِ روی صفحه به‌جای نوتیفِ معمولی برای پیامکِ تازه‌رسیده
    private const val KEY_POPUP_INSTEAD_OF_NOTIFICATION_NAME = "popup_instead_of_notification_enabled"
    // تأخیر ارسال پیام (ثانیه) - ۰ = فوری، ۱ تا ۱۵
    private const val KEY_SEND_DELAY_SECONDS_NAME = "send_delay_seconds"
    // جدید: وقتی کاربر نوتیفِ پیامکِ تازه‌رسیده رو با دست (سوایپ) بیرون بندازه، اون پیام خودکار خوانده‌شده علامت بخوره
    private const val KEY_MARK_READ_ON_NOTIFICATION_DISMISS_NAME = "mark_read_on_notification_dismiss_enabled"

    private val KEY_TRASH_ENABLED = booleanPreferencesKey(KEY_TRASH_ENABLED_NAME)
    private val KEY_CALENDAR_TYPE = stringPreferencesKey(KEY_CALENDAR_TYPE_NAME)
    private val KEY_CLOCK_FORMAT = stringPreferencesKey(KEY_CLOCK_FORMAT_NAME)
    private val KEY_THEME_MODE = stringPreferencesKey(KEY_THEME_MODE_NAME)
    private val KEY_DELIVERY_NOTIFICATIONS = booleanPreferencesKey(KEY_DELIVERY_NOTIFICATIONS_NAME)
    private val KEY_NOTIFICATION_ACTIONS = stringPreferencesKey(KEY_NOTIFICATION_ACTIONS_NAME)
    private val KEY_MAX_PINNED_CONVERSATIONS = intPreferencesKey(KEY_MAX_PINNED_CONVERSATIONS_NAME)
    private val KEY_GROUP_MESSAGING_ENABLED = booleanPreferencesKey(KEY_GROUP_MESSAGING_ENABLED_NAME)
    private val KEY_SHOW_CONTACT_NUMBER_IN_LIST = booleanPreferencesKey(KEY_SHOW_CONTACT_NUMBER_IN_LIST_NAME)
    private val KEY_SWIPE_RIGHT_TO_LEFT_ACTION = stringPreferencesKey(KEY_SWIPE_RIGHT_TO_LEFT_ACTION_NAME)
    private val KEY_SWIPE_LEFT_TO_RIGHT_ACTION = stringPreferencesKey(KEY_SWIPE_LEFT_TO_RIGHT_ACTION_NAME)
    private val KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION = booleanPreferencesKey(KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION_NAME)
    private val KEY_ALPHABET_INDEX_BAR_ENABLED = booleanPreferencesKey(KEY_ALPHABET_INDEX_BAR_ENABLED_NAME)
    private val KEY_POPUP_INSTEAD_OF_NOTIFICATION = booleanPreferencesKey(KEY_POPUP_INSTEAD_OF_NOTIFICATION_NAME)
    private val KEY_SEND_DELAY_SECONDS = intPreferencesKey(KEY_SEND_DELAY_SECONDS_NAME)
    private val KEY_MARK_READ_ON_NOTIFICATION_DISMISS = booleanPreferencesKey(KEY_MARK_READ_ON_NOTIFICATION_DISMISS_NAME)

    const val DEFAULT_MAX_PINNED_CONVERSATIONS = 3
    const val DEFAULT_SEND_DELAY_SECONDS = 0
    const val MIN_SEND_DELAY_SECONDS = 0
    const val MAX_SEND_DELAY_SECONDS = 15

    val DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION = SwipeAction.DELETE
    val DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION = SwipeAction.MARK_READ

    private fun defaultNotificationActionSettings(): List<NotificationActionSetting> = listOf(
        NotificationActionSetting(NotificationActionType.MARK_READ, true),
        NotificationActionSetting(NotificationActionType.DELETE, true),
        NotificationActionSetting(NotificationActionType.REPLY, false),
        NotificationActionSetting(NotificationActionType.BLOCK, false),
        NotificationActionSetting(NotificationActionType.QUICK_ADD_GROUP, false),
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
        val maxPinnedConversations: Int = DEFAULT_MAX_PINNED_CONVERSATIONS,
        val groupMessagingEnabled: Boolean = false,
        val showContactNumberInListEnabled: Boolean = false,
        val swipeRightToLeftAction: SwipeAction = DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION,
        val swipeLeftToRightAction: SwipeAction = DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION,
        val swipeDeleteRequiresConfirmation: Boolean = true,
        val alphabetIndexBarEnabled: Boolean = true,
        // جدید: به‌جای نوتیفِ معمولی، پیامکِ تازه‌رسیده با یه پاپ‌آپِ روی صفحه نشون داده بشه
        val popupInsteadOfNotificationEnabled: Boolean = false,
        // تأخیر ارسال (ثانیه) - ۰ = فوری
        val sendDelaySeconds: Int = DEFAULT_SEND_DELAY_SECONDS,
        // جدید: با سوایپ‌کردنِ (بیرون‌انداختنِ) نوتیفِ پیامک، همون پیام خوانده‌شده علامت بخوره
        val markReadOnNotificationDismissEnabled: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

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
                        maxPinnedConversations = prefs[KEY_MAX_PINNED_CONVERSATIONS] ?: DEFAULT_MAX_PINNED_CONVERSATIONS,
                        groupMessagingEnabled = prefs[KEY_GROUP_MESSAGING_ENABLED] ?: false,
                        showContactNumberInListEnabled = prefs[KEY_SHOW_CONTACT_NUMBER_IN_LIST] ?: false,
                        swipeRightToLeftAction = SwipeAction.fromId(prefs[KEY_SWIPE_RIGHT_TO_LEFT_ACTION], DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION),
                        swipeLeftToRightAction = SwipeAction.fromId(prefs[KEY_SWIPE_LEFT_TO_RIGHT_ACTION], DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION),
                        swipeDeleteRequiresConfirmation = prefs[KEY_SWIPE_DELETE_REQUIRES_CONFIRMATION] ?: true,
                        alphabetIndexBarEnabled = prefs[KEY_ALPHABET_INDEX_BAR_ENABLED] ?: true,
                        popupInsteadOfNotificationEnabled = prefs[KEY_POPUP_INSTEAD_OF_NOTIFICATION] ?: false,
                        sendDelaySeconds = (prefs[KEY_SEND_DELAY_SECONDS] ?: DEFAULT_SEND_DELAY_SECONDS)
                            .coerceIn(MIN_SEND_DELAY_SECONDS, MAX_SEND_DELAY_SECONDS),
                        markReadOnNotificationDismissEnabled = prefs[KEY_MARK_READ_ON_NOTIFICATION_DISMISS] ?: false
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

    /**
     * تغییرِ تم - چون SmsAppTheme مستقیماً از این state با collectAsState() می‌خونه،
     * همین‌جا فوری آپدیتش می‌کنیم تا رنگ‌ها بی‌درنگ عوض بشن؛ دیگه لازم نیست منتظرِ
     * رفت‌وبرگشتِ DataStore (که رویِ Dispatchers.IO و async هست) بمونیم. write() هم
     * برای persist شدنِ مقدار روی دیسک صدا زده می‌شه، ولی UI دیگه به اون گره نخورده.
     * توجه: بعدِ صدا زدنِ این تابع دیگه لازم نیست Activity.recreate() صدا زده بشه -
     * هیچ‌جای این پروژه attachBaseContext override نشده که به رفرش نیاز داشته باشه،
     * و recreate() فقط باعثِ ری‌لودِ کاملِ اکتیویتی (و ریسکِ کرش وسط کلیک) می‌شه.
     */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        write(context) { it[KEY_THEME_MODE] = mode.name }
    }

    fun isDeliveryNotificationsEnabled(context: Context): Boolean = _state.value.deliveryNotificationsEnabled
    fun setDeliveryNotificationsEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_DELIVERY_NOTIFICATIONS] = enabled }

    fun getNotificationActionSettings(context: Context): List<NotificationActionSetting> =
        _state.value.notificationActions

    fun setNotificationActionSettings(context: Context, settings: List<NotificationActionSetting>) {
        val raw = settings.joinToString(",") { "${it.type.id}:${if (it.enabled) "1" else "0"}" }
        write(context) { it[KEY_NOTIFICATION_ACTIONS] = raw }
    }

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

    fun isAlphabetIndexBarEnabled(context: Context): Boolean = _state.value.alphabetIndexBarEnabled
    fun setAlphabetIndexBarEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_ALPHABET_INDEX_BAR_ENABLED] = enabled }

    /** پیامکِ تازه‌رسیده به‌جای نوتیفِ معمولی، با پاپ‌آپِ روی صفحه (QuickReplyPopupActivity) نشون داده بشه */
    fun isPopupInsteadOfNotificationEnabled(context: Context): Boolean = _state.value.popupInsteadOfNotificationEnabled
    fun setPopupInsteadOfNotificationEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_POPUP_INSTEAD_OF_NOTIFICATION] = enabled }

    /** تأخیر ارسال پیام به ثانیه (۰ = فوری، ۱ تا ۱۵) */
    fun getSendDelaySeconds(context: Context): Int = _state.value.sendDelaySeconds
    fun setSendDelaySeconds(context: Context, seconds: Int) {
        val clamped = seconds.coerceIn(MIN_SEND_DELAY_SECONDS, MAX_SEND_DELAY_SECONDS)
        write(context) { it[KEY_SEND_DELAY_SECONDS] = clamped }
    }

    /** سوایپ‌کردنِ (بیرون‌انداختنِ دستیِ) نوتیفِ پیامکِ تازه‌رسیده، اون پیام رو خودکار خوانده‌شده کنه */
    fun isMarkReadOnNotificationDismissEnabled(context: Context): Boolean = _state.value.markReadOnNotificationDismissEnabled
    fun setMarkReadOnNotificationDismissEnabled(context: Context, enabled: Boolean) =
        write(context) { it[KEY_MARK_READ_ON_NOTIFICATION_DISMISS] = enabled }

    private fun write(context: Context, block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch {
            context.applicationContext.settingsDataStore.edit { prefs -> block(prefs) }
        }
    }
}