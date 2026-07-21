package com.petro.smsapp.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager

class SmsRepository(private val context: Context) {

    /**
     * خواندن لیست مکالمات، گروه‌بندی‌شده بر اساس thread_id
     * از Telephony.Sms.Conversations برای خلاصه استفاده می‌کنیم
     */
    fun getConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        val uri = Telephony.Sms.Conversations.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.Conversations.THREAD_ID,
            Telephony.Sms.Conversations.SNIPPET,
            Telephony.Sms.Conversations.MESSAGE_COUNT
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.Conversations.THREAD_ID))
                val snippet = cursor.getStringOrNull(cursor.getColumnIndex(Telephony.Sms.Conversations.SNIPPET)) ?: ""

                val address = getAddressForThread(threadId)
                val displayName = getContactName(address) ?: address
                val (date, unread) = getThreadMeta(threadId)

                conversations.add(
                    Conversation(
                        threadId = threadId,
                        address = address,
                        displayName = displayName,
                        snippet = snippet,
                        date = date,
                        unreadCount = unread
                    )
                )
            }
        }
        return conversations.sortedByDescending { it.date }
    }

    /**
     * خواندن همه پیام‌های یک مکالمه (thread) به ترتیب زمانی
     */
    fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val uri = Telephony.Sms.CONTENT_URI
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())

        context.contentResolver.query(
            uri, null, selection, selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(cursorToMessage(cursor))
            }
        }
        return messages
    }

    /**
     * ارسال پیامک - پیامک‌های بلند رو خودکار تقسیم می‌کنه
     * subscriptionId: برای گوشی‌های دو سیم‌کارت، مشخص می‌کنه از کدوم سیم ارسال بشه (null = پیش‌فرض سیستم)
     */
    fun sendSms(address: String, body: String, subscriptionId: Int? = null) {
        val smsManager = if (subscriptionId != null && subscriptionId != -1) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(SmsManager::class.java)
        }
        val parts = smsManager.divideMessage(body)
        smsManager.sendMultipartTextMessage(address, null, parts, null, null)

        // ذخیره توی sent box (برای وقتی که اپ پیش‌فرض هستیم)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
        context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    /**
     * حذف کل مکالمه (اگه لازم بشه - فعلاً جایی صداش نمی‌زنیم چون خیلی مخرب بود برای اکشن نوتیف)
     */
    fun deleteThread(threadId: Long) {
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString())
        )
    }

    /**
     * حذف فقط یک پیام مشخص (برای اکشن «حذف» روی نوتیفیکیشن)
     */
    fun deleteMessage(messageId: Long) {
        context.contentResolver.delete(
            android.content.ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
            null,
            null
        )
    }

    /**
     * پیدا کردن thread موجود برای یه شماره، یا ساختن یه thread جدید اگه وجود نداشته باشه
     * (لازم برای شروع مکالمه‌ی جدید از صفحه "پیام جدید")
     */
    fun getOrCreateThreadId(address: String): Long {
        return Telephony.Threads.getOrCreateThreadId(context, setOf(address))
    }

    fun markThreadAsRead(threadId: Long) {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI, values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString())
        )
    }

    private fun getAddressForThread(threadId: Long): String {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getStringOrNull(0) ?: ""
            }
        }
        return ""
    }

    private fun getThreadMeta(threadId: Long): Pair<Long, Int> {
        var date = 0L
        var unread = 0
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.DATE, Telephony.Sms.READ),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            var first = true
            while (cursor.moveToNext()) {
                if (first) {
                    date = cursor.getLong(0)
                    first = false
                }
                if (cursor.getInt(1) == 0) unread++
            }
        }
        return date to unread
    }

    private fun getContactName(address: String): String? {
        if (address.isBlank()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getStringOrNull(0)
            }
        }
        return null
    }

    private fun cursorToMessage(cursor: Cursor): SmsMessage {
        fun col(name: String) = cursor.getColumnIndex(name)
        val type = cursor.getInt(col(Telephony.Sms.TYPE))
        val dateSentIdx = col(Telephony.Sms.DATE_SENT)
        return SmsMessage(
            id = cursor.getLong(col(Telephony.Sms._ID)),
            threadId = cursor.getLong(col(Telephony.Sms.THREAD_ID)),
            address = cursor.getStringOrNull(col(Telephony.Sms.ADDRESS)) ?: "",
            body = cursor.getStringOrNull(col(Telephony.Sms.BODY)) ?: "",
            date = cursor.getLong(col(Telephony.Sms.DATE)),
            dateSent = if (dateSentIdx >= 0) cursor.getLong(dateSentIdx) else 0L,
            type = type,
            isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            isRead = cursor.getInt(col(Telephony.Sms.READ)) == 1
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        if (index < 0) return null
        return if (isNull(index)) null else getString(index)
    }
}
