package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * وقتی زمانِ یه پیام زمان‌بندی‌شده برسه، AlarmManager این ریسیور رو صدا می‌زنه.
 * چون خواندن/حذف از ScheduledMessageRepository حالا روی Room (suspend) ئه، از
 * goAsync() + coroutine استفاده می‌کنیم.
 */
class ScheduledSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_SCHEDULED) return
        val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (id == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduledMessageRepository = AppContainer.scheduledMessageRepository(context)
                val message = scheduledMessageRepository.get(id)
                if (message == null) {
                    Log.d("ScheduledSms", "پیام زمان‌بندی‌شده‌ی $id دیگه وجود نداره (لغو شده یا زودتر ارسال شده)")
                    return@launch
                }

                SmsRepository(context).sendSms(message.address, message.body, message.subscriptionId)
                scheduledMessageRepository.remove(id)
                Log.d("ScheduledSms", "پیام زمان‌بندی‌شده‌ی $id ارسال شد")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEND_SCHEDULED = "com.petro.smsapp.ACTION_SEND_SCHEDULED"
        const val EXTRA_SCHEDULED_ID = "extra_scheduled_id"
    }
}
