package com.petro.smsapp.data.repository

import com.petro.smsapp.data.PrivateNumber
import com.petro.smsapp.data.db.PrivateNumberDao
import com.petro.smsapp.data.db.PrivateNumberEntity
import com.petro.smsapp.util.PhoneNumberUtils
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

    /**
     * هم‌خانواده‌ی normalize توی BlockRepository - همون دلیل: آدرس‌های واقعاً شماره‌ای
     * (طبق PhoneNumberUtils.isSendableAddress) بر اساس آخرین ۹ رقم نرمال میشن، ولی
     * Sender ID های حروفی (اسم اپراتور یا Sender ID انگلیسی) که رقمی توشون نیست، بدونِ
     * این تغییر کلیدشون همیشه خالی درمی‌اومد و اصلاً قابل‌خصوصی‌کردن نبودن.
     */
    private fun normalize(number: String): String {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return ""
        return if (PhoneNumberUtils.isSendableAddress(trimmed)) {
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length > 9) digits.takeLast(9) else digits
        } else {
            trimmed.uppercase()
        }
    }

    private fun PrivateNumberEntity.toDomain() = PrivateNumber(threadId, address, displayName, madePrivateAt)
}