package com.petro.smsapp.util

/**
 * محاسبه‌ی تعداد کاراکتر باقی‌مانده و تعداد پیامک (segment) طبق همون قاعده‌ای که خودِ
 * اپراتور/سیستم موقع ارسال SMS ازش استفاده می‌کنه - کاملاً مستقل از UI، تا هم توی
 * نوار ارسال (MessageInputBar) و هم زیر هر حباب پیام (ThreadScreen) قابل استفاده‌ی
 * مجدد باشه.
 *
 * قاعده‌ی استاندارد GSM 03.38:
 * - اگه متن فقط شامل کاراکترهای الفبای پایه‌ی GSM 7-bit باشه (حروف انگلیسی، اعداد،
 *   علائم رایج و چندتا حرف یونانی/لاتین خاص)، هر پیامک تکی حداکثر ۱۶۰ کاراکتره و
 *   وقتی چندبخشی (concatenated) میشه، هر بخش ۱۵۳ کاراکتر جا داره (۷ بایت از هر بخش
 *   صرفِ هدرِ اتصال‌دهنده‌ی بخش‌ها به هم میشه).
 * - بعضی کاراکترهای GSM 7-bit (مثل [ ] { } ^ ~ \ | و €) «توسعه‌یافته» حساب میشن و هرکدوم
 *   واقعاً ۲ کاراکتر از ظرفیت پیامک رو می‌گیرن (چون نیاز به یه کاراکترِ Escape دارن).
 * - اگه متن حتی یه کاراکترِ خارج از این الفبا داشته باشه (فارسی، عربی، ایموجی و هر
 *   یونیکدِ دیگه) کلِ پیامک از حالت GSM 7-bit خارج و UCS-2 میشه: هر پیامک تکی فقط ۷۰
 *   کاراکتر و هر بخشِ چندبخشی فقط ۶۷ کاراکتر جا داره.
 */
object SmsSegmentCalculator {

    data class SmsSegmentInfo(
        // تعداد کاراکترِ باقی‌مونده تا رسیدن به مرزِ ظرفیتِ فعلی (می‌تونه صفر باشه، منفی نمیشه
        // چون به‌محض رد شدن از مرز، segmentCount خودش زیاد میشه و ظرفیت‌ کل بازمحاسبه میشه)
        val remainingChars: Int,
        // تعداد پیامکی که برای ارسال این متن لازمه (حداقل ۱، حتی برای متن خالی)
        val segmentCount: Int,
        // true یعنی متن به‌خاطر داشتنِ کاراکتر غیرِ GSM-7 (مثلاً فارسی) به‌صورت UCS-2 ارسال میشه
        val isUnicode: Boolean
    )

    private const val SINGLE_SEGMENT_GSM7 = 160
    private const val MULTIPART_SEGMENT_GSM7 = 153
    private const val SINGLE_SEGMENT_UCS2 = 70
    private const val MULTIPART_SEGMENT_UCS2 = 67

    // الفبای پایه‌ی GSM 7-bit (هر کاراکتر = ۱ واحد ظرفیت)
    private val GSM_7BIT_BASIC =
        "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡" +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"

    // کاراکترهای «توسعه‌یافته»ی GSM 7-bit (هرکدوم = ۲ واحد ظرفیت، چون نیاز به Escape دارن)
    private val GSM_7BIT_EXTENDED = "^{}\\[~]|€"

    private fun charUnits(c: Char): Int? = when {
        GSM_7BIT_BASIC.contains(c) -> 1
        GSM_7BIT_EXTENDED.contains(c) -> 2
        else -> null // یعنی این کاراکتر اصلاً تو GSM 7-bit نیست (مثلاً فارسی)
    }

    /** true یعنی کلِ متن با الفبای GSM 7-bit قابل‌نمایشه (بدونِ نیاز به UCS-2) */
    private fun isGsm7Compatible(text: String): Boolean = text.all { charUnits(it) != null }

    fun calculate(text: String): SmsSegmentInfo {
        val isUnicode = !isGsm7Compatible(text)

        val length = if (isUnicode) {
            text.length
        } else {
            text.sumOf { charUnits(it) ?: 1 }
        }

        val singleLimit = if (isUnicode) SINGLE_SEGMENT_UCS2 else SINGLE_SEGMENT_GSM7
        val multipartLimit = if (isUnicode) MULTIPART_SEGMENT_UCS2 else MULTIPART_SEGMENT_GSM7

        val segmentCount = when {
            length <= singleLimit -> 1
            else -> kotlin.math.ceil(length.toDouble() / multipartLimit).toInt()
        }

        val totalCapacity = if (segmentCount <= 1) singleLimit else multipartLimit * segmentCount
        val remainingChars = totalCapacity - length

        return SmsSegmentInfo(
            remainingChars = remainingChars,
            segmentCount = segmentCount,
            isUnicode = isUnicode
        )
    }
}
