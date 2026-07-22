package com.petro.smsapp.data

import android.content.Context

/**
 * سطل زباله - پیام‌هایی که کاربر حذف کرده ولی چون تیک «سطل زباله» توی تنظیمات فعاله،
 * به‌جای حذف فیزیکی، فقط «مخفی» میشن.
 *
 * برخلاف FavoriteStore (که فقط یه اسنپ‌شات نگه می‌داره)، اینجا خودِ ردیف توی Sms
 * provider دست‌نخورده باقی می‌مونه - چون قراره قابل ری‌استور باشه و محتوای واقعی
 * (متن، وضعیت دلیوری و ...) نباید از دست بره. فقط از کوئری‌های عادی
 * (SmsRepository.getConversations/getMessagesForThread) فیلتر میشه.
 *
 * کلید هر ورودی «trashed_<id>» با مقدار زمان دقیق حذف‌شدن (برای مرتب‌سازی توی
 * صفحه‌ی سطل زباله، جدیدترین بالا).
 */
object TrashStore {
    private const val PREFS_NAME = "trash_store"
    private const val KEY_PREFIX = "trashed_"

    fun isTrashed(context: Context, messageId: Long): Boolean {
        return prefs(context).contains(key(messageId))
    }

    fun moveToTrash(context: Context, messageId: Long) {
        prefs(context).edit().putLong(key(messageId), System.currentTimeMillis()).apply()
    }

    /** برداشتن از سطل زباله - هم برای ری‌استور واقعی، هم برای پاک کردن ایندکس بعد از حذف همیشگی */
    fun restore(context: Context, messageId: Long) {
        prefs(context).edit().remove(key(messageId)).apply()
    }

    fun getTrashedAt(context: Context, messageId: Long): Long =
        prefs(context).getLong(key(messageId), 0L)

    fun getTrashedIds(context: Context): Set<Long> {
        return prefs(context).all.keys
            .mapNotNull { k -> k.removePrefix(KEY_PREFIX).toLongOrNull() }
            .toSet()
    }

    private fun key(messageId: Long) = "$KEY_PREFIX$messageId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
