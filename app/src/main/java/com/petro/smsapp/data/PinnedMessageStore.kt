package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * پیام‌های «پین‌شده» داخل خودِ چتِ یک مخاطب - از منوی کلیکِ پیام (MessageActionsSheet)
 * قابل‌فعال/غیرفعال کردنه. برخلاف PinStore (که کلِ مکالمه رو توی لیست اصلی پین می‌کنه)،
 * این استور روی تک‌تکِ پیام‌ها (messageId) کار می‌کنه - هم‌خانواده‌ی الگوی FavoriteStore،
 * ولی بدون قفلِ حذف (پین‌بودن مانع حذف پیام نمیشه).
 */
object PinnedMessageStore {
    private const val PREFS_NAME = "pinned_message_store"
    private const val KEY_INDEX = "pinned_message_ids"

    fun isPinned(context: Context, messageId: Long): Boolean {
        return ids(prefs(context)).contains(messageId.toString())
    }

    fun pin(context: Context, messageId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { add(messageId.toString()) }
        p.edit().putStringSet(KEY_INDEX, updatedIds).apply()
    }

    fun unpin(context: Context, messageId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(messageId.toString()) }
        p.edit().putStringSet(KEY_INDEX, updatedIds).apply()
    }

    fun togglePin(context: Context, messageId: Long) {
        if (isPinned(context, messageId)) unpin(context, messageId) else pin(context, messageId)
    }

    fun getAllPinnedIds(context: Context): Set<Long> {
        return ids(prefs(context)).mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** موقع حذف واقعیِ یه پیام، متادیتای پینش هم پاک بشه */
    fun clear(context: Context, messageId: Long) {
        unpin(context, messageId)
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
