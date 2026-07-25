package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * پیام‌هایی که به‌خاطر تطبیق با یکی از «کلمات کلیدی بلاک» بلاک شدن - نه به‌خاطر بلاک‌بودنِ
 * خودِ شماره‌ی فرستنده. چون این پیام‌ها می‌تونن از یه شماره‌ی کاملاً بلاک‌نشده اومده باشن (فقط
 * متنشون مشکوکه)، برخلاف BlockStore (که بر اساس آدرس فیلتر می‌کنه)، اینجا باید بر اساس
 * خودِ id ردیفِ پیام تصمیم بگیریم - هم‌خانواده‌ی الگوی TrashStore.
 *
 * SmsRepository از getAllBlockedMessageIds برای مخفی‌کردن این پیام‌ها از لیست مکالمات/چت
 * استفاده می‌کنه، و از getMatchedKeyword برای نشون‌دادن «بلاک‌شده بر اساس چه کلمه‌ای» توی
 * صفحه‌ی «پیامک‌های بلاک‌شده».
 */
object BlockedKeywordMessageStore {
    private const val PREFS_NAME = "block_keyword_message_store"
    private const val KEY_INDEX = "blocked_keyword_message_ids"

    fun isMessageBlocked(context: Context, messageId: Long): Boolean {
        return ids(prefs(context)).contains(messageId.toString())
    }

    fun getMatchedKeyword(context: Context, messageId: Long): String? {
        val p = prefs(context)
        return p.getString(entryKey(messageId), null)?.let { jsonStr ->
            runCatching { JSONObject(jsonStr).getString("keyword") }.getOrNull()
        }
    }

    fun markBlocked(context: Context, messageId: Long, keyword: String) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("keyword", keyword)
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
