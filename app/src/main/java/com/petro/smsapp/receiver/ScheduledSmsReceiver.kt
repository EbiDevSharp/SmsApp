package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.petro.smsapp.data.ScheduledMessageStore
import com.petro.smsapp.data.SmsRepository

/**
 * وقتی زمانِ یه پیام زمان‌بندی‌شده برسه، AlarmManager این ریسیور رو صدا می‌زنه. پیام رو
 * از ScheduledMessageStore می‌خونه (اگه کاربر قبلش لغوش کرده یا زودتر «اکنون ارسال شود»
 * زده باشه، دیگه توی استور نیست و اینجا کاری نمی‌کنیم)، واقعاً می‌فرستدش، و از لیست
 * انتظار درش میاره.
 */
class ScheduledSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_SCHEDULED) return
        val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
        if (id == -1L) return

        val message = ScheduledMessageStore.get(context, id)
        if (message == null) {
            Log.d("ScheduledSms", "پیام زمان‌بندی‌شده‌ی $id دیگه وجود نداره (لغو شده یا زودتر ارسال شده)")
            return
        }

        SmsRepository(context).sendSms(message.address, message.body, message.subscriptionId)
        ScheduledMessageStore.remove(context, id)
        Log.d("ScheduledSms", "پیام زمان‌بندی‌شده‌ی $id ارسال شد")
    }

    companion object {
        const val ACTION_SEND_SCHEDULED = "com.petro.smsapp.ACTION_SEND_SCHEDULED"
        const val EXTRA_SCHEDULED_ID = "extra_scheduled_id"
    }
}
