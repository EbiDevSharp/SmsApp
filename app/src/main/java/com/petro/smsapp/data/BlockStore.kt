package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * اطلاعات یه شماره‌ی بلاک‌شده - برای نمایش توی صفحه‌ی «شماره‌های بلاک‌شده».
 */
data class BlockedNumber(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val blockedAt: Long
)

/**
 * بلاک‌کردن بر اساس thread (که خودش بر اساس شماره‌ست، دقیقاً همون گروه‌بندی‌ای که
 * خودِ اندروید برای مکالمات استفاده می‌کنه - قابل‌اعتمادتر از تطبیق رشته‌ای شماره‌هاست).
 *
 * وقتی یه thread بلاک میشه:
 * - از لیست اصلی مکالمات (SmsRepository.getConversations) حذف میشه.
 * - پیام‌های تازه‌ای که از همین thread برسن، نه نوتیف میدن نه صدا (SmsDeliverReceiver چک می‌کنه).
 * - همه‌ی پیام‌هاش (قدیم + جدید) توی صفحه‌ی «پیامک‌های بلاک‌شده» قابل دیدنن.
 *
 * ساختار دقیقاً مثل FavoriteStore: هر شماره با کلید block_<threadId> به JSON ذخیره میشه،
 * و یه ست جدا (blocked_thread_ids) به‌عنوان ایندکس.
 */
object BlockStore {
    private const val PREFS_NAME = "block_store"
    private const val KEY_INDEX = "blocked_thread_ids"

    fun isThreadBlocked(context: Context, threadId: Long): Boolean {
        return ids(prefs(context)).contains(threadId.toString())
    }

    fun blockThread(context: Context, threadId: Long, address: String, displayName: String) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("threadId", threadId)
            put("address", address)
            put("displayName", displayName)
            put("blockedAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(threadId.toString()) }
        p.edit()
            .putString(key(threadId), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun unblockThread(context: Context, threadId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(threadId.toString()) }
        p.edit()
            .remove(key(threadId))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun getBlockedThreadIds(context: Context): Set<Long> {
        return ids(prefs(context)).mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun getAllBlockedNumbers(context: Context): List<BlockedNumber> {
        val p = prefs(context)
        return ids(p).mapNotNull { idStr ->
            p.getString(key(idStr.toLong()), null)?.let { jsonStr ->
                runCatching {
                    val obj = JSONObject(jsonStr)
                    BlockedNumber(
                        threadId = obj.getLong("threadId"),
                        address = obj.getString("address"),
                        displayName = obj.getString("displayName"),
                        blockedAt = obj.getLong("blockedAt")
                    )
                }.getOrNull()
            }
        }.sortedByDescending { it.blockedAt }
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun key(threadId: Long) = "block_$threadId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
