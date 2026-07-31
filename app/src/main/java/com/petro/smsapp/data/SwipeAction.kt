package com.petro.smsapp.data

/**
 * عملیاتی که با سویپ (کشیدنِ افقیِ) ردیف‌های لیستِ مکالمات قابل‌انجامه.
 * جهتِ اجرای هرکدوم (راست‌به‌چپ / چپ‌به‌راست) از تنظیمات قابل‌تغییره - این enum فقط
 * خودِ «نوع عملیات» رو مشخص می‌کنه، نه اینکه با کدوم جهتِ سویپ اجرا میشه.
 */
enum class SwipeAction(val id: String, val label: String) {
    NONE("none", "هیچکدام"),
    MARK_READ("mark_read", "خوانده شدن"),
    MARK_UNREAD("mark_unread", "ناخوانده شدن"),
    DELETE("delete", "حذف"),
    CALL("call", "تماس"),
    BLOCK("block", "بلاک");

    companion object {
        fun fromId(id: String?, default: SwipeAction = NONE): SwipeAction =
            entries.find { it.id == id } ?: default
    }
}
