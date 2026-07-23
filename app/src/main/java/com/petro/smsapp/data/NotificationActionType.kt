package com.petro.smsapp.data

/**
 * دکمه‌های قابل‌نمایش روی نوتیفیکیشن پیامک. این یه مجموعه‌ست که قراره در آینده موارد
 * بیشتری (فوروارد، ذخیره‌ی مخاطب و ...) بهش اضافه بشه.
 *
 * هرکدوم یه id ثابت داره که توی AppSettings ذخیره میشه - این یعنی بعداً میشه اسم/آیکن
 * رو عوض کرد بدون اینکه تنظیمات ذخیره‌شده‌ی کاربرهای قبلی خراب بشه (فقط خودِ id نباید
 * تغییر کنه یا حذف بشه).
 */
enum class NotificationActionType(val id: String, val label: String) {
    MARK_READ("mark_read", "خوانده شد"),
    DELETE("delete", "حذف"),
    REPLY("reply", "پاسخ سریع"),
    BLOCK("block", "بلاک"),
    CALL("call", "تماس");

    companion object {
        fun fromId(id: String): NotificationActionType? = entries.find { it.id == id }
    }
}

/**
 * وضعیت یه دکمه‌ی نوتیف: خودِ نوعش + اینکه فعاله یا نه. ترتیب داخل لیست (توی
 * AppSettings.State.notificationActions) همون ترتیب نمایش روی نوتیفه - کاربر از تنظیمات
 * می‌تونه هم جابه‌جاش کنه هم روشن/خاموشش کنه (مثلاً شاید کسی نخواد دکمه‌ی حذف سمت
 * چپ نوتیف باشه).
 */
data class NotificationActionSetting(
    val type: NotificationActionType,
    val enabled: Boolean
)
