package com.petro.smsapp.data.repository

import android.content.Context
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.FilterGroup
import com.petro.smsapp.data.FilterGroupKeyword
import com.petro.smsapp.data.FilterGroupNumber
import com.petro.smsapp.data.FilterGroupPattern
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.data.FilterMatchResult
import com.petro.smsapp.data.FilterMatchType
import com.petro.smsapp.data.PatternType
import com.petro.smsapp.data.db.FilterGroupDao
import com.petro.smsapp.data.db.FilterGroupEntity
import com.petro.smsapp.data.db.FilterGroupKeywordEntity
import com.petro.smsapp.data.db.FilterGroupMatchedMessageEntity
import com.petro.smsapp.data.db.FilterGroupNumberEntity
import com.petro.smsapp.data.db.FilterGroupPatternEntity
import com.petro.smsapp.data.db.GroupIdCount
import com.petro.smsapp.util.PhoneNumberUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * ماژولِ عمومیِ «گروهِ فیلتر» - جایگزینِ کاملِ بخشِ قدیمیِ «بلاک». کاربر خودش N تا
 * گروهِ دلخواه می‌سازه (هرکدوم شماره/کلمه/الگو/تنظیماتِ مستقلِ خودش رو داره) و هر پیامِ
 * دریافتی فقط عضوِ *اولین* گروهی میشه که باهاش مچ میشه (بر اساسِ priority).
 */
class FilterGroupRepository(private val dao: FilterGroupDao) {

    // ---- خودِ گروه‌ها ----

    /** لیستِ گروه‌ها به‌همراه شمارنده‌ی هر بخش - چهار Flow با combine ترکیب میشن تا نیازی به یه کوئریِ JOINِ سنگین نباشه */
    fun observeGroupSummaries(): Flow<List<FilterGroupSummary>> =
        combine(
            dao.observeGroups(),
            dao.observeNumberCounts(),
            dao.observeKeywordCounts(),
            dao.observePatternCounts(),
            dao.observeMessageCounts()
        ) { groups, numbers, keywords, patterns, messages ->
            val numberMap = numbers.toCountMap()
            val keywordMap = keywords.toCountMap()
            val patternMap = patterns.toCountMap()
            val messageMap = messages.toCountMap()
            groups.map { g ->
                FilterGroupSummary(
                    group = g.toDomain(),
                    numberCount = numberMap[g.id] ?: 0,
                    keywordCount = keywordMap[g.id] ?: 0,
                    patternCount = patternMap[g.id] ?: 0,
                    messageCount = messageMap[g.id] ?: 0
                )
            }
        }

    private fun List<GroupIdCount>.toCountMap(): Map<Long, Int> = associate { it.groupId to it.cnt }

    suspend fun getGroup(id: Long): FilterGroup? = dao.getGroup(id)?.toDomain()

    /** ساختِ یه گروهِ جدید - همیشه با پایین‌ترین اولویت (آخر از همه چک میشه) اضافه میشه */
    suspend fun createGroup(
        name: String,
        hideFromMainList: Boolean,
        showNotifications: Boolean,
        blockNonContacts: Boolean
    ): Long {
        val nextPriority = dao.getMaxPriority() + 1
        return dao.insertGroup(
            FilterGroupEntity(
                name = name,
                priority = nextPriority,
                hideFromMainList = hideFromMainList,
                showNotifications = showNotifications,
                blockNonContacts = blockNonContacts,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateGroup(
        id: Long,
        name: String,
        hideFromMainList: Boolean,
        showNotifications: Boolean,
        blockNonContacts: Boolean
    ) {
        dao.updateGroup(id, name, hideFromMainList, showNotifications, blockNonContacts)
    }

    /** جابه‌جاییِ اولویتِ دو گروهِ همسایه (دکمه‌های بالا/پایینِ هرردیف تویِ هابِ گروه‌ها) */
    /** بازنویسیِ کاملِ اولویتِ همه‌ی گروه‌ها بعد از درگ‌اند‌دراپ - ترتیبِ لیستِ ورودی همون ترتیبِ نهاییه */
    suspend fun reorderGroups(orderedGroupIds: List<Long>) {
        orderedGroupIds.forEachIndexed { index, id -> dao.updatePriority(id, index) }
    }

    suspend fun deleteGroup(id: Long) {
        dao.deleteGroup(id)
        // Room کلیدِ خارجی/کسکید نداره، پس ردیف‌های وابسته دستی پاک میشن
        dao.getKeywordsForGroupOnce(id).forEach { dao.deleteKeyword(it.id) }
        dao.getPatternsForGroupOnce(id).forEach { dao.deletePattern(it.id) }
        // شماره‌ها و matched-messageها روی groupId کلید شدن، حذفِ مستقیم با کوئری ساده‌تره
        dao.getNumbersForGroupOnce(id).forEach { dao.deleteNumber(id, it.normalizedAddress) }
        dao.getMatchesForGroup(id).forEach { dao.deleteMatch(it.messageId) }
    }

    // ---- شماره‌ها ----

    fun observeNumbersForGroup(groupId: Long): Flow<List<FilterGroupNumber>> =
        dao.observeNumbersForGroup(groupId).map { list -> list.map { it.toDomain() } }

    /** true اگه واقعاً تازه اضافه شد، false اگه از قبل تو همین گروه بود */
    suspend fun addNumber(groupId: Long, address: String, displayName: String): Boolean {
        val key = normalize(address)
        if (key.isBlank()) return false
        val rowId = dao.insertNumberIfAbsent(FilterGroupNumberEntity(groupId, key, address, displayName, System.currentTimeMillis()))
        return rowId != -1L
    }

    suspend fun removeNumber(groupId: Long, address: String) {
        val key = normalize(address)
        if (key.isBlank()) return
        dao.deleteNumber(groupId, key)
    }

    /** آیا این شماره از قبل تو یه گروهِ دیگه‌ای هست - برای هشدارِ UX، نه یه محدودیتِ سخت */
    suspend fun findExistingGroupForNumber(address: String): Long? {
        val key = normalize(address)
        if (key.isBlank()) return null
        return dao.findGroupIdForNumber(key)
    }

    // ---- کلمات کلیدی ----

    fun observeKeywordsForGroup(groupId: Long): Flow<List<FilterGroupKeyword>> =
        dao.observeKeywordsForGroup(groupId).map { list -> list.map { it.toDomain() } }

    suspend fun addKeyword(groupId: Long, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (dao.getKeywordsForGroupOnce(groupId).any { it.text.equals(trimmed, ignoreCase = true) }) return false
        dao.insertKeyword(FilterGroupKeywordEntity("${System.currentTimeMillis()}_${trimmed.hashCode()}", groupId, trimmed, System.currentTimeMillis()))
        return true
    }

    suspend fun removeKeyword(id: String) = dao.deleteKeyword(id)

    // ---- الگوها ----

    fun observePatternsForGroup(groupId: Long): Flow<List<FilterGroupPattern>> =
        dao.observePatternsForGroup(groupId).map { list -> list.map { it.toDomain() } }

    suspend fun addPattern(groupId: Long, type: PatternType, value: String): Boolean {
        val trimmed = value.trim()
        val normalized = digitsOnly(trimmed)
        if (normalized.isBlank()) return false
        if (dao.getPatternsForGroupOnce(groupId).any { it.type == type.name && digitsOnly(it.value) == normalized }) return false
        dao.insertPattern(FilterGroupPatternEntity("${System.currentTimeMillis()}_${trimmed.hashCode()}", groupId, type.name, trimmed, System.currentTimeMillis()))
        return true
    }

    suspend fun removePattern(id: String) = dao.deletePattern(id)

    // ---- موتورِ تشخیص ----

    /**
     * گروه‌ها رو به‌ترتیبِ اولویت چک می‌کنه؛ اولین گروهی که (به هر طریقی: شماره، کلمه،
     * الگو، یا خارج‌از‌مخاطبین) مچ بشه برگردونده میشه. اگه هیچ گروهی مچ نشه null.
     */
    suspend fun findMatchingGroup(context: Context, address: String, body: String): FilterMatchResult? {
        val groups = dao.getGroupsOrderedByPriority()
        if (groups.isEmpty()) return null

        val numberKey = normalize(address)
        val addressDigits = digitsOnly(address)

        for (entity in groups) {
            if (numberKey.isNotBlank() && dao.numberExistsInGroup(entity.id, numberKey)) {
                return FilterMatchResult(entity.toDomain(), FilterMatchType.NUMBER, address)
            }

            if (body.isNotBlank()) {
                val keyword = dao.getKeywordsForGroupOnce(entity.id).firstOrNull { body.contains(it.text, ignoreCase = true) }
                if (keyword != null) {
                    return FilterMatchResult(entity.toDomain(), FilterMatchType.KEYWORD, keyword.text)
                }
            }

            if (addressDigits.isNotBlank()) {
                val pattern = dao.getPatternsForGroupOnce(entity.id).firstOrNull { p ->
                    val patternDigits = digitsOnly(p.value)
                    if (patternDigits.isBlank()) return@firstOrNull false
                    when (p.type) {
                        PatternType.STARTS_WITH.name -> addressDigits.startsWith(patternDigits)
                        PatternType.ENDS_WITH.name -> addressDigits.endsWith(patternDigits)
                        else -> false
                    }
                }
                if (pattern != null) {
                    return FilterMatchResult(entity.toDomain(), FilterMatchType.PATTERN, pattern.value)
                }
            }

            if (entity.blockNonContacts && ContactsCache.getName(context, address) == null) {
                return FilterMatchResult(entity.toDomain(), FilterMatchType.NON_CONTACT, address)
            }
        }
        return null
    }

    suspend fun markMatched(messageId: Long, groupId: Long, matchType: FilterMatchType, matchedValue: String?) {
        dao.insertMatch(FilterGroupMatchedMessageEntity(messageId, groupId, matchType.name, matchedValue, System.currentTimeMillis()))
    }

    suspend fun getMatch(messageId: Long): FilterMatchResultForMessage? {
        val row = dao.getMatch(messageId) ?: return null
        val group = dao.getGroup(row.groupId) ?: return null
        return FilterMatchResultForMessage(group.toDomain(), FilterMatchType.valueOf(row.matchType), row.matchedValue)
    }

    suspend fun getMatchedMessageIdsForGroup(groupId: Long): Map<Long, FilterMatchResultForMessage> {
        val group = dao.getGroup(groupId)?.toDomain() ?: return emptyMap()
        return dao.getMatchesForGroup(groupId).associate {
            it.messageId to FilterMatchResultForMessage(group, FilterMatchType.valueOf(it.matchType), it.matchedValue)
        }
    }

    /** موقعِ حذفِ واقعیِ یه پیام، ردِ اینکه مالِ کدوم گروه بوده هم پاک بشه */
    suspend fun clearMessageMatch(messageId: Long) = dao.deleteMatch(messageId)

    /** id همه‌ی پیام‌هایی که تویِ یه گروهِ hideFromMainList=true افتادن - برای فیلترِ لیستِ اصلی */
    suspend fun getHiddenMessageIds(): Set<Long> = dao.getHiddenMessageIds().toSet()

    private fun digitsOnly(value: String): String = value.filter { it.isDigit() }

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

    private fun FilterGroupEntity.toDomain() = FilterGroup(id, name, priority, hideFromMainList, showNotifications, blockNonContacts, createdAt)
    private fun FilterGroupNumberEntity.toDomain() = FilterGroupNumber(groupId, address, displayName, addedAt)
    private fun FilterGroupKeywordEntity.toDomain() = FilterGroupKeyword(id, groupId, text, addedAt)
    private fun FilterGroupPatternEntity.toDomain() = FilterGroupPattern(id, groupId, PatternType.valueOf(type), value, addedAt)
}

/** نسخه‌ی سبکِ FilterMatchResult که به‌جای خودِ متن دوباره‌خونی، برای نمایشِ لیستِ پیام‌های یه گروه استفاده میشه */
data class FilterMatchResultForMessage(
    val group: FilterGroup,
    val matchType: FilterMatchType,
    val matchedValue: String?
)
