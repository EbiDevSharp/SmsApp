package com.petro.smsapp.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.petro.smsapp.receiver.ScheduledSmsReceiver

/**
 * مسئول ثبت/لغو واقعیِ alarm سیستمی برای هر پیام زمان‌بندی‌شده. جدا از ScheduledMessageStore
 * نگه داشته شده تا اون فایل فقط «داده» رو مدیریت کنه، این یکی فقط «زمان‌بندی سیستم» رو.
 */
object AlarmScheduler {

    fun schedule(context: Context, message: ScheduledMessage) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, message.id)

        try {
            // از اندروید ۱۲ (S) به بعد، alarm دقیق نیاز به مجوز SCHEDULE_EXACT_ALARM داره که
            // کاربر باید دستی از تنظیمات گوشی بده (این اپ موقع باز شدن درخواستش می‌کنه).
            // اگه نداشتیمش، به‌جای کرش یا رد شدن کامل، با AlarmManager.set معمولی (غیردقیق)
            // ثبت می‌کنیم - ممکنه چند دقیقه دیر برسه ولی حداقل اصلاً از قلم نمی‌افته.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, message.scheduledAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.scheduledAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, message.scheduledAt, pendingIntent)
        }
    }

    fun cancel(context: Context, id: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildPendingIntent(context, id)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ScheduledSmsReceiver::class.java).apply {
            action = ScheduledSmsReceiver.ACTION_SEND_SCHEDULED
            data = Uri.parse("smsapp://scheduled/$id")
            putExtra(ScheduledSmsReceiver.EXTRA_SCHEDULED_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
