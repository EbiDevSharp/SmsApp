package com.petro.smsapp.data

/**
 * عملیاتی که با سویپ (کشیدنِ افقیِ) ردیف‌های لیستِ مکالمات قابل‌انجامه.
 */
enum class SwipeAction(val id: String, val label: String) {
    NONE("none", "هیچکدام"),
    MARK_READ("mark_read", "خوانده شدن"),
    MARK_UNREAD("mark_unread", "ناخوانده شدن"),
    DELETE("delete", "حذف"),
    CALL("call", "تماس"),
    BLOCK("block", "افزودن به گروه");

    companion object {
        fun fromId(id: String?, default: SwipeAction = NONE): SwipeAction =
            entries.find { it.id == id } ?: default
    }
}
