package com.petro.smsapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * جایگزین FavoriteStore (SharedPreferences) - هر ردیف یه پیامِ فیوریت‌شده.
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val messageId: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val date: Long
)

/** جایگزین PrivateStore (بخش شماره‌ها - رمز PIN جدا رفته توی DataStore) */
@Entity(tableName = "private_numbers")
data class PrivateNumberEntity(
    @PrimaryKey val normalizedAddress: String,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val madePrivateAt: Long
)

/** جایگزین TrashStore */
@Entity(tableName = "trashed_messages")
data class TrashEntity(
    @PrimaryKey val messageId: Long,
    val trashedAt: Long
)

/** جایگزین PinStore (پین کل مکالمه توی لیست اصلی) */
@Entity(tableName = "pinned_conversations")
data class PinEntity(
    @PrimaryKey val threadId: Long,
    val pinnedAt: Long
)

/** جایگزین PinnedMessageStore (پین یه پیام خاص داخل چت). */
@Entity(tableName = "pinned_messages")
data class PinnedMessageEntity(
    @PrimaryKey val messageId: Long,
    val threadId: Long = 0L
)

/** جایگزین ScheduledMessageStore */
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey val id: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val scheduledAt: Long,
    // -1 یعنی null (سیمِ پیش‌فرض سیستم) - هم‌خانواده‌ی همون قرارداد قبلی
    val subscriptionId: Int
)

/** جایگزین DeliveryStore */
@Entity(tableName = "delivery_times")
data class DeliveryEntity(
    @PrimaryKey val messageId: Long,
    val deliveredAtMillis: Long
)

/**
 * یه گروهِ پیامکیِ ذخیره‌شده - وقتی کاربر به چند مخاطب هم‌زمان پیام می‌فرسته (پیام
 * گروهی) و می‌خواد بعداً بدونِ انتخابِ دوباره‌ی تک‌تکِ مخاطبین، همون گروه رو پیدا کنه.
 * این «گروهِ پیامکی» کاملاً مستقل از «گروهِ فیلتر» (پایینِ همین فایل) هست - یکی برای
 * گیرنده‌های ارسال، یکی برای دسته‌بندی/فیلترِ پیام‌های دریافتی.
 */
@Entity(tableName = "message_groups")
data class MessageGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

/** عضوهای هر گروهِ پیامکی - groupId به MessageGroupEntity.id اشاره می‌کنه */
@Entity(tableName = "message_group_members")
data class MessageGroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val address: String,
    val displayName: String
)

// ============================================================================
// گروهِ فیلتر (Filter Group) - جایگزینِ عمومیِ کاملِ بخشِ قبلیِ «بلاک».
//
// قبلاً «بلاک» یه مقصدِ ثابتِ واحد بود (یه لیستِ شماره، یه لیستِ کلمه، یه لیستِ الگو).
// الان کاربر خودش N تا گروهِ دلخواه می‌سازه (مثلاً «تبلیغاتی»، «خرید»، «بانک») و هرکدوم
// مستقلاً شماره/کلمه/الگوی خودش رو داره، به‌علاوه‌ی پنج تا تنظیمِ مخصوصِ خودش:
//   - hideFromMainList: پیام‌های این گروه از لیستِ اصلیِ مکالمات مخفی بشن یا نه
//   - showNotifications: با اینکه پیام تویِ این گروه افتاده، بازم نوتیف/صدا بده یا نه
//   - blockNonContacts: هر فرستنده‌ای که تو مخاطبینِ گوشی نیست خودکار بره تو این گروه
//   - showInNotificationPicker: این گروه تویِ شیتِ انتخابِ گروهِ دکمه‌ی نوتیف نشون داده بشه یا نه
//   - isQuickAddTarget: این گروه، مقصدِ دکمه‌ی «افزودن سریع به گروه»ِ روی نوتیفه (فقط
//     همیشه حداکثر یه گروه می‌تونه این باشه - با ست‌شدنِ یکی، بقیه خودکار خاموش میشن)
//
// چون هر پیام فقط عضوِ *یه* گروه میشه (اولین گروهی که باهاش مچ میشه)، فیلدِ priority
// ترتیبِ چکِ گروه‌ها رو مشخص می‌کنه (عددِ کوچیک‌تر = زودتر چک میشه).
// ============================================================================

@Entity(tableName = "filter_groups")
data class FilterGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val priority: Int,
    val hideFromMainList: Boolean,
    val showNotifications: Boolean,
    val blockNonContacts: Boolean,
    val showInNotificationPicker: Boolean = true,
    // مقصدِ دکمه‌ی «افزودن سریع به گروه»ِ روی نوتیف - حداکثر یه گروه می‌تونه true باشه
    val isQuickAddTarget: Boolean = false,
    val createdAt: Long
)

/** شماره‌های عضوِ یه گروهِ فیلتر - کلیدِ اصلی ترکیبیه چون (نظری) یه شماره می‌تونه تو چند گروه اضافه شده باشه (فقط اولین گروهِ باالویت واقعاً اعمال میشه) */
@Entity(tableName = "filter_group_numbers", primaryKeys = ["groupId", "normalizedAddress"])
data class FilterGroupNumberEntity(
    val groupId: Long,
    val normalizedAddress: String,
    val address: String,
    val displayName: String,
    val addedAt: Long
)

/** کلمات کلیدیِ عضوِ یه گروهِ فیلتر */
@Entity(tableName = "filter_group_keywords")
data class FilterGroupKeywordEntity(
    @PrimaryKey val id: String,
    val groupId: Long,
    val text: String,
    val addedAt: Long
)

/** الگوهای شروع/پایانِ شماره‌یِ عضوِ یه گروهِ فیلتر */
@Entity(tableName = "filter_group_patterns")
data class FilterGroupPatternEntity(
    @PrimaryKey val id: String,
    val groupId: Long,
    val type: String, // STARTS_WITH / ENDS_WITH
    val value: String,
    val addedAt: Long
)

/**
 * ردیابیِ اینکه هر پیامِ دریافتی دقیقاً با کدوم گروه و از چه طریقی (شماره/کلمه/الگو/
 * خارج‌از‌مخاطبین) مچ شده - چون هر پیام فقط عضوِ یه گروهه، یه ردیفِ تکی کافیه (برخلافِ
 * قبل که سه جدولِ جدا برای سه نوعِ بلاک بود).
 */
@Entity(tableName = "filter_group_matched_messages")
data class FilterGroupMatchedMessageEntity(
    @PrimaryKey val messageId: Long,
    val groupId: Long,
    val matchType: String, // NUMBER / KEYWORD / PATTERN / NON_CONTACT
    val matchedValue: String?,
    val matchedAt: Long
)
