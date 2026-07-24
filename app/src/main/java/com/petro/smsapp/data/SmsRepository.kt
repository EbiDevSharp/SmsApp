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
     * لایه‌ی دفاعی برای عملیات‌های خواندن. اپ می‌تونه پیش‌فرض پیامک باشه ولی کاربر بعداً
     * از تنظیمات سیستم مجوز READ_SMS رو دستی برداشته باشه - در اون حالت
     * requireDefaultSmsApp چیزی نمی‌گیره چون همچنان پیش‌فرضیم، ولی contentResolver.query
     * با SecurityException کرش می‌کنه. این تابع قبل از هر خوندن صدا زده میشه؛ اگه مجوز
     * نبود، به‌جای کرش، فقط لاگ می‌کنیم و لیست خالی برمی‌گردونیم.
     */
    private fun requireReadSmsPermission(operation: String): Boolean {
        val hasPermission = PermissionHelper.hasReadSmsPermission(context)
        if (!hasPermission) {
            Log.w("SmsRepository", "عملیات «$operation» انجام نشد چون مجوز READ_SMS نیست")
        }
        return hasPermission
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
        if (!requireReadSmsPermission("خواندن لیست مکالمات")) return emptyList()
        val threadMeta = getAllThreadsMeta()
        val drafts = getAllDrafts()

        // اتحاد threadId هایی که پیام واقعی دارن + threadId هایی که فقط پیش‌نویس دارن
        // (مثلاً مکالمه‌ی جدیدی که کاربر شروع کرده ولی هنوز چیزی نفرستاده)
        val allThreadIds = threadMeta.keys + drafts.keys

        val conversations = allThreadIds.mapNotNull { threadId ->
            val meta = threadMeta[threadId]
            val draft = drafts[threadId]

            // آدرس رو ترجیحاً از آخرین پیام واقعی می‌گیریم؛ اگه thread فقط پیش‌نویس داره،
            // از آدرس خودِ ردیف پیش‌نویس استفاده می‌کنیم. اگه هیچ‌کدوم آدرس معتبر نداشتن
            // (مثلاً پیش‌نویسِ بی‌آدرس - همون موردی که قبلاً کرش می‌کرد)، این thread رو
            // اصلاً نشون نمی‌دیم؛ چون بدون آدرس نه می‌شه مخاطب رو شناخت نه می‌شه وارد چتش شد.
            val address = meta?.address?.takeIf { it.isNotBlank() }
                ?: draft?.address?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            if (BlockStore.isAddressBlocked(context, address) || PrivateStore.isAddressPrivate(context, address)) {
                return@mapNotNull null
            }

            val displayName = ContactsCache.getName(context, address) ?: address

            // پیش‌نویس فقط وقتی روی snippet/isDraft تأثیر می‌ذاره که واقعاً از آخرین پیامِ
            // واقعیِ این thread جدیدتر باشه - وگرنه (مثلاً یه پیش‌نویسِ قدیمیِ دست‌نخورده که
            // بعدش یه پیام واقعیِ جدیدتر روی همون thread اومده) باید متن/رنگِ همون پیامِ واقعیِ
            // جدیدتر نشون داده بشه، نه پیش‌نویسِ کهنه. قبلاً همیشه (فقط بر اساس وجودِ پیش‌نویس،
            // بدون مقایسه‌ی تاریخ) پیش‌نویس رو نشون می‌داد که همون چیزیه که کاربر بهش می‌گفت
            // «پیام درفت باید بر اساس تاریخ باشه».
            val draftIsNewer = draft != null && draft.date >= (meta?.date ?: 0L)

            Conversation(
                threadId = threadId,
                address = address,
                displayName = displayName,
                snippet = if (draftIsNewer) "پیش‌نویس: ${draft!!.body}" else (meta?.snippet ?: ""),
                date = maxOf(meta?.date ?: 0L, draft?.date ?: 0L),
                unreadCount = meta?.unreadCount ?: 0,
                isDraft = draftIsNewer
            )
        }
        return conversations.sortedByDescending { it.date }
    }

    private data class ThreadMeta(val address: String, val date: Long, val unreadCount: Int, val snippet: String)
    private data class DraftMeta(val address: String, val body: String, val date: Long)

    /**
     * یه پاس روی کل جدول sms (مرتب‌شده بر اساس DATE نزولی) تا برای هر thread، آخرین آدرس/
     * تاریخ/متن (اولین ردیفی که برای اون thread می‌بینیم چون نزولیه) و تعداد پیام نخونده رو
     * بسازیم. پیام‌های توی سطل زباله (TrashStore) کاملاً نادیده گرفته میشن - نه تو محاسبه‌ی
     * جدیدترین پیام دخیلن، نه تو شمارش نخونده‌ها؛ اگه یه thread فقط پیام سطل‌زباله‌ای داشته
     * باشه، اصلاً توی نتیجه نمیاد (یعنی از لیست مکالمات ناپدید میشه، درست مثل حذف واقعی).
     *
     * پیام‌های نوع DRAFT هم اینجا کاملاً نادیده گرفته میشن - قبلاً نادیده گرفته نمی‌شدن و همین
     * باعث می‌شد اگه آخرین ردیفِ یه thread پیش‌نویس بود، متنش به‌جای آخرین پیامِ واقعی نشون داده
     * بشه (و چون ADDRESS پیش‌نویس گاهی خالیه، مکالمه «ناشناس» و باز کردنش کرش می‌کرد). پیش‌نویس‌ها
     * الان جدا توی getAllDrafts مدیریت میشن.
     */
    private fun getAllThreadsMeta(): Map<Long, ThreadMeta> {
        val result = mutableMapOf<Long, ThreadMeta>()
        val unreadCounts = mutableMapOf<Long, Int>()
        val trashedIds = TrashStore.getTrashedIds(context)
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.BODY, Telephony.Sms.TYPE
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
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)
                while (cursor.moveToNext()) {
                    if (cursor.getLong(idIdx) in trashedIds) continue
                    if (typeIdx >= 0 && cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue

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
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن لیست مکالمات - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyMap()
        }
        return result.mapValues { (threadId, meta) -> meta.copy(unreadCount = unreadCounts[threadId] ?: 0) }
    }

    /**
     * پیش‌نویس‌ها (Telephony.Sms.MESSAGE_TYPE_DRAFT) جدا و مستقل از پیام‌های واقعی خونده میشن -
     * برای هر thread حداکثر یه پیش‌نویس نگه می‌داریم (جدیدترینش، اگه چندتا بود).
     */
    private fun getAllDrafts(): Map<Long, DraftMeta> {
        val result = mutableMapOf<Long, DraftMeta>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.TYPE} = ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_DRAFT.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val threadIdIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIdx)
                    val body = cursor.getStringOrNull(bodyIdx) ?: ""
                    if (body.isBlank()) continue // پیش‌نویس خالی، چیزی برای نشون‌دادن نیست
                    if (!result.containsKey(threadId)) { // نزولیه، اولین‌بار = جدیدترین
                        result[threadId] = DraftMeta(
                            address = cursor.getStringOrNull(addressIdx) ?: "",
                            body = body,
                            date = cursor.getLong(dateIdx)
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیش‌نویس‌ها", e)
            return emptyMap()
        }
        return result
    }

    /** خواندن متن پیش‌نویسِ یک thread مشخص - برای پرکردن خودکار کادر متن وقتی وارد چتش میشیم */
    fun getDraftText(threadId: Long): String {
        if (!requireReadSmsPermission("خواندن پیش‌نویس")) return ""
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_DRAFT.toString()),
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                if (cursor.moveToFirst()) return cursor.getStringOrNull(bodyIdx) ?: ""
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیش‌نویسِ یک مکالمه", e)
        }
        return ""
    }

    /**
     * ذخیره/آپدیت/حذفِ پیش‌نویسِ یک thread، دقیقاً هماهنگ با رفتار اپ‌های پیامک استاندارد:
     * - اگه body خالی باشه، هر پیش‌نویس قبلی این thread پاک میشه (چیزی برای نگه‌داشتن نیست).
     * - اگه body چیزی داشته باشه، اول پیش‌نویس(های) قبلیِ همین thread پاک میشن (تا تکراری نشه)
     *   بعد یه ردیف DRAFT جدید با متن تازه درج میشه.
     */
    fun saveDraft(threadId: Long, address: String, body: String) {
        if (!requireDefaultSmsApp("ذخیره‌ی پیش‌نویس")) return
        try {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_DRAFT.toString())
            )
            if (body.isNotBlank()) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.THREAD_ID, threadId)
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_DRAFT)
                    put(Telephony.Sms.READ, 1)
                }
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع ذخیره‌ی پیش‌نویس", e)
        }
    }

    /**
     * خواندن همه پیام‌های یک مکالمه (thread) به ترتیب زمانی - پیام‌های توی سطل زباله
     * فیلتر میشن، همون‌طور که تو getConversations هم فیلتر میشن.
     */
    fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیام‌های یک مکالمه")) return emptyList()
        val messages = mutableListOf<SmsMessage>()
        val trashedIds = TrashStore.getTrashedIds(context)
        val uri = Telephony.Sms.CONTENT_URI
        // پیش‌نویس رو از لیست پیام‌های واقعی مکالمه کنار می‌ذاریم - پیش‌نویس حباب چت نیست،
        // فقط توی کادر متنِ پایین صفحه (از طریق getDraftText) برمی‌گرده
        val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} != ?"
        val selectionArgs = arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_DRAFT.toString())

        try {
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
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیام‌های مکالمه - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyList()
        }
        return messages
    }

    /**
     * همه‌ی پیام‌های همه‌ی شماره‌های بلاک‌شده رو با هم برمی‌گردونه (جدیدترین بالا)، مستقیم
     * بر اساس آدرس (نه threadId) - چون اگه یه thread خالی بشه (همه‌ی پیام‌هاش حذف/سطل‌زباله
     * بشن)، اندروید ممکنه اون threadId رو دیگه نگه نداره یا برای پیام بعدی یه threadId
     * جدید بسازه؛ ولی آدرس همیشه همون آدرسه.
     */
    fun getMessagesForBlockedThreads(): List<BlockedMessageEntry> {
        val blockedNumbers = BlockStore.getAllBlockedNumbers(context)
        if (blockedNumbers.isEmpty()) return emptyList()
        return getMessagesByAddresses(blockedNumbers.map { it.address })
            .map { message ->
                val name = blockedNumbers.find { it.address == message.address }?.displayName ?: message.address
                BlockedMessageEntry(message, name)
            }
    }

    /**
     * همه‌ی پیام‌های همه‌ی شماره‌های خصوصی‌شده رو با هم برمی‌گردونه (جدیدترین بالا)، مستقیم
     * بر اساس آدرس - برای صفحه‌ی «پیامک‌های خصوصی» (پشتِ رمز ۴ رقمی).
     */
    fun getMessagesForPrivateThreads(): List<PrivateMessageEntry> {
        val privateNumbers = PrivateStore.getAllPrivateNumbers(context)
        if (privateNumbers.isEmpty()) return emptyList()
        return getMessagesByAddresses(privateNumbers.map { it.address })
            .map { message ->
                val name = privateNumbers.find { it.address == message.address }?.displayName ?: message.address
                PrivateMessageEntry(message, name)
            }
    }

    /** خوندن مستقیم پیام‌ها بر اساس لیست آدرس (نه threadId) - سطل‌زباله همچنان فیلتر میشه */
    private fun getMessagesByAddresses(addresses: List<String>): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیامک‌های بلاک/خصوصی‌شده")) return emptyList()
        if (addresses.isEmpty()) return emptyList()
        val trashedIds = TrashStore.getTrashedIds(context)
        val messages = mutableListOf<SmsMessage>()
        val selection = addresses.joinToString(" OR ") { "${Telephony.Sms.ADDRESS} = ?" }
        val selectionArgs = addresses.toTypedArray()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, null, selection, selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val message = cursorToMessage(cursor)
                    if (message.id in trashedIds) continue
                    messages.add(message)
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیامک‌های بلاک/خصوصی‌شده", e)
            return emptyList()
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
            // ذخیره‌ی سیمِ استفاده‌شده روی خودِ ردیف - برای این‌که «ارسال دوباره»ی یه پیامِ
            // ناموفق بتونه دقیقاً از همون سیم دوباره امتحان کنه، نه سیمِ پیش‌فرضِ سیستم
            if (subscriptionId != null && subscriptionId != -1) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        val insertedUri = try {
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع ذخیره‌ی پیام ارسالی توی sent box", e)
            null
        }
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
        try {
            context.contentResolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                values, null, null
            )
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع آپدیت وضعیت دلیوری", e)
            return
        }
        if (delivered) {
            DeliveryStore.setDeliveredAt(context, messageId, deliveredAtMillis)
        }
    }

    /**
     * حذف کل مکالمه (اگه لازم بشه - فعلاً جایی صداش نمی‌زنیم چون خیلی مخرب بود برای اکشن نوتیف)
     */
    fun deleteThread(threadId: Long) {
        if (!requireDefaultSmsApp("حذف مکالمه")) return
        try {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع حذف مکالمه", e)
        }
    }

    /**
     * حذف دسته‌جمعی چند مکالمه با هم - برای حالت «انتخاب چندتایی» توی صفحه‌ی اصلی لیست پیام‌ها.
     *
     * دقیقاً همون قانونِ حذف تکی (deleteMessage) رو برای تک‌تکِ پیام‌های هر مکالمه‌ی انتخاب‌شده
     * رعایت می‌کنه: پیام‌های فیوریت‌شده قفلن و دست‌نخورده می‌مونن (شمارش میشن تا بعداً به کاربر
     * اطلاع داده بشه)، و بسته به تنظیم «سطل زباله»، بقیه‌ی پیام‌ها یا مخفی/قابل‌بازیابی میشن یا
     * فیزیکی حذف. اگه یه مکالمه فقط پیام فیوریت داشته باشه، عملاً هیچی ازش حذف نمیشه و توی
     * لیست باقی می‌مونه - دقیقاً همون رفتار محافظتی‌ای که برای حذف تکی هم داریم.
     */
    fun deleteThreads(threadIds: Set<Long>): BulkDeleteResult {
        if (!requireDefaultSmsApp("حذف دسته‌جمعی مکالمه‌ها")) {
            return BulkDeleteResult(movedToTrash = false, blockedFavoriteCount = 0)
        }
        val trashEnabled = AppSettings.isTrashEnabled(context)
        var blockedCount = 0
        threadIds.forEach { threadId ->
            getMessageIdsForThread(threadId).forEach { messageId ->
                if (FavoriteStore.isFavorite(context, messageId)) {
                    blockedCount++
                    return@forEach
                }
                if (trashEnabled) {
                    TrashStore.moveToTrash(context, messageId)
                } else {
                    try {
                        context.contentResolver.delete(
                            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                            null,
                            null
                        )
                        DeliveryStore.clear(context, messageId)
                    } catch (e: SecurityException) {
                        Log.w("SmsRepository", "SecurityException موقع حذف دسته‌جمعی مکالمه‌ها", e)
                    }
                }
            }
        }
        return BulkDeleteResult(movedToTrash = trashEnabled, blockedFavoriteCount = blockedCount)
    }

    private fun getMessageIdsForThread(threadId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                while (cursor.moveToNext()) ids.add(cursor.getLong(idIdx))
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن id های یک مکالمه", e)
        }
        return ids
    }

    /**
     * حذف دسته‌جمعی چند پیامِ تکی از داخل یه مکالمه (برای حالت «انتخاب چندتایی» داخل چت،
     * برخلاف deleteThreads که کل مکالمه رو حذف می‌کنه). همون قانون‌های deleteMessage
     * (قفل فیوریت + احترام به تنظیم سطل زباله) برای تک‌تک پیام‌ها رعایت میشه.
     */
    fun deleteMessages(messageIds: Set<Long>): BulkDeleteResult {
        if (!requireDefaultSmsApp("حذف دسته‌جمعی پیام‌ها")) {
            return BulkDeleteResult(movedToTrash = false, blockedFavoriteCount = 0)
        }
        val trashEnabled = AppSettings.isTrashEnabled(context)
        var blockedCount = 0
        messageIds.forEach { messageId ->
            if (FavoriteStore.isFavorite(context, messageId)) {
                blockedCount++
                return@forEach
            }
            if (trashEnabled) {
                TrashStore.moveToTrash(context, messageId)
            } else {
                try {
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                        null,
                        null
                    )
                    DeliveryStore.clear(context, messageId)
                } catch (e: SecurityException) {
                    Log.w("SmsRepository", "SecurityException موقع حذف دسته‌جمعی پیام‌ها", e)
                }
            }
        }
        return BulkDeleteResult(movedToTrash = trashEnabled, blockedFavoriteCount = blockedCount)
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
        try {
            context.contentResolver.delete(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                null,
                null
            )
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع حذف پیام", e)
            return false
        }
        DeliveryStore.clear(context, messageId)
        return true
    }

    /**
     * خواندن همه‌ی پیام‌های توی سطل زباله، به‌همراه نام/شماره‌ی طرف مکالمه، مرتب‌شده
     * از جدیدترین‌حذف‌شده به قدیمی‌ترین. چون خودِ ردیف‌ها هنوز واقعاً توی Sms provider
     * هستن (TrashStore فقط مخفی‌شون می‌کنه)، مستقیم با _ID IN (...) می‌خونیمشون.
     */
    fun getTrashedMessages(): List<TrashedMessage> {
        if (!requireReadSmsPermission("خواندن سطل زباله")) return emptyList()
        val trashedIds = TrashStore.getTrashedIds(context)
        if (trashedIds.isEmpty()) return emptyList()

        val placeholders = trashedIds.joinToString(",") { "?" }
        val selection = "${Telephony.Sms._ID} IN ($placeholders)"
        val selectionArgs = trashedIds.map { it.toString() }.toTypedArray()

        val result = mutableListOf<TrashedMessage>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, null, selection, selectionArgs, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val message = cursorToMessage(cursor)
                    val displayName = ContactsCache.getName(context, message.address) ?: message.address
                    result.add(TrashedMessage(message, displayName, TrashStore.getTrashedAt(context, message.id)))
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن سطل زباله - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyList()
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
        try {
            context.contentResolver.delete(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                null,
                null
            )
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع حذف همیشگی از سطل زباله", e)
            return false
        }
        DeliveryStore.clear(context, messageId)
        TrashStore.restore(context, messageId) // دیگه ردیفی نیست، ایندکس سطل زباله رو هم پاک کن
        return true
    }

    /**
     * پیدا کردن thread موجود برای یه شماره، یا ساختن یه thread جدید اگه وجود نداشته باشه
     * (لازم برای شروع مکالمه‌ی جدید از صفحه "پیام جدید")
     */
    /** try/catch به‌جای پیش‌چک، چون این تابع از جریان «پیام جدید» صدا زده میشه و بهتره
     *  به‌جای خالی برگردوندن، صفر برگردونه (thread نامعتبر) اگه مجوزی نبود */
    fun getOrCreateThreadId(address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, setOf(address))
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع ساخت/پیدا کردن threadId", e)
            0L
        }
    }

    fun markThreadAsRead(threadId: Long): Boolean {
        if (!requireDefaultSmsApp("علامت‌گذاری مکالمه به‌عنوان خونده‌شده")) return false
        val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
        try {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خونده‌شده‌کردن مکالمه", e)
            return false
        }
        return true
    }

    private fun cursorToMessage(cursor: Cursor): SmsMessage {
        fun col(name: String) = cursor.getColumnIndex(name)
        val type = cursor.getInt(col(Telephony.Sms.TYPE))
        val dateSentIdx = col(Telephony.Sms.DATE_SENT)
        val statusIdx = col(Telephony.Sms.STATUS)
        val subIdIdx = col(Telephony.Sms.SUBSCRIPTION_ID)
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
            isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                    type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
                    type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
            isRead = cursor.getInt(col(Telephony.Sms.READ)) == 1,
            status = status,
            deliveredAt = if (status == Telephony.Sms.STATUS_COMPLETE) DeliveryStore.getDeliveredAt(context, id) else 0L,
            subscriptionId = if (subIdIdx >= 0) cursor.getInt(subIdIdx) else -1
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        if (index < 0) return null
        return if (isNull(index)) null else getString(index)
    }
}