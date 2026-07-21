package com.petro.smsapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * اسنپ‌شات یه پیام فیوریت‌شده. اطلاعات لازم برای نمایش توی صفحه‌ی «علاقه‌مندی‌ها»
 * (اسم شخص، متن، تاریخ/ساعت) رو جدا از خود پیام نگه می‌داریم تا حتی اگه بعداً اسم
 * مخاطب عوض بشه، همون اسمی که موقع فیوریت‌کردن بوده نمایش داده بشه.
 */
data class FavoriteMessage(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val displayName: String,
    val body: String,
    val date: Long
)

/**
 * لیست پیام‌های فیوریت‌شده، با SharedPreferences ذخیره میشه (شبیه الگوی DeliveryStore).
 *
 * هر پیام با کلید fav_<id> به‌صورت JSON ذخیره میشه، و یه ست جدا از id ها (favorite_ids)
 * به‌عنوان ایندکس نگه داشته میشه تا بشه همه‌ی فیوریت‌ها رو لیست کرد.
 *
 * نکته‌ی مهم: تا وقتی پیامی اینجا فیوریت باشه، SmsRepository.deleteMessage اجازه‌ی
 * حذفش رو نمی‌ده (نقش قفل/لاک) - برای برداشتن این قفل، اول باید از همین‌جا (removeFavorite)
 * پاک بشه.
 */
object FavoriteStore {
    private const val PREFS_NAME = "favorite_store"
    private const val KEY_INDEX = "favorite_ids"

    fun isFavorite(context: Context, messageId: Long): Boolean {
        return ids(prefs(context)).contains(messageId.toString())
    }

    fun addFavorite(context: Context, message: FavoriteMessage) {
        val p = prefs(context)
        val json = JSONObject().apply {
            put("messageId", message.messageId)
            put("threadId", message.threadId)
            put("address", message.address)
            put("displayName", message.displayName)
            put("body", message.body)
            put("date", message.date)
        }
        val updatedIds = ids(p).toMutableSet().apply { add(message.messageId.toString()) }
        p.edit()
            .putString(key(message.messageId), json.toString())
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun removeFavorite(context: Context, messageId: Long) {
        val p = prefs(context)
        val updatedIds = ids(p).toMutableSet().apply { remove(messageId.toString()) }
        p.edit()
            .remove(key(messageId))
            .putStringSet(KEY_INDEX, updatedIds)
            .apply()
    }

    fun getAllFavorites(context: Context): List<FavoriteMessage> {
        val p = prefs(context)
        return ids(p).mapNotNull { idStr ->
            p.getString(key(idStr.toLong()), null)?.let { jsonStr ->
                runCatching {
                    val obj = JSONObject(jsonStr)
                    FavoriteMessage(
                        messageId = obj.getLong("messageId"),
                        threadId = obj.getLong("threadId"),
                        address = obj.getString("address"),
                        displayName = obj.getString("displayName"),
                        body = obj.getString("body"),
                        date = obj.getLong("date")
                    )
                }.getOrNull()
            }
        }.sortedByDescending { it.date }
    }

    private fun ids(p: SharedPreferences): Set<String> = p.getStringSet(KEY_INDEX, emptySet()) ?: emptySet()

    private fun key(messageId: Long) = "fav_$messageId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
