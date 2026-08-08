package com.petro.smsapp.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.MainActivity
import com.petro.smsapp.R
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.SmsRepository
import com.petro.smsapp.data.UnreadReminderScheduler
import com.petro.smsapp.util.SmsAlertSoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * با رسیدن زمان یادآوری پیام خوانده‌نشده:
 * - اگر thread هنوز unread دارد → طبق تنظیمات نوتیف و/یا صدا
 * - در غیر این صورت زنجیره لغو می‌شود
 */
class UnreadReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!AppSettings.isUnreadReminderEnabled(context)) {
                    UnreadReminderScheduler.cancelForThread(context, threadId)
                    return@launch
                }

                val pending = UnreadReminderScheduler.get(context, threadId)
                if (pending == null) return@launch

                val stillUnread = hasUnreadInThread(context, threadId)
                if (!stillUnread) {
                    UnreadReminderScheduler.cancelForThread(context, threadId)
                    Log.d(TAG, "thread $threadId already read — cancelled")
                    return@launch
                }

                val showNotif = AppSettings.isUnreadReminderShowNotification(context)
                val playSound = AppSettings.isUnreadReminderPlaySound(context)

                if (showNotif) {
                    showReminderNotification(context, pending, silent = !playSound)
                }
                // اگر نوتیف روی کانال صدادار پست شده، صدا از کانال می‌آید؛
                // فقط وقتی نوتیف خاموش است یا نوتیف بی‌صدا است، دستی پخش می‌کنیم.
                if (playSound && !showNotif) {
                    SmsAlertSoundPlayer.playFromSmsChannel(context)
                } else if (playSound && showNotif) {
                    // نوتیف روی کانال بی‌صدا پست می‌شود و صدا دستی یک‌بار پخش می‌شود
                    // تا با تنظیم playSound سازگار بماند (کنترل مستقل از صدای کانال سیستم).
                    SmsAlertSoundPlayer.playFromSmsChannel(context)
                }

                UnreadReminderScheduler.onAlarm(context, threadId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun hasUnreadInThread(context: Context, threadId: Long): Boolean {
        return try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
                null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "hasUnread check failed", e)
            true // در صورت خطا بهتر است یادآوری بماند تا از دست نرود
        }
    }

    private fun showReminderNotification(
        context: Context,
        pending: UnreadReminderScheduler.PendingReminder,
        silent: Boolean
    ) {
        val channelId = if (silent) REMINDER_SILENT_CHANNEL else "sms_channel"
        val notificationId = pending.notificationId

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_THREAD_ID, pending.threadId)
            putExtra(MainActivity.EXTRA_ADDRESS, pending.address)
            putExtra(MainActivity.EXTRA_DISPLAY_NAME, pending.displayName)
        }
        val contentPi = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = loadContactPhoto(context, pending.contactPhotoUri) ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        val title = "یادآوری: ${pending.displayName}"
        val body = pending.body.ifBlank { "پیام خوانده‌نشده" }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .setOnlyAlertOnce(false)

        if (largeIcon != null) builder.setLargeIcon(largeIcon)

        if (AppSettings.isMarkReadOnNotificationDismissEnabled(context)) {
            val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_READ
                data = Uri.parse("smsapp://mark-read-reminder/${pending.threadId}")
                putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, pending.threadId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val dismissPi = PendingIntent.getBroadcast(
                context, notificationId * 10 + 9, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setDeleteIntent(dismissPi)
        }

        // دکمه خوانده شد
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            data = Uri.parse("smsapp://mark-read/${pending.threadId}")
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, pending.threadId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPi = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(R.drawable.ic_check, "خوانده شد", markReadPi).build()
        )

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission missing", e)
        }
    }

    private fun loadContactPhoto(context: Context, photoUri: String?): android.graphics.Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "UnreadReminder"
        const val ACTION_REMIND = "com.petro.smsapp.ACTION_UNREAD_REMIND"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val REMINDER_SILENT_CHANNEL = "sms_reminder_silent_channel"
    }
}
