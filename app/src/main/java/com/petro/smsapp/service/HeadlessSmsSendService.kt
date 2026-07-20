package com.petro.smsapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager

/**
 * وقتی کاربر از "Respond via Message" (مثلا رد کردن تماس با پیام آماده) استفاده کنه
 * این سرویس بدون باز شدن اپ، پیام رو می‌فرسته
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val message = it.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = it.data?.schemeSpecificPart

            if (!message.isNullOrEmpty() && !recipients.isNullOrEmpty()) {
                val smsManager = getSystemService(SmsManager::class.java)
                recipients.split(";").forEach { number ->
                    smsManager.sendTextMessage(number, null, message, null, null)
                }
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
