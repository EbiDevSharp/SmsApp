package com.petro.smsapp.data

import android.provider.Telephony

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    // یعنی snippet در واقع متن یه پیش‌نویسه (نه آخرین پیام واقعی ارسال/دریافت‌شده) -
    // برای نمایش رنگ/لیبل متفاوت توی لیست مکالمات (شبیه گوگل مسیجز)
    val isDraft: Boolean = false,
    // یعنی این مکالمه از تنظیمات پین شده و باید بالای لیست اصلی (قبل از بقیه) نشون داده بشه -
    // از PinStore میاد، برای همینه که getConversations لیست رو بر این اساس مرتب می‌کنه
    val isPinned: Boolean = false,
    // تعداد کلِ پیام‌های این مکالمه (نه فقط خوانده‌نشده‌ها) - برای گزینه‌ی مرتب‌سازیِ
    // «پرپیام‌ترین اول» توی آکاردئونِ فیلترِ درآور. توی همون یه‌بار کوئری‌ای که
    // SmsRepository.getAllThreadsMeta هرحال می‌زنه شمرده میشه، بدونِ کوئریِ اضافه.
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
    // status از Telephony.Sms.STATUS میاد: -1 یعنی گزارش دلیوری نخواستیم/نداریم،
    // 0 یعنی تحویل موفق، 32/64 یعنی در حال انتظار، 128+ یعنی ناموفق (فقط برای پیام‌های ارسالی معنی داره)
    val status: Int = -1,
    // زمان دقیق تحویل به گیرنده (از DeliveryStore میاد، چون خود Sms provider همچین ستونی نداره)
    val deliveredAt: Long = 0L,
    // سیمی که این پیام باهاش ارسال شده (Telephony.Sms.SUBSCRIPTION_ID) - برای گوشی‌های
    // دوسیم‌کارته. -1 یعنی نامشخص/پیام دریافتیه. موقع «ارسال دوباره»ی یه پیام ناموفق، از
    // همین مقدار استفاده میشه تا دقیقاً از همون سیمی که اول امتحان شده بود دوباره بره،
    // نه سیمِ پیش‌فرض سیستم.
    val subscriptionId: Int = -1
) {
    val isDelivered: Boolean get() = isOutgoing && status == 0
    // پیام ارسالیِ ناموفق (مثلاً به‌خاطر قطعی آنتن) - دو مکانیزمِ کاملاً جدا و مستقلِ اندروید
    // می‌تونن این حالت رو نشون بدن: ۱) SmsStatusReceiver خودمون بعد از خطای ارسال ستونِ
    // STATUS رو به STATUS_FAILED آپدیت می‌کنه (رفتار عادی اپ). ۲) خودِ سیستم اندروید، مستقل
    // از هر آپدیتی که ما بزنیم، بعضی وقت‌ها (مثلاً رادیو خاموش بوده یا مشکل سیم‌کارت) مستقیم
    // TYPE ردیف رو به MESSAGE_TYPE_FAILED عوض می‌کنه بدون اینکه اصلاً STATUS رو دست بزنه.
    // قبلاً فقط حالت اول چک می‌شد، پس حالت دوم (که بیشتر هم پیش میاد) اصلاً به کاربر نشون
    // داده نمی‌شد و پیام برای همیشه بدون هیچ نشونه‌ی خطایی می‌موند.
    val isFailed: Boolean get() = isOutgoing && (status == Telephony.Sms.STATUS_FAILED || type == Telephony.Sms.MESSAGE_TYPE_FAILED)
    // در حال ارسال - سیستم موقتاً TYPE رو به OUTBOX می‌ذاره تا وقتی که یا موفق بشه (SENT)
    // یا شکست بخوره (FAILED). قبلاً هیچ نشونه‌ی بصری‌ای نداشت و دقیقاً شبیه پیامِ ارسال‌شده‌ی
    // معمولی نشون داده می‌شد.
    val isSending: Boolean get() = isOutgoing && type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
    // در صف ارسال - سیستم وقتی رادیو موقتاً در دسترس نیست (مثلاً حالت پرواز) پیام رو اینجا
    // نگه می‌داره تا بعداً خودش امتحان کنه. مثل isSending، قبلاً هیچ نشونه‌ای نداشت.
    val isQueued: Boolean get() = isOutgoing && type == Telephony.Sms.MESSAGE_TYPE_QUEUED
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
 * از چه طریقی یه پیام بلاک شده - برای نمایش توی صفحه‌ی «پیامک‌های بلاک‌شده».
 * PHONE_NUMBER یعنی خودِ شماره‌ی فرستنده صریحاً بلاک بوده؛ KEYWORD یعنی شماره بلاک نبوده ولی
 * متن پیام شامل یکی از «کلمات کلیدی بلاک» تعریف‌شده توسط کاربر بوده؛ PATTERN یعنی شماره‌ی
 * فرستنده صریحاً بلاک نبوده ولی با یکی از «الگوهای بلاکِ شماره» (شروع/پایانِ شماره) مچ شده.
 */
enum class BlockSource {
    PHONE_NUMBER,
    KEYWORD,
    PATTERN,
    // شماره‌ی فرستنده جزو مخاطبینِ ذخیره‌شده‌ی گوشی نبوده و کاربر از تنظیماتِ بلاک،
    // «بلاک شماره‌های خارج از مخاطبین» رو فعال کرده بوده
    NOT_IN_CONTACTS
}

/**
 * یه پیامِ متعلق به یه شماره‌ی بلاک‌شده، حاویِ یه کلمه‌ی کلیدیِ بلاک، یا مچ‌شده با یه الگوی
 * بلاکِ شماره - به‌همراه نام/شماره‌ی طرف مکالمه - برای نمایش توی صفحه‌ی «پیامک‌های بلاک‌شده».
 * matchedKeyword فقط وقتی پر میشه که blockSource برابر KEYWORD باشه؛ matchedPatternType و
 * matchedPatternValue فقط وقتی پر میشن که blockSource برابر PATTERN باشه.
 */
data class BlockedMessageEntry(
    val message: SmsMessage,
    val contactDisplayName: String,
    val blockSource: BlockSource = BlockSource.PHONE_NUMBER,
    val matchedKeyword: String? = null,
    val matchedPatternType: BlockPatternType? = null,
    val matchedPatternValue: String? = null
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
