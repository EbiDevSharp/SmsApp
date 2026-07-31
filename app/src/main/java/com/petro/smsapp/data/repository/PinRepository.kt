package com.petro.smsapp.data.repository

import com.petro.smsapp.data.db.PinDao
import com.petro.smsapp.data.db.PinEntity
import com.petro.smsapp.data.db.PinnedMessageDao
import com.petro.smsapp.data.db.PinnedMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * جایگزینِ یک‌جای PinStore (پین کلِ مکالمه) و PinnedMessageStore (پین یه پیام خاص
 * داخل چت) - هردو مفهوماً «پین» هستن.
 */
class PinRepository(
    private val convDao: PinDao,
    private val messageDao: PinnedMessageDao
) {
    // ---- پین کل مکالمه ----

    fun observePinnedThreadIds(): Flow<Set<Long>> =
        convDao.observeAll().map { list -> list.map { it.threadId }.toSet() }

    suspend fun isThreadPinned(threadId: Long): Boolean = convDao.getAllOnce().any { it.threadId == threadId }

    suspend fun getPinnedAt(threadId: Long): Long = convDao.getAllOnce().find { it.threadId == threadId }?.pinnedAt ?: 0L

    suspend fun getPinnedCount(): Int = convDao.count()

    suspend fun pinThread(threadId: Long) = convDao.insert(PinEntity(threadId, System.currentTimeMillis()))

    suspend fun unpinThread(threadId: Long) = convDao.delete(threadId)

    // ---- پین یه پیام خاص داخل چت ----

    fun observePinnedMessageIds(): Flow<Set<Long>> = messageDao.observeAllIds().map { it.toSet() }

    /** برای فیلترِ «دارای پیام سنجاق‌شده» توی آکاردئونِ درآور - همه‌ی threadId هایی که حداقل یه پیامِ پین‌شده دارن */
    fun observePinnedMessageThreadIds(): Flow<Set<Long>> = messageDao.observeThreadIds().map { it.toSet() }

    suspend fun togglePinMessage(threadId: Long, messageId: Long) {
        if (messageDao.isPinned(messageId)) {
            messageDao.delete(messageId)
        } else {
            messageDao.insert(PinnedMessageEntity(messageId, threadId))
        }
    }

    suspend fun clearPinnedMessage(messageId: Long) = messageDao.delete(messageId)
}