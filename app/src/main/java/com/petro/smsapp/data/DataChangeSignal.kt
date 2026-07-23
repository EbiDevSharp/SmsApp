package com.petro.smsapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * بعضی تغییرات (مثل بلاک‌کردن از روی دکمه‌ی خودِ نوتیف) توی یه BroadcastReceiver جدا
 * از SmsViewModel اتفاق می‌افتن و فقط SharedPreferences رو عوض می‌کنن (نه SMS Provider)،
 * پس ContentObserver خودکار متوجهشون نمیشه. این یه سیگنال ساده‌ی درون‌حافظه‌ست: هر جا
 * یه تغییری از بیرون اتفاق افتاد، عدد رو زیاد کن؛ SmsViewModel بهش گوش میده و لیست‌ها
 * رو دوباره لود می‌کنه.
 */
object DataChangeSignal {
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    fun notifyChanged() {
        _tick.value = _tick.value + 1
    }
}
