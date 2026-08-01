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
import com.petro.smsapp.data.repository.BlockRepository
import com.petro.smsapp.data.repository.DeliveryRepository
import com.petro.smsapp.data.repository.PinRepository
import com.petro.smsapp.data.repository.PrivateRepository
import com.petro.smsapp.data.repository.TrashRepository
import com.petro.smsapp.receiver.SmsStatusReceiver

/**
 * لایه‌ی خواندن/نوشتنِ Telephony.Sms Provider - این پروایدر خودِ اندروید همیشه source
 * of truth می‌مونه (نمی‌شه به Room منتقلش کرد)، ولی همه‌ی متادیتای مربوط به بلاک/
 * خصوصی/سطل‌زباله/پین/دلیوری که قبلاً SharedPreferences بودن، الان از طریق
 * Repositoryهای تزریق‌شده (روی Room) میان - بدون هیچ کش میانیِ اضافه.
 *
 * همه‌ی متدهای این کلاس suspend شدن (قبلاً synchronous بودن چون SharedPreferences
 * synchronous بود) - چون همه‌جا همین الان هم از داخل withContext(Dispatchers.IO)
 * صدا زده میشن (توی SmsViewModel)، این تغییر امن و بدون بلاک‌کردن Main Thread ئه.
 */
class SmsRepository(
    private val context: Context,
    private val blockRepository: BlockRepository = AppContainer.blockRepository(context),
    private val privateRepository: PrivateRepository = AppContainer.privateRepository(context),
    private val trashRepository: TrashRepository = AppContainer.trashRepository(context),
    private val pinRepository: PinRepository = AppContainer.pinRepository(context),
    private val deliveryRepository: DeliveryRepository = AppContainer.deliveryRepository(context)
) {

    private fun requireDefaultSmsApp(operation: String): Boolean {
        val isDefault = DefaultSmsAppHelper.isDefaultSmsApp(context)
        if (!isDefault) {
            Log.w("SmsRepository", "عملیات «$operation» انجام نشد چون اپ در حال حاضر پیش‌فرض پیامک نیست")
        }
        return isDefault
    }

    private fun requireReadSmsPermission(operation: String): Boolean {
        val hasPermission = PermissionHelper.hasReadSmsPermission(context)
        if (!hasPermission) {
            Log.w("SmsRepository", "عملیات «$operation» انجام نشد چون مجوز READ_SMS نیست")
        }
        return hasPermission
    }

    suspend fun getConversations(): List<Conversation> {
        if (!requireReadSmsPermission("خواندن لیست مکالمات")) return emptyList()
        val threadMeta = getAllThreadsMeta()
        val drafts = getAllDrafts()

        val allThreadIds = threadMeta.keys + drafts.keys

        val conversations = allThreadIds.mapNotNull { threadId ->
            val meta = threadMeta[threadId]
            val draft = drafts[threadId]

            val address = meta?.address?.takeIf { it.isNotBlank() }
                ?: draft?.address?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            if (privateRepository.isAddressPrivate(address)) {
                return@mapNotNull null
            }
            if (!AppSettings.isShowBlockedInMessageListEnabled(context) && blockRepository.isAddressBlocked(address)) {
                return@mapNotNull null
            }

            val displayName = ContactsCache.getName(context, address) ?: address

            val draftIsNewer = draft != null && draft.date >= (meta?.date ?: 0L)

            Conversation(
                threadId = threadId,
                address = address,
                displayName = displayName,
                snippet = if (draftIsNewer) "پیش‌نویس: ${draft!!.body}" else (meta?.snippet ?: ""),
                date = maxOf(meta?.date ?: 0L, draft?.date ?: 0L),
                unreadCount = meta?.unreadCount ?: 0,
                isDraft = draftIsNewer,
                isPinned = pinRepository.isThreadPinned(threadId),
                messageCount = meta?.messageCount ?: 0
            )
        }
        val pinnedAtByThread = conversations.filter { it.isPinned }
            .associate { it.threadId to pinRepository.getPinnedAt(it.threadId) }

        return conversations.sortedWith(
            compareByDescending<Conversation> { it.isPinned }
                .thenByDescending { pinnedAtByThread[it.threadId] ?: 0L }
                .thenByDescending { it.date }
        )
    }

    private data class ThreadMeta(val address: String, val date: Long, val unreadCount: Int, val messageCount: Int, val snippet: String)
    private data class DraftMeta(val address: String, val body: String, val date: Long)

    private suspend fun getAllThreadsMeta(): Map<Long, ThreadMeta> {
        val result = mutableMapOf<Long, ThreadMeta>()
        val unreadCounts = mutableMapOf<Long, Int>()
        // تعدادِ کلِ پیام‌های هر thread - برای گزینه‌ی مرتب‌سازیِ «پرپیام‌ترین اول».
        // توی همین یه کوئریِ فعلی (که هرحال همه‌ی ردیف‌ها رو یه‌بار می‌خونه) شمرده
        // میشه، بدونِ نیاز به هیچ کوئریِ اضافه‌ی جدا.
        val messageCounts = mutableMapOf<Long, Int>()
        val trashedIds = trashRepository.getTrashedIds()
        val showBlockedInList = AppSettings.isShowBlockedInMessageListEnabled(context)
        val keywordBlockedIds = if (showBlockedInList) emptySet() else blockRepository.getKeywordBlockedMessageIds()
        val patternBlockedIds = if (showBlockedInList) emptySet() else blockRepository.getPatternBlockedMessageIds()
        val nonContactBlockedIds = if (showBlockedInList) emptySet() else blockRepository.getNonContactBlockedMessageIds()
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
                    if (cursor.getLong(idIdx) in keywordBlockedIds) continue
                    if (cursor.getLong(idIdx) in patternBlockedIds) continue
                    if (cursor.getLong(idIdx) in nonContactBlockedIds) continue
                    if (typeIdx >= 0 && cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue

                    val threadId = cursor.getLong(threadIdIdx)
                    messageCounts[threadId] = (messageCounts[threadId] ?: 0) + 1
                    if (cursor.getInt(readIdx) == 0) {
                        unreadCounts[threadId] = (unreadCounts[threadId] ?: 0) + 1
                    }
                    if (!result.containsKey(threadId)) {
                        result[threadId] = ThreadMeta(
                            address = cursor.getStringOrNull(addressIdx) ?: "",
                            date = cursor.getLong(dateIdx),
                            unreadCount = 0,
                            messageCount = 0,
                            snippet = cursor.getStringOrNull(bodyIdx) ?: ""
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن لیست مکالمات - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyMap()
        }
        return result.mapValues { (threadId, meta) ->
            meta.copy(unreadCount = unreadCounts[threadId] ?: 0, messageCount = messageCounts[threadId] ?: 0)
        }
    }

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
                    if (body.isBlank()) continue
                    if (!result.containsKey(threadId)) {
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

    suspend fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیام‌های یک مکالمه")) return emptyList()
        val messages = mutableListOf<SmsMessage>()
        val trashedIds = trashRepository.getTrashedIds()
        val showBlockedInList = AppSettings.isShowBlockedInMessageListEnabled(context)
        val keywordBlockedIds = if (showBlockedInList) emptySet() else blockRepository.getKeywordBlockedMessageIds()
        val patternBlockedIds = if (showBlockedInList) emptySet() else blockRepository.getPatternBlockedMessageIds()
        val nonContactBlockedIds = if (showBlockedInList) emptySet() else blockRepository.getNonContactBlockedMessageIds()
        val uri = Telephony.Sms.CONTENT_URI
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
                    if (message.id in keywordBlockedIds) continue
                    if (message.id in patternBlockedIds) continue
                    if (message.id in nonContactBlockedIds) continue
                    messages.add(message)
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیام‌های مکالمه - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyList()
        }
        return messages
    }

    suspend fun getMessagesForBlockedThreads(): List<BlockedMessageEntry> {
        val blockedNumbers = blockRepository.getAllBlockedNumbersOnce()
        val phoneBlockedEntries = if (blockedNumbers.isEmpty()) {
            emptyList()
        } else {
            getMessagesByAddresses(blockedNumbers.map { it.address })
                .map { message ->
                    val name = blockedNumbers.find { it.address == message.address }?.displayName ?: message.address
                    BlockedMessageEntry(message, name, BlockSource.PHONE_NUMBER)
                }
        }

        val keywordBlockedIds = blockRepository.getKeywordBlockedMessageIds()
        val keywordBlockedEntries = if (keywordBlockedIds.isEmpty()) {
            emptyList()
        } else {
            getMessagesByIds(keywordBlockedIds)
                .filter { message -> blockedNumbers.none { it.address == message.address } }
                .map { message ->
                    val name = ContactsCache.getName(context, message.address) ?: message.address
                    val keyword = blockRepository.getMatchedKeyword(message.id)
                    BlockedMessageEntry(message, name, BlockSource.KEYWORD, keyword)
                }
        }

        val patternBlockedIds = blockRepository.getPatternBlockedMessageIds()
        val patternBlockedEntries = if (patternBlockedIds.isEmpty()) {
            emptyList()
        } else {
            getMessagesByIds(patternBlockedIds)
                .filter { message -> blockedNumbers.none { it.address == message.address } }
                .map { message ->
                    val name = ContactsCache.getName(context, message.address) ?: message.address
                    val matched = blockRepository.getMatchedPattern(message.id)
                    BlockedMessageEntry(
                        message, name, BlockSource.PATTERN,
                        matchedPatternType = matched?.type,
                        matchedPatternValue = matched?.value
                    )
                }
        }

        val nonContactBlockedIds = blockRepository.getNonContactBlockedMessageIds()
        val nonContactBlockedEntries = if (nonContactBlockedIds.isEmpty()) {
            emptyList()
        } else {
            getMessagesByIds(nonContactBlockedIds)
                .filter { message -> blockedNumbers.none { it.address == message.address } }
                .map { message ->
                    val name = ContactsCache.getName(context, message.address) ?: message.address
                    BlockedMessageEntry(message, name, BlockSource.NOT_IN_CONTACTS)
                }
        }

        return (phoneBlockedEntries + keywordBlockedEntries + patternBlockedEntries + nonContactBlockedEntries).sortedByDescending { it.message.date }
    }

    private suspend fun getMessagesByIds(ids: Set<Long>): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیامک‌های بلاک‌شده بر اساس کلمه")) return emptyList()
        if (ids.isEmpty()) return emptyList()
        val trashedIds = trashRepository.getTrashedIds()
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${Telephony.Sms._ID} IN ($placeholders)"
        val selectionArgs = ids.map { it.toString() }.toTypedArray()

        val messages = mutableListOf<SmsMessage>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, null, selection, selectionArgs, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val message = cursorToMessage(cursor)
                    if (message.id in trashedIds) continue
                    messages.add(message)
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیامک‌های بلاک‌شده بر اساس کلمه", e)
            return emptyList()
        }
        return messages
    }

    suspend fun getMessagesForPrivateThreads(): List<PrivateMessageEntry> {
        val privateNumbers = privateRepository.getAllPrivateNumbersOnce()
        if (privateNumbers.isEmpty()) return emptyList()
        return getMessagesByAddresses(privateNumbers.map { it.address })
            .map { message ->
                val name = privateNumbers.find { it.address == message.address }?.displayName ?: message.address
                PrivateMessageEntry(message, name)
            }
    }

    private suspend fun getMessagesByAddresses(addresses: List<String>): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیامک‌های بلاک/خصوصی‌شده")) return emptyList()
        if (addresses.isEmpty()) return emptyList()
        val trashedIds = trashRepository.getTrashedIds()
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

    fun sendSms(address: String, body: String, subscriptionId: Int? = null) {
        if (!requireDefaultSmsApp("ارسال پیامک")) return

        val smsManager: SmsManager = if (subscriptionId != null && subscriptionId != -1) {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        } else {
            context.getSystemService(SmsManager::class.java)
                ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val parts = smsManager.divideMessage(body)

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, now)
            put(Telephony.Sms.DATE_SENT, now)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
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

    suspend fun updateDeliveryStatus(messageId: Long, delivered: Boolean, deliveredAtMillis: Long) {
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
            deliveryRepository.setDeliveredAt(messageId, deliveredAtMillis)
        }
    }

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

    suspend fun deleteThreads(threadIds: Set<Long>): BulkDeleteResult {
        if (!requireDefaultSmsApp("حذف دسته‌جمعی مکالمه‌ها")) {
            return BulkDeleteResult(movedToTrash = false, blockedFavoriteCount = 0)
        }
        val trashEnabled = AppSettings.isTrashEnabled(context)
        var blockedCount = 0
        val favoriteRepository = AppContainer.favoriteRepository(context)
        threadIds.forEach { threadId ->
            pinRepository.unpinThread(threadId)
            getMessageIdsForThread(threadId).forEach { messageId ->
                if (favoriteRepository.isFavorite(messageId)) {
                    blockedCount++
                    return@forEach
                }
                if (trashEnabled) {
                    trashRepository.moveToTrash(messageId)
                } else {
                    try {
                        context.contentResolver.delete(
                            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                            null,
                            null
                        )
                        deliveryRepository.clear(messageId)
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

    suspend fun deleteMessages(messageIds: Set<Long>): BulkDeleteResult {
        if (!requireDefaultSmsApp("حذف دسته‌جمعی پیام‌ها")) {
            return BulkDeleteResult(movedToTrash = false, blockedFavoriteCount = 0)
        }
        val trashEnabled = AppSettings.isTrashEnabled(context)
        var blockedCount = 0
        val favoriteRepository = AppContainer.favoriteRepository(context)
        messageIds.forEach { messageId ->
            if (favoriteRepository.isFavorite(messageId)) {
                blockedCount++
                return@forEach
            }
            if (trashEnabled) {
                trashRepository.moveToTrash(messageId)
            } else {
                try {
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                        null,
                        null
                    )
                    deliveryRepository.clear(messageId)
                    blockRepository.clearMessageBlockMetadata(messageId)
                    pinRepository.clearPinnedMessage(messageId)
                } catch (e: SecurityException) {
                    Log.w("SmsRepository", "SecurityException موقع حذف دسته‌جمعی پیام‌ها", e)
                }
            }
        }
        return BulkDeleteResult(movedToTrash = trashEnabled, blockedFavoriteCount = blockedCount)
    }

    suspend fun deleteMessage(messageId: Long): Boolean {
        if (!requireDefaultSmsApp("حذف پیام")) return false
        val favoriteRepository = AppContainer.favoriteRepository(context)
        if (favoriteRepository.isFavorite(messageId)) {
            return false
        }
        if (AppSettings.isTrashEnabled(context)) {
            trashRepository.moveToTrash(messageId)
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
        deliveryRepository.clear(messageId)
        return true
    }

    suspend fun getTrashedMessages(): List<TrashedMessage> {
        if (!requireReadSmsPermission("خواندن سطل زباله")) return emptyList()
        val trashedIds = trashRepository.getTrashedIds()
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
                    result.add(TrashedMessage(message, displayName, trashRepository.getTrashedAt(message.id)))
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن سطل زباله - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyList()
        }
        return result.sortedByDescending { it.trashedAt }
    }

    suspend fun restoreFromTrash(messageId: Long) {
        trashRepository.restore(messageId)
    }

    suspend fun permanentlyDelete(messageId: Long): Boolean {
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
        deliveryRepository.clear(messageId)
        blockRepository.clearMessageBlockMetadata(messageId)
        pinRepository.clearPinnedMessage(messageId)
        trashRepository.restore(messageId)
        return true
    }

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

    /**
     * معکوسِ markThreadAsRead - برای عملیاتِ سویپِ «ناخوانده شدن» روی لیستِ مکالمات.
     * فقط پیام‌های *دریافتی* (نه ارسالی موفق) رو ناخوانده علامت می‌زنه، چون مفهومِ
     * خوانده/ناخوانده منطقاً فقط برای پیام‌های ورودی معنی داره.
     */
    fun markThreadAsUnread(threadId: Long): Boolean {
        if (!requireDefaultSmsApp("علامت‌گذاری مکالمه به‌عنوان ناخوانده")) return false
        val values = ContentValues().apply { put(Telephony.Sms.READ, 0) }
        try {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} != ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_SENT.toString())
            )
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع ناخوانده‌کردن مکالمه", e)
            return false
        }
        return true
    }

    /** suspend چون deliveryRepository.getDeliveredAt روی Room ئه - همه‌ی صداکننده‌هاش خودشون suspend هستن */
    private suspend fun cursorToMessage(cursor: Cursor): SmsMessage {
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
            deliveredAt = if (status == Telephony.Sms.STATUS_COMPLETE) deliveryRepository.getDeliveredAt(id) else 0L,
            subscriptionId = if (subIdIdx >= 0) cursor.getInt(subIdIdx) else -1
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        if (index < 0) return null
        return if (isNull(index)) null else getString(index)
    }
}
