package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * یه قانون بلاک بر اساس متن پیام - اگه بدنه‌ی پیامِ ورودی شامل این عبارت باشه (بدون توجه
 * به بزرگی/کوچکی حروف)، پیام بلاک میشه؛ صرف‌نظر از اینکه شماره‌ی فرستنده خودش بلاک باشه یا نه.
 */
data class BlockKeyword(
    val id: String,
    val text: String,
    val addedAt: Long
)

/**
 * لیست کلمات/عبارت‌های کلیدیِ بلاک - هم‌خانواده‌ی BlockStore ولی به‌جای شماره، روی
 * متن پیام کار می‌کنه. SmsDeliverReceiver موقع رسیدن هر پیام، بدنه‌ش رو با findMatch
 * چک می‌کنه؛ اگه مچ شد پیام بلاک میشه (بدون نوتیف/صدا) و کدوم‌کلمه‌بودنش توی
 * BlockedKeywordMessageStore ذخیره میشه تا توی صفحه‌ی «پیامک‌های بلاک‌شده» نشون داده بشه.
 */
object BlockKeywordStore {
    private const val PREFS_NAME = "block_keyword_store"
    private const val KEY_INDEX = "keyword_ids"

    fun getAllKeywords(context: Context): List<BlockKeyword> {
        val p = prefs(context)
        return ids(p).mapNotNull { id ->
            p.getString(entryKey(id), null)?.let { jsonStr ->
                runCatching {
                    val obj = JSONObject(jsonStr)
                    BlockKeyword(id = id, text = obj.getString("text"), addedAt = obj.getLong("addedAt"))
                }.getOrNull()
            }
        }.sortedByDescending { it.addedAt }
    }

    /**
     * افزودن یه کلمه‌ی جدید. اگه از قبل (بدون توجه به بزرگی/کوچکی حروف) وجود داشته، دوباره
     * اضافه نمیشه و false برمی‌گرده - تا UI بتونه به کاربر بگه «از قبل اضافه شده».
     */
    fun addKeyword(context: Context, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (getAllKeywords(context).any { it.text.equals(trimmed, ignoreCase = true) }) return false

        val p = prefs(context)
        val id = "${System.currentTimeMillis()}_${trimmed.hashCode()}"
        val json = JSONObject().apply {
            put("text", trimmed)
            put("addedAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(id) }
        p.edit()
            .putString(entryKey(id), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
        return true
    }

    fun removeKeyword(context: Context, id: String) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(id) }
        p.edit()
            .remove(entryKey(id))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    /**
     * اولین کلمه‌ای که توی متن پیام پیدا بشه رو برمی‌گردونه (یا null اگه هیچ‌کدوم از
     * قانون‌ها مچ نشدن). SmsDeliverReceiver دقیقاً با همین نتیجه تصمیم می‌گیره بلاک کنه یا نه.
     */
    fun findMatch(context: Context, body: String): BlockKeyword? {
        if (body.isBlank()) return null
        return getAllKeywords(context).firstOrNull { body.contains(it.text, ignoreCase = true) }
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun entryKey(id: String) = "keyword_$id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
