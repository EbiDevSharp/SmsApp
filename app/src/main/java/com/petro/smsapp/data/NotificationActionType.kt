package com.petro.smsapp.data

/**
 * دکمه‌های قابل‌نمایش روی نوتیفیکیشن پیامک.
 *
 * BLOCK: قبلاً مستقیم شماره رو بلاک می‌کرد؛ الان چون مقصدِ ثابتی وجود نداره (کاربر N
 * تا گروهِ دلخواه داره)، این دکمه اپ رو باز می‌کنه و یه شیتِ کوچیکِ «به کدوم گروه اضافه
 * بشه؟» نشون میده (NotificationActionReceiver.ACTION_BLOCK).
 */
enum class NotificationActionType(val id: String, val label: String) {
    MARK_READ("mark_read", "خوانده شد"),
    DELETE("delete", "حذف"),
    REPLY("reply", "پاسخ سریع"),
    BLOCK("block", "افزودن به گروه"),
    CALL("call", "تماس");

    companion object {
        fun fromId(id: String): NotificationActionType? = entries.find { it.id == id }
    }
}

data class NotificationActionSetting(
    val type: NotificationActionType,
    val enabled: Boolean
)
