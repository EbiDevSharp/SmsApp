package com.petro.smsapp.util

/**
 * تشخیصِ لینک‌ها (با یا بدون http/https/www)، شماره‌تلفن‌ها و بقیه‌ی رشته‌های عددیِ
 * غیرتلفنی (مثل کدِ رهگیری) داخلِ متنِ یه پیام. جدا از UI (توی util) نگه داشته شده
 * تا هم قابل تست باشه، هم اگه جای دیگه‌ای از برنامه لازم شد دوباره استفاده بشه.
 *
 * الگوریتم: اول لینک‌ها پیدا میشن، بعد شماره‌تلفن‌ها (با الگوهای اختصاصیِ ایران +
 * الگوی عمومیِ بین‌المللی)، در آخر بقیه‌ی رشته‌های عددی به‌عنوانِ NUMBER معمولی. هر
 * بازه‌ای که از قبل جزوِ یه match قوی‌تر بوده (لینک/تلفن) نادیده گرفته میشه تا یه
 * چیز دوبار هایلایت/کلیک‌پذیر نشه.
 */
object MessageLinkifier {

    enum class MatchType { URL, PHONE_NUMBER, NUMBER }

    data class LinkifyMatch(
        val start: Int,
        val end: Int,
        val value: String,
        val type: MatchType
    )

    // لینک‌های صریح (با http/https/www) + دامنه‌های بدون‌پروتکل که به یکی از پسوندهای
    // رایج ختم میشن (مثلاً example.com یا سایت.ir/صفحه)
    private val URL_REGEX = Regex(
        "(?i)\\b((?:https?://|www\\.)[^\\s<>\"'«»]+" +
                "|[a-z0-9][a-z0-9\\-]{0,62}(?:\\.[a-z0-9][a-z0-9\\-]{0,62})+\\." +
                "(?:com|ir|net|org|io|co|info|biz|me|tv|app|dev|xyz|online|site|top|club|" +
                "shop|store|link|click|live|news|blog|edu|gov|cc|us|uk|de|fr|ru|cn|in|ca|" +
                "au|jp|kr|vip|pro|name|mobi|asia|tech|codes)\\b(?:/[^\\s<>\"'«»]*)?)"
    )

    // شماره‌ی موبایلِ ایران: پیش‌شمارهٔ اختیاریِ +98 / 0098 / 0 + رقمِ 9 + ۹ رقمِ بعدی،
    // با جداکننده‌های اختیاریِ فاصله/خط‌تیره بینِ گروه‌ها (مثلاً 0912 345 6789)
    private const val IRAN_MOBILE = "(?:\\+98|0098|0)?9\\d{2}[-\\s]?\\d{3}[-\\s]?\\d{4}"

    // شماره‌ی ثابتِ ایران: صفرِ اجباری + کدِ شهر (۲ یا ۳ رقم) + ۷ یا ۸ رقمِ باقی‌مانده
    private const val IRAN_LANDLINE = "0\\d{2,3}[-\\s]?\\d{3,4}[-\\s]?\\d{3,4}"

    // شماره‌ی بین‌المللیِ عمومی: با + شروع میشه (کدِ کشور غیر از ۹۸، چون اون بالاتر پوشش داده شده)
    private const val INTL_GENERIC = "\\+(?!98\\b)\\d{1,3}[-\\s]?\\d{2,4}[-\\s]?\\d{3,4}[-\\s]?\\d{2,4}"

    private val PHONE_REGEX = Regex(
        "(?<![\\w@.\\d])($IRAN_MOBILE|$IRAN_LANDLINE|$INTL_GENERIC)(?![\\w@.\\d])"
    )

    // رشته‌ی عددیِ عمومی (غیرتلفنی) - همون قاعده‌ی قبلی، برای چیزهایی مثلِ کدِ رهگیری
    private val NUMBER_REGEX = Regex("(?<![\\w@.])(\\+?[0-9][0-9\\-\\s()]{2,}[0-9])(?![\\w@.])")

    private const val MIN_NUMBER_DIGITS = 4
    // شماره‌تلفن باید حداقل این‌قدر رقمِ خالص داشته باشه، وگرنه به‌اشتباه یه عددِ کوتاه (مثلاً کدِ ۴ رقمی) تلفن حساب میشه
    private const val MIN_PHONE_DIGITS = 7

    fun findMatches(text: String): List<LinkifyMatch> {
        if (text.isBlank()) return emptyList()

        val urlMatches = URL_REGEX.findAll(text).map { m ->
            LinkifyMatch(m.range.first, m.range.last + 1, m.value, MatchType.URL)
        }.toList()

        val phoneMatches = PHONE_REGEX.findAll(text).mapNotNull { m ->
            val start = m.range.first
            val end = m.range.last + 1
            val value = m.value
            if (value.count { it.isDigit() } < MIN_PHONE_DIGITS) return@mapNotNull null
            val overlapsUrl = urlMatches.any { url -> start < url.end && end > url.start }
            if (overlapsUrl) return@mapNotNull null
            LinkifyMatch(start, end, value, MatchType.PHONE_NUMBER)
        }.toList()

        val numberMatches = NUMBER_REGEX.findAll(text).mapNotNull { m ->
            val start = m.range.first
            val end = m.range.last + 1
            val value = m.value
            val digitCount = value.count { it.isDigit() }
            if (digitCount < MIN_NUMBER_DIGITS) return@mapNotNull null
            val overlapsOther = (urlMatches + phoneMatches).any { other -> start < other.end && end > other.start }
            if (overlapsOther) return@mapNotNull null
            LinkifyMatch(start, end, value, MatchType.NUMBER)
        }.toList()

        return (urlMatches + phoneMatches + numberMatches).sortedBy { it.start }
    }
}