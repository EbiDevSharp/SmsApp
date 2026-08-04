package com.petro.smsapp.util

/**
 * تشخیصِ لینک‌ها (با یا بدون http/https/www - رجیکسِ کامل، نه فقط پروتکل‌دار) و
 * رشته‌های عددی (شماره یا هر عددِ به‌اندازه‌ی کافی طولانی) داخلِ متنِ یه پیام. جدا از
 * UI (توی util) نگه داشته شده تا هم قابل تست باشه، هم اگه جای دیگه‌ای از برنامه لازم
 * شد (نه فقط حبابِ چت) همینجا دوباره استفاده بشه.
 *
 * الگوریتم: اول همه‌ی لینک‌ها پیدا میشن؛ بعد دنبالِ رشته‌های عددی می‌گردیم، ولی هر
 * بازه‌ای که از قبل جزوِ یه لینک بوده (مثلاً پورت یا بخشی از دامنه/مسیر) نادیده گرفته
 * میشه تا یه چیز دوبار هایلایت/کلیک‌پذیر نشه.
 */
object MessageLinkifier {

    enum class MatchType { URL, NUMBER }

    data class LinkifyMatch(
        val start: Int,
        val end: Int,
        val value: String,
        val type: MatchType
    )

    // لینک‌های صریح (با http/https/www) + دامنه‌های بدون‌پروتکل که به یکی از پسوندهای
    // رایج ختم میشن (مثلاً example.com یا سایت.ir/صفحه) - چون بدونِ محدودکردنِ پسوند،
    // هر رشته‌ی حاوی نقطه (مثلِ "۳.۵" یا اسمِ فایل) به‌اشتباه لینک تشخیص داده می‌شد.
    private val URL_REGEX = Regex(
        "(?i)\\b((?:https?://|www\\.)[^\\s<>\"'«»]+" +
            "|[a-z0-9][a-z0-9\\-]{0,62}(?:\\.[a-z0-9][a-z0-9\\-]{0,62})+\\." +
            "(?:com|ir|net|org|io|co|info|biz|me|tv|app|dev|xyz|online|site|top|club|" +
            "shop|store|link|click|live|news|blog|edu|gov|cc|us|uk|de|fr|ru|cn|in|ca|" +
            "au|jp|kr|vip|pro|name|mobi|asia|tech|codes)\\b(?:/[^\\s<>\"'«»]*)?)"
    )

    // رشته‌ی عددی: با + اختیاری شروع میشه، وسطش می‌تونه رقم/فاصله/خط‌تیره/پرانتز باشه.
    // lookbehind/lookahead جلوی چسبیدن به حروف یا نقطه رو می‌گیره (مثلاً وسطِ ایمیل یا
    // یه عددِ اعشاری قاطی نشه).
    private val NUMBER_REGEX = Regex("(?<![\\w@.])(\\+?[0-9][0-9\\-\\s()]{2,}[0-9])(?![\\w@.])")

    private const val MIN_NUMBER_DIGITS = 4

    fun findMatches(text: String): List<LinkifyMatch> {
        if (text.isBlank()) return emptyList()

        val urlMatches = URL_REGEX.findAll(text).map { m ->
            LinkifyMatch(m.range.first, m.range.last + 1, m.value, MatchType.URL)
        }.toList()

        val numberMatches = NUMBER_REGEX.findAll(text).mapNotNull { m ->
            val start = m.range.first
            val end = m.range.last + 1
            val value = m.value
            val digitCount = value.count { it.isDigit() }
            if (digitCount < MIN_NUMBER_DIGITS) return@mapNotNull null
            val overlapsUrl = urlMatches.any { url -> start < url.end && end > url.start }
            if (overlapsUrl) return@mapNotNull null
            LinkifyMatch(start, end, value, MatchType.NUMBER)
        }.toList()

        return (urlMatches + numberMatches).sortedBy { it.start }
    }
}
