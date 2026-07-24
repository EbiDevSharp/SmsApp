package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

/**
 * اطلاعات یه شماره‌ی خصوصی‌شده - برای نمایش توی صفحه‌ی «شماره‌های خصوصی».
 */
data class PrivateNumber(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val madePrivateAt: Long
)

/**
 * خصوصی‌کردن بر اساس **شماره** - دقیقاً همون دلیل و ساختار BlockStore (نگاه کن به توضیحات
 * اونجا). قبلاً بر اساس threadId بود و همون باگ رو داشت: اگه همه‌ی پیام‌های thread حذف
 * بشن، ربطش با شماره قطع می‌شد.
 *
 * قانون مهم (طبق درخواست کاربر): یه شماره هم‌زمان نمی‌تونه هم بلاک باشه هم خصوصی؛ این
 * قانون توی SmsViewModel.blockConversations/makeConversationsPrivate رعایت میشه.
 *
 * رمز به‌صورت هش‌شده (SHA-256 + salt تصادفی) ذخیره میشه، نه متن ساده.
 */
object PrivateStore {
    private const val PREFS_NAME = "private_store"
    private const val KEY_INDEX = "private_address_keys"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    fun isAddressPrivate(context: Context, address: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        return ids(prefs(context)).contains(key)
    }

    /** برای جاهایی که فقط threadId در دسترسه (مثل UI قدیمی) - جستجو بر اساس متادیتا */
    fun isThreadPrivate(context: Context, threadId: Long): Boolean {
        return getAllPrivateNumbers(context).any { it.threadId == threadId }
    }

    /**
     * خصوصی‌کردن یه شماره. اگه از قبل خصوصی بوده، هیچ کاری نمی‌کنه و false برمی‌گردونه؛
     * اگه واقعاً تازه اضافه شد، true.
     */
    fun makePrivate(context: Context, threadId: Long, address: String, displayName: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        val p = prefs(context)
        if (ids(p).contains(key)) return false

        val json = JSONObject().apply {
            put("threadId", threadId)
            put("address", address)
            put("displayName", displayName)
            put("madePrivateAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(key) }
        p.edit()
            .putString(entryKey(key), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
        return true
    }

    fun removePrivateByAddress(context: Context, address: String) {
        val key = normalize(address)
        if (key.isBlank()) return
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(key) }
        p.edit()
            .remove(entryKey(key))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    /** برای دکمه‌ی «حذف از خصوصی» توی UI که فعلاً فقط threadId داره */
    fun removePrivate(context: Context, threadId: Long) {
        val entry = getAllPrivateNumbers(context).find { it.threadId == threadId } ?: return
        removePrivateByAddress(context, entry.address)
    }

    fun getAllPrivateNumbers(context: Context): List<PrivateNumber> {
        val p = prefs(context)
        return ids(p).mapNotNull { key ->
            p.getString(entryKey(key), null)?.let { jsonStr ->
                runCatching {
                    val obj = JSONObject(jsonStr)
                    PrivateNumber(
                        threadId = obj.getLong("threadId"),
                        address = obj.getString("address"),
                        displayName = obj.getString("displayName"),
                        madePrivateAt = obj.getLong("madePrivateAt")
                    )
                }.getOrNull()
            }
        }.sortedByDescending { it.madePrivateAt }
    }

    // ---- رمز ۴ رقمی ورود به بخش خصوصی ----

    fun hasPin(context: Context): Boolean = prefs(context).contains(KEY_PIN_HASH)

    fun setPin(context: Context, pin: String) {
        val salt = generateSalt()
        prefs(context).edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hashPin(pin, salt))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val p = prefs(context)
        val salt = p.getString(KEY_PIN_SALT, null) ?: return false
        val storedHash = p.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin, salt) == storedHash
    }

    /** حذف کامل رمز - دفعه‌ی بعد که کاربر وارد بخش خصوصی بشه، دوباره از اول یه رمز جدید می‌سازه */
    fun removePin(context: Context) {
        prefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun entryKey(normalizedKey: String) = "private_$normalizedKey"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** هم‌خانواده‌ی نرمال‌سازی ContactsCache/BlockStore */
    private fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > 9) digitsOnly.takeLast(9) else digitsOnly
    }
}
