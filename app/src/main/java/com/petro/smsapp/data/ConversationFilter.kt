package com.petro.smsapp.data

/**
 * فیلترهای قابل‌اعمال روی لیست اصلی مکالمات - از طریق آکاردئونِ بالای درآور انتخاب میشن.
 * چندتایی‌انتخابن (مثلاً هم‌زمان «خوانده‌نشده» و «سنجاق‌شده») و با منطق OR با هم
 * ترکیب میشن: یعنی هر مکالمه‌ای که حداقل با یکی از فیلترهای انتخاب‌شده مچ بشه نشون داده میشه.
 *
 * هر آیتم جدید فقط باید به این enum اضافه بشه و منطقِ matches خودش رو پیاده کنه - جای
 * دیگه‌ای (UI آکاردئون، فیلترِ لیست) نیازی به تغییر نداره. id هر آیتم ثابت می‌مونه
 * (دقیقاً هم‌قاعده‌ی NotificationActionType) چون بعداً ممکنه انتخاب‌های کاربر توی
 * DataStore با همین id ذخیره بشن.
 */
enum class ConversationFilterType(val id: String, val label: String) {
    UNREAD("unread", "خوانده‌نشده"),
    // چت‌هایی که کاربر از منوی «انتخاب چندتایی» سنجاق‌شون کرده (بالای لیست اصلی نشون داده میشن)
    PINNED("pinned", "سنجاق‌شده"),
    // چت‌هایی که طرفِ مکالمه‌شون تو مخاطبینِ گوشی ذخیره نشده - یعنی اسمِ نمایشی همون
    // خودِ آدرس/شماره‌ست (address == displayName)، نه یه اسمِ واقعیِ گرفته‌شده از ContactsCache
    NON_CONTACT("non_contact", "غیر مخاطب"),
    // چت‌هایی که حداقل یه پیام (نه خودِ چت) داخلشون سنجاق شده - جدا از فیلترِ PINNED که
    // مربوط به سنجاق‌شدنِ خودِ مکالمه از لیستِ اصلیه
    HAS_PINNED_MESSAGE("has_pinned_message", "سنجاق‌شده"),
    // چت‌هایی که حداقل یه پیامِ فیوریت‌شده (علاقه‌مندی) داخلشون هست
    HAS_FAVORITE_MESSAGE("has_favorite_message", "علاقه‌مند"),
    // چت‌هایی که یه پیش‌نویسِ ذخیره‌شده دارن (Conversation.isDraft)
    DRAFT("draft", "پیش‌نویس"),
    // چت‌هایی که حداقل یه پیامشون تویِ یکی از گروه‌های فیلتر افتاده (Conversation.isGrouped)
    GROUPED("grouped", "گروه");

    /**
     * این مکالمه با این فیلتر مچ میشه یا نه. فیلترهایی که فقط به فیلدهای خودِ Conversation
     * نیاز دارن (UNREAD/PINNED/NON_CONTACT) context رو استفاده نمی‌کنن؛ فیلترهایی که به
     * دیتای سطحِ پیام نیاز دارن (HAS_PINNED_MESSAGE/HAS_FAVORITE_MESSAGE) از context می‌خونن،
     * چون Conversation خودش این اطلاعات رو نگه نمی‌داره (تا مدلِ دامنه ساده بمونه).
     */
    fun matches(conversation: Conversation, context: ConversationFilterContext = ConversationFilterContext()): Boolean =
        when (this) {
            UNREAD -> conversation.unreadCount > 0
            PINNED -> conversation.isPinned
            NON_CONTACT -> conversation.address.isNotBlank() && conversation.address == conversation.displayName
            HAS_PINNED_MESSAGE -> context.pinnedMessageThreadIds.contains(conversation.threadId)
            HAS_FAVORITE_MESSAGE -> context.favoriteThreadIds.contains(conversation.threadId)
            DRAFT -> conversation.isDraft
            GROUPED -> conversation.isGrouped
        }

    companion object {
        fun fromId(id: String): ConversationFilterType? = entries.find { it.id == id }
    }
}

/**
 * دیتای کمکیِ سطحِ پیام که برای بعضی فیلترها لازمه ولی خودِ Conversation نداردش -
 * threadId هایی که حداقل یه پیامِ پین‌شده یا فیوریت‌شده دارن. جدا از Conversation نگه
 * داشته شده تا مدلِ دامنه‌ی Conversation ساده و مستقل از این جزئیات بمونه.
 */
data class ConversationFilterContext(
    val pinnedMessageThreadIds: Set<Long> = emptySet(),
    val favoriteThreadIds: Set<Long> = emptySet()
)

/**
 * اعمالِ مجموعه‌ی فیلترهای انتخاب‌شده روی لیست مکالمات - اگه هیچ فیلتری انتخاب نشده
 * باشه (selected خالی)، کل لیست بدون تغییر برمی‌گرده. این فیلتر روی همون لیستِ
 * درحافظه‌ی مکالمات اعمال میشه (نه یه کوئری جداگانه) چون خودِ لیست مکالمات از قبل
 * یه‌بار کامل از SmsRepository خونده شده - نیازی به کوئری اضافه به Telephony Provider نیست.
 */
fun List<Conversation>.applyConversationFilters(
    selected: Set<ConversationFilterType>,
    context: ConversationFilterContext = ConversationFilterContext()
): List<Conversation> {
    if (selected.isEmpty()) return this
    return filter { conversation -> selected.any { it.matches(conversation, context) } }
}