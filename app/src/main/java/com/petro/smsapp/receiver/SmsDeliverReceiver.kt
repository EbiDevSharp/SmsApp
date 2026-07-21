package com.petro.smsapp.receiver

import android.app.PendingIntent
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

        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(sender))
        showNotification(context, sender, fullBody, threadId)
    }

    private fun showNotification(context: Context, sender: String, body: String, threadId: Long) {
        val channelId = "sms_channel"
        val notificationId = sender.hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("thread_address", sender)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadIntent = Intent(context, com.petro.smsapp.receiver.NotificationActionReceiver::class.java).apply {
            action = com.petro.smsapp.receiver.NotificationActionReceiver.ACTION_MARK_READ
            data = android.net.Uri.parse("smsapp://mark-read/$threadId")
            putExtra(com.petro.smsapp.receiver.NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(com.petro.smsapp.receiver.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 2, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(context, com.petro.smsapp.receiver.NotificationActionReceiver::class.java).apply {
            action = com.petro.smsapp.receiver.NotificationActionReceiver.ACTION_DELETE
            data = android.net.Uri.parse("smsapp://delete/$threadId")
            putExtra(com.petro.smsapp.receiver.NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(com.petro.smsapp.receiver.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 2 + 1, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(sender)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_check, "خوانده شد", markReadPendingIntent)
            .addAction(R.drawable.ic_delete, "حذف", deletePendingIntent)
            .build()

        NotificationManagerCompat.from(context).apply {
            try {
                notify(notificationId, notification)
            } catch (e: SecurityException) {
                // پرمیشن نوتیفیکیشن داده نشده
            }
        }
    }
}
