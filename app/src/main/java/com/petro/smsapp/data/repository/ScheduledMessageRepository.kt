package com.petro.smsapp.data.repository

import com.petro.smsapp.data.ScheduledMessage
import com.petro.smsapp.data.db.ScheduledMessageDao
import com.petro.smsapp.data.db.ScheduledMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** جایگزین ScheduledMessageStore - زمان‌بندیِ واقعیِ سیستم (AlarmManager) جدا توی AlarmScheduler می‌مونه */
class ScheduledMessageRepository(private val dao: ScheduledMessageDao) {

    fun observeAll(): Flow<List<ScheduledMessage>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForThread(threadId: Long): Flow<List<ScheduledMessage>> =
        dao.observeForThread(threadId).map { list -> list.map { it.toDomain() } }

    suspend fun getForThreadOnce(threadId: Long): List<ScheduledMessage> =
        dao.getForThreadOnce(threadId).map { it.toDomain() }

    suspend fun get(id: Long): ScheduledMessage? = dao.get(id)?.toDomain()

    suspend fun save(message: ScheduledMessage) = dao.insert(message.toEntity())

    suspend fun remove(id: Long) = dao.delete(id)

    suspend fun getAllOnce(): List<ScheduledMessage> = dao.getAllOnce().map { it.toDomain() }

    private fun ScheduledMessageEntity.toDomain() = ScheduledMessage(
        id, threadId, address, displayName, body, scheduledAt,
        subscriptionId = if (subscriptionId == -1) null else subscriptionId
    )

    private fun ScheduledMessage.toEntity() = ScheduledMessageEntity(
        id, threadId, address, displayName, body, scheduledAt,
        subscriptionId = subscriptionId ?: -1
    )
}
