package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * نوع یه قانونِ بلاکِ الگویی: شماره‌ی فرستنده باید با یه عبارت خاص شروع بشه یا تموم بشه.
 */
enum class BlockPatternType {
    STARTS_WITH,
    ENDS_WITH
}

/**
 * یه قانون بلاک بر اساس **الگوی شماره** - هم‌خانواده‌ی BlockKeyword ولی به‌جای متنِ پیام،
 * روی خودِ شماره‌ی فرستنده کار می‌کنه. مثلاً «همه‌ی شماره‌هایی که با +98 یا 0930 شروع میشن»
 * یا «همه‌ی شماره‌هایی که به 9325 ختم میشن»، بدون اینکه لازم باشه تک‌تک شماره‌ها جدا بلاک بشن.
 */
data class BlockPattern(
    val id: String,
    val type: BlockPatternType,
    val value: String,
    val addedAt: Long
)

/**
 * لیست الگوهای بلاکِ شماره - هم‌خانواده‌ی BlockKeywordStore. SmsDeliverReceiver موقع رسیدن هر
 * پیام، شماره‌ی فرستنده رو با findMatch چک می‌کنه؛ اگه مچ شد پیام بلاک میشه (بدون نوتیف/صدا) و
 * کدوم‌الگو‌بودنش توی BlockedPatternMessageStore ذخیره میشه تا توی صفحه‌ی «پیامک‌های بلاک‌شده»
 * نشون داده بشه - دقیقاً همون رفتاری که برای کلمات کلیدی داریم.
 *
 * مقایسه فقط بر اساس **رقم‌ها**ست (هم‌خانواده‌ی نرمال‌سازیِ BlockStore/ContactsCache) تا فرمت‌های
 * مختلفِ یه پیش‌شماره (+98 یا 0098 یا ۰۰۹۸) یا فاصله/خط‌تیره‌ی وسط شماره باعث عدم‌تطبیق نشن.
 */
object BlockPatternStore {
    private const val PREFS_NAME = "block_pattern_store"
    private const val KEY_INDEX = "pattern_ids"

    fun getAllPatterns(context: Context): List<BlockPattern> {
        val p = prefs(context)
        return ids(p).mapNotNull { id ->
            p.getString(entryKey(id), null)?.let { jsonStr ->
                runCatching {
                    val obj = JSONObject(jsonStr)
                    BlockPattern(
                        id = id,
                        type = BlockPatternType.valueOf(obj.getString("type")),
                        value = obj.getString("value"),
                        addedAt = obj.getLong("addedAt")
                    )
                }.getOrNull()
            }
        }.sortedByDescending { it.addedAt }
    }

    /**
     * افزودن یه الگوی جدید. اگه از قبل همین نوع + همین رقم‌ها وجود داشته، دوباره اضافه
     * نمیشه و false برمی‌گرده - تا UI بتونه به کاربر بگه «از قبل اضافه شده».
     */
    fun addPattern(context: Context, type: BlockPatternType, value: String): Boolean {
        val trimmed = value.trim()
        val normalized = digitsOnly(trimmed)
        if (normalized.isBlank()) return false
        if (getAllPatterns(context).any { it.type == type && digitsOnly(it.value) == normalized }) return false

        val p = prefs(context)
        val id = "${System.currentTimeMillis()}_${trimmed.hashCode()}"
        val json = JSONObject().apply {
            put("type", type.name)
            put("value", trimmed)
            put("addedAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(id) }
        p.edit()
            .putString(entryKey(id), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
        return true
    }

    fun removePattern(context: Context, id: String) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(id) }
        p.edit()
            .remove(entryKey(id))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    /**
     * اولین الگویی که با شماره‌ی فرستنده مچ بشه رو برمی‌گردونه (یا null اگه هیچ‌کدوم مچ نشدن).
     * SmsDeliverReceiver دقیقاً با همین نتیجه تصمیم می‌گیره بلاک کنه یا نه.
     */
    fun findMatch(context: Context, address: String): BlockPattern? {
        val addressDigits = digitsOnly(address)
        if (addressDigits.isBlank()) return null
        return getAllPatterns(context).firstOrNull { pattern ->
            val patternDigits = digitsOnly(pattern.value)
            if (patternDigits.isBlank()) return@firstOrNull false
            when (pattern.type) {
                BlockPatternType.STARTS_WITH -> addressDigits.startsWith(patternDigits)
                BlockPatternType.ENDS_WITH -> addressDigits.endsWith(patternDigits)
            }
        }
    }

    private fun digitsOnly(value: String): String = value.filter { it.isDigit() }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun entryKey(id: String) = "pattern_$id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
