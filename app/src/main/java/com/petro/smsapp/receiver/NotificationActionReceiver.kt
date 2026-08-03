package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.petro.smsapp.MainActivity
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دکمه‌های داینامیک روی نوتیفیکیشن پیامک این ریسیور رو صدا می‌زنن.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotifAction", "دریافت شد: action=${intent.action}")
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // ACTION_BLOCK دیگه به Room نیاز نداره - فقط اپ رو با یه Intent باز می‌کنه که
        // اونجا صفحه‌ی «به کدوم گروه اضافه بشه؟» نشون داده بشه، پس همینجا synchronous انجامش میدیم
        if (intent.action == ACTION_BLOCK) {
            val address = intent.getStringExtra(EXTRA_ADDRESS)
            if (address != null) {
                val displayName = ContactsCache.getName(context, address) ?: address
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_QUICK_GROUP_ADDRESS, address)
                    putExtra(MainActivity.EXTRA_QUICK_GROUP_DISPLAY_NAME, displayName)
                }
                context.startActivity(openIntent)
            }
            if (notificationId != -1) {
                NotificationManagerCompat.from(context).cancel(notificationId)
            }
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(context, intent)
            } finally {
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, intent: Intent) {
        val repository = SmsRepository(context)

        when (intent.action) {
            ACTION_MARK_READ -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                if (threadId != -1L) {
                    repository.markThreadAsRead(threadId)
                    Log.d("NotifAction", "thread $threadId خوانده‌شده علامت خورد")
                }
            }
            ACTION_DELETE -> {
                val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
                if (messageId != -1L) {
                    repository.deleteMessage(messageId)
                    Log.d("NotifAction", "message $messageId حذف شد")
                }
            }
            ACTION_REPLY -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_QUICK_REPLY)?.toString()?.trim()
                if (address != null && !replyText.isNullOrEmpty()) {
                    repository.sendSms(address, replyText)
                    Log.d("NotifAction", "پاسخ سریع برای $address ارسال شد")
                }
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "com.petro.smsapp.ACTION_MARK_READ"
        const val ACTION_DELETE = "com.petro.smsapp.ACTION_DELETE"
        const val ACTION_BLOCK = "com.petro.smsapp.ACTION_BLOCK"
        const val ACTION_REPLY = "com.petro.smsapp.ACTION_REPLY"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val KEY_QUICK_REPLY = "key_quick_reply"
    }
}
