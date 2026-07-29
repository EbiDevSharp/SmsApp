package com.petro.smsapp.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.petro.smsapp.ActiveThreadTracker
import com.petro.smsapp.MainActivity
import com.petro.smsapp.R
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.NotificationActionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * وقتی اپ ما "پیش‌فرض پیامک" باشه، این ریسیور به جای سیستم پیام رو دریافت می‌کنه.
 *
 * چون تصمیم‌گیریِ بلاک‌بودن حالا از روی Room (suspend) میاد، نه SharedPreferences
 * (synchronous)، کل منطق داخل goAsync() + یه coroutine روی Dispatchers.IO اجرا میشه -
 * وگرنه Main Thread تا پایان کوئری‌های دیتابیس قفل می‌موند (ریسک ANR).
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "ناشناس"
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val sentTimestamp = messages[0].timestampMillis
        val receivedTimestamp = System.currentTimeMillis()

        // چون contentResolver.insert سریع و خودِ سیستمه (نه Room)، همینجا synchronous
        // انجامش می‌دیم؛ فقط تصمیم‌گیریِ بلاک/خصوصی/نوتیف که به Room نیاز داره میره تو goAsync
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, fullBody)
            put(Telephony.Sms.DATE, receivedTimestamp)
            put(Telephony.Sms.DATE_SENT, sentTimestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        val insertedUri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return
        val messageId = android.content.ContentUris.parseId(insertedUri)
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(sender))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleBlockAndNotify(context, sender, fullBody, threadId, messageId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBlockAndNotify(
        context: Context,
        sender: String,
        fullBody: String,
        threadId: Long,
        messageId: Long
    ) {
        val privateRepository = AppContainer.privateRepository(context)
        val blockRepository = AppContainer.blockRepository(context)

        if (privateRepository.isAddressPrivate(sender)) {
            return
        }

        val isNumberBlocked = blockRepository.isAddressBlocked(sender)
        val matchedKeyword = blockRepository.findKeywordMatch(fullBody)
        val matchedPattern = if (!isNumberBlocked) blockRepository.findPatternMatch(sender) else null
        val isBlockedAsNonContact = !isNumberBlocked && matchedKeyword == null && matchedPattern == null &&
            AppSettings.isBlockNonContactsEnabled(context) &&
            ContactsCache.getName(context, sender) == null

        if (isNumberBlocked || matchedKeyword != null || matchedPattern != null || isBlockedAsNonContact) {
            if (!isNumberBlocked && matchedKeyword != null) {
                blockRepository.markKeywordBlocked(messageId, matchedKeyword.text)
            } else if (!isNumberBlocked && matchedPattern != null) {
                blockRepository.markPatternBlocked(messageId, matchedPattern.type, matchedPattern.value)
            } else if (isBlockedAsNonContact) {
                blockRepository.markNonContactBlocked(messageId, sender)
            }
            DataChangeSignal.notifyChanged()

            if (!AppSettings.isShowBlockedNotificationsEnabled(context)) {
                return
            }
        }

        if (ActiveThreadTracker.activeThreadId == threadId) {
            return
        }

        showNotification(context, sender, fullBody, threadId, messageId)
    }

    private fun showNotification(
        context: Context,
        sender: String,
        body: String,
        threadId: Long,
        messageId: Long
    ) {
        val channelId = "sms_channel"
        val notificationId = sender.hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            putExtra(MainActivity.EXTRA_ADDRESS, sender)
            putExtra(MainActivity.EXTRA_DISPLAY_NAME, ContactsCache.getName(context, sender) ?: sender)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(sender))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        val enabledActions = AppSettings.getNotificationActionSettings(context)
            .filter { it.enabled }
            .take(3)

        enabledActions.forEach { setting ->
            val action = when (setting.type) {
                NotificationActionType.MARK_READ -> buildMarkReadAction(context, threadId, notificationId)
                NotificationActionType.DELETE -> buildDeleteAction(context, messageId, notificationId)
                NotificationActionType.REPLY -> buildReplyAction(context, sender, notificationId)
                NotificationActionType.BLOCK -> buildBlockAction(context, threadId, sender, notificationId)
                NotificationActionType.CALL -> buildCallAction(context, sender, notificationId)
            }
            builder.addAction(action)
        }

        NotificationManagerCompat.from(context).apply {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // پرمیشن نوتیفیکیشن داده نشده
            }
        }
    }

    private fun buildMarkReadAction(context: Context, threadId: Long, notificationId: Int): NotificationCompat.Action {
        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            data = Uri.parse("smsapp://mark-read/$threadId")
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1, markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_check, "خوانده شد", markReadPendingIntent).build()
    }

    private fun buildDeleteAction(context: Context, messageId: Long, notificationId: Int): NotificationCompat.Action {
        val deleteIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DELETE
            data = Uri.parse("smsapp://delete/$messageId")
            putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_delete, "حذف", deletePendingIntent).build()
    }

    private fun buildBlockAction(context: Context, threadId: Long, address: String, notificationId: Int): NotificationCompat.Action {
        val blockIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_BLOCK
            data = Uri.parse("smsapp://block/$threadId")
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 3, blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_block, "بلاک", blockPendingIntent).build()
    }

    private fun buildCallAction(context: Context, address: String, notificationId: Int): NotificationCompat.Action {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address"))
        val callPendingIntent = PendingIntent.getActivity(
            context, notificationId * 10 + 4, dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_call, "تماس", callPendingIntent).build()
    }

    private fun buildReplyAction(context: Context, address: String, notificationId: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_QUICK_REPLY)
            .setLabel("پاسخ سریع...")
            .build()

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            data = Uri.parse("smsapp://reply/$notificationId")
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 5, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_reply, "پاسخ", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }
}
