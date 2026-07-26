package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.petro.smsapp.data.AlarmScheduler
import com.petro.smsapp.data.ScheduledMessageStore
import com.petro.smsapp.data.SmsRepository

/**
 * alarm های AlarmManager با ریستارت گوشی پاک میشن. این ریسیور با روشن شدن گوشی، همه‌ی
 * پیام‌های زمان‌بندی‌شده‌ی هنوز-در-انتظار رو دوباره تو AlarmManager ثبت می‌کنه؛ اگه زمانِ
 * یکی‌شون همون موقع خاموش بودن گوشی گذشته باشه، همون لحظه که گوشی روشن شد می‌فرستدش.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = ScheduledMessageStore.getAll(context)
        val now = System.currentTimeMillis()

        pending.forEach { message ->
            if (message.scheduledAt > now) {
                AlarmScheduler.schedule(context, message)
            } else {
                SmsRepository(context).sendSms(message.address, message.body, message.subscriptionId)
                ScheduledMessageStore.remove(context, message.id)
            }
        }
    }
}
