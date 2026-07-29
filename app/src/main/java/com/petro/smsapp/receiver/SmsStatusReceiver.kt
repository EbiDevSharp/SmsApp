package com.petro.smsapp.receiver

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.MainActivity
import com.petro.smsapp.R
import com.petro.smsapp.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دو تا اکشن رو مدیریت می‌کنه (نتیجه‌ی ارسال / گزارش دلیوری). چون
 * SmsRepository.updateDeliveryStatus حالا suspend شده (Room)، از goAsync() +
 * coroutine استفاده می‌کنیم؛ resultCode باید همون لحظه‌ی synchronous خونده بشه
 * (بعد از برگشتن از onReceive دیگه معتبر نیست)، پس قبل از launch ذخیره‌ش می‌کنیم.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId == -1L) return

        // resultCode فقط تا وقتی onReceive برنگشته معتبره؛ همینجا می‌خونیمش
        val capturedResultCode = resultCode

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SMS_SENT -> handleSentResult(context, messageId, capturedResultCode)
                    ACTION_SMS_DELIVERED -> handleDeliveryReport(context, messageId, intent, capturedResultCode)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSentResult(context: Context, messageId: Long, resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) return

        val repository = SmsRepository(context)
        repository.updateDeliveryStatus(messageId, delivered = false, deliveredAtMillis = 0L)
        showSendFailedNotification(context, messageId)
    }

    private suspend fun handleDeliveryReport(context: Context, messageId: Long, intent: Intent, resultCode: Int) {
        val delivered = isDeliverySuccessful(intent, resultCode)
        val repository = SmsRepository(context)
        val now = System.currentTimeMillis()
        repository.updateDeliveryStatus(messageId, delivered, now)

        if (delivered) {
            if (com.petro.smsapp.data.AppSettings.isDeliveryNotificationsEnabled(context)) {
                showDeliveredNotification(context, messageId)
            }
        }
    }

    private fun isDeliverySuccessful(intent: Intent, resultCode: Int): Boolean {
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

    private fun showSendFailedNotification(context: Context, messageId: Long) {
        val channelId = "sms_channel"
        val notificationId = ("sendfailed_$messageId").hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle("ارسال پیام ناموفق بود")
            .setContentText("پیام شما ارسال نشد - آنتن یا اتصال شبکه رو بررسی کنید")
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
        const val ACTION_SMS_SENT = "com.petro.smsapp.ACTION_SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.petro.smsapp.ACTION_SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }
}
