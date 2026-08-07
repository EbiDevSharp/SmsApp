package com.petro.smsapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.petro.smsapp.ui.MessageEntry

/**
 * صفِ مشترکِ سشن‌های پاپ‌آپِ پیامک - چه پاپ‌آپ به‌صورتِ Overlay نمایش داده بشه
 * (وقتی صفحه باز است، [com.petro.smsapp.service.PopupOverlayService]) چه به‌صورتِ
 * یه Activity تمام‌صفحه (وقتی صفحه قفله، [QuickReplyPopupActivity])، هر دو دقیقاً
 * از همینجا می‌خونن/می‌نویسن.
 *
 * چرا مشترک: قبلاً هرکدوم صفِ خودشون رو جدا نگه می‌داشتن - کدِ کاملاً تکراری، و
 * اگه وضعیتِ قفل درست وسطِ کار عوض می‌شد این دو تا صفِ کاملاً بی‌خبر از هم می‌تونستن
 * ناهماهنگ بشن (مثلاً پیامِ تازه‌ای که موقعِ قفل‌بودن به صفِ Activity اضافه شده،
 * اگه کاربر همون لحظه قفل رو باز کنه، توسطِ Overlay هیچ‌وقت دیده نمی‌شد). الان
 * صرفِ‌نظر از این‌که کدوم شکلِ نمایش در حالِ استفاده‌ست، منبعِ حقیقتِ سشن‌ها یکیه.
 */
object PopupSessionQueue {

    class Session(
        val threadId: Long,
        val address: String,
        var messageId: Long,
        val displayName: String,
        val photoUri: String?,
        val isKnownContact: Boolean
    ) {
        val messages: SnapshotStateList<MessageEntry> = mutableStateListOf()
        var replyText by mutableStateOf("")
        var lastMessageAtMillis by mutableStateOf(0L)
        var historyLoaded by mutableStateOf(false)
    }

    val sessions: SnapshotStateList<Session> = mutableStateListOf()
    var activeIndex by mutableStateOf(0)

    /**
     * پیامِ تازه‌رسیده رو یا به Sessionِ موجودِ همون مخاطب اضافه می‌کنه (بدونِ اینکه
     * پنجره/اکتیویتیِ فعلی دست بخوره) یا - اگه مخاطب تازه‌ست - یه Session جدید به
     * تهِ صف اضافه می‌کنه. هر دو مسیرِ نمایش (Overlay و Activity) دقیقاً همین متد رو
     * صدا می‌زنن، پس رفتارِ صف‌بندی بینِ حالتِ قفل/باز کاملاً یکسانه.
     *
     * @return true اگه این اولین سشنِ صف بود - یعنی تازه باید خودِ UI پاپ‌آپ ساخته/باز بشه
     */
    fun addIncomingMessage(
        threadId: Long,
        messageId: Long,
        address: String,
        body: String,
        date: Long,
        displayName: String,
        photoUri: String?,
        isKnownContact: Boolean
    ): Boolean {
        val existing = sessions.firstOrNull { it.address == address || (threadId != -1L && it.threadId == threadId) }
        if (existing != null) {
            existing.messages.add(MessageEntry(text = body, isOutgoing = false, timestampMillis = date))
            existing.messageId = messageId
            existing.lastMessageAtMillis = date
            return false
        }

        val session = Session(
            threadId = threadId,
            address = address,
            messageId = messageId,
            displayName = displayName,
            photoUri = photoUri,
            isKnownContact = isKnownContact
        ).apply {
            messages.add(MessageEntry(text = body, isOutgoing = false, timestampMillis = date))
            lastMessageAtMillis = date
        }

        val wasEmpty = sessions.isEmpty()
        sessions.add(session)
        if (wasEmpty) activeIndex = 0
        return wasEmpty
    }

    /** فقط Sessionِ فعال رو از صف برمی‌داره. @return true اگه بعدِ این حذف، کلِ صف خالی شد. */
    fun closeActiveSession(): Boolean {
        if (activeIndex !in sessions.indices) return sessions.isEmpty()
        sessions.removeAt(activeIndex)
        return if (sessions.isEmpty()) {
            activeIndex = 0
            true
        } else {
            if (activeIndex >= sessions.size) activeIndex = sessions.size - 1
            false
        }
    }

    /** کلِ صف رو خالی می‌کنه - برای وقتی کاربر داره میره داخلِ خودِ اپ یا پاپ‌آپ کاملاً بسته میشه. */
    fun clear() {
        sessions.clear()
        activeIndex = 0
    }

    fun switchToNext() {
        if (sessions.size <= 1) return
        activeIndex = (activeIndex + 1) % sessions.size
    }

    fun switchToPrevious() {
        if (sessions.size <= 1) return
        activeIndex = (activeIndex - 1 + sessions.size) % sessions.size
    }
}
