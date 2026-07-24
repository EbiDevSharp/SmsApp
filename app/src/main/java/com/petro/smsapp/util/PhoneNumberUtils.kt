package com.petro.smsapp.util

/**
 * تشخیص اینکه یه آدرسِ مکالمه (ستونِ address توی جدول اس‌ام‌اس) واقعاً یه «شماره» هست که
 * میشه بهش پیام فرستاد، یا صرفاً یه Sender ID حروفی (مثلاً اسم اپراتور مثل "ایرانسل"،
 * "همراه‌اول"، یا یه Sender ID انگلیسی مثل "GOOGLE") که سیستم موقع دریافتِ اس‌ام‌اس‌های
 * تبلیغاتی/سرویسی به‌عنوان فرستنده ثبت می‌کنه. این‌جور آدرس‌ها اصلاً شماره نیستن، پس نمیشه
 * (و منطقی نیست) بهشون اس‌ام‌اس ارسالی زد - نه SmsManager می‌تونه واقعاً به یه رشته‌ی حروفی
 * پیامک بفرسته، نه اپراتور قبولش می‌کنه.
 *
 * قاعده: یه آدرس «قابل‌ارسال» حساب میشه اگه حداقل یه رقم داشته باشه و بقیه‌ی کاراکترهاش هم
 * فقط از بین رقم، +، -، فاصله و پرانتز باشن (یعنی هیچ حرفی توش نباشه). کد کوتاه‌های عددی
 * (مثل شماره‌های ۴ یا ۵ رقمیِ سرویس‌ها) هم چون کاملاً عددی‌ان قابل‌ارسال حساب میشن.
 */
object PhoneNumberUtils {
    private val NON_PHONE_CHAR = Regex("[^0-9+\\-() ]")

    fun isSendableAddress(address: String): Boolean {
        if (address.isBlank()) return false
        val hasDigit = address.any { it.isDigit() }
        val onlyPhoneChars = !NON_PHONE_CHAR.containsMatchIn(address)
        return hasDigit && onlyPhoneChars
    }
}
