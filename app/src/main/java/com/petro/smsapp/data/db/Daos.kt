package com.petro.smsapp.data.db

import androidx.room.Dao
import androidx.room.Delete
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
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAllOnce(): List<BlockedNumberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalizedAddress = :key)")
    suspend fun isBlocked(key: String): Boolean

    @Query("SELECT * FROM blocked_numbers WHERE threadId = :threadId LIMIT 1")
    suspend fun findByThreadId(threadId: Long): BlockedNumberEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: BlockedNumberEntity): Long

    @Query("DELETE FROM blocked_numbers WHERE normalizedAddress = :key")
    suspend fun deleteByKey(key: String)
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
interface BlockKeywordDao {
    @Query("SELECT * FROM block_keywords ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockKeywordEntity>>

    @Query("SELECT * FROM block_keywords")
    suspend fun getAllOnce(): List<BlockKeywordEntity>

    @Insert
    suspend fun insert(entity: BlockKeywordEntity)

    @Query("DELETE FROM block_keywords WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BlockPatternDao {
    @Query("SELECT * FROM block_patterns ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockPatternEntity>>

    @Query("SELECT * FROM block_patterns")
    suspend fun getAllOnce(): List<BlockPatternEntity>

    @Insert
    suspend fun insert(entity: BlockPatternEntity)

    @Query("DELETE FROM block_patterns WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BlockedKeywordMessageDao {
    @Query("SELECT messageId FROM blocked_keyword_messages")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT keyword FROM blocked_keyword_messages WHERE messageId = :messageId")
    suspend fun getMatchedKeyword(messageId: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedKeywordMessageEntity)

    @Query("DELETE FROM blocked_keyword_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

@Dao
interface BlockedPatternMessageDao {
    @Query("SELECT messageId FROM blocked_pattern_messages")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT * FROM blocked_pattern_messages WHERE messageId = :messageId")
    suspend fun getMatch(messageId: Long): BlockedPatternMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedPatternMessageEntity)

    @Query("DELETE FROM blocked_pattern_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

@Dao
interface BlockedNonContactMessageDao {
    @Query("SELECT messageId FROM blocked_non_contact_messages")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BlockedNonContactMessageEntity)

    @Query("DELETE FROM blocked_non_contact_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
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

/** نتیجه‌ی خامِ کوئریِ JOIN برای شمارشِ تعداد اعضای هر گروه - Entity نیست، فقط یه POJO خروجیِ Room */
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
}
