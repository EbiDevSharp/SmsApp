package com.petro.smsapp.data

data class PrivateNumber(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val madePrivateAt: Long
)

data class FavoriteMessage(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val date: Long
)

data class ScheduledMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val scheduledAt: Long,
    val subscriptionId: Int?
)

/** یه عضوِ داخلِ یه گروهِ پیامکیِ ذخیره‌شده (گیرنده‌های ارسال - نه ربطی به گروهِ فیلتر) */
data class MessageGroupMember(
    val address: String,
    val displayName: String
)

data class MessageGroupSummary(
    val id: Long,
    val name: String,
    val memberCount: Int,
    val createdAt: Long
)

// ============================================================================
// گروهِ فیلتر - جایگزینِ عمومیِ بخشِ قدیمیِ «بلاک»
// ============================================================================

enum class PatternType { STARTS_WITH, ENDS_WITH }

/** به چه طریقی یه پیام با یه گروه مچ شده */
enum class FilterMatchType { NUMBER, KEYWORD, PATTERN, NON_CONTACT }

data class FilterGroup(
    val id: Long,
    val name: String,
    val priority: Int,
    /** پیام‌های این گروه از لیستِ اصلیِ مکالمات مخفی بشن */
    val hideFromMainList: Boolean,
    /** با اینکه پیام تویِ این گروه افتاده، بازم نوتیف/صدا بده */
    val showNotifications: Boolean,
    /** هر فرستنده‌ای که تو مخاطبینِ گوشی نیست خودکار بره تو این گروه */
    val blockNonContacts: Boolean,
    val createdAt: Long
)

/** خلاصه‌ی یه گروهِ فیلتر به‌همراه شمارنده‌های هر بخش - برای صفحه‌ی هابِ «گروه‌ها» */
data class FilterGroupSummary(
    val group: FilterGroup,
    val numberCount: Int,
    val keywordCount: Int,
    val patternCount: Int,
    val messageCount: Int
)

data class FilterGroupNumber(
    val groupId: Long,
    val address: String,
    val displayName: String,
    val addedAt: Long
)

data class FilterGroupKeyword(
    val id: String,
    val groupId: Long,
    val text: String,
    val addedAt: Long
)

data class FilterGroupPattern(
    val id: String,
    val groupId: Long,
    val type: PatternType,
    val value: String,
    val addedAt: Long
)

/** نتیجه‌ی تشخیصِ اینکه یه پیامِ تازه‌رسیده با کدوم گروه و از چه طریقی مچ شده */
data class FilterMatchResult(
    val group: FilterGroup,
    val matchType: FilterMatchType,
    val matchedValue: String?
)
