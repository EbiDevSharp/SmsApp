package com.petro.smsapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY date DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT messageId FROM favorites")
    fun observeAllIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE messageId = :messageId)")
    suspend fun isFavorite(messageId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

@Dao
interface PrivateNumberDao {
    @Query("SELECT * FROM private_numbers ORDER BY madePrivateAt DESC")
    fun observeAll(): Flow<List<PrivateNumberEntity>>

    @Query("SELECT * FROM private_numbers")
    suspend fun getAllOnce(): List<PrivateNumberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM private_numbers WHERE normalizedAddress = :key)")
    suspend fun isPrivate(key: String): Boolean

    @Query("SELECT * FROM private_numbers WHERE threadId = :threadId LIMIT 1")
    suspend fun findByThreadId(threadId: Long): PrivateNumberEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: PrivateNumberEntity): Long

    @Query("DELETE FROM private_numbers WHERE normalizedAddress = :key")
    suspend fun deleteByKey(key: String)
}

@Dao
interface TrashDao {
    @Query("SELECT messageId FROM trashed_messages")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT messageId FROM trashed_messages")
    fun observeAllIds(): Flow<List<Long>>

    @Query("SELECT trashedAt FROM trashed_messages WHERE messageId = :messageId")
    suspend fun getTrashedAt(messageId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrashEntity)

    @Query("DELETE FROM trashed_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

@Dao
interface PinDao {
    @Query("SELECT * FROM pinned_conversations")
    fun observeAll(): Flow<List<PinEntity>>

    @Query("SELECT * FROM pinned_conversations")
    suspend fun getAllOnce(): List<PinEntity>

    @Query("SELECT COUNT(*) FROM pinned_conversations")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PinEntity)

    @Query("DELETE FROM pinned_conversations WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)
}

@Dao
interface PinnedMessageDao {
    @Query("SELECT messageId FROM pinned_messages")
    fun observeAllIds(): Flow<List<Long>>

    @Query("SELECT DISTINCT threadId FROM pinned_messages")
    fun observeThreadIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_messages WHERE messageId = :messageId)")
    suspend fun isPinned(messageId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PinnedMessageEntity)

    @Query("DELETE FROM pinned_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE threadId = :threadId ORDER BY scheduledAt ASC")
    fun observeForThread(threadId: Long): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE threadId = :threadId ORDER BY scheduledAt ASC")
    suspend fun getForThreadOnce(threadId: Long): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ScheduledMessageEntity?

    @Query("SELECT * FROM scheduled_messages")
    suspend fun getAllOnce(): List<ScheduledMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduledMessageEntity)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface DeliveryDao {
    @Query("SELECT deliveredAtMillis FROM delivery_times WHERE messageId = :messageId")
    suspend fun getDeliveredAt(messageId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeliveryEntity)

    @Query("DELETE FROM delivery_times WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

/** نتیجه‌ی خامِ کوئریِ JOIN برای شمارشِ تعداد اعضای هر «گروهِ پیامکی» (گیرنده‌های ارسال) */
data class GroupWithMemberCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val memberCount: Int
)

@Dao
interface MessageGroupDao {
    @Query(
        "SELECT g.id as id, g.name as name, g.createdAt as createdAt, COUNT(m.id) as memberCount " +
                "FROM message_groups g LEFT JOIN message_group_members m ON m.groupId = g.id " +
                "GROUP BY g.id ORDER BY g.createdAt DESC"
    )
    fun observeGroupsWithMemberCount(): Flow<List<GroupWithMemberCount>>

    @Query("SELECT * FROM message_group_members WHERE groupId = :groupId")
    suspend fun getMembers(groupId: Long): List<MessageGroupMemberEntity>

    @Insert
    suspend fun insertGroup(entity: MessageGroupEntity): Long

    @Insert
    suspend fun insertMembers(members: List<MessageGroupMemberEntity>)

    @Query("DELETE FROM message_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("DELETE FROM message_group_members WHERE groupId = :groupId")
    suspend fun deleteMembers(groupId: Long)

    @Query("UPDATE message_groups SET name = :name WHERE id = :groupId")
    suspend fun renameGroup(groupId: Long, name: String)
}

// ============================================================================
// FilterGroupDao - یه Dao واحد برای کلِ ماژولِ «گروهِ فیلتر» (جایگزینِ عمومیِ بلاک).
// عمداً همه‌چی (خودِ گروه‌ها + شماره‌ها + کلمات + الگوها + پیام‌های مچ‌شده) توی یه
// Dao جمع شده چون همه‌شون مفهوماً یه ماژولِ واحدن و اکثرِ عملیات‌ها (مثلاً پیدا کردنِ
// گروهِ مچ‌شده برای یه پیام) به چندتاشون هم‌زمان نیاز دارن.
// ============================================================================

/** خلاصه‌ی هر گروهِ فیلتر با شمارنده‌های هر بخش - برای هابِ اصلیِ صفحه‌ی «گروه‌ها» */
data class FilterGroupCounts(
    val numberCount: Int,
    val keywordCount: Int,
    val patternCount: Int,
    val messageCount: Int
)

data class GroupIdCount(val groupId: Long, val cnt: Int)

@Dao
interface FilterGroupDao {

    // ---- خودِ گروه‌ها ----

    @Query("SELECT * FROM filter_groups ORDER BY priority ASC")
    fun observeGroups(): Flow<List<FilterGroupEntity>>

    /** برای منطقِ تشخیصِ داخلِ SmsDeliverReceiver - یه‌بار‌خوانیِ ساده، مرتب بر اساسِ اولویت (کوچیک‌تر = زودتر چک میشه) */
    @Query("SELECT * FROM filter_groups ORDER BY priority ASC")
    suspend fun getGroupsOrderedByPriority(): List<FilterGroupEntity>

    @Query("SELECT * FROM filter_groups WHERE id = :id LIMIT 1")
    suspend fun getGroup(id: Long): FilterGroupEntity?

    @Query("SELECT COALESCE(MAX(priority), -1) FROM filter_groups")
    suspend fun getMaxPriority(): Int

    @Insert
    suspend fun insertGroup(entity: FilterGroupEntity): Long

    @Query(
        "UPDATE filter_groups SET name = :name, hideFromMainList = :hideFromMainList, " +
                "showNotifications = :showNotifications, blockNonContacts = :blockNonContacts, " +
                "showInNotificationPicker = :showInNotificationPicker WHERE id = :id"
    )
    suspend fun updateGroup(
        id: Long,
        name: String,
        hideFromMainList: Boolean,
        showNotifications: Boolean,
        blockNonContacts: Boolean,
        showInNotificationPicker: Boolean
    )

    @Query("UPDATE filter_groups SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    /** تنظیمِ گلوبالِ «دریافتِ نوتیف» رویِ همه‌ی گروه‌ها هم‌زمان - از هابِ اصلیِ صفحه‌ی گروه‌ها صدا زده میشه */
    @Query("UPDATE filter_groups SET showNotifications = :enabled")
    suspend fun setAllShowNotifications(enabled: Boolean)

    /** تنظیمِ گلوبالِ «نمایش در لیستِ پیام‌ها» رویِ همه‌ی گروه‌ها هم‌زمان - hideFromMainList برعکسِ این مقداره */
    @Query("UPDATE filter_groups SET hideFromMainList = :hide")
    suspend fun setAllHideFromMainList(hide: Boolean)

    @Query("DELETE FROM filter_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    // ---- هدفِ دکمه‌ی «افزودن سریع به گروه»ِ روی نوتیف ----

    /**
     * چون همیشه حداکثر یه گروه می‌تونه هدفِ افزودنِ سریع باشه (رفتارِ رادیویی، نه
     * چندانتخابی)، ست‌کردنِ یه گروهِ جدید همیشه با یه UPDATE اول همه رو صفر می‌کنه.
     */
    @Query("UPDATE filter_groups SET isQuickAddTarget = 0")
    suspend fun clearQuickAddTarget()

    @Query("UPDATE filter_groups SET isQuickAddTarget = 1 WHERE id = :id")
    suspend fun setQuickAddTarget(id: Long)

    /** id همون یه گروهی که الان هدفِ افزودنِ سریعه - null اگه هیچ‌کدوم انتخاب نشده باشه */
    @Query("SELECT id FROM filter_groups WHERE isQuickAddTarget = 1 LIMIT 1")
    suspend fun getQuickAddTargetGroupId(): Long?

    // ---- شماره‌ها ----

    @Query("SELECT * FROM filter_group_numbers WHERE groupId = :groupId ORDER BY addedAt DESC")
    fun observeNumbersForGroup(groupId: Long): Flow<List<FilterGroupNumberEntity>>

    @Query("SELECT * FROM filter_group_numbers WHERE groupId = :groupId")
    suspend fun getNumbersForGroupOnce(groupId: Long): List<FilterGroupNumberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM filter_group_numbers WHERE groupId = :groupId AND normalizedAddress = :key)")
    suspend fun numberExistsInGroup(groupId: Long, key: String): Boolean

    /** آیا این شماره تو *هر* گروهی از قبل ثبت شده - برای پیغامِ «قبلاً به گروهِ دیگه‌ای اضافه شده» */
    @Query("SELECT groupId FROM filter_group_numbers WHERE normalizedAddress = :key LIMIT 1")
    suspend fun findGroupIdForNumber(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNumberIfAbsent(entity: FilterGroupNumberEntity): Long

    @Query("DELETE FROM filter_group_numbers WHERE groupId = :groupId AND normalizedAddress = :key")
    suspend fun deleteNumber(groupId: Long, key: String)

    @Query("SELECT groupId as groupId, COUNT(*) as cnt FROM filter_group_numbers GROUP BY groupId")
    fun observeNumberCounts(): Flow<List<GroupIdCount>>

    // ---- کلمات کلیدی ----

    @Query("SELECT * FROM filter_group_keywords WHERE groupId = :groupId ORDER BY addedAt DESC")
    fun observeKeywordsForGroup(groupId: Long): Flow<List<FilterGroupKeywordEntity>>

    @Query("SELECT * FROM filter_group_keywords WHERE groupId = :groupId")
    suspend fun getKeywordsForGroupOnce(groupId: Long): List<FilterGroupKeywordEntity>

    @Insert
    suspend fun insertKeyword(entity: FilterGroupKeywordEntity)

    @Query("DELETE FROM filter_group_keywords WHERE id = :id")
    suspend fun deleteKeyword(id: String)

    @Query("SELECT groupId as groupId, COUNT(*) as cnt FROM filter_group_keywords GROUP BY groupId")
    fun observeKeywordCounts(): Flow<List<GroupIdCount>>

    // ---- الگوها ----

    @Query("SELECT * FROM filter_group_patterns WHERE groupId = :groupId ORDER BY addedAt DESC")
    fun observePatternsForGroup(groupId: Long): Flow<List<FilterGroupPatternEntity>>

    @Query("SELECT * FROM filter_group_patterns WHERE groupId = :groupId")
    suspend fun getPatternsForGroupOnce(groupId: Long): List<FilterGroupPatternEntity>

    @Insert
    suspend fun insertPattern(entity: FilterGroupPatternEntity)

    @Query("DELETE FROM filter_group_patterns WHERE id = :id")
    suspend fun deletePattern(id: String)

    @Query("SELECT groupId as groupId, COUNT(*) as cnt FROM filter_group_patterns GROUP BY groupId")
    fun observePatternCounts(): Flow<List<GroupIdCount>>

    // ---- ردیابیِ پیام‌های مچ‌شده ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(entity: FilterGroupMatchedMessageEntity)

    /** بک‌فیلِ دسته‌جمعیِ پیام‌های قبلی موقعِ افزودنِ دستیِ یه شماره به گروه - یه insert برای کلِ لیست، نه یکی‌یکی */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(entities: List<FilterGroupMatchedMessageEntity>)

    @Query("DELETE FROM filter_group_matched_messages WHERE messageId = :messageId")
    suspend fun deleteMatch(messageId: Long)

    /** حذفِ دسته‌جمعیِ چند match هم‌زمان - برای وقتی یه شماره از گروه حذف میشه */
    @Query("DELETE FROM filter_group_matched_messages WHERE messageId IN (:messageIds)")
    suspend fun deleteMatches(messageIds: List<Long>)

    @Query("SELECT * FROM filter_group_matched_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMatch(messageId: Long): FilterGroupMatchedMessageEntity?

    /** از بینِ یه لیستِ id، فقط اونایی که از قبل match دارن - برای اینکه بک‌فیل فقط رویِ باقی‌مونده انجام بشه، نه یه کوئریِ جدا برای هر پیام */
    @Query("SELECT messageId FROM filter_group_matched_messages WHERE messageId IN (:messageIds)")
    suspend fun getMatchedMessageIdsAmong(messageIds: List<Long>): List<Long>

    @Query("SELECT * FROM filter_group_matched_messages WHERE groupId = :groupId")
    suspend fun getMatchesForGroup(groupId: Long): List<FilterGroupMatchedMessageEntity>

    /**
     * id همه‌ی پیام‌هایی که تویِ یه گروهِ hideFromMainList=1 افتادن - برای فیلترِ لیستِ
     * اصلیِ مکالمات؛ این تنها جایی‌ست که SmsRepository لازمه بدونه.
     */
    @Query(
        "SELECT m.messageId FROM filter_group_matched_messages m " +
                "JOIN filter_groups g ON g.id = m.groupId WHERE g.hideFromMainList = 1"
    )
    suspend fun getHiddenMessageIds(): List<Long>
    /**
     * id همه‌ی پیام‌هایی که به هر گروهی (صرف‌نظر از hideFromMainList) مچ شدن - برای
     * فیلترِ «گروه‌بندی‌شده»ی آکاردئونِ درآور. برخلافِ getHiddenMessageIds که فقط
     * گروه‌های hideFromMainList=1 رو برمی‌گردونه، این همه‌ی گروه‌ها رو شامل میشه.
     */
    @Query("SELECT messageId FROM filter_group_matched_messages")
    suspend fun getAllMatchedMessageIds(): List<Long>
    @Query("SELECT groupId as groupId, COUNT(*) as cnt FROM filter_group_matched_messages GROUP BY groupId")
    fun observeMessageCounts(): Flow<List<GroupIdCount>>
}
