package com.petro.smsapp

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * مدیریت درخواست "اپ پیش‌فرض پیامک" شدن
 * از اندروید 10 (API 29) به بعد باید از RoleManager استفاده کرد
 * برای نسخه‌های قدیمی‌تر از Intent قدیمی استفاده می‌کنیم
 */
object DefaultSmsAppHelper {

    fun isDefaultSmsApp(context: Context): Boolean {
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    /**
     * این intent رو با startActivityForResult یا رجیستری‌شده‌ی ActivityResultLauncher صدا بزن
     */
    fun getRequestRoleIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }
}
