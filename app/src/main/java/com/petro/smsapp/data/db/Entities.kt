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

/**
 * جایگزین BlockStore - کلید اصلی همون normalizedAddress قبلیه (۹ رقم آخر شماره)،
 * دقیقاً هم‌خانواده‌ی منطق قبلی که مستقل از threadId عمل می‌کرد.
 */
@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val normalizedAddress: String,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val blockedAt: Long
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

/** جایگزین BlockKeywordStore */
@Entity(tableName = "block_keywords")
data class BlockKeywordEntity(
    @PrimaryKey val id: String,
    val text: String,
    val addedAt: Long
)

/** جایگزین BlockPatternStore - type به‌صورت رشته (اسم enum) ذخیره میشه */
@Entity(tableName = "block_patterns")
data class BlockPatternEntity(
    @PrimaryKey val id: String,
    val type: String,
    val value: String,
    val addedAt: Long
)

/** جایگزین BlockedKeywordMessageStore */
@Entity(tableName = "blocked_keyword_messages")
data class BlockedKeywordMessageEntity(
    @PrimaryKey val messageId: Long,
    val keyword: String,
    val blockedAt: Long
)

/** جایگزین BlockedPatternMessageStore */
@Entity(tableName = "blocked_pattern_messages")
data class BlockedPatternMessageEntity(
    @PrimaryKey val messageId: Long,
    val type: String,
    val value: String,
    val blockedAt: Long
)

/** جایگزین BlockedNonContactMessageStore */
@Entity(tableName = "blocked_non_contact_messages")
data class BlockedNonContactMessageEntity(
    @PrimaryKey val messageId: Long,
    val address: String,
    val blockedAt: Long
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

/**
 * جایگزین PinnedMessageStore (پین یه پیام خاص داخل چت).
 *
 * فیلدِ threadId بعداً اضافه شد (نسخه‌ی ۳ دیتابیس - MIGRATION_2_3 توی AppDatabase) تا
 * بشه فهمید هر پیامِ پین‌شده مالِ کدوم مکالمه‌ست، بدونِ نیاز به کوئری اضافه به Telephony
 * Provider - برای فیلترِ «دارای پیام سنجاق‌شده» توی آکاردئونِ درآور لازم شد.
 */
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
 * اعضای هر گروه توی MessageGroupMemberEntity جداگانه نگه داشته میشن (یک‌به‌چند
 * ساده، بدون @Relation، چون تعداد اعضا معمولاً کمه و کوئری‌شون سبک و مستقله).
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