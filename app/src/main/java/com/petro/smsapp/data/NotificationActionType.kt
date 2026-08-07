package com.petro.smsapp.data

/**
 * دکمه‌های قابل‌نمایش روی نوتیفیکیشن پیامک.
 *
 * BLOCK: قبلاً مستقیم شماره رو بلاک می‌کرد؛ الان چون مقصدِ ثابتی وجود نداره (کاربر N
 * تا گروهِ دلخواه داره)، این دکمه اپ رو باز می‌کنه و یه شیتِ کوچیکِ «به کدوم گروه اضافه
 * بشه؟» نشون میده (NotificationActionReceiver.ACTION_BLOCK).
 *
 * QUICK_ADD_GROUP: برخلافِ BLOCK، این دکمه هیچ شیت/اپی رو باز نمی‌کنه - مستقیم و
 * بی‌درنگ (توی خودِ ریسیور، بدونِ باز شدنِ اپ) فرستنده رو به همون گروهی که از قبل
 * توی صفحه‌ی تنظیماتِ خودِ گروه به‌عنوانِ «هدفِ افزودنِ سریع» مشخص شده اضافه می‌کنه.
 * اگه هیچ گروهی به‌عنوانِ هدف انتخاب نشده باشه، این دکمه عملاً کاری انجام نمیده.
 */
enum class NotificationActionType(val id: String, val label: String) {
    MARK_READ("mark_read", "خوانده شد"),
    DELETE("delete", "حذف"),
    REPLY("reply", "پاسخ سریع"),
    BLOCK("block", "افزودن به گروه"),
    QUICK_ADD_GROUP("quick_add_group", "افزودن سریع به گروه"),
    CALL("call", "تماس");

    companion object {
        fun fromId(id: String): NotificationActionType? = entries.find { it.id == id }
    }
}

data class NotificationActionSetting(
    val type: NotificationActionType,
    val enabled: Boolean
)

/**
 * نحوه نمایش دکمه‌های اکشن روی پاپ‌آپ پیامک (جدا از نوتیف معمولی).
 * - ICON_AND_LABEL: آیکن + متن (پیش‌فرض فعلی)
 * - ICON_ONLY: فقط آیکن
 * - LABEL_ONLY: فقط متن
 */
enum class PopupActionDisplayMode(val id: String, val label: String) {
    ICON_AND_LABEL("icon_and_label", "آیکن و متن"),
    ICON_ONLY("icon_only", "فقط آیکن"),
    LABEL_ONLY("label_only", "فقط متن");

    companion object {
        fun fromId(id: String?): PopupActionDisplayMode =
            entries.find { it.id == id } ?: ICON_AND_LABEL
    }
}
