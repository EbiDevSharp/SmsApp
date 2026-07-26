package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * پیام‌هایی که به‌خاطر «خارج از مخاطبین بودنِ شماره‌ی فرستنده» بلاک شدن - نه به‌خاطر
 * بلاک‌بودنِ صریحِ خودِ شماره (BlockStore)، نه یه کلمه‌ی کلیدی (BlockKeywordStore)، و نه یه
 * الگوی شماره (BlockPatternStore). کاملاً هم‌خانواده‌ی BlockedKeywordMessageStore/
 * BlockedPatternMessageStore: چون این پیام‌ها می‌تونن از یه شماره‌ی کاملاً بلاک‌نشده اومده
 * باشن (فقط جزو مخاطبین نیستن)، بر اساس خودِ id ردیفِ پیام تصمیم می‌گیریم، نه آدرس.
 *
 * فقط وقتی کاربر از صفحه‌ی «تنظیمات بلاک»، «بلاک شماره‌های خارج از مخاطبین» رو فعال کرده
 * باشه این استور پر میشه (SmsDeliverReceiver چک می‌کنه).
 */
object BlockedNonContactMessageStore {
    private const val PREFS_NAME = "block_non_contact_message_store"
    private const val KEY_INDEX = "blocked_non_contact_message_ids"

    fun isMessageBlocked(context: Context, messageId: Long): Boolean {
        return ids(prefs(context)).contains(messageId.toString())
    }

    fun markBlocked(context: Context, messageId: Long, address: String) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("address", address)
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
