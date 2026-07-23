package com.petro.smsapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * چک مجوزهای runtime، جدا از منطق «اپ پیش‌فرض پیامک بودن» (که DefaultSmsAppHelper مسئولشه).
 *
 * این دو تا با هم فرق دارن: اپ می‌تونه پیش‌فرض پیامک باشه ولی کاربر بعداً از تنظیمات
 * سیستم مجوز READ_CONTACTS یا READ_SMS رو دستی برداشته باشه - در اون حالت
 * DefaultSmsAppHelper.isDefaultSmsApp همچنان true برمی‌گردونه، ولی واقعاً دیگه اجازه‌ی
 * خوندن نداریم و contentResolver.query با SecurityException کرش می‌کنه.
 *
 * قبلاً این چک هیچ‌جا (نه تو SmsRepository نه تو ContactsCache) برای عملیات‌های خواندن
 * انجام نمی‌شد - فقط عملیات‌های نوشتن (ارسال/حذف) با requireDefaultSmsApp محافظت می‌شدن.
 * همین باعث می‌شد اگه مجوز مخاطبین برداشته می‌شد، همون اولین بار که لیست مکالمات لود
 * می‌شد (که برای اسم هر مکالمه سراغ ContactsCache می‌ره) کل اپ کرش کنه.
 */
object PermissionHelper {
    fun hasReadSmsPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.READ_SMS)

    fun hasReadContactsPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.READ_CONTACTS)

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
