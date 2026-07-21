package com.petro.smsapp.util

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) بدون هیچ کتابخانه‌ی جانبی.
 *
 * الگوریتم ریاضی استاندارد و عمومیه (همون چیزی که کتابخونه‌های معروف jalaali مثل
 * jalaali-js هم بر پایه‌اش کار می‌کنن) - نسخه‌ی خودمونه، فقط منطق حسابی رو پیاده کرده.
 * ورودی و خروجی هر دو year/month(1-12)/day هستن.
 */
object JalaliCalendar {

    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    data class YMD(val year: Int, val month: Int, val day: Int)

    /** تبدیل تاریخ میلادی (year, month 1-12, day) به شمسی */
    fun toJalali(gYear: Int, gMonth: Int, gDay: Int): YMD {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gMonth > 2) gYear + 1 else gYear

        var days = 355666 + (365 * gYear) +
            ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) +
            gDay + gDaysInMonth[gMonth - 1]

        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }

        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return YMD(jy, jm, jd)
    }

    /** تبدیل تاریخ شمسی به میلادی (year, month 1-12, day) - برای آینده (مثلاً انتخاب‌گر تاریخ شمسی) */
    fun toGregorian(jYear: Int, jMonth: Int, jDay: Int): YMD {
        var jy = jYear + 1595
        var days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jDay +
            if (jMonth < 7) (jMonth - 1) * 31 else ((jMonth - 7) * 30) + 186

        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }

        var gd = days + 1
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        if (isLeap) gDaysInMonth[2] = 29

        var gm = 0
        while (gm < 13 && gd > gDaysInMonth[gm]) {
            gd -= gDaysInMonth[gm]
            gm++
        }
        return YMD(gy, gm, gd)
    }
}
