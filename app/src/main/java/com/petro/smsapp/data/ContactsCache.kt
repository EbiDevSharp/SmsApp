package com.petro.smsapp.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

/**
 * کش تمام مخاطبین گوشی در حافظه، به‌جای زدن یه Query جدا (PhoneLookup) برای هر مکالمه.
 *
 * قبلاً توی getConversations() برای هر thread یه query جدا به ContactsContract.PhoneLookup
 * زده می‌شد - یعنی برای ۵۰ مکالمه، ۵۰ query اضافه به Contacts Provider. الان کل مخاطبین
 * یه بار (اولین باری که لازم بشه) خونده میشن تو یه HashMap، و بعدش هر lookup فقط یه
 * HashMap.get هست که عملاً O(1) و بدون IPC اضافه‌ست.
 *
 * کلید HashMap فقط ۹ رقم آخر شماره‌ست (نه کل شماره) چون فرمت شماره‌ی ذخیره‌شده تو پیامک
 * (مثلاً +98912...، 0912...، یا 912...) ممکنه دقیقاً با فرمتی که تو مخاطبین ذخیره شده یکی
 * نباشه؛ همون کاری که خودِ PhoneLookup داخلی انجام میده رو با یه نسخه‌ی ساده‌شده شبیه‌سازی می‌کنیم.
 *
 * علاوه بر اسم، آدرسِ عکسِ پروفایلِ مخاطب (ستونِ PHOTO_URI که خودِ Contacts Provider
 * برمی‌گردونه) هم همینجا کش میشه - قبلاً اصلاً خونده نمی‌شد و برای همین هیچ‌جای برنامه
 * (نه لیست مکالمات، نه خودِ چت) عکسِ واقعیِ مخاطب نشون داده نمی‌شد، همیشه فقط دایره‌ی
 * رنگی با حرفِ اول اسم بود.
 *
 * object هست (نه یه کلاس معمولی) چون باید در طول عمر اپ یه کش مشترک باشه، نه اینکه هر بار
 * SmsRepository جدید ساخته میشه از اول خونده بشه.
 *
 * لایه‌ی دفاعی مجوز: قبل از خوندن، مجوز READ_CONTACTS چک میشه؛ اگه نبود، به‌جای کرش،
 * فقط لاگ می‌کنیم و یه کش خالی برمی‌گردونیم (یعنی جای اسم مخاطب، خودِ شماره نشون داده میشه).
 * isLoaded فقط بعد از یه خوندن *موفق* true میشه - یعنی اگه مجوز بعداً داده بشه، دفعه‌ی
 * بعدی که getName صدا زده بشه، خودش دوباره تلاش می‌کنه (نیازی به invalidate دستی نیست).
 * علاوه بر چک اولیه، خودِ query هم توی try/catch(SecurityException) هست - برای حالت
 * نادر race condition (مجوز درست بعد از چک، قبل از اجرای واقعی کوئری برداشته بشه).
 */
object ContactsCache {

    /** اطلاعاتِ کش‌شده‌ی هر مخاطب - name همیشه پره، photoUri فقط اگه مخاطب واقعاً عکس داشته باشه */
    data class Entry(val name: String, val photoUri: String?)

    @Volatile private var cache: Map<String, Entry> = emptyMap()
    @Volatile private var isLoaded = false

    /**
     * پیشوندهای رایجی که شماره‌های ایرانی موقعِ ذخیره تو مخاطبین یا موقعِ دریافتِ
     * پیامک (originatingAddress) ممکنه باهاش شروع بشن: 0098 / 98 / 0 (کاراکترِ +
     * قبلش، چون فقط رقم فیلتر میشه، از قبل حذف شده). ریجکس عمداً به‌ترتیبِ طولِ
     * پیشوند (بلندتر اول) نوشته شده تا "0098" اشتباهی با شاخه‌ی "0" مچ نشه.
     */
    private val IRAN_PHONE_PREFIX_REGEX = Regex("^(0098|98|0)")

    /** موقعی صدا زده میشه که مخاطبین گوشی عوض شده باشن (اضافه/حذف/ادیت) تا دفعه‌ی بعد دوباره خونده بشن */
    fun invalidate() {
        isLoaded = false
    }

    fun getName(context: Context, address: String): String? {
        ensureLoaded(context)
        val key = normalize(address)
        if (key.isBlank()) return null
        return cache[key]?.name
    }

    /** آدرسِ (content://) عکسِ پروفایلِ مخاطب - null اگه مخاطب پیدا نشه یا عکسی نداشته باشه */
    fun getPhotoUri(context: Context, address: String): String? {
        ensureLoaded(context)
        val key = normalize(address)
        if (key.isBlank()) return null
        return cache[key]?.photoUri
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        if (!PermissionHelper.hasReadContactsPermission(context)) {
            Log.w("ContactsCache", "مجوز READ_CONTACTS نیست - اسم/عکس مخاطبین لود نمیشه (به‌جاش خودِ شماره نشون داده میشه)")
            cache = emptyMap()
            return // isLoaded=false می‌مونه، دفعه‌ی بعد که مجوز داده بشه دوباره تلاش میشه
        }
        cache = loadAllContacts(context.applicationContext)
        isLoaded = true
    }

    private fun loadAllContacts(context: Context): Map<String, Entry> {
        val map = HashMap<String, Entry>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                if (nameIdx < 0 || numberIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val photoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val key = normalize(number)
                    // اگه یه شماره تکراری با دو اسم/عکس مختلف بود (بعیده ولی ممکنه)، اولی رو نگه می‌داریم
                    if (key.isNotBlank()) map.putIfAbsent(key, Entry(name, photoUri))
                }
            }
        } catch (e: SecurityException) {
            Log.w("ContactsCache", "SecurityException موقع خوندن مخاطبین - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyMap()
        }
        return map
    }

    /**
     * نرمال‌سازیِ شماره برای استفاده به‌عنوانِ کلیدِ HashMap - مستقل از اینکه شماره با
     * 0، +98، 0098 یا 98 شروع شده باشه یا هیچ‌کدوم.
     *
     * مرحله‌ی اول: با IRAN_PHONE_PREFIX_REGEX پیشوندهای رایجِ ایرانی رو صریحاً حذف
     * می‌کنیم تا به هسته‌ی ۱۰ رقمیِ شماره (بدونِ کدِ کشور/صفرِ ابتدایی) برسیم.
     *
     * مرحله‌ی دوم (لایه‌ی دفاعیِ اضافه): حتی اگه مرحله‌ی اول به هر دلیلی پیشوند رو
     * تشخیص نده (مثلاً یه فرمتِ غیرمنتظره یا شماره‌ی خارجی)، بازم فقط ۹ رقمِ آخر
     * به‌عنوانِ کلیدِ نهایی در نظر گرفته میشه - این باعث میشه هر دو طرفِ مقایسه
     * (شماره‌ی ذخیره‌شده تو مخاطبین و شماره‌ی رسیده تو پیامک) حتی با فرمت‌های
     * متفاوت، سرِ همون هسته‌ی مشترک به هم برسن.
     */
    private fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        if (digitsOnly.isBlank()) return ""
        val withoutPrefix = digitsOnly.replaceFirst(IRAN_PHONE_PREFIX_REGEX, "")
        val core = withoutPrefix.ifBlank { digitsOnly }
        return if (core.length > 9) core.takeLast(9) else core
    }
}