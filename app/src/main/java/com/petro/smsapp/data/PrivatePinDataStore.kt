package com.petro.smsapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.privatePinDataStore by preferencesDataStore(name = "private_pin")

/**
 * رمز ۴ رقمی ورود به بخش «خصوصی» - جدا از PrivateRepository (که Room و لیستِ شماره‌هاست)
 * چون این فقط دو مقدار تکی (هش + salt) هست، دقیقاً همون‌جور دیتایی که گوگل DataStore رو
 * برایش پیشنهاد می‌کنه (نه Room).
 */
object PrivatePinDataStore {
    private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
    private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")

    suspend fun hasPin(context: Context): Boolean =
        context.privatePinDataStore.data.first()[KEY_PIN_HASH] != null

    suspend fun setPin(context: Context, pin: String) {
        val salt = generateSalt()
        context.privatePinDataStore.edit { prefs ->
            prefs[KEY_PIN_SALT] = salt
            prefs[KEY_PIN_HASH] = hashPin(pin, salt)
        }
    }

    suspend fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.privatePinDataStore.data.first()
        val salt = prefs[KEY_PIN_SALT] ?: return false
        val storedHash = prefs[KEY_PIN_HASH] ?: return false
        return hashPin(pin, salt) == storedHash
    }

    suspend fun removePin(context: Context) {
        context.privatePinDataStore.edit { prefs ->
            prefs.remove(KEY_PIN_HASH)
            prefs.remove(KEY_PIN_SALT)
        }
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
}
