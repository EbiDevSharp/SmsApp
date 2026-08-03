package com.petro.smsapp.data

import android.provider.Telephony

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val isDraft: Boolean = false,
    val isPinned: Boolean = false,
    val messageCount: Int = 0
)

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val type: Int,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val status: Int = -1,
    val deliveredAt: Long = 0L,
    val subscriptionId: Int = -1
) {
    val isDelivered: Boolean get() = isOutgoing && status == 0
    val isFailed: Boolean get() = isOutgoing && (status == Telephony.Sms.STATUS_FAILED || type == Telephony.Sms.MESSAGE_TYPE_FAILED)
    val isSending: Boolean get() = isOutgoing && type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
    val isQueued: Boolean get() = isOutgoing && type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

data class TrashedMessage(
    val message: SmsMessage,
    val contactDisplayName: String,
    val trashedAt: Long
)

/**
 * یه پیامِ متعلق به یه گروهِ فیلتر - جایگزینِ عمومیِ BlockedMessageEntry قبلی. groupName
 * همینجا (نه فقط groupId) نگه داشته میشه تا صفحه‌ی نمایش مجبور نباشه جدا از لیستِ
 * گروه‌ها اسمِ گروه رو پیدا کنه.
 */
data class FilteredMessageEntry(
    val message: SmsMessage,
    val contactDisplayName: String,
    val groupId: Long,
    val groupName: String,
    val matchType: FilterMatchType,
    val matchedValue: String? = null
)

data class PrivateMessageEntry(
    val message: SmsMessage,
    val contactDisplayName: String
)

data class BulkDeleteResult(
    val movedToTrash: Boolean,
    val blockedFavoriteCount: Int
)
