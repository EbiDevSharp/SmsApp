package com.petro.smsapp.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.petro.smsapp.receiver.UnreadReminderReceiver
import org.json.JSONArray
import org.json.JSONObject

/**
 * زمان‌بندی یادآوری پیام‌های خوانده‌نشده.
 * برای هر thread حداکثر یک زنجیره‌ی یادآوری نگه می‌دارد؛ با پیام جدید ریست می‌شود.
 * با markThreadAsRead یا تمام شدن تعداد، لغو می‌شود.
 */
object UnreadReminderScheduler {

    private const val TAG = "UnreadReminder"
    private const val PREFS = "unread_reminders"
    private const val KEY_ITEMS = "items"

    data class PendingReminder(
        val threadId: Long,
        val address: String,
        val displayName: String,
        val body: String,
        val contactPhotoUri: String?,
        val notificationId: Int,
        val remainingCount: Int,
        val nextTriggerAt: Long
    )

    /**
     * بعد از دریافت پیامک جدید، اگر یادآوری فعال باشد برای این thread زمان‌بندی کن.
     * زنجیره قبلی همان thread (در صورت وجود) جایگزین می‌شود.
     */
    fun scheduleForIncoming(
        context: Context,
        threadId: Long,
        address: String,
        displayName: String,
        body: String,
        contactPhotoUri: String?,
        notificationId: Int
    ) {
        if (!AppSettings.isUnreadReminderEnabled(context)) return
        val count = AppSettings.getUnreadReminderCount(context).coerceIn(
            AppSettings.MIN_UNREAD_REMINDER_COUNT,
            AppSettings.MAX_UNREAD_REMINDER_COUNT
        )
        val intervalMin = AppSettings.getUnreadReminderIntervalMinutes(context)
        val intervalMs = intervalMin * 60_000L
        val nextAt = System.currentTimeMillis() + intervalMs

        val item = PendingReminder(
            threadId = threadId,
            address = address,
            displayName = displayName,
            body = body,
            contactPhotoUri = contactPhotoUri,
            notificationId = notificationId,
            remainingCount = count,
            nextTriggerAt = nextAt
        )
        upsert(context, item)
        setAlarm(context, item)
        Log.d(TAG, "scheduled thread=$threadId count=$count in ${intervalMin}m")
    }

    fun cancelForThread(context: Context, threadId: Long) {
        cancelAlarm(context, threadId)
        remove(context, threadId)
        Log.d(TAG, "cancelled thread=$threadId")
    }

    fun cancelAll(context: Context) {
        loadAll(context).forEach { cancelAlarm(context, it.threadId) }
        prefs(context).edit().remove(KEY_ITEMS).apply()
    }

    /** بعد از بوت: alarmهای باقی‌مانده را دوباره ثبت کن. */
    fun rescheduleAll(context: Context) {
        if (!AppSettings.isUnreadReminderEnabled(context)) {
            cancelAll(context)
            return
        }
        val now = System.currentTimeMillis()
        val kept = mutableListOf<PendingReminder>()
        loadAll(context).forEach { item ->
            if (item.remainingCount <= 0) return@forEach
            val next = if (item.nextTriggerAt <= now) now + 5_000L else item.nextTriggerAt
            val updated = item.copy(nextTriggerAt = next)
            kept.add(updated)
            setAlarm(context, updated)
        }
        saveAll(context, kept)
        Log.d(TAG, "rescheduled ${kept.size} reminders after boot")
    }

    /**
     * وقتی alarm می‌زند: اگر هنوز unread باشد، نوتیف/صدا طبق تنظیمات، و در صورت باقی‌ماندن تعداد دوباره زمان‌بندی.
     * @return true اگر هنوز چیزی برای این thread باقی مانده
     */
    fun onAlarm(context: Context, threadId: Long): PendingReminder? {
        val item = loadAll(context).find { it.threadId == threadId } ?: return null
        val remainingAfter = item.remainingCount - 1
        if (remainingAfter <= 0) {
            remove(context, threadId)
            Log.d(TAG, "last reminder fired for thread=$threadId")
            return item.copy(remainingCount = 0)
        }
        val intervalMs = AppSettings.getUnreadReminderIntervalMinutes(context) * 60_000L
        val next = item.copy(
            remainingCount = remainingAfter,
            nextTriggerAt = System.currentTimeMillis() + intervalMs
        )
        upsert(context, next)
        setAlarm(context, next)
        Log.d(TAG, "reminder fired thread=$threadId remaining=$remainingAfter")
        return next.copy(remainingCount = item.remainingCount) // remaining before decrement for caller display
    }

    fun get(context: Context, threadId: Long): PendingReminder? =
        loadAll(context).find { it.threadId == threadId }

    // ─── AlarmManager ───

    private fun setAlarm(context: Context, item: PendingReminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = buildPendingIntent(context, item.threadId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, item.nextTriggerAt, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.nextTriggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, item.nextTriggerAt, pi)
        }
    }

    private fun cancelAlarm(context: Context, threadId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = buildPendingIntent(context, threadId)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun buildPendingIntent(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, UnreadReminderReceiver::class.java).apply {
            action = UnreadReminderReceiver.ACTION_REMIND
            data = Uri.parse("smsapp://unread-reminder/$threadId")
            putExtra(UnreadReminderReceiver.EXTRA_THREAD_ID, threadId)
        }
        val requestCode = ("unread_rem_$threadId").hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ─── persistence ───

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadAll(context: Context): List<PendingReminder> {
        val raw = prefs(context).getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PendingReminder(
                            threadId = o.getLong("threadId"),
                            address = o.getString("address"),
                            displayName = o.optString("displayName", o.getString("address")),
                            body = o.optString("body", ""),
                            contactPhotoUri = o.optString("contactPhotoUri", null).takeIf { !it.isNullOrBlank() },
                            notificationId = o.optInt("notificationId", o.getLong("threadId").toInt()),
                            remainingCount = o.getInt("remainingCount"),
                            nextTriggerAt = o.getLong("nextTriggerAt")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to parse reminders", e)
            emptyList()
        }
    }

    private fun saveAll(context: Context, items: List<PendingReminder>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("threadId", item.threadId)
                put("address", item.address)
                put("displayName", item.displayName)
                put("body", item.body)
                put("contactPhotoUri", item.contactPhotoUri ?: "")
                put("notificationId", item.notificationId)
                put("remainingCount", item.remainingCount)
                put("nextTriggerAt", item.nextTriggerAt)
            })
        }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun upsert(context: Context, item: PendingReminder) {
        val others = loadAll(context).filter { it.threadId != item.threadId }
        saveAll(context, others + item)
    }

    private fun remove(context: Context, threadId: Long) {
        saveAll(context, loadAll(context).filter { it.threadId != threadId })
    }
}
