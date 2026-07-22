package com.petro.smsapp.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.petro.smsapp.DefaultSmsAppHelper
import com.petro.smsapp.receiver.SmsStatusReceiver

class SmsRepository(private val context: Context) {

    /**
     * فقط اپ پیش‌فرض پیامک اجازه‌ی نوشتن (ارسال/حذف/آپدیت) روی Telephony.Sms رو داره.
     * قبلاً این چک فقط موقع باز شدن اپ (MainActivity) انجام می‌شد؛ ولی اگه بعداً کاربر
     * اپ پیش‌فرض رو عوض کنه (یا هیچ‌وقت قبول نکنه) و دوباره سراغ ارسال/حذف بره،
     * contentResolver با SecurityException کرش می‌کرد. این تابع قبل از هر نوشتن صدا زده
     * میشه؛ اگه پیش‌فرض نبودیم، به‌جای کرش، فقط لاگ می‌کنیم و عملیات رو انجام نمی‌دیم.
     */
    private fun requireDefaultSmsApp(operation: String): Boolean {
        val isDefault = DefaultSmsAppHelper.isDefaultSmsApp(context)
        if (!isDefault) {
            Log.w("SmsRepository", "عملیات «$operation» انجام نشد چون اپ در حال حاضر پیش‌فرض پیامک نیست")
        }
        return isDefault
    }

    /**
     * خواندن لیست مکالمات، گروه‌بندی‌شده بر اساس thread_id
     *
     * قبلاً اینجا یه کوئری جدا به Telephony.Sms.Conversations می‌زدیم فقط برای گرفتن
     * snippet (خلاصه‌ی آخرین پیام)، که مشکلش این بود: اون جدول از مفهوم «سطل زباله»ی
     * ما خبر نداره - یعنی اگه آخرین پیام یه مکالمه تو سطل زباله بود، snippet همچنان
     * متن همون پیام حذف‌شده رو نشون می‌داد. الان به‌جاش توی همون یه پاسِ بالک روی
     * جدول sms (که برای آدرس/تاریخ/تعداد نخونده هم می‌زدیم) snippet رو هم از روی
     * جدیدترین پیامِ غیر-سطل‌زباله‌ای می‌سازیم - هم دقیق‌تره هم یه کوئری کمتر.
     */
    fun getConversations(): List<Conversation> {
        val threadMeta = getAllThreadsMeta()
        val conversations = threadMeta.map { (threadId, meta) ->
            val displayName = ContactsCache.getName(context, meta.address) ?: meta.address
            Conversation(
                threadId = threadId,
                address = meta.address,
                displayName = displayName,
                snippet = meta.snippet,
                date = meta.date,
                unreadCount = meta.unreadCount
            )
        }
        return conversations.sortedByDescending { it.date }
    }

    private data class ThreadMeta(val address: String, val date: Long, val unreadCount: Int, val snippet: String)

    /**
     * یه پاس روی کل جدول sms (مرتب‌شده بر اساس DATE نزولی) تا برای هر thread، آخرین آدرس/
     * تاریخ/متن (اولین ردیفی که برای اون thread می‌بینیم چون نزولیه) و تعداد پیام نخونده رو
     * بسازیم. پیام‌های توی سطل زباله (TrashStore) کاملاً نادیده گرفته میشن - نه تو محاسبه‌ی
     * جدیدترین پیام دخیلن، نه تو شمارش نخونده‌ها؛ اگه یه thread فقط پیام سطل‌زباله‌ای داشته
     * باشه، اصلاً توی نتیجه نمیاد (یعنی از لیست مکالمات ناپدید میشه، درست مثل حذف واقعی).
     */
    private fun getAllThreadsMeta(): Map<Long, ThreadMeta> {
        val result = mutableMapOf<Long, ThreadMeta>()
        val unreadCounts = mutableMapOf<Long, Int>()
        val trashedIds = TrashStore.getTrashedIds(context)
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.BODY
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
            val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                if (cursor.getLong(idIdx) in trashedIds) continue

                val threadId = cursor.getLong(threadIdIdx)
                if (cursor.getInt(readIdx) == 0) {
                    unreadCounts[threadId] = (unreadCounts[threadId] ?: 0) + 1
                }
                // چون نزولیه، اولین باری که یه threadId رو می‌بینیم همون جدیدترین پیام غیر-سطل‌زباله‌ایشه
                if (!result.containsKey(threadId)) {
                    result[threadId] = ThreadMeta(
                        address = cursor.getStringOrNull(addressIdx) ?: "",
                        date = cursor.getLong(dateIdx),
                        unreadCount = 0, // بعداً از unreadCounts پر میشه
                        snippet = cursor.getStringOrNull(bodyIdx) ?: ""
                    )
                }
            }
        }
        return result.mapValues { (threadId, meta) -> meta.copy(unreadCount = unreadCounts[threadId] ?: 0) }
    }

    /**
     * خواندن همه پیام‌های یک مکالمه (thread) به ترتیب زمانی - پیام‌های توی سطل زباله
     * فیلتر میشن، همون‌طور که تو getConversations هم فیلتر میشن.
     */
    fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val trashedIds = TrashStore.getTrashedIds(context)
        val uri = Telephony.Sms.CONTENT_URI
        val selection = "${Telephony.Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())

        context.contentResolver.query(
            uri, null, selection, selectionArgs,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursorToMessage(cursor)
                if (message.id in trashedIds) continue
                messages.add(message)
            }
        }
        return messages
    }

    /**
     * ارسال پیامک - پیامک‌های بلند رو خودکار تقسیم می‌کنه
     * subscriptionId: برای گوشی‌های دو سیم‌کارت، مشخص می‌کنه از کدوم سیم ارسال بشه (null = پیش‌فرض سیستم)
     *
     * برای نمایش «تیک دلیوری» زیر پیام ارسالی، اول ردیف رو با STATUS_PENDING توی
     * sent box ذخیره می‌کنیم تا messageId رو داشته باشیم، بعد با همون id یه sentIntent
     * (برای فهمیدن اینکه خودِ ارسال موفق بوده یا نه - مثلاً آنتن نبودن/رادیو خاموش) و یه
     * deliveryIntent (برای فهمیدن اینکه گیرنده تحویل گرفته یا نه) به SmsStatusReceiver می‌سازیم.
     *
     * نکته: برای پیامک چندپارتی، فقط به آخرین پارت PendingIntent می‌دیم (بقیه null)،
     * وگرنه به‌ازای هر پارت یه گزارش جدا می‌رسید و هم وضعیت چندبار آپدیت می‌شد هم
     * نوتیف «تحویل داده شد» چندبار نشون داده می‌شد.
     */
    fun sendSms(address: String, body: String, subscriptionId: Int? = null) {
        if (!requireDefaultSmsApp("ارسال پیامک")) return

        val smsManager: SmsManager = if (subscriptionId != null && subscriptionId != -1) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(body)

        // ذخیره توی sent box (اپ پیش‌فرض پیامک خودش مسئول این کاره - سیستم دیگه این کار رو خودکار انجام نمیده)
        // قبل از ارسال، تا id رو داشته باشیم
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.DATE_SENT, now)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
        }
        val insertedUri = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        val messageId = insertedUri?.let { ContentUris.parseId(it) }

        var sentIntents: ArrayList<PendingIntent?>? = null
        var deliveryIntents: ArrayList<PendingIntent?>? = null

        if (messageId != null) {
            val sentPending = PendingIntent.getBroadcast(
                context, messageId.toInt(),
                Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    data = Uri.parse("smsapp://sent/$messageId")
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val deliveryPending = PendingIntent.getBroadcast(
                context, messageId.toInt(),
                Intent(context, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                    data = Uri.parse("smsapp://delivery/$messageId")
                    putExtra(SmsStatusReceiver.EXTRA_MESSAGE_ID, messageId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // فقط آخرین پارت PendingIntent می‌گیره، بقیه null
            sentIntents = ArrayList<PendingIntent?>(parts.size).apply {
                repeat(parts.size - 1) { add(null) }
                add(sentPending)
            }
            deliveryIntents = ArrayList<PendingIntent?>(parts.size).apply {
                repeat(parts.size - 1) { add(null) }
                add(deliveryPending)
            }
        }

        smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveryIntents)
    }

    /**
     * وقتی گزارش دلیوری از SmsStatusReceiver می‌رسه، وضعیت همون ردیف رو آپدیت می‌کنیم
     * تا توی UI تیک دلیوری نشون داده بشه. زمان دقیق تحویل جدا توی DeliveryStore ذخیره میشه
     * چون Sms provider ستونی برای اون نداره.
     */
    fun updateDeliveryStatus(messageId: Long, delivered: Boolean, deliveredAtMillis: Long) {
        if (!requireDefaultSmsApp("آپدیت وضعیت دلیوری")) return
        val values = ContentValues().apply {
            put(Telephony.Sms.STATUS, if (delivered) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED)
        }
        context.contentResolver.update(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
            values, null, null
        )
        if (delivered) {
            DeliveryStore.setDeliveredAt(context, messageId, deliveredAtMillis)
        }
    }

    /**
     * حذف کل مکالمه (اگه لازم بشه - فعلاً جایی صداش نمی‌زنیم چون خیلی مخرب بود برای اکشن نوتیف)
     */
    fun deleteThread(threadId: Long) {
        if (!requireDefaultSmsApp("حذف مکالمه")) return
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString())
        )
    }

    /**
     * حذف فقط یک پیام مشخص (برای اکشن «حذف» روی نوتیفیکیشن و منوی داخل مکالمه)
     *
     * اگه پیام فیوریت‌شده باشه، اصلاً حذف نمیشه (نقش قفل/لاک - طبق درخواست کاربر) و
     * false برگردونده میشه؛ برای برداشتن این قفل، اول باید از صفحه‌ی «علاقه‌مندی‌ها»
     * از فیوریت خارج بشه.
     *
     * اگه تیک «سطل زباله» توی تنظیمات فعال باشه، به‌جای حذف فیزیکی، پیام فقط با
     * TrashStore.moveToTrash مخفی میشه (خودِ ردیف دست‌نخورده می‌مونه) تا از صفحه‌ی
     * «سطل زباله» قابل ری‌استور باشه. اگه غیرفعال باشه، رفتار قبلی (حذف واقعی) ادامه داره.
     *
     * @return true اگه حذف/انتقال به سطل زباله انجام شد، false اگه به‌خاطر قفل‌بودن (فیوریت) رد شد
     */
    fun deleteMessage(messageId: Long): Boolean {
        if (!requireDefaultSmsApp("حذف پیام")) return false
        if (FavoriteStore.isFavorite(context, messageId)) {
            return false
        }
        if (AppSettings.isTrashEnabled(context)) {
            TrashStore.moveToTrash(context, messageId)
            return true
        }
        context.contentResolver.delete(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
            null,
            null
        )
        DeliveryStore.clear(context, messageId)
        return true
    }

    /**
     * خواندن همه‌ی پیام‌های توی سطل زباله، به‌همراه نام/شماره‌ی طرف مکالمه، مرتب‌شده
     * از جدیدترین‌حذف‌شده به قدیمی‌ترین. چون خودِ ردیف‌ها هنوز واقعاً توی Sms provider
     * هستن (TrashStore فقط مخفی‌شون می‌کنه)، مستقیم با _ID IN (...) می‌خونیمشون.
     */
    fun getTrashedMessages(): List<TrashedMessage> {
        val trashedIds = TrashStore.getTrashedIds(context)
        if (trashedIds.isEmpty()) return emptyList()

        val placeholders = trashedIds.joinToString(",") { "?" }
        val selection = "${Telephony.Sms._ID} IN ($placeholders)"
        val selectionArgs = trashedIds.map { it.toString() }.toTypedArray()

        val result = mutableListOf<TrashedMessage>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, null, selection, selectionArgs, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursorToMessage(cursor)
                val displayName = ContactsCache.getName(context, message.address) ?: message.address
                result.add(TrashedMessage(message, displayName, TrashStore.getTrashedAt(context, message.id)))
            }
        }
        return result.sortedByDescending { it.trashedAt }
    }

    /** بازگردوندن یه پیام از سطل زباله - چون ردیف اصلی همیشه واقعی بوده، فقط از ایندکس مخفی‌سازی درش میاریم */
    fun restoreFromTrash(messageId: Long) {
        TrashStore.restore(context, messageId)
    }

    /** حذف همیشگی از داخل خودِ صفحه‌ی سطل زباله - این دیگه واقعاً فیزیکیه و برگشت نداره */
    fun permanentlyDelete(messageId: Long): Boolean {
        if (!requireDefaultSmsApp("حذف همیشگی از سطل زباله")) return false
        context.contentResolver.delete(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
            null,
            null
        )
        DeliveryStore.clear(context, messageId)
        TrashStore.restore(context, messageId) // دیگه ردیفی نیست، ایندکس سطل زباله رو هم پاک کن
        return true
    }

    /**
     * پیدا کردن thread موجود برای یه شماره، یا ساختن یه thread جدید اگه وجود نداشته باشه
     * (لازم برای شروع مکالمه‌ی جدید از صفحه "پیام جدید")
     */
    fun getOrCreateThreadId(address: String): Long {
        return Telephony.Threads.getOrCreateThreadId(context, setOf(address))
    }

    fun markThreadAsRead(threadId: Long): Boolean {
        if (!requireDefaultSmsApp("علامت‌گذاری مکالمه به‌عنوان خونده‌شده")) return false
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        context.contentResolver.update(
            Telephony.Sms.CONTENT_URI, values,
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
            arrayOf(threadId.toString())
        )
        return true
    }

    private fun cursorToMessage(cursor: Cursor): SmsMessage {
        fun col(name: String) = cursor.getColumnIndex(name)
        val type = cursor.getInt(col(Telephony.Sms.TYPE))
        val dateSentIdx = col(Telephony.Sms.DATE_SENT)
        val statusIdx = col(Telephony.Sms.STATUS)
        val id = cursor.getLong(col(Telephony.Sms._ID))
        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
        return SmsMessage(
            id = id,
            threadId = cursor.getLong(col(Telephony.Sms.THREAD_ID)),
            address = cursor.getStringOrNull(col(Telephony.Sms.ADDRESS)) ?: "",
            body = cursor.getStringOrNull(col(Telephony.Sms.BODY)) ?: "",
            date = cursor.getLong(col(Telephony.Sms.DATE)),
            dateSent = if (dateSentIdx >= 0) cursor.getLong(dateSentIdx) else 0L,
            type = type,
            isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX,
            isRead = cursor.getInt(col(Telephony.Sms.READ)) == 1,
            status = status,
            deliveredAt = if (status == Telephony.Sms.STATUS_COMPLETE) DeliveryStore.getDeliveredAt(context, id) else 0L
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        if (index < 0) return null
        return if (isNull(index)) null else getString(index)
    }
}
