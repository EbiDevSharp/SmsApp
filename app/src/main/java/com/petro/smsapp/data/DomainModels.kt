package com.petro.smsapp.data

/**
 * این فایل مدل‌های دامنه‌ای رو نگه می‌داره که قبلاً هرکدوم کنار *Store مربوط به خودشون
 * (توی همین پکیج data) تعریف شده بودن. چون اون فایل‌ها (BlockStore.kt، PrivateStore.kt،
 * FavoriteStore.kt، BlockKeywordStore.kt، BlockPatternStore.kt، ScheduledMessageStore.kt)
 * حذف شدن و منطق ذخیره‌سازی رفته زیر data/repository، خودِ مدل‌ها اینجا جمع شدن - تا
 * همه‌ی فایل‌های UI که این تایپ‌ها رو import می‌کردن (BlockedNumbersScreen، PrivateNumbersScreen،
 * BlockKeywordsScreen، BlockPatternsScreen، FavoritesScreen، ThreadScreen و ...) دست‌نخورده بمونن.
 */

data class BlockedNumber(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val blockedAt: Long
)

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

data class BlockKeyword(
    val id: String,
    val text: String,
    val addedAt: Long
)

enum class BlockPatternType {
    STARTS_WITH,
    ENDS_WITH
}

data class BlockPattern(
    val id: String,
    val type: BlockPatternType,
    val value: String,
    val addedAt: Long
)

data class BlockedPatternMatch(
    val type: BlockPatternType,
    val value: String
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
