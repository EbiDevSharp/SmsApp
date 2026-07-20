package com.petro.smsapp.data

import android.content.Context
import android.provider.ContactsContract

class ContactsRepository(private val context: Context) {

    /**
     * خواندن مخاطبینی که شماره تلفن دارن، با فیلتر جستجو روی نام یا شماره
     * اگه query خالی باشه، همه مخاطبین رو برمی‌گردونه
     */
    fun searchContacts(query: String): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()
        val seenNumbers = mutableSetOf<String>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection: String?
        val selectionArgs: Array<String>?
        if (query.isBlank()) {
            selection = null
            selectionArgs = null
        } else {
            selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            selectionArgs = arrayOf("%$query%", "%$query%")
        }

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
