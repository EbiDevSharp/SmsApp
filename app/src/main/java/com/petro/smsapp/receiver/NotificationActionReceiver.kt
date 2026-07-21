package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.data.SmsRepository

/**
 * دکمه‌های "خوانده شد" و "حذف" روی نوتیفیکیشن پیامک این ریسیور رو صدا می‌زنن
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotifAction", "دریافت شد: action=${intent.action}")
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (threadId == -1L) {
            Log.d("NotifAction", "threadId نامعتبره، خارج شدیم")
            return
        }

        val repository = SmsRepository(context)
        when (intent.action) {
            ACTION_MARK_READ -> {
                repository.markThreadAsRead(threadId)
                Log.d("NotifAction", "thread $threadId خوانده‌شده علامت خورد")
            }
            ACTION_DELETE -> {
                repository.deleteThread(threadId)
                Log.d("NotifAction", "thread $threadId حذف شد")
            }
        }

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.petro.smsapp.ACTION_MARK_READ"
        const val ACTION_DELETE = "com.petro.smsapp.ACTION_DELETE"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
