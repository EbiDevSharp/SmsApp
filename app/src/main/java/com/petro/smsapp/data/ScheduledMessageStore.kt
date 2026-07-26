package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * یه پیام زمان‌بندی‌شده که هنوز واقعاً ارسال نشده - قبل از فرارسیدن زمانش، هیچ ردیف
 * واقعی توی Sms provider نداره (چون هنوز فرستاده نشده)، فقط اینجا (توی این استور) به‌عنوان
 * «در انتظار» نگه داشته میشه و توی UI به‌شکل یه حباب مجازی (نه واقعی) نمایش داده میشه.
 */
data class ScheduledMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val scheduledAt: Long,
    val subscriptionId: Int?
)

/**
 * پیام‌های زمان‌بندی‌شده‌ی در انتظار ارسال. دقیقاً همون الگوی FavoriteStore/PrivateStore
 * (JSON per entry + یه ایندکس جدا از id ها)، با این فرق که خودِ AlarmManager هم باید
 * برای هر ورودی یه alarm واقعی ثبت کنه (AlarmScheduler مسئول اون بخشه، جدا از این فایل،
 * تا این فایل فقط مسئول «داده» بمونه نه «زمان‌بندی سیستم»).
 */
object ScheduledMessageStore {
    private const val PREFS_NAME = "scheduled_message_store"
    private const val KEY_INDEX = "scheduled_ids"

    fun save(context: Context, message: ScheduledMessage) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("id", message.id)
            put("threadId", message.threadId)
            put("address", message.address)
            put("displayName", message.displayName)
            put("body", message.body)
            put("scheduledAt", message.scheduledAt)
            // JSONObject با null مستقیم راحت کار نمی‌کنه، پس subscriptionId==null رو با -1 نشون می‌دیم
            put("subscriptionId", message.subscriptionId ?: -1)
        }
        val updatedIds = ids(p).toMutableSet().apply { add(message.id.toString()) }
        p.edit()
            .putString(key(message.id), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    /** حذف از لیست انتظار - چه چون واقعاً ارسال شد، چه چون کاربر لغوش کرد */
    fun remove(context: Context, id: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(id.toString()) }
        p.edit().remove(key(id)).putStringSet(KEY_INDEX, updatedIds).apply()
    }

    fun get(context: Context, id: Long): ScheduledMessage? {
        val raw = prefs(context).getString(key(id), null) ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }

    fun getForThread(context: Context, threadId: Long): List<ScheduledMessage> {
        return getAll(context).filter { it.threadId == threadId }
    }

    fun getAll(context: Context): List<ScheduledMessage> {
        val p = prefs(context)
        return ids(p)
            .mapNotNull { idStr -> idStr.toLongOrNull() }
            .mapNotNull { id -> p.getString(key(id), null) }
            .mapNotNull { raw -> runCatching { parse(raw) }.getOrNull() }
            .sortedBy { it.scheduledAt }
    }

    private fun parse(raw: String): ScheduledMessage {
        val obj = JSONObject(raw)
        val subId = obj.getInt("subscriptionId")
        return ScheduledMessage(
            id = obj.getLong("id"),
            threadId = obj.getLong("threadId"),
            address = obj.getString("address"),
            displayName = obj.getString("displayName"),
            body = obj.getString("body"),
            scheduledAt = obj.getLong("scheduledAt"),
            subscriptionId = if (subId == -1) null else subId
        )
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun key(id: Long) = "scheduled_$id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
