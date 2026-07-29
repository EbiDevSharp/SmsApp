package com.petro.smsapp.data.repository

import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.data.db.FavoriteDao
import com.petro.smsapp.data.db.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun FavoriteEntity.toDomain() = FavoriteMessage(messageId, threadId, address, displayName, body, date)
private fun FavoriteMessage.toEntity() = FavoriteEntity(messageId, threadId, address, displayName, body, date)

/**
 * جایگزین FavoriteStore - بدون هیچ کشِ میانی؛ Room خودش تک منبع حقیقته و از طریق
 * Flow مستقیم reactive میشه (insert/delete بلافاصله به همه‌ی collectorها می‌رسه).
 */
class FavoriteRepository(private val dao: FavoriteDao) {

    fun observeFavorites(): Flow<List<FavoriteMessage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeFavoriteIds(): Flow<Set<Long>> =
        dao.observeAllIds().map { it.toSet() }

    suspend fun isFavorite(messageId: Long): Boolean = dao.isFavorite(messageId)

    suspend fun addFavorite(message: FavoriteMessage) = dao.insert(message.toEntity())

    suspend fun removeFavorite(messageId: Long) = dao.delete(messageId)

    suspend fun toggleFavorite(message: FavoriteMessage) {
        if (dao.isFavorite(message.messageId)) {
            dao.delete(message.messageId)
        } else {
            dao.insert(message.toEntity())
        }
    }
}
