package com.petro.smsapp.data.repository

import com.petro.smsapp.data.MessageTemplate
import com.petro.smsapp.data.db.TemplateDao
import com.petro.smsapp.data.db.TemplateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun TemplateEntity.toDomain() = MessageTemplate(id, title, body, createdAt, updatedAt)

/**
 * مخزن تمپلیت‌های متنی - برای منوی «+» و باکس ارسال پیام.
 */
class TemplateRepository(private val dao: TemplateDao) {

    fun observeTemplates(): Flow<List<MessageTemplate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAllOnce(): List<MessageTemplate> =
        dao.getAllOnce().map { it.toDomain() }

    suspend fun getById(id: Long): MessageTemplate? =
        dao.getById(id)?.toDomain()

    suspend fun add(title: String, body: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            TemplateEntity(
                title = title.trim(),
                body = body,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun update(id: Long, title: String, body: String) {
        dao.update(id, title.trim(), body, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()
}
