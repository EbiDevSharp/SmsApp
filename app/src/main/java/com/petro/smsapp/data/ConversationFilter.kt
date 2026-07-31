package com.petro.smsapp.data

/**
 * فیلترهای قابل‌اعمال روی لیست اصلی مکالمات - از طریق آکاردئونِ بالای درآور انتخاب میشن.
 * چندتایی‌انتخابن (مثلاً هم‌زمان «خوانده‌نشده» و بعداً «سنجاق‌شده») و با منطق OR با هم
 * ترکیب میشن: یعنی هر مکالمه‌ای که حداقل با یکی از فیلترهای انتخاب‌شده مچ بشه نشون داده میشه.
 *
 * فعلاً فقط UNREAD وجود داره؛ چون قراره بعداً از تنظیمات داینامیک بشه (کاربر بتونه
 * آیتم‌ها رو کم/زیاد کنه)، هر آیتم جدید فقط باید به این enum اضافه بشه و منطقِ
 * matches خودش رو پیاده کنه - جای دیگه‌ای (UI آکاردئون، فیلترِ لیست) نیازی به تغییر نداره.
 * id هر آیتم ثابت می‌مونه (دقیقاً هم‌قاعده‌ی NotificationActionType) چون بعداً ممکنه
 * انتخاب‌های کاربر توی DataStore با همین id ذخیره بشن.
 */
enum class ConversationFilterType(val id: String, val label: String) {
    UNREAD("unread", "خوانده‌نشده");

    /** این مکالمه با این فیلتر مچ میشه یا نه */
    fun matches(conversation: Conversation): Boolean = when (this) {
        UNREAD -> conversation.unreadCount > 0
    }

    companion object {
        fun fromId(id: String): ConversationFilterType? = entries.find { it.id == id }
    }
}

/**
 * اعمالِ مجموعه‌ی فیلترهای انتخاب‌شده روی لیست مکالمات - اگه هیچ فیلتری انتخاب نشده
 * باشه (selected خالی)، کل لیست بدون تغییر برمی‌گرده. این فیلتر روی همون لیستِ
 * درحافظه‌ی مکالمات اعمال میشه (نه یه کوئری جداگانه) چون خودِ لیست مکالمات از قبل
 * یه‌بار کامل از SmsRepository خونده شده - نیازی به کوئری اضافه به Telephony Provider نیست.
 */
fun List<Conversation>.applyConversationFilters(selected: Set<ConversationFilterType>): List<Conversation> {
    if (selected.isEmpty()) return this
    return filter { conversation -> selected.any { it.matches(conversation) } }
}
