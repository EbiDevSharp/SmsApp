package com.petro.smsapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal

class SmsApplication : Application() {
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

        registerContactsChangeObserver()
    }

    /**
     * ContactsCache یه بار همه‌ی مخاطبین رو می‌خونه و کش می‌کنه (isLoaded=true می‌مونه)
     * و فقط با invalidate() دوباره از دیتابیس مخاطبین می‌خونه. قبلاً هیچ‌جا این invalidate
     * با تغییرِ واقعیِ مخاطبینِ گوشی صدا زده نمی‌شد - یعنی اگه کاربر همون لحظه یه مخاطب
     * جدید ذخیره می‌کرد یا اسمِ یکی رو عوض می‌کرد، تا وقتی پروسسِ اپ عوض نمی‌شد، اسمِ
     * جدید توی لیستِ مکالمات/نوتیف‌ها دیده نمی‌شد.
     *
     * این ContentObserver روی کل دیتابیس مخاطبین (ContactsContract.Contacts.CONTENT_URI)
     * ثبت میشه: با هر تغییری (اضافه/حذف/ویرایشِ اسم یا شماره) کش رو نامعتبر می‌کنه و از
     * طریق DataChangeSignal به SmsViewModel هم اطلاع میده تا لیست‌ها (که از همون کش
     * برای نمایش اسمِ مخاطب استفاده می‌کنن) دوباره لود بشن.
     */
    private fun registerContactsChangeObserver() {
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
    }
}
