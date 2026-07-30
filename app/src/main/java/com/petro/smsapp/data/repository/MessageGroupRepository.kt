package com.petro.smsapp.data.repository

import com.petro.smsapp.data.MessageGroupMember
import com.petro.smsapp.data.MessageGroupSummary
import com.petro.smsapp.data.db.MessageGroupDao
import com.petro.smsapp.data.db.MessageGroupEntity
import com.petro.smsapp.data.db.MessageGroupMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * گروه‌های پیامکیِ ذخیره‌شده - برای وقتی که کاربر می‌خواد یه پیامِ گروهی رو بعداً دوباره
 * به همون دسته از مخاطبین بفرسته، بدونِ اینکه هربار از اول تک‌تکشون رو انتخاب کنه.
 *
 * عمداً یه فایل/Repository کاملاً جدا از SmsRepository و بقیه‌ی ریپازیتوری‌هاست - تا هر
 * بخش (خودِ اس‌ام‌اس‌های سیستم، بلاک، خصوصی، گروه‌ها) مستقل و ماژولار بمونه.
 *
 * observeGroupSummaries فقط id/اسم/تعدادِ اعضا رو (با یه کوئریِ JOIN سبک) reactive
 * برمی‌گردونه - خودِ اعضا فقط وقتی واقعاً لازم بشه (کاربر یه گروه رو برای بارگذاری
 * انتخاب کنه) با getGroupMembers جداگانه خونده میشن؛ اینجوری صفحه‌ی لیستِ گروه‌ها
 * مجبور نیست همه‌ی اعضای همه‌ی گروه‌ها رو هم‌زمان تو حافظه نگه داره.
 */
class MessageGroupRepository(private val dao: MessageGroupDao) {

    fun observeGroupSummaries(): Flow<List<MessageGroupSummary>> =
        dao.observeGroupsWithMemberCount().map { list ->
            list.map { MessageGroupSummary(it.id, it.name, it.memberCount, it.createdAt) }
        }

    suspend fun getGroupMembers(groupId: Long): List<MessageGroupMember> =
        dao.getMembers(groupId).map { MessageGroupMember(it.address, it.displayName) }

    suspend fun saveGroup(name: String, members: List<MessageGroupMember>): Long {
        val groupId = dao.insertGroup(MessageGroupEntity(name = name, createdAt = System.currentTimeMillis()))
        dao.insertMembers(
            members.map { MessageGroupMemberEntity(groupId = groupId, address = it.address, displayName = it.displayName) }
        )
        return groupId
    }

    suspend fun deleteGroup(groupId: Long) {
        dao.deleteMembers(groupId)
        dao.deleteGroup(groupId)
    }
}
