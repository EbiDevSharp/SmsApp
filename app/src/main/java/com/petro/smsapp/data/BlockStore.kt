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
 * بلاک‌کردن بر اساس **شماره** (نه thread). قبلاً بر اساس threadId بود، ولی این باگ داشت:
 * اگه همه‌ی پیام‌های یه thread حذف بشن (مثلاً از صفحه‌ی «پیامک‌های بلاک‌شده»)، اندروید
 * ممکنه اون threadId رو دیگه نگه نداره یا برای پیام بعدیِ همون شماره یه threadId جدید
 * بسازه - یعنی ربط بلاک با اون شماره قطع می‌شد و نوتیف دوباره برمی‌گشت.
 *
 * الان کلید اصلی، شماره‌ی نرمال‌شده‌ست (فقط ۹ رقم آخر، هم‌خانواده‌ی ContactsCache) که
 * کاملاً مستقل از هر threadId یا وجود/عدم‌وجود پیامه. threadId فقط به‌عنوان یه متادیتای
 * نمایشی (برای دکمه‌ی آنبلاک از روی لیست) نگه داشته میشه، نه به‌عنوان کلید.
 *
 * وقتی یه شماره بلاک میشه:
 * - از لیست اصلی مکالمات (SmsRepository.getConversations) حذف میشه.
 * - پیام‌های تازه‌ای که از همین شماره برسن، نه نوتیف میدن نه صدا (SmsDeliverReceiver چک می‌کنه).
 * - همه‌ی پیام‌هاش (قدیم + جدید، از روی خودِ آدرس) توی صفحه‌ی «پیامک‌های بلاک‌شده» قابل دیدنن.
 */
object BlockStore {
    private const val PREFS_NAME = "block_store"
    private const val KEY_INDEX = "blocked_address_keys"

    fun isAddressBlocked(context: Context, address: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        return ids(prefs(context)).contains(key)
    }

    /** برای جاهایی که فقط threadId در دسترسه (مثل UI قدیمی) - جستجو بر اساس متادیتا */
    fun isThreadBlocked(context: Context, threadId: Long): Boolean {
        return getAllBlockedNumbers(context).any { it.threadId == threadId }
    }

    /**
     * بلاک‌کردن یه شماره. اگه از قبل بلاک بوده، هیچ کاری نمی‌کنه و false برمی‌گردونه
     * (تا UI بتونه بگه «از قبل بلاک بوده»)؛ اگه واقعاً تازه اضافه شد، true برمی‌گردونه.
     */
    fun blockNumber(context: Context, threadId: Long, address: String, displayName: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        val p = prefs(context)
        if (ids(p).contains(key)) return false // از قبل بلاک بوده - رد کن، آپدیت نکن

        val json = JSONObject().apply {
            put("threadId", threadId)
            put("address", address)
            put("displayName", displayName)
            put("blockedAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(key) }
        p.edit()
            .putString(entryKey(key), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
        return true
    }

    fun unblockAddress(context: Context, address: String) {
        val key = normalize(address)
        if (key.isBlank()) return
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(key) }
        p.edit()
            .remove(entryKey(key))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    /** برای دکمه‌ی «آنبلاک» توی UI که فعلاً فقط threadId داره */
    fun unblockThread(context: Context, threadId: Long) {
        val entry = getAllBlockedNumbers(context).find { it.threadId == threadId } ?: return
        unblockAddress(context, entry.address)
    }

    fun getAllBlockedNumbers(context: Context): List<BlockedNumber> {
        val p = prefs(context)
        return ids(p).mapNotNull { key ->
            p.getString(entryKey(key), null)?.let { jsonStr ->
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

    private fun entryKey(normalizedKey: String) = "block_$normalizedKey"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** هم‌خانواده‌ی نرمال‌سازی ContactsCache - آخرین ۹ رقم شماره، تا فرمت‌های +98/0/... یکی حساب بشن */
    private fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > 9) digitsOnly.takeLast(9) else digitsOnly
    }
}
