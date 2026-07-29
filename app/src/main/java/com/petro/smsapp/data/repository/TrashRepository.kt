package com.petro.smsapp.data.repository

import com.petro.smsapp.data.db.TrashDao
import com.petro.smsapp.data.db.TrashEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** جایگزین TrashStore */
class TrashRepository(private val dao: TrashDao) {

    fun observeTrashedIds(): Flow<Set<Long>> = dao.observeAllIds().map { it.toSet() }

    suspend fun getTrashedIds(): Set<Long> = dao.getAllIds().toSet()

    suspend fun isTrashed(messageId: Long): Boolean = dao.getTrashedAt(messageId) != null

    suspend fun moveToTrash(messageId: Long) = dao.insert(TrashEntity(messageId, System.currentTimeMillis()))

    suspend fun restore(messageId: Long) = dao.delete(messageId)

    suspend fun getTrashedAt(messageId: Long): Long = dao.getTrashedAt(messageId) ?: 0L
}
