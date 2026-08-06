package com.petro.smsapp.util

import android.content.Context
import android.content.res.Configuration
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ThemeMode

/**
 * دلیلِ وجودِ این فایل:
 *
 * وقتی گوشی رو دارکه ولی کاربر تویِ خودِ اپ تمِ «روشن» رو صریحاً انتخاب کرده،
 * Compose (MaterialTheme.colorScheme تویِ Theme.kt) درست رفتار می‌کنه چون خودش
 * settings.themeMode رو می‌خونه. اما resourceهای XML مثل values-night/themes.xml
 * (windowBackground، پرنتِ تمِ اکتیویتی، و هر چیزِ دیگه‌ای که با قید night انتخاب
 * میشه) بر اساسِ Configuration واقعیِ گوشی انتخاب میشن، نه انتخابِ کاربر تویِ اپ -
 * برای همین با اینکه محتوای Compose روشنه، پشتِ صحنه (پیش از رندرِ اول، پشتِ
 * دیالوگ‌های سیستمی، یا هر جزءِ غیرِCompose) هنوز از قیدِ night استفاده میشه و
 * باعثِ ناهماهنگی/نامرئی‌شدنِ بعضی رنگ‌ها میشه (مثلاً نوشته‌ی سفیدِ تمِ تاریک رویِ
 * پس‌زمینه‌ی روشن).
 *
 * راه‌حل: تویِ attachBaseContext هر اکتیویتی، یه Configuration جدید با uiMode
 * دستکاری‌شده می‌سازیم که صریحاً NIGHT_YES یا NIGHT_NO رو انتخابِ کاربر
 * (نه گوشی) نشون بده. اینطوری values-night همیشه دقیقاً هم‌راستا با تمِ
 * انتخاب‌شده‌ی خودِ اپ انتخاب میشه، صرف‌نظر از اینکه خودِ گوشی چه حالتیه.
 */
fun Context.withPersistedThemeConfig(): Context {
    val mode = AppSettings.getThemeModeSync(this)
    // تویِ حالتِ «سیستم» عمداً کاری نمی‌کنیم - همون رفتارِ واقعیِ گوشی درسته
    if (mode == ThemeMode.SYSTEM) return this

    val night = mode == ThemeMode.DARK
    val config = Configuration(resources.configuration)
    config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    return createConfigurationContext(config)
}
