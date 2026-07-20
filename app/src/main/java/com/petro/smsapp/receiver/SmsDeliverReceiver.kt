package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.MainActivity
import com.petro.smsapp.R

/**
 * وقتی اپ ما "پیش‌فرض پیامک" باشه، این ریسیور به جای سیستم پیام رو دریافت می‌کنه
 * و خودمون مسئول ذخیره‌ش توی SMS Provider هستیم (سیستم دیگه خودکار ذخیره نمی‌کنه)
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "ناشناس"
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis

        // ذخیره توی content provider سیستم (جدول Sms.Inbox)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, fullBody)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)

        showNotification(context, sender, fullBody)
    }

    private fun showNotification(context: Context, sender: String, body: String) {
        val channelId = "sms_channel"
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("thread_address", sender)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, sender.hashCode(), openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message) // این آیکون رو باید خودت اضافه کنی
            .setContentTitle(sender)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).apply {
            // نیازمند پرمیشن POST_NOTIFICATIONS در اندروید 13+
            try {
                notify(sender.hashCode(), notification)
            } catch (e: SecurityException) {
                // پرمیشن نوتیفیکیشن داده نشده
            }
        }
    }
}
