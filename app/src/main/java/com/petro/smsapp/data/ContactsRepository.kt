package com.petro.smsapp.data

import android.content.Context
import android.provider.ContactsContract

class ContactsRepository(private val context: Context) {

    /**
     * خواندن مخاطبینی که شماره تلفن دارن، با فیلتر جستجو روی نام یا شماره.
     *
     * اگه query خالی باشه، لیست خالی برمی‌گردونیم - قبلاً اینجا با کوئری خالی کل
     * مخاطبین گوشی برگردونده می‌شد (یعنی همون لحظه‌ی باز شدنِ صفحه‌ی «پیام جدید»
     * کل مخاطبین لود می‌شدن)، که هم غیرضروری بود هم کند. الان فقط وقتی کاربر واقعاً
     * چیزی تایپ کرده باشه نتیجه‌ای برمی‌گرده.
     *
     * شماره‌های ذخیره‌شده تو گوشی معمولاً به‌فرمت +98938... یا 0098938... یا 938...
     * هستن - یعنی صفرِ ابتداییِ رایج (مثل 0938...) توشون نیست. برای اینکه کاربر بتونه
     * هم با صفر تایپ کنه (0938...) هم بدونش (938...)، یه نسخه‌ی بدون صفرِ ابتدایی از
     * کوئری هم می‌سازیم و همه‌چیز رو توی همون یک کوئری دیتابیس (نه چند کوئری جدا) با
     * OR اضافه می‌کنیم.
     */
    fun searchContacts(query: String): List<ContactInfo> {
        if (query.isBlank()) return emptyList()

        val digitsOnly = query.filter { it.isDigit() }
        val digitsNoLeadingZero = if (digitsOnly.length > 1 && digitsOnly.startsWith("0")) {
            digitsOnly.trimStart('0')
        } else {
            digitsOnly
        }

        val selectionParts = mutableListOf(
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        )
        val selectionArgs = mutableListOf("%$query%", "%$query%")

        // فقط وقتی نسخه‌ی بدون صفر واقعاً چیزی اضافه می‌کنه (یعنی کوئری با صفر شروع
        // شده بود و بعد از حذفش هنوز رقمی مونده) این شرطِ اضافه رو به کوئری می‌زنیم
        if (digitsNoLeadingZero.isNotBlank() && digitsNoLeadingZero != query) {
            selectionParts.add("${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?")
            selectionArgs.add("%$digitsNoLeadingZero%")
        }

        return queryContacts(selectionParts.joinToString(" OR "), selectionArgs.toTypedArray())
    }

    /**
     * کل مخاطبینِ دارای شماره‌ی گوشی - فقط برای صفحه‌ی داخلیِ «انتخاب چندتاییِ مخاطبین»
     * (ContactPickerScreen) صدا زده میشه، یعنی فقط وقتی کاربر صریحاً همون دکمه رو بزنه؛
     * نه به‌صورت خودکار موقع باز شدنِ صفحه‌ی «پیام جدید».
     */
    fun getAllContacts(): List<ContactInfo> = queryContacts(selection = null, selectionArgs = null)

    private fun queryContacts(selection: String?, selectionArgs: Array<String>?): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()
        val seenNumbers = mutableSetOf<String>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            uri, projection, selection, selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIdx) ?: continue
                val normalized = number.replace(" ", "").replace("-", "")
                // جلوگیری از تکراری نشون دادن یه شماره (مخاطب می‌تونه چند شماره یکسان ذخیره‌شده داشته باشه)
                if (!seenNumbers.add(normalized)) continue

                contacts.add(
                    ContactInfo(
                        contactId = cursor.getLong(idIdx),
                        name = cursor.getString(nameIdx) ?: number,
                        phoneNumber = number
                    )
                )
            }
        }
        return contacts
    }
}
