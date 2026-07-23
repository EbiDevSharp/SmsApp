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
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.BlockStore
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.data.PrivateStore

/**
 * وقتی اپ ما "پیش‌فرض پیامک" باشه، این ریسیور به جای سیستم پیام رو دریافت می‌کنه
 * و خودمون مسئول ذخیره‌ش توی SMS Provider هستیم (سیستم دیگه خودکار ذخیره نمی‌کنه)
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "ناشناس"
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        // زمانی که شبکه/فرستنده پیام رو فرستاده (از PDU میاد)
        val sentTimestamp = messages[0].timestampMillis
        // زمانی که این گوشی واقعاً پیام رو پردازش کرده - اگه گوشی خاموش بوده، این خیلی دیرتر از sentTimestamp میشه
        val receivedTimestamp = System.currentTimeMillis()

        // ذخیره توی content provider سیستم (جدول Sms.Inbox)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, fullBody)
            put(Telephony.Sms.DATE, receivedTimestamp)
            put(Telephony.Sms.DATE_SENT, sentTimestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.let { insertedUri ->
            val messageId = android.content.ContentUris.parseId(insertedUri)
            val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(sender))

            // شماره‌ی بلاک‌شده: پیام همچنان ذخیره میشه (تا توی صفحه‌ی «پیامک‌های بلاک‌شده»
            // قابل دیدن باشه) ولی هیچ نوتیف و هیچ صدایی نمیده، و از لیست اصلی هم مخفیه
            // (فیلترش توی SmsRepository.getConversations انجام میشه).
            if (BlockStore.isThreadBlocked(context, threadId)) {
                return
            }

            // شماره‌ی خصوصی‌شده: دقیقاً همون منطق بلاک - پیام ذخیره میشه (برای صفحه‌ی
            // «پیامک‌های خصوصی») ولی هیچ نوتیف/صدایی نمیده و از لیست اصلی مخفیه.
            if (PrivateStore.isThreadPrivate(context, threadId)) {
                return
            }

            // اگه کاربر همین الان (توی فورگراند) داخل همین مکالمه‌ست، لازم نیست نوتیف/صدا بدیم -
            // چون ContentObserver توی ViewModel خودش صفحه‌ی چت رو زنده آپدیت می‌کنه و کاربر
            // همون لحظه پیام رو روی صفحه می‌بینه.
            if (ActiveThreadTracker.activeThreadId == threadId) {
                return
            }

            showNotification(context, sender, fullBody, threadId, messageId)
        }
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

        // آیکن کوچیکِ نوار وضعیت طبق قانون خودِ اندروید همیشه یه‌رنگ/سیلوئته (نمیشه رنگی
        // نشونش داد، محدودیت خودِ سیستم‌عامله). چیزی که واقعاً «آیکن اپ» رو قابل تشخیص
        // می‌کنه large icon هست که میشه رنگی/کامل نشونش داد - قبلاً اصلاً ست نمی‌شد.
        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(sender)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        // دکمه‌های نوتیف داینامیکن: ترتیب و روشن/خاموش بودنشون از تنظیمات میاد (صفحه‌ی
        // «دکمه‌های نوتیفیکیشن» تو تنظیمات) و می‌تونه جابه‌جا/فعال-غیرفعال بشه. اندروید
        // معمولاً بیشتر از ۳ تا اکشن رو خوب نشون نمی‌ده (بسته به لانچر/OEM ممکنه بقیه رو
        // قایم کنه یا overflow کنه)، پس فقط ۳ تای اول از دکمه‌های فعال رو اضافه می‌کنیم.
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
        // ACTION_DIAL (نه ACTION_CALL) چون فقط شماره‌گیر رو با شماره‌ی پرشده باز می‌کنه،
        // بدون نیاز به مجوز CALL_PHONE - کاربر خودش باید دکمه‌ی تماس رو تو دایلر بزنه
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
        // برخلاف بقیه‌ی اکشن‌ها، PendingIntent مربوط به RemoteInput باید MUTABLE باشه
        // (سیستم لازمه جواب تایپ‌شده رو داخل همین Intent قرار بده) - FLAG_IMMUTABLE
        // باعث میشه پاسخ سریع اصلاً کار نکنه (خصوصاً روی اندروید ۱۲ به بالا).
        val replyPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 5, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(R.drawable.ic_reply, "پاسخ", replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()
    }
}
