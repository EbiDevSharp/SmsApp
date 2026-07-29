package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.petro.smsapp.data.AlarmScheduler
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.SmsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * alarm های AlarmManager با ریستارت گوشی پاک میشن. این ریسیور با روشن شدن گوشی، همه‌ی
 * پیام‌های زمان‌بندی‌شده‌ی هنوز-در-انتظار رو دوباره تو AlarmManager ثبت می‌کنه. چون
 * خواندن از ScheduledMessageRepository حالا suspend ئه، goAsync() + coroutine لازمه.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduledMessageRepository = AppContainer.scheduledMessageRepository(context)
                val pending = scheduledMessageRepository.getAllOnce()
                val now = System.currentTimeMillis()

                pending.forEach { message ->
                    if (message.scheduledAt > now) {
                        AlarmScheduler.schedule(context, message)
                    } else {
                        SmsRepository(context).sendSms(message.address, message.body, message.subscriptionId)
                        scheduledMessageRepository.remove(message.id)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
