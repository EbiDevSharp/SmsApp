package com.petro.smsapp.data

import android.content.Context

/**
 * جدول Sms سیستم ستونی برای «زمان تحویل به گیرنده» نداره (فقط STATUS رو داره که
 * می‌گه تحویل موفق بوده یا نه، بدون زمان دقیق). برای همین زمان دقیق دلیوری رو
 * خودمون جدا، با کلید messageId، توی SharedPreferences نگه می‌داریم.
 *
 * سبک و بدون نیاز به دیتابیس جداست؛ اگه بعداً لازم شد می‌تونیم عوضش کنیم با یه
 * جدول واقعی بدون اینکه جای دیگه‌ای از کد رو لمس کنیم.
 */
object DeliveryStore {
    private const val PREFS_NAME = "delivery_store"

    fun setDeliveredAt(context: Context, messageId: Long, deliveredAtMillis: Long) {
        prefs(context).edit().putLong(key(messageId), deliveredAtMillis).apply()
    }

    fun getDeliveredAt(context: Context, messageId: Long): Long {
        return prefs(context).getLong(key(messageId), 0L)
    }

    fun clear(context: Context, messageId: Long) {
        prefs(context).edit().remove(key(messageId)).apply()
    }

    private fun key(messageId: Long) = "delivered_$messageId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
