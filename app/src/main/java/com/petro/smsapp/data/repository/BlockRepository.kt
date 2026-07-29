package com.petro.smsapp.data.repository

import com.petro.smsapp.data.BlockKeyword
import com.petro.smsapp.data.BlockPattern
import com.petro.smsapp.data.BlockPatternType
import com.petro.smsapp.data.BlockedNumber
import com.petro.smsapp.data.BlockedPatternMatch
import com.petro.smsapp.data.db.BlockKeywordDao
import com.petro.smsapp.data.db.BlockKeywordEntity
import com.petro.smsapp.data.db.BlockPatternDao
import com.petro.smsapp.data.db.BlockPatternEntity
import com.petro.smsapp.data.db.BlockedKeywordMessageDao
import com.petro.smsapp.data.db.BlockedKeywordMessageEntity
import com.petro.smsapp.data.db.BlockedNonContactMessageDao
import com.petro.smsapp.data.db.BlockedNonContactMessageEntity
import com.petro.smsapp.data.db.BlockedNumberDao
import com.petro.smsapp.data.db.BlockedNumberEntity
import com.petro.smsapp.data.db.BlockedPatternMessageDao
import com.petro.smsapp.data.db.BlockedPatternMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * جایگزینِ یک‌جای BlockStore + BlockKeywordStore + BlockPatternStore + سه استورِ
 * «پیام‌های بلاک‌شده بر اساس X». همه‌ی این‌ها مفهوماً بخش «بلاک» هستن، برای همین یه
 * Repository واحد - دقیقاً هم‌راستا با BlockScreen/BlockSettingsScreen که همه‌شون رو
 * با هم نشون میدن.
 */
class BlockRepository(
    private val numberDao: BlockedNumberDao,
    private val keywordDao: BlockKeywordDao,
    private val patternDao: BlockPatternDao,
    private val keywordMessageDao: BlockedKeywordMessageDao,
    private val patternMessageDao: BlockedPatternMessageDao,
    private val nonContactMessageDao: BlockedNonContactMessageDao
) {
    // ---- شماره‌های بلاک‌شده ----

    fun observeBlockedNumbers(): Flow<List<BlockedNumber>> =
        numberDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAllBlockedNumbersOnce(): List<BlockedNumber> = numberDao.getAllOnce().map { it.toDomain() }

    suspend fun isAddressBlocked(address: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        return numberDao.isBlocked(key)
    }

    /** true اگه واقعاً تازه بلاک شد، false اگه از قبل بلاک بوده */
    suspend fun blockNumber(threadId: Long, address: String, displayName: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        val rowId = numberDao.insertIfAbsent(BlockedNumberEntity(key, threadId, address, displayName, System.currentTimeMillis()))
        return rowId != -1L
    }

    suspend fun unblockAddress(address: String) {
        val key = normalize(address)
        if (key.isBlank()) return
        numberDao.deleteByKey(key)
    }

    suspend fun unblockThread(threadId: Long) {
        val entity = numberDao.findByThreadId(threadId) ?: return
        numberDao.deleteByKey(entity.normalizedAddress)
    }

    // ---- کلمات کلیدی ----

    fun observeKeywords(): Flow<List<BlockKeyword>> =
        keywordDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addKeyword(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (keywordDao.getAllOnce().any { it.text.equals(trimmed, ignoreCase = true) }) return false
        keywordDao.insert(BlockKeywordEntity("${System.currentTimeMillis()}_${trimmed.hashCode()}", trimmed, System.currentTimeMillis()))
        return true
    }

    suspend fun removeKeyword(id: String) = keywordDao.delete(id)

    suspend fun findKeywordMatch(body: String): BlockKeyword? {
        if (body.isBlank()) return null
        return keywordDao.getAllOnce().firstOrNull { body.contains(it.text, ignoreCase = true) }?.toDomain()
    }

    // ---- الگوهای شماره ----

    fun observePatterns(): Flow<List<BlockPattern>> =
        patternDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addPattern(type: BlockPatternType, value: String): Boolean {
        val trimmed = value.trim()
        val normalized = digitsOnly(trimmed)
        if (normalized.isBlank()) return false
        if (patternDao.getAllOnce().any { it.type == type.name && digitsOnly(it.value) == normalized }) return false
        patternDao.insert(BlockPatternEntity("${System.currentTimeMillis()}_${trimmed.hashCode()}", type.name, trimmed, System.currentTimeMillis()))
        return true
    }

    suspend fun removePattern(id: String) = patternDao.delete(id)

    suspend fun findPatternMatch(address: String): BlockPattern? {
        val addressDigits = digitsOnly(address)
        if (addressDigits.isBlank()) return null
        return patternDao.getAllOnce().firstOrNull { entity ->
            val patternDigits = digitsOnly(entity.value)
            if (patternDigits.isBlank()) return@firstOrNull false
            when (entity.type) {
                BlockPatternType.STARTS_WITH.name -> addressDigits.startsWith(patternDigits)
                BlockPatternType.ENDS_WITH.name -> addressDigits.endsWith(patternDigits)
                else -> false
            }
        }?.toDomain()
    }

    // ---- ردیابی پیام‌های بلاک‌شده بر اساس کلمه/الگو/خارج‌از‌مخاطبین ----

    suspend fun getKeywordBlockedMessageIds(): Set<Long> = keywordMessageDao.getAllIds().toSet()
    suspend fun getPatternBlockedMessageIds(): Set<Long> = patternMessageDao.getAllIds().toSet()
    suspend fun getNonContactBlockedMessageIds(): Set<Long> = nonContactMessageDao.getAllIds().toSet()

    suspend fun getMatchedKeyword(messageId: Long): String? = keywordMessageDao.getMatchedKeyword(messageId)
    suspend fun getMatchedPattern(messageId: Long): BlockedPatternMatch? =
        patternMessageDao.getMatch(messageId)?.let { BlockedPatternMatch(BlockPatternType.valueOf(it.type), it.value) }

    suspend fun markKeywordBlocked(messageId: Long, keyword: String) =
        keywordMessageDao.insert(BlockedKeywordMessageEntity(messageId, keyword, System.currentTimeMillis()))

    suspend fun markPatternBlocked(messageId: Long, type: BlockPatternType, value: String) =
        patternMessageDao.insert(BlockedPatternMessageEntity(messageId, type.name, value, System.currentTimeMillis()))

    suspend fun markNonContactBlocked(messageId: Long, address: String) =
        nonContactMessageDao.insert(BlockedNonContactMessageEntity(messageId, address, System.currentTimeMillis()))

    /** موقع حذف واقعیِ یه پیام، متادیتای بلاک‌بودنش هم از هر سه جدول پاک بشه */
    suspend fun clearMessageBlockMetadata(messageId: Long) {
        keywordMessageDao.delete(messageId)
        patternMessageDao.delete(messageId)
        nonContactMessageDao.delete(messageId)
    }

    private fun digitsOnly(value: String): String = value.filter { it.isDigit() }

    /** هم‌خانواده‌ی نرمال‌سازیِ قبلی: آخرین ۹ رقم شماره */
    private fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > 9) digitsOnly.takeLast(9) else digitsOnly
    }

    private fun BlockedNumberEntity.toDomain() = BlockedNumber(threadId, address, displayName, blockedAt)
    private fun BlockKeywordEntity.toDomain() = BlockKeyword(id, text, addedAt)
    private fun BlockPatternEntity.toDomain() = BlockPattern(id, BlockPatternType.valueOf(type), value, addedAt)
}
