package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.data.SmsRepository

/**
 * دکمه‌های "خوانده شد" و "حذف" روی نوتیفیکیشن پیامک این ریسیور رو صدا می‌زنن
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (threadId == -1L) return

        val repository = SmsRepository(context)
        when (intent.action) {
            ACTION_MARK_READ -> repository.markThreadAsRead(threadId)
            ACTION_DELETE -> repository.deleteThread(threadId)
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
