package com.petro.smsapp.data

import android.content.Context

/**
 * تنظیمات ساده‌ی اپ که نیاز به دیتابیس ندارن، با SharedPreferences ذخیره میشن.
 *
 * فعلاً فقط یه پرچم داره: «سطل زباله فعاله یا نه». صفحه‌ی «سطل زباله» توی درارو فعلاً
 * placeholder هست (هنوز UI براش نساختیم)، ولی SmsRepository.deleteMessage از همین
 * پرچم استفاده می‌کنه تا وقتی سطل زباله رو کامل ساختیم، فقط کافیه isTrashEnabled رو
 * از یه سوییچ توی SettingsScreen true کنیم و منطق move-to-trash خودش فعال میشه.
 */
object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_TRASH_ENABLED = "trash_enabled"

    fun isTrashEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TRASH_ENABLED, false)
    }

    fun setTrashEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRASH_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
