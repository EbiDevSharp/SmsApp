package com.petro.smsapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.PermissionHelper

class SmsApplication : Application() {

    @Volatile private var contactsObserverRegistered = false

    override fun onCreate() {
        super.onCreate()
        // باید قبل از هر استفاده‌ی دیگه از AppSettings.state (مثلاً توی DateFormatter) صدا زده بشه
        AppSettings.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "sms_channel",
                "پیامک‌ها",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        ensureContactsObserverRegistered()
    }

    /**
     * قبلاً این ثبتِ observer همیشه و بدونِ هیچ چکِ مجوزی توی onCreate صدا زده می‌شد.
     * رو امولاتور/اندروید استودیو معمولاً مشکلی پیش نمی‌آد، ولی رویِ بعضی گوشی‌های
     * واقعی (وقتی هنوز هیچ‌وقت مجوزِ READ_CONTACTS داده نشده - دقیقاً حالتِ اولین
     * اجرای اپ رو گوشی)، registerContentObserver رویِ ContactsContract.Contacts.CONTENT_URI
     * با SecurityException کرش می‌کنه. چون این کد توی Application.onCreate ئه (قبل از
     * هر Activity)، این کرش یعنی کلِ اپ حتی قبل از رسیدنِ MainActivity به مرحله‌ی
     * «انتخابِ اپ پیش‌فرض» بسته میشه - دقیقاً همون رفتاری که فقط با گرفتنِ دستیِ مجوزِ
     * مخاطبین از تنظیماتِ گوشی (قبل از باز کردنِ دوباره‌ی اپ) درست میشه.
     *
     * الان قبل از ثبتِ observer چک مجوز میشه (اگه نبود، فقط بی‌سروصدا ثبت نمیشه -
     * ContactsCache خودش از قبل برای همین حالت مقاومه و فقط اسمِ مخاطب رو نشون نمیده)،
     * و علاوه بر اون، خودِ registerContentObserver هم توی try/catch(SecurityException)
     * قرار گرفته، برای حالتِ نادرِ race condition. این تابع public ئه تا MainActivity
     * بعد از گرفتنِ موفقِ مجوزِ مخاطبین (توی همون سشن، بدونِ نیاز به ری‌استارتِ اپ)،
     * دوباره صداش بزنه و observer واقعاً ثبت بشه.
     */
    fun ensureContactsObserverRegistered() {
        if (contactsObserverRegistered) return
        if (!PermissionHelper.hasReadContactsPermission(this)) return
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    ContactsCache.invalidate()
                    DataChangeSignal.notifyChanged()
                }
            }
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
            contactsObserverRegistered = true
        } catch (e: SecurityException) {
            Log.w("SmsApplication", "SecurityException موقع ثبتِ ContentObserver مخاطبین - بعداً با گرفتنِ مجوز دوباره تلاش میشه", e)
        }
    }
}
