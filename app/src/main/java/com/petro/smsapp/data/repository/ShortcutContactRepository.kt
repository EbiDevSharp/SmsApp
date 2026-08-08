package com.petro.smsapp.data.repository

import com.petro.smsapp.data.db.ShortcutContactDao
import com.petro.smsapp.data.db.ShortcutContactEntity
import kotlinx.coroutines.flow.Flow

/**
 * مخزن مخاطبین شورتکات لانچر - لانگ‌کلیک روی آیکون اپ.
 */
class ShortcutContactRepository(private val dao: ShortcutContactDao) {

    fun observeAll(): Flow<List<ShortcutContactEntity>> = dao.observeAll()

    suspend fun getAllOnce(): List<ShortcutContactEntity> = dao.getAllOnce()

    suspend fun isShortcutContact(address: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        return dao.getByNormalized(key) != null
    }

    suspend fun add(address: String, displayName: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        dao.insert(
            ShortcutContactEntity(
                normalizedAddress = key,
                address = address.trim(),
                displayName = displayName.ifBlank { address.trim() },
                addedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun remove(address: String) {
        val key = normalize(address)
        if (key.isBlank()) return
        dao.delete(key)
    }

    suspend fun removeByNormalized(normalized: String) = dao.delete(normalized)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun count(): Int = dao.count()

    private fun normalize(number: String): String =
        number.filter { it.isDigit() || it == '+' }
}
