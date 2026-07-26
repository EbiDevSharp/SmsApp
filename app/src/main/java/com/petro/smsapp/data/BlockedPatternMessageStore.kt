package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** یه نتیجه‌ی «مچ»: کدوم نوع الگو (شروع/پایان) با کدوم مقدار، پیام رو بلاک کرده. */
data class BlockedPatternMatch(
    val type: BlockPatternType,
    val value: String
)

/**
 * پیام‌هایی که به‌خاطر تطبیق با یکی از «الگوهای بلاکِ شماره» بلاک شدن - نه به‌خاطر بلاک‌بودنِ
 * صریحِ خودِ شماره‌ی فرستنده (BlockStore) و نه به‌خاطر یه کلمه‌ی کلیدی توی متن (BlockKeywordStore).
 * کاملاً هم‌خانواده‌ی BlockedKeywordMessageStore: چون این پیام‌ها می‌تونن از یه شماره‌ی کاملاً
 * بلاک‌نشده اومده باشن (فقط الگوی شماره‌شون مشکوکه)، بر اساس خودِ id ردیفِ پیام تصمیم می‌گیریم.
 *
 * SmsRepository از getAllBlockedMessageIds برای مخفی‌کردن این پیام‌ها از لیست مکالمات/چت
 * استفاده می‌کنه، و از getMatchedPattern برای نشون‌دادن «بلاک‌شده بر اساس کدوم الگو» توی
 * صفحه‌ی «پیامک‌های بلاک‌شده».
 */
object BlockedPatternMessageStore {
    private const val PREFS_NAME = "block_pattern_message_store"
    private const val KEY_INDEX = "blocked_pattern_message_ids"

    fun isMessageBlocked(context: Context, messageId: Long): Boolean {
        return ids(prefs(context)).contains(messageId.toString())
    }

    fun getMatchedPattern(context: Context, messageId: Long): BlockedPatternMatch? {
        val p = prefs(context)
        return p.getString(entryKey(messageId), null)?.let { jsonStr ->
            runCatching {
                val obj = JSONObject(jsonStr)
                BlockedPatternMatch(
                    type = BlockPatternType.valueOf(obj.getString("type")),
                    value = obj.getString("value")
                )
            }.getOrNull()
        }
    }

    fun markBlocked(context: Context, messageId: Long, type: BlockPatternType, value: String) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("type", type.name)
            put("value", value)
            put("blockedAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(messageId.toString()) }
        p.edit()
            .putString(entryKey(messageId), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun getAllBlockedMessageIds(context: Context): Set<Long> {
        return ids(prefs(context)).mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** موقع حذف واقعیِ یه پیام (از صفحه‌ی «پیامک‌های بلاک‌شده»)، متادیتای مربوطش هم پاک بشه */
    fun clear(context: Context, messageId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(messageId.toString()) }
        p.edit()
            .remove(entryKey(messageId))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun entryKey(messageId: Long) = "msg_$messageId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
