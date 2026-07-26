package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * مکالمه‌های «پین‌شده» توی لیستِ اصلیِ مکالمات - هم‌خانواده‌ی الگوی FavoriteStore، ولی
 * کلید اصلی threadId هست (نه messageId) چون پین یه ویژگیِ کلِ مکالمه‌ست، نه یه پیام خاص.
 *
 * برای هر مکالمه‌ی پین‌شده، زمانِ پین‌شدن هم ذخیره میشه تا وقتی چند مکالمه هم‌زمان پین
 * هستن، جدیدترینِ پین‌شده بالاتر از بقیه (ولی همچنان بالاتر از مکالمه‌های پین‌نشده) بشینه.
 *
 * محدودیتِ «حداکثر چند مکالمه قابل‌پینه» توی AppSettings.getMaxPinnedConversations نگه
 * داشته میشه (چون یه تنظیمِ کاربره، نه دیتای خودِ پین)؛ رعایتِ این محدودیت وظیفه‌ی
 * SmsViewModel هست، نه خودِ این استور.
 */
object PinStore {
    private const val PREFS_NAME = "pin_store"
    private const val KEY_INDEX = "pinned_thread_ids"

    fun isPinned(context: Context, threadId: Long): Boolean {
        return ids(prefs(context)).contains(threadId.toString())
    }

    fun getPinnedCount(context: Context): Int = ids(prefs(context)).size

    fun pin(context: Context, threadId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { add(threadId.toString()) }
        p.edit()
            .putLong(entryKey(threadId), System.currentTimeMillis())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun unpin(context: Context, threadId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(threadId.toString()) }
        p.edit()
            .remove(entryKey(threadId))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    /** زمانی که یه مکالمه پین شده - برای مرتب‌سازیِ جدیدترین‌پین‌شده بالاتر. اگه پین نباشه، 0. */
    fun getPinnedAt(context: Context, threadId: Long): Long {
        val p = prefs(context)
        if (!isPinned(context, threadId)) return 0L
        return p.getLong(entryKey(threadId), 0L)
    }

    fun getAllPinnedThreadIds(context: Context): Set<Long> {
        return ids(prefs(context)).mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun entryKey(threadId: Long) = "pin_$threadId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
