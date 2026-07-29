package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دکمه‌های داینامیک روی نوتیفیکیشن پیامک این ریسیور رو صدا می‌زنن. چون همه‌ی این
 * اکشن‌ها الان به Room (suspend) نیاز دارن، از goAsync() + coroutine روی Dispatchers.IO
 * استفاده می‌کنیم تا Main Thread قفل نشه.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotifAction", "دریافت شد: action=${intent.action}")
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

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
            ACTION_BLOCK -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                if (threadId != -1L && address != null) {
                    val displayName = ContactsCache.getName(context, address) ?: address
                    val blockRepository = AppContainer.blockRepository(context)
                    val wasNewlyBlocked = blockRepository.blockNumber(threadId, address, displayName)
                    DataChangeSignal.notifyChanged()
                    if (wasNewlyBlocked) {
                        Log.d("NotifAction", "شماره $address بلاک شد")
                    } else {
                        Log.d("NotifAction", "شماره $address از قبل بلاک بود، تغییری اعمال نشد")
                    }
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
