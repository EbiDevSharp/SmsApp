package com.petro.smsapp.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int
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
    // status از Telephony.Sms.STATUS میاد: -1 یعنی گزارش دلیوری نخواستیم/نداریم،
    // 0 یعنی تحویل موفق، 32/64 یعنی در حال انتظار، 128+ یعنی ناموفق (فقط برای پیام‌های ارسالی معنی داره)
    val status: Int = -1,
    // زمان دقیق تحویل به گیرنده (از DeliveryStore میاد، چون خود Sms provider همچین ستونی نداره)
    val deliveredAt: Long = 0L
) {
    val isDelivered: Boolean get() = isOutgoing && status == 0
}
