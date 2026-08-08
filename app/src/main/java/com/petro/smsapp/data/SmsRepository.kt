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
import com.petro.smsapp.data.repository.DeliveryRepository
import com.petro.smsapp.data.repository.FilterGroupRepository
import com.petro.smsapp.data.repository.PinRepository
import com.petro.smsapp.data.repository.PrivateRepository
import com.petro.smsapp.data.repository.TrashRepository
import com.petro.smsapp.receiver.SmsStatusReceiver

/**
 * لایه‌ی خواندن/نوشتنِ Telephony.Sms Provider - این پروایدر خودِ اندروید همیشه source
 * of truth می‌مونه، ولی همه‌ی متادیتای مربوط به گروهِ فیلتر/خصوصی/سطل‌زباله/پین/دلیوری
 * از طریقِ Repositoryهای تزریق‌شده (روی Room) میان.
 */
class SmsRepository(
    private val context: Context,
    private val filterGroupRepository: FilterGroupRepository = AppContainer.filterGroupRepository(context),
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

    /**
     * نتیجهٔ بارگذاری لیست مکالمات به‌همراه مجموعه‌های لازم برای فیلترهای سطحِ پیام
     * (سیم ۱/۲، ارسالی، دریافتی) که در همان پاسِ اسکن پیام‌ها جمع می‌شن.
     */
    data class ConversationsLoadResult(
        val conversations: List<Conversation>,
        val sim1ThreadIds: Set<Long> = emptySet(),
        val sim2ThreadIds: Set<Long> = emptySet(),
        val outgoingThreadIds: Set<Long> = emptySet(),
        val incomingThreadIds: Set<Long> = emptySet()
    )

    suspend fun getConversations(): ConversationsLoadResult {
        if (!requireReadSmsPermission("خواندن لیست مکالمات")) return ConversationsLoadResult(emptyList())
        val threadsResult = getAllThreadsMeta()
        val threadMeta = threadsResult.meta
        val groupedThreadIds = threadsResult.groupedThreadIds
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
                messageCount = meta?.messageCount ?: 0,
                isGrouped = groupedThreadIds.contains(threadId)
            )
        }
        val pinnedAtByThread = conversations.filter { it.isPinned }
            .associate { it.threadId to pinRepository.getPinnedAt(it.threadId) }

        val sorted = conversations.sortedWith(
            compareByDescending<Conversation> { it.isPinned }
                .thenByDescending { pinnedAtByThread[it.threadId] ?: 0L }
                .thenByDescending { it.date }
        )
        return ConversationsLoadResult(
            conversations = sorted,
            sim1ThreadIds = threadsResult.sim1ThreadIds,
            sim2ThreadIds = threadsResult.sim2ThreadIds,
            outgoingThreadIds = threadsResult.outgoingThreadIds,
            incomingThreadIds = threadsResult.incomingThreadIds
        )
    }

    private data class ThreadMeta(val address: String, val date: Long, val unreadCount: Int, val messageCount: Int, val snippet: String)
    private data class DraftMeta(val address: String, val body: String, val date: Long)
    /** خروجیِ getAllThreadsMeta - متادیتای هر ترد + threadId های گروه‌بندی‌شده و مجموعه‌های فیلتر سیم/ارسالی/دریافتی */
    private data class ThreadsResult(
        val meta: Map<Long, ThreadMeta>,
        val groupedThreadIds: Set<Long>,
        val sim1ThreadIds: Set<Long> = emptySet(),
        val sim2ThreadIds: Set<Long> = emptySet(),
        val outgoingThreadIds: Set<Long> = emptySet(),
        val incomingThreadIds: Set<Long> = emptySet()
    )

    private suspend fun getAllThreadsMeta(): ThreadsResult {
        val result = mutableMapOf<Long, ThreadMeta>()
        val unreadCounts = mutableMapOf<Long, Int>()
        val messageCounts = mutableMapOf<Long, Int>()
        val groupedThreadIds = mutableSetOf<Long>()
        val sim1ThreadIds = mutableSetOf<Long>()
        val sim2ThreadIds = mutableSetOf<Long>()
        val outgoingThreadIds = mutableSetOf<Long>()
        val incomingThreadIds = mutableSetOf<Long>()
        val trashedIds = trashRepository.getTrashedIds()
        // پیام‌هایی که تویِ یه گروهِ فیلترِ hideFromMainList=true افتادن - از لیستِ اصلی مخفی میشن
        val hiddenByFilterGroup = filterGroupRepository.getHiddenMessageIds()
        // پیام‌هایی که عضوِ *هر* گروهی هستن (چه مخفی چه غیرِمخفی) - برای فیلترِ «گروه‌بندی‌شده»
        val allGroupedMessageIds = filterGroupRepository.getAllMatchedMessageIds()

        // نگاشت subscriptionId -> slotIndex برای فیلتر سیم ۱/۲ (اسلات ۰ و ۱)
        val subIdToSlot = try {
            SimRepository(context).getActiveSims().associate { it.subscriptionId to it.slotIndex }
        } catch (_: Exception) {
            emptyMap()
        }

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.BODY, Telephony.Sms.TYPE,
                    // فقط SUBSCRIPTION_ID (sub_id) - ستون sim_id روی خیلی از دستگاه‌ها وجود نداره
                    // و گذاشتنش تو projection باعث SQLiteException و کرش میشه
                    Telephony.Sms.SUBSCRIPTION_ID
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
                val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                val simIdIdx = cursor.getColumnIndex("sim_id")
                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(idIdx)
                    if (messageId in trashedIds) continue
                    if (messageId in hiddenByFilterGroup) continue
                    if (typeIdx >= 0 && cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue

                    val threadId = cursor.getLong(threadIdIdx)
                    if (messageId in allGroupedMessageIds) {
                        groupedThreadIds.add(threadId)
                    }
                    messageCounts[threadId] = (messageCounts[threadId] ?: 0) + 1
                    if (cursor.getInt(readIdx) == 0) {
                        unreadCounts[threadId] = (unreadCounts[threadId] ?: 0) + 1
                    }
                    // کوئری بر اساس DATE DESC است؛ اولین باری که یک threadId دیده می‌شود
                    // همان آخرین پیام مکالمه است. فیلتر ارسالی/دریافتی فقط روی همین پیام آخر
                    // اعمال می‌شود (نه «حداقل یک پیام در کل تاریخچه» که تقریباً همیشه هر دو true می‌شود).
                    val isFirstForThread = !result.containsKey(threadId)
                    if (isFirstForThread) {
                        result[threadId] = ThreadMeta(
                            address = cursor.getStringOrNull(addressIdx) ?: "",
                            date = cursor.getLong(dateIdx),
                            unreadCount = 0,
                            messageCount = 0,
                            snippet = cursor.getStringOrNull(bodyIdx) ?: ""
                        )

                        val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
                        val isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                                type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                                type == Telephony.Sms.MESSAGE_TYPE_FAILED ||
                                type == Telephony.Sms.MESSAGE_TYPE_QUEUED
                        if (isOutgoing) {
                            outgoingThreadIds.add(threadId)
                        } else if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                            incomingThreadIds.add(threadId)
                        }
                    }

                    // سیم ۱ / سیم ۲ بر اساس subscriptionId یا sim_id
                    val rawSubId = when {
                        subIdIdx >= 0 && !cursor.isNull(subIdIdx) -> cursor.getInt(subIdIdx)
                        simIdIdx >= 0 && !cursor.isNull(simIdIdx) -> cursor.getInt(simIdIdx)
                        else -> -1
                    }
                    if (rawSubId >= 0) {
                        when (subIdToSlot[rawSubId]) {
                            0 -> sim1ThreadIds.add(threadId)
                            1 -> sim2ThreadIds.add(threadId)
                            else -> {
                                // اگه نگاشت slot پیدا نشد، بعضی دستگاه‌ها subscriptionId رو مستقیم اسلات نگه می‌دارن
                                when (rawSubId) {
                                    0 -> sim1ThreadIds.add(threadId)
                                    1 -> sim2ThreadIds.add(threadId)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن لیست مکالمات - مجوز احتمالاً همین لحظه برداشته شده", e)
            return ThreadsResult(emptyMap(), emptySet())
        }
        val finalMeta = result.mapValues { (threadId, meta) ->
            meta.copy(unreadCount = unreadCounts[threadId] ?: 0, messageCount = messageCounts[threadId] ?: 0)
        }
        return ThreadsResult(
            meta = finalMeta,
            groupedThreadIds = groupedThreadIds,
            sim1ThreadIds = sim1ThreadIds,
            sim2ThreadIds = sim2ThreadIds,
            outgoingThreadIds = outgoingThreadIds,
            incomingThreadIds = incomingThreadIds
        )
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

    /**
     * جستجوی متن داخل body پیام‌ها و برگرداندن threadIdهایی که حداقل یک پیامِ مچ‌شده دارند.
     *
     * @param outgoingOnly فقط پیام‌های ارسالی (SENT/OUTBOX/FAILED/QUEUED)
     * @param incomingOnly فقط پیام‌های دریافتی (INBOX)
     * اگر هر دو false باشند، همه نوع‌ها به‌جز draft جستجو می‌شوند.
     * اگر هر دو true باشند (نباید از UI بیاید)، مثل any رفتار می‌کند.
     */
    suspend fun searchThreadsByMessageBody(
        query: String,
        outgoingOnly: Boolean = false,
        incomingOnly: Boolean = false
    ): Set<Long> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptySet()
        if (!requireReadSmsPermission("جستجوی متن پیام‌ها")) return emptySet()

        val trashedIds = trashRepository.getTrashedIds()
        val hiddenByFilterGroup = filterGroupRepository.getHiddenMessageIds()
        val matched = linkedSetOf<Long>()

        // جلوگیری از wildcard تزریقی کاربر در LIKE
        fun escapeLike(raw: String): String =
            raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

        val likeArg = "%${escapeLike(trimmed)}%"

        val (typeSql, typeArgs) = when {
            outgoingOnly && !incomingOnly -> {
                "${Telephony.Sms.TYPE} IN (?, ?, ?, ?)" to listOf(
                    Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString(),
                    Telephony.Sms.MESSAGE_TYPE_FAILED.toString(),
                    Telephony.Sms.MESSAGE_TYPE_QUEUED.toString()
                )
            }
            incomingOnly && !outgoingOnly -> {
                "${Telephony.Sms.TYPE} = ?" to listOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
            }
            else -> {
                "${Telephony.Sms.TYPE} != ?" to listOf(Telephony.Sms.MESSAGE_TYPE_DRAFT.toString())
            }
        }

        val selection = "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\' AND $typeSql"
        val selectionArgs = arrayOf(likeArg) + typeArgs.toTypedArray()

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.BODY),
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(idIdx)
                    if (messageId in trashedIds) continue
                    if (messageId in hiddenByFilterGroup) continue
                    matched.add(cursor.getLong(threadIdx))
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع جستجوی متن پیام‌ها", e)
            return emptySet()
        } catch (e: Exception) {
            // بعضی OEMها ESCAPE را پشتیبانی نمی‌کنند؛ یک‌بار بدون ESCAPE retry
            Log.w("SmsRepository", "جستجو با ESCAPE شکست خورد، retry بدون ESCAPE", e)
            try {
                val fallbackSelection = "${Telephony.Sms.BODY} LIKE ? AND $typeSql"
                val fallbackArgs = arrayOf("%$trimmed%") + typeArgs.toTypedArray()
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID),
                    fallbackSelection,
                    fallbackArgs,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                    while (cursor.moveToNext()) {
                        val messageId = cursor.getLong(idIdx)
                        if (messageId in trashedIds) continue
                        if (messageId in hiddenByFilterGroup) continue
                        matched.add(cursor.getLong(threadIdx))
                    }
                }
            } catch (e2: Exception) {
                Log.w("SmsRepository", "جستجوی متن پیام‌ها ناموفق بود", e2)
                return emptySet()
            }
        }
        return matched
    }

    suspend fun getMessagesForThread(threadId: Long): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیام‌های یک مکالمه")) return emptyList()
        val messages = mutableListOf<SmsMessage>()
        val trashedIds = trashRepository.getTrashedIds()
        val hiddenByFilterGroup = filterGroupRepository.getHiddenMessageIds()
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
                    if (message.id in hiddenByFilterGroup) continue
                    messages.add(message)
                }
            }
        } catch (e: SecurityException) {
            Log.w("SmsRepository", "SecurityException موقع خوندن پیام‌های مکالمه - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyList()
        }
        return messages
    }

    /** همه‌ی پیام‌های عضوِ یه گروهِ فیلترِ خاص - برای صفحه‌ی «پیام‌های این گروه» */
    suspend fun getMessagesForFilterGroup(groupId: Long): List<FilteredMessageEntry> {
        val group = filterGroupRepository.getGroup(groupId) ?: return emptyList()
        val matches = filterGroupRepository.getMatchedMessageIdsForGroup(groupId)
        if (matches.isEmpty()) return emptyList()
        return getMessagesByIds(matches.keys)
            .map { message ->
                val match = matches[message.id]
                val name = ContactsCache.getName(context, message.address) ?: message.address
                FilteredMessageEntry(
                    message = message,
                    contactDisplayName = name,
                    groupId = group.id,
                    groupName = group.name,
                    matchType = match?.matchType ?: FilterMatchType.NUMBER,
                    matchedValue = match?.matchedValue
                )
            }
            .sortedByDescending { it.message.date }
    }

    private suspend fun getMessagesByIds(ids: Set<Long>): List<SmsMessage> {
        if (!requireReadSmsPermission("خواندن پیامک‌های یه گروهِ فیلتر")) return emptyList()
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
            Log.w("SmsRepository", "SecurityException موقع خوندن پیامک‌های یه گروهِ فیلتر", e)
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
        if (!requireReadSmsPermission("خواندن پیامک‌های خصوصی‌شده")) return emptyList()
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
            Log.w("SmsRepository", "SecurityException موقع خوندن پیامک‌های خصوصی‌شده", e)
            return emptyList()
        }
        return messages
    }

    /**
     * @return شناسه‌ی پیام ذخیره‌شده در Sent box (برای پیگیری تیک ارسال/دلیوری)، یا null
     * اگر ذخیره/ارسال ممکن نبود.
     */
    fun sendSms(address: String, body: String, subscriptionId: Int? = null): Long? {
        if (!requireDefaultSmsApp("ارسال پیامک")) return null

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
        return messageId
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

    /**
     * خواندن type و status یک پیام از Telephony - برای به‌روزرسانی تیک‌های
     * ارسال/دلیوری داخل پاپ‌آپ (بدون نیاز به لود کل ترد).
     * @return Pair(type, status) یا null اگر پیام پیدا نشد
     */
    fun getMessageTypeAndStatus(messageId: Long): Pair<Int, Int>? {
        if (messageId <= 0L) return null
        return try {
            context.contentResolver.query(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                arrayOf(Telephony.Sms.TYPE, Telephony.Sms.STATUS),
                null, null, null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val type = cursor.getInt(0)
                val status = cursor.getInt(1)
                type to status
            }
        } catch (e: Exception) {
            Log.w("SmsRepository", "خطا موقع خواندن type/status پیام $messageId", e)
            null
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

    /**
     * فقط idِ پیامک‌های یه thread خاص - بدونِ خوندنِ بدنه/تاریخ/نوع، برای عملیات‌های
     * سبکی مثلِ حذفِ دسته‌جمعی یا بک‌فیلِ matchِ گروهِ فیلتر که فقط id لازم دارن.
     */
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
     * بعد از اضافه‌شدنِ دستیِ یه شماره به یه گروهِ فیلتر (نه از طریقِ پیامِ تازه‌رسیده)،
     * پیام‌های از قبل موجودِ همون thread رو هم به این گروه وصل می‌کنه - وگرنه
     * تنظیماتِ گروه (مثلاً «از لیستِ اصلی مخفی بشه») فقط رویِ پیام‌های *بعدی* اعمال
     * می‌شد، نه پیام‌های قدیمی‌ای که همون لحظه‌ی اضافه‌کردن تو لیستِ اصلی بودن.
     */
    suspend fun applyGroupToExistingThreadMessages(groupId: Long, threadId: Long, address: String) {
        val ids = getMessageIdsForThread(threadId)
        if (ids.isEmpty()) return
        filterGroupRepository.matchMessagesToGroup(groupId, address, ids)
    }

    /** برعکسِ applyGroupToExistingThreadMessages - وقتی یه شماره از یه گروه حذف میشه، برای برگردوندنِ مکالمه‌ی مخفی‌شده به لیستِ اصلی */
    suspend fun removeGroupFromExistingThreadMessages(groupId: Long, address: String) {
        filterGroupRepository.unmatchMessagesForAddressInGroup(groupId, address)
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
                    filterGroupRepository.clearMessageMatch(messageId)
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
        filterGroupRepository.clearMessageMatch(messageId)
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

    private suspend fun cursorToMessage(cursor: Cursor): SmsMessage {
        fun col(name: String) = cursor.getColumnIndex(name)
        val type = cursor.getInt(col(Telephony.Sms.TYPE))
        val dateSentIdx = col(Telephony.Sms.DATE_SENT)
        val statusIdx = col(Telephony.Sms.STATUS)
        // استاندارد: sub_id (Telephony.Sms.SUBSCRIPTION_ID)
        // بعضی OEMها (مثل شیائومی قدیمی) به‌جاش ستون sim_id دارن
        val subIdIdx = col(Telephony.Sms.SUBSCRIPTION_ID)
        val simIdIdx = col("sim_id")
        val id = cursor.getLong(col(Telephony.Sms._ID))
        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
        val rawSubId = when {
            subIdIdx >= 0 && !cursor.isNull(subIdIdx) -> cursor.getInt(subIdIdx)
            simIdIdx >= 0 && !cursor.isNull(simIdIdx) -> cursor.getInt(simIdIdx)
            else -> -1
        }
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
            subscriptionId = rawSubId
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? {
        if (index < 0) return null
        return if (isNull(index)) null else getString(index)
    }
}
