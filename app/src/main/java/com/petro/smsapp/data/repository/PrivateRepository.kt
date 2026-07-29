package com.petro.smsapp.data.repository

import com.petro.smsapp.data.PrivateNumber
import com.petro.smsapp.data.db.PrivateNumberDao
import com.petro.smsapp.data.db.PrivateNumberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** جایگزین PrivateStore (فقط بخش شماره‌ها؛ رمز PIN توی PrivatePinDataStore رفته) */
class PrivateRepository(private val dao: PrivateNumberDao) {

    fun observePrivateNumbers(): Flow<List<PrivateNumber>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAllPrivateNumbersOnce(): List<PrivateNumber> = dao.getAllOnce().map { it.toDomain() }

    suspend fun isAddressPrivate(address: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        return dao.isPrivate(key)
    }

    suspend fun makePrivate(threadId: Long, address: String, displayName: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        val rowId = dao.insertIfAbsent(PrivateNumberEntity(key, threadId, address, displayName, System.currentTimeMillis()))
        return rowId != -1L
    }

    suspend fun removePrivateByAddress(address: String) {
        val key = normalize(address)
        if (key.isBlank()) return
        dao.deleteByKey(key)
    }

    suspend fun removePrivate(threadId: Long) {
        val entity = dao.findByThreadId(threadId) ?: return
        dao.deleteByKey(entity.normalizedAddress)
    }

    private fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > 9) digitsOnly.takeLast(9) else digitsOnly
    }

    private fun PrivateNumberEntity.toDomain() = PrivateNumber(threadId, address, displayName, madePrivateAt)
}
