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
 * تشخیصِ گروهِ فیلتر از روی Room (suspend) میاد، پس کل منطق داخل goAsync() + یه
 * coroutine روی Dispatchers.IO اجرا میشه.
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
                handleFilterAndNotify(context, sender, fullBody, threadId, messageId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleFilterAndNotify(
        context: Context,
        sender: String,
        fullBody: String,
        threadId: Long,
        messageId: Long
    ) {
        val privateRepository = AppContainer.privateRepository(context)
        val filterGroupRepository = AppContainer.filterGroupRepository(context)

        if (privateRepository.isAddressPrivate(sender)) {
            return
        }

        // به‌ترتیبِ اولویت، اولین گروهی که مچ بشه برنده‌ست
        val matched = filterGroupRepository.findMatchingGroup(context, sender, fullBody)
        if (matched != null) {
            filterGroupRepository.markMatched(messageId, matched.group.id, matched.matchType, matched.matchedValue)
            DataChangeSignal.notifyChanged()

            if (!matched.group.showNotifications) {
                return
            }
        }

        if (ActiveThreadTracker.activeThreadId == threadId) {
            return
        }

        // اسمِ همون یه گروهی که الان هدفِ «افزودن سریع» ئه - برای نمایش روی خودِ دکمه‌ی
        // نوتیف (مثلاً «افزودن به تبلیغاتی»)، تا کاربر بدونِ باز کردنِ اپ بدونه این دکمه
        // دقیقاً کجا اضافه می‌کنه. اگه هیچ گروهی هدف نباشه null می‌مونه.
        val quickAddTargetGroupName = filterGroupRepository.getQuickAddTargetGroupId()
            ?.let { filterGroupRepository.getGroup(it)?.name }

        showNotification(context, sender, fullBody, threadId, messageId, quickAddTargetGroupName)
    }

    private fun showNotification(
        context: Context,
        sender: String,
        body: String,
        threadId: Long,
        messageId: Long,
        quickAddTargetGroupName: String?
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
                NotificationActionType.BLOCK -> buildAddToGroupAction(context, threadId, sender, notificationId)
                NotificationActionType.QUICK_ADD_GROUP -> buildQuickAddGroupAction(context, threadId, sender, notificationId, quickAddTargetGroupName)
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

    /**
     * قبلاً این دکمه مستقیم شماره رو بلاک می‌کرد. الان چون مقصدِ ثابتی نیست (کاربر N
     * تا گروهِ دلخواه داره)، این دکمه اپ رو باز می‌کنه و یه شیتِ کوچیکِ «به کدوم گروه اضافه
     * بشه؟» نشون میده (NotificationActionReceiver.ACTION_BLOCK خودِ کارِ باز کردنِ اپ رو انجام میده).
     */
    private fun buildAddToGroupAction(context: Context, threadId: Long, address: String, notificationId: Int): NotificationCompat.Action {
        val blockIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_BLOCK
            data = Uri.parse("smsapp://add-to-group/$threadId")
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val blockPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 3, blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_block, "افزودن به گروه", blockPendingIntent).build()
    }

    /**
     * برخلافِ BLOCK، این دکمه هیچ اپ/شیتی رو باز نمی‌کنه - مستقیم و بی‌درنگ توسطِ
     * NotificationActionReceiver (goAsync) فرستنده رو به همون گروهی که از قبل توی
     * صفحه‌ی تنظیماتِ خودِ گروه به‌عنوانِ «هدفِ افزودنِ سریع» انتخاب شده اضافه می‌کنه.
     *
     * برچسبِ خودِ دکمه هم دیگه ثابت نیست - اگه یه گروهِ هدف انتخاب شده باشه، مستقیم
     * اسمِ همون گروه روش نشون داده میشه (مثلاً «افزودن به تبلیغاتی») تا کاربر بدونِ
     * باز کردنِ اپ بدونه این دکمه دقیقاً کجا اضافه می‌کنه. اگه هیچ گروهی هدف نباشه،
     * برچسبِ عمومیِ قبلی می‌مونه (زدنش هم در این حالت کاری انجام نمیده).
     */
    private fun buildQuickAddGroupAction(
        context: Context,
        threadId: Long,
        address: String,
        notificationId: Int,
        quickAddTargetGroupName: String?
    ): NotificationCompat.Action {
        val quickAddIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_QUICK_ADD_GROUP
            data = Uri.parse("smsapp://quick-add-group/$threadId")
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val quickAddPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 6, quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = quickAddTargetGroupName ?: "افزودن سریع به گروه"
        return NotificationCompat.Action.Builder(R.drawable.ic_group_add, label, quickAddPendingIntent).build()
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
