package com.petro.smsapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * وجود این ریسیور برای "اپ پیش‌فرض پیامک" شدن الزامیه،
 * حتی اگه فعلا MMS رو کامل پیاده‌سازی نکنیم.
 * پیاده‌سازی کامل MMS (دانلود از طریق APN، پارس PDU) پیچیده‌ست؛
 * فعلا فقط اسکلتش رو می‌ذاریم که اپ کرش نکنه.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TODO: در آینده - دانلود و پارس MMS از طریق transaction-id
    }
}
