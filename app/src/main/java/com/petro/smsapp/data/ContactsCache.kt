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

    @Volatile private var cache: Map<String, String> = emptyMap()
    @Volatile private var isLoaded = false

    /** موقعی صدا زده میشه که مخاطبین گوشی عوض شده باشن (اضافه/حذف/ادیت) تا دفعه‌ی بعد دوباره خونده بشن */
    fun invalidate() {
        isLoaded = false
    }

    fun getName(context: Context, address: String): String? {
        ensureLoaded(context)
        val key = normalize(address)
        if (key.isBlank()) return null
        return cache[key]
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        if (!PermissionHelper.hasReadContactsPermission(context)) {
            Log.w("ContactsCache", "مجوز READ_CONTACTS نیست - اسم مخاطبین لود نمیشه (به‌جاش خودِ شماره نشون داده میشه)")
            cache = emptyMap()
            return // isLoaded=false می‌مونه، دفعه‌ی بعد که مجوز داده بشه دوباره تلاش میشه
        }
        cache = loadAllContacts(context.applicationContext)
        isLoaded = true
    }

    private fun loadAllContacts(context: Context): Map<String, String> {
        val map = HashMap<String, String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (nameIdx < 0 || numberIdx < 0) return@use
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val key = normalize(number)
                    // اگه یه شماره تکراری با دو اسم مختلف بود (بعیده ولی ممکنه)، اولی رو نگه می‌داریم
                    if (key.isNotBlank()) map.putIfAbsent(key, name)
                }
            }
        } catch (e: SecurityException) {
            Log.w("ContactsCache", "SecurityException موقع خوندن مخاطبین - مجوز احتمالاً همین لحظه برداشته شده", e)
            return emptyMap()
        }
        return map
    }

    private fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > 9) digitsOnly.takeLast(9) else digitsOnly
    }
}

