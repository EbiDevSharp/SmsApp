package com.petro.smsapp.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * کش تمام مخاطبین گوشی.
 *
 * نکته مهم:
 * Query مربوط به Contacts Provider همیشه روی Dispatchers.IO انجام می‌شود
 * تا Main Thread و Compose را متوقف نکند.
 */
object ContactsCache {

    data class Entry(
        val name: String,
        val photoUri: String?
    )

    @Volatile
    private var cache: Map<String, Entry> = emptyMap()

    @Volatile
    private var isLoaded = false

    @Volatile
    private var isLoading = false

    private var loadJob: Job? = null

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    /**
     * با تغییر این مقدار، UIهایی که collect می‌کنند دوباره Compose می‌شوند.
     */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    private val IRAN_PHONE_PREFIX_REGEX = Regex("^(0098|98|0)")

    /**
     * شروع لود مخاطبین در Background.
     *
     * این تابع هیچ Queryای روی Main Thread انجام نمی‌دهد.
     */
    fun preload(context: Context) {
        if (isLoaded || isLoading) return

        if (!PermissionHelper.hasReadContactsPermission(context)) {
            Log.w(
                "ContactsCache",
                "READ_CONTACTS permission ندارد"
            )
            return
        }

        synchronized(this) {
            if (isLoaded || isLoading) return
            isLoading = true

            loadJob?.cancel()

            loadJob = scope.launch {
                try {
                    val result = loadAllContacts(context.applicationContext)

                    cache = result
                    isLoaded = true

                    Log.d(
                        "ContactsCache",
                        "Contacts loaded in background: ${result.size}"
                    )

                    /*
                     * به UI اطلاع می‌دهیم که کش آماده شده.
                     */
                    _version.value++

                } catch (e: Exception) {
                    Log.e(
                        "ContactsCache",
                        "Error loading contacts",
                        e
                    )
                } finally {
                    isLoading = false
                }
            }
        }
    }

    /**
     * وقتی مخاطبین گوشی تغییر کردند.
     */
    fun invalidate(context: Context? = null) {
        cache = emptyMap()
        isLoaded = false

        synchronized(this) {
            loadJob?.cancel()
            loadJob = null
            isLoading = false
        }

        _version.value++

        /*
         * اگر Context داریم، بلافاصله دوباره در Background لود می‌کنیم.
         */
        if (context != null) {
            preload(context.applicationContext)
        }
    }

    /**
     * دریافت نام از کش.
     *
     * این تابع دیگر هیچ Queryای انجام نمی‌دهد.
     */
    fun getName(
        context: Context,
        address: String
    ): String? {
        /*
         * اگر هنوز لود نشده، فقط Background load را شروع می‌کنیم.
         */
        if (!isLoaded) {
            preload(context)
        }

        val key = normalize(address)

        if (key.isBlank()) {
            return null
        }

        return cache[key]?.name
    }

    /**
     * دریافت URI عکس از کش.
     *
     * این تابع دیگر هیچ Queryای انجام نمی‌دهد.
     */
    fun getPhotoUri(
        context: Context,
        address: String
    ): String? {
        /*
         * اگر هنوز لود نشده، فقط Background load را شروع می‌کنیم.
         */
        if (!isLoaded) {
            preload(context)
        }

        val key = normalize(address)

        if (key.isBlank()) {
            return null
        }

        return cache[key]?.photoUri
    }

    /**
     * آیا کش آماده است؟
     */
    fun isReady(): Boolean {
        return isLoaded
    }

    /**
     * خواندن تمام مخاطبین.
     *
     * این تابع فقط از داخل Dispatchers.IO اجرا می‌شود.
     */
    private fun loadAllContacts(
        context: Context
    ): Map<String, Entry> {

        val map = HashMap<String, Entry>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        try {

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->

                val nameIdx =
                    cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )

                val numberIdx =
                    cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                val photoIdx =
                    cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    )

                if (nameIdx < 0 || numberIdx < 0) {
                    return@use
                }

                while (cursor.moveToNext()) {

                    val number =
                        cursor.getString(numberIdx)
                            ?: continue

                    val name =
                        cursor.getString(nameIdx)
                            ?: continue

                    val photoUri =
                        if (photoIdx >= 0) {
                            cursor.getString(photoIdx)
                        } else {
                            null
                        }

                    val key = normalize(number)

                    if (key.isNotBlank()) {

                        map.putIfAbsent(
                            key,
                            Entry(
                                name = name,
                                photoUri = photoUri
                            )
                        )

                        Log.d(
                            "ContactsCache",
                            "CONTACT: $name | NUMBER: $number | PHOTO_URI: $photoUri"
                        )
                    }
                }
            }

        } catch (e: SecurityException) {

            Log.w(
                "ContactsCache",
                "SecurityException هنگام خواندن مخاطبین",
                e
            )

            return emptyMap()
        }

        return map
    }

    /**
     * نرمال‌سازی شماره تلفن.
     */
    private fun normalize(
        number: String
    ): String {

        val digitsOnly =
            number.filter { it.isDigit() }

        if (digitsOnly.isBlank()) {
            return ""
        }

        val withoutPrefix =
            digitsOnly.replaceFirst(
                IRAN_PHONE_PREFIX_REGEX,
                ""
            )

        val core =
            withoutPrefix.ifBlank {
                digitsOnly
            }

        return if (core.length > 9) {
            core.takeLast(9)
        } else {
            core
        }
    }
}