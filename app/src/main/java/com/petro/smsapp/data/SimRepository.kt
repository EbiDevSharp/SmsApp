package com.petro.smsapp.data

import android.content.Context
import android.telephony.SubscriptionManager

class SimRepository(private val context: Context) {

    /**
     * لیست سیم‌کارت‌های فعال گوشی. اگه گوشی تک‌سیم باشه، یه آیتم یا لیست خالی برمی‌گرده
     * و توی UI اصلاً انتخابگر سیم نشون داده نمیشه.
     */
    fun getActiveSims(): List<SimInfo> {
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return try {
            subscriptionManager.activeSubscriptionInfoList?.map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString()?.ifBlank { null }
                        ?: "سیم ${info.simSlotIndex + 1}"
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            // پرمیشن READ_PHONE_STATE هنوز داده نشده
            emptyList()
        }
    }
}
