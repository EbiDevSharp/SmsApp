package com.petro.smsapp.util

/**
 * منطق گروه‌بندی حروف برای نوار کناری الفبا (AlphabetIndexBar) روی لیست اصلی مکالمات.
 *
 * هر مخاطب بر اساس اولین کاراکترِ قویِ displayName (نه address) به یکی از سه دسته
 * می‌ره: الفبای فارسی، الفبای انگلیسی، یا «#» (برای مخاطبینی که اسمِ واقعی ندارن و
 * فقط خودِ شماره نمایش داده میشه - دقیقاً هم‌قاعده‌ی ConversationFilterType.NON_CONTACT
 * که address == displayName رو ملاکِ «مخاطبِ ناشناس» می‌دونه).
 *
 * این کلاس هیچ کوئری‌ای به دیتابیس/Provider نمی‌زنه - فقط روی لیستِ مکالماتی که از
 * قبل توی حافظه‌ست (همون چیزی که ConversationListScreen می‌گیره) محاسبه می‌کنه.
 */
object AlphabetIndexHelper {

    val PERSIAN_ALPHABET = listOf(
        "ا", "ب", "پ", "ت", "ث", "ج", "چ", "ح", "خ", "د", "ذ", "ر", "ز", "ژ",
        "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ک", "گ", "ل", "م",
        "ن", "و", "ه", "ی"
    )

    val ENGLISH_ALPHABET = ('A'..'Z').map { it.toString() }

    const val OTHER_GROUP = "#"

    /** ترتیبِ ثابتِ کامل - فقط برای مرتب کردنِ حروفِ فعال بر همین اساس استفاده میشه */
    val FULL_ORDER: List<String> = PERSIAN_ALPHABET + ENGLISH_ALPHABET + listOf(OTHER_GROUP)

    /**
     * حروفِ فارسیِ مشابه (آ/أ/إ/ٱ و ي/ك عربی) که باید زیرِ همون گروهِ اصلیِ فارسی جمع
     * بشن - وگرنه مثلاً «آرش» یه گروهِ جداگانه از «احمد» می‌شد.
     */
    private val PERSIAN_NORMALIZATION = mapOf(
        'آ' to 'ا', 'أ' to 'ا', 'إ' to 'ا', 'ٱ' to 'ا',
        'ي' to 'ی', 'ك' to 'ک'
    )

    private fun isPersianChar(c: Char): Boolean {
        val code = c.code
        return code in 0x0600..0x06FF || code in 0x0750..0x077F ||
            code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF
    }

    private fun isEnglishLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    /**
     * گروهِ این مخاطب توی نوار - از روی displayName. اگه displayName همون آدرس/شماره
     * باشه (مخاطبِ ناشناس) یا اصلاً حرفِ قابل‌دسته‌بندی نداشته باشه، میره توی «#».
     */
    fun groupFor(displayName: String, address: String): String {
        if (displayName.isBlank() || displayName == address) return OTHER_GROUP
        val firstChar = displayName.trim().firstOrNull { isPersianChar(it) || isEnglishLetter(it) }
            ?: return OTHER_GROUP
        return when {
            isPersianChar(firstChar) -> (PERSIAN_NORMALIZATION[firstChar] ?: firstChar).toString()
            else -> firstChar.uppercaseChar().toString()
        }
    }

    /**
     * از روی یه لیستِ (letter به آدرس‌دهی‌شده)، فقط حروفِ واقعاً موجود رو با ترتیبِ
     * ثابتِ FULL_ORDER برمی‌گردونه - برای پر نشدنِ نوار از حروفِ خالی/غیرموجود.
     */
    fun sortPresentLetters(presentLetters: Set<String>): List<String> =
        FULL_ORDER.filter { it in presentLetters }
}
