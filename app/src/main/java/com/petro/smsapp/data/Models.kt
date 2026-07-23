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

/**
 * یه پیامِ توی سطل زباله، به‌همراه نام/شماره‌ی طرف مکالمه و زمان دقیق حذف‌شدن -
 * برای نمایش توی صفحه‌ی «سطل زباله». خودِ SmsMessage همچنان واقعیه (ردیف اصلی
 * توی Sms provider پاک نشده)، فقط از لیست‌های عادی (مکالمات/پیام‌های یک مکالمه) فیلتر شده.
 */
data class TrashedMessage(
    val message: SmsMessage,
    val contactDisplayName: String,
    val trashedAt: Long
)

/**
 * یه پیامِ متعلق به یه شماره‌ی بلاک‌شده، به‌همراه نام/شماره‌ی طرف مکالمه -
 * برای نمایش توی صفحه‌ی «پیامک‌های بلاک‌شده».
 */
data class BlockedMessageEntry(
    val message: SmsMessage,
    val contactDisplayName: String
)

/**
 * یه پیامِ متعلق به یه شماره‌ی خصوصی‌شده، به‌همراه نام/شماره‌ی طرف مکالمه -
 * برای نمایش توی صفحه‌ی «پیامک‌های خصوصی» (پشتِ رمز ۴ رقمی).
 */
data class PrivateMessageEntry(
    val message: SmsMessage,
    val contactDisplayName: String
)

/**
 * نتیجه‌ی حذف دسته‌جمعیِ چند مکالمه از صفحه‌ی اصلی (بعد از انتخاب چندتایی).
 * movedToTrash یعنی بر اساس تنظیمات، پیام‌ها به‌جای حذف فیزیکی به سطل زباله منتقل شدن.
 * blockedFavoriteCount یعنی چند تا از پیام‌ها به‌خاطر فیوریت‌بودن (قفل) دست‌نخورده موندن.
 */
data class BulkDeleteResult(
    val movedToTrash: Boolean,
    val blockedFavoriteCount: Int
)
