package com.petro.smsapp.data

/**
 * ترتیبِ نمایشِ لیستِ مکالمات، وقتی کاربر از آکاردئونِ فیلترِ درآور صریحاً یه گزینه‌ی
 * مرتب‌سازی انتخاب کنه. همیشه تک‌انتخابیه (نه چندتایی مثلِ فیلترهای وضعیتِ پیام) چون
 * دوتا معیارِ مرتب‌سازیِ هم‌زمان معنی نداره.
 *
 * وقتی هیچ‌کدوم انتخاب نشده (sortType == null)، ترتیبِ پیش‌فرضِ خودِ
 * SmsRepository.getConversations() (پین‌شده‌ها بالا، بعد بر اساسِ pinnedAt، بعد
 * بر اساسِ تاریخِ آخرین پیام) دست‌نخورده می‌مونه. به‌محضِ انتخابِ هرکدوم از این
 * گزینه‌ها، پین‌بودنِ مکالمه دیگه هیچ اولویتی نداره - کلِ لیست فقط بر همون معیار
 * مرتب میشه.
 */
enum class ConversationSortType(val id: String, val label: String) {
    NEWEST_FIRST("newest_first", "جدیدترین اول"),
    OLDEST_FIRST("oldest_first", "قدیمی‌ترین اول"),
    ALPHABETICAL("alphabetical", "الفبایی"),
    UNREAD_FIRST("unread_first", "خوانده‌نشده‌ها اول"),
    MOST_MESSAGES_FIRST("most_messages_first", "پرپیام‌ترین اول");

    companion object {
        fun fromId(id: String?): ConversationSortType? = entries.find { it.id == id }
    }
}

/**
 * اعمالِ مرتب‌سازیِ انتخابی روی لیستِ مکالمات - کاملاً درحافظه، بدونِ هیچ کوئریِ
 * اضافه (همه‌ی فیلدهای لازم از قبل رو خودِ Conversation هستن). اگه sortType نال
 * باشه، لیست بدونِ هیچ تغییری (با همون ترتیبِ ورودی) برمی‌گرده.
 */
fun List<Conversation>.applySort(sortType: ConversationSortType?): List<Conversation> {
    if (sortType == null) return this
    return when (sortType) {
        ConversationSortType.NEWEST_FIRST -> sortedByDescending { it.date }
        ConversationSortType.OLDEST_FIRST -> sortedBy { it.date }
        ConversationSortType.ALPHABETICAL -> sortedBy { it.displayName }
        ConversationSortType.UNREAD_FIRST -> sortedWith(
            compareByDescending<Conversation> { it.unreadCount > 0 }.thenByDescending { it.date }
        )
        ConversationSortType.MOST_MESSAGES_FIRST -> sortedByDescending { it.messageCount }
    }
}
