package com.petro.smsapp.receiver

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.MainActivity
import com.petro.smsapp.R
import com.petro.smsapp.data.SmsRepository

/**
 * وقتی برای یه پیامک ارسالی، گزارش دلیوری (Delivery Report) از شبکه برسه، این ریسیور
 * صدا زده میشه. وضعیت پیام رو توی Sms provider آپدیت می‌کنه (برای تیک زیر حباب پیام) و
 * یه نوتیف «تحویل داده شد» نشون میده - فعلاً روی همون کانال/صدای نوتیف پیامک معمولی.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SMS_DELIVERED) return

        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId == -1L) return

        val delivered = isDeliverySuccessful(intent)
        val repository = SmsRepository(context)
        val now = System.currentTimeMillis()
        repository.updateDeliveryStatus(messageId, delivered, now)

        if (delivered) {
            showDeliveredNotification(context, messageId)
        }
    }

    /**
     * نتیجه‌ی resultCode به‌تنهایی همیشه دقیق نیست؛ اگه PDU گزارش دلیوری همراه intent باشه
     * (که معمولاً هست)، وضعیت واقعی رو از خودش می‌خونیم. status == 0 یعنی تحویل موفق.
     */
    private fun isDeliverySuccessful(intent: Intent): Boolean {
        return try {
            val pdu = intent.getByteArrayExtra("pdu")
            if (pdu != null) {
                val format = intent.getStringExtra("format")
                val smsPdu = if (format != null) {
                    android.telephony.SmsMessage.createFromPdu(pdu, format)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsMessage.createFromPdu(pdu)
                }
                smsPdu?.status == 0
            } else {
                resultCode == Activity.RESULT_OK
            }
        } catch (e: Exception) {
            resultCode == Activity.RESULT_OK
        }
    }

    private fun showDeliveredNotification(context: Context, messageId: Long) {
        val channelId = "sms_channel"
        val notificationId = ("delivered_$messageId").hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("پیام تحویل داده شد")
            .setContentText("پیام ارسالی شما با موفقیت به گیرنده رسید")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).apply {
            try {
                notify(notificationId, notification)
            } catch (e: SecurityException) {
                // پرمیشن نوتیفیکیشن داده نشده
            }
        }
    }

    companion object {
        const val ACTION_SMS_DELIVERED = "com.petro.smsapp.ACTION_SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }
}
