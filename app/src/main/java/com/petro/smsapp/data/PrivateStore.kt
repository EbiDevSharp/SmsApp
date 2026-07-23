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
 * خصوصی‌کردن بر اساس thread - دقیقاً همون ساختار BlockStore (هر شماره با کلید
 * private_<threadId> به JSON، به‌علاوه‌ی یه ایندکس جدا)، به‌علاوه‌ی مدیریت رمز ۴ رقمی
 * ورودِ کل بخش «خصوصی».
 *
 * وقتی یه thread خصوصی میشه:
 * - از لیست اصلی مکالمات (SmsRepository.getConversations) حذف میشه.
 * - همه‌ی پیام‌هاش (قدیم + جدید) فقط از پشتِ رمز ۴ رقمی، توی صفحه‌ی «پیامک‌های خصوصی» دیده میشن.
 *
 * قانون مهم (طبق درخواست کاربر): یه شماره هم‌زمان نمی‌تونه هم بلاک باشه هم خصوصی؛ این
 * قانون توی SmsViewModel.blockConversations/makeConversationsPrivate رعایت میشه - قبل
 * از اضافه‌کردن به هرکدوم، وضعیتش تو اون‌یکی چک میشه و اگه بود، رد میشه.
 *
 * رمز به‌صورت هش‌شده (SHA-256 + salt تصادفی) ذخیره میشه، نه متن ساده.
 */
object PrivateStore {
    private const val PREFS_NAME = "private_store"
    private const val KEY_INDEX = "private_thread_ids"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    fun isThreadPrivate(context: Context, threadId: Long): Boolean {
        return ids(prefs(context)).contains(threadId.toString())
    }

    fun makePrivate(context: Context, threadId: Long, address: String, displayName: String) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("threadId", threadId)
            put("address", address)
            put("displayName", displayName)
            put("madePrivateAt", System.currentTimeMillis())
        }
        val updatedIds = ids(p).toMutableSet().apply { add(threadId.toString()) }
        p.edit()
            .putString(key(threadId), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun removePrivate(context: Context, threadId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(threadId.toString()) }
        p.edit()
            .remove(key(threadId))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun getPrivateThreadIds(context: Context): Set<Long> {
        return ids(prefs(context)).mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun getAllPrivateNumbers(context: Context): List<PrivateNumber> {
        val p = prefs(context)
        return ids(p).mapNotNull { idStr ->
            p.getString(key(idStr.toLong()), null)?.let { jsonStr ->
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

    private fun key(threadId: Long) = "private_$threadId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
