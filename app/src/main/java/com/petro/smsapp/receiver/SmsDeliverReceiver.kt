package com.petro.smsapp.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.petro.smsapp.ActiveThreadTracker
import com.petro.smsapp.MainActivity
import com.petro.smsapp.QuickReplyPopupActivity
import com.petro.smsapp.R
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.service.PopupOverlayService
import com.petro.smsapp.util.SmsAlertSoundPlayer
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

        // تشخیص سیم‌کارتی که پیام روش اومده (dual-SIM)
        // اندروید معمولاً یکی از این extras رو تو SMS_DELIVER می‌ذاره:
        // "subscription" / EXTRA_SUBSCRIPTION_INDEX / "slot" / EXTRA_SLOT_INDEX
        val subscriptionId = resolveIncomingSubscriptionId(context, intent)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, fullBody)
            put(Telephony.Sms.DATE, receivedTimestamp)
            put(Telephony.Sms.DATE_SENT, sentTimestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        val insertedUri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) ?: return
        val messageId = android.content.ContentUris.parseId(insertedUri)
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(sender))

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleFilterAndNotify(context, sender, fullBody, threadId, messageId, receivedTimestamp)
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
        messageId: Long,
        receivedTimestamp: Long
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

        // نکته‌ی مهم (رفعِ باگ): قبلاً همینجا عنوانِ نوتیف مستقیم خودِ sender (شماره‌ی
        // خام) بود و اصلاً سراغِ ContactsCache نمی‌رفت - یعنی حتی برای فرستنده‌هایی که
        // تو مخاطبینِ گوشی ذخیره بودن، نوتیف فقط شماره رو نشون می‌داد. الان دقیقاً
        // هم‌قاعده‌ی بقیه‌ی برنامه (لیستِ مکالمات، صفحه‌ی چت) از ContactsCache.getName
        // استفاده می‌کنیم و اگه مخاطب پیدا نشه، به‌عنوانِ fallback خودِ شماره میاد.
        val displayName = ContactsCache.getName(context, sender) ?: sender

        // عکسِ پروفایلِ مخاطب (اگه فرستنده جزوِ مخاطبینِ ذخیره‌شده‌ی گوشی باشه و عکس
        // داشته باشه) - از همون کشِ مشترکِ ContactsCache که برای آواتارهای توی UI هم
        // استفاده میشه، پس کوئریِ اضافه‌ای به Contacts Provider زده نمیشه.
        val contactPhotoUri = ContactsCache.getPhotoUri(context, sender)

        // اگه کاربر از تنظیمات «پاپ‌آپِ پیامک روی صفحه» رو فعال کرده باشه:
        //
        // نکته‌ی مهم (رفعِ باگ): قبلاً اینجا صرفاً پرمیشنِ overlay چک می‌شد و اگه
        // داده شده بود، همیشه Overlay انتخاب می‌شد - ولی تست‌ها نشون دادن که پنجره‌ی
        // TYPE_APPLICATION_OVERLAY اصلاً روی صفحه‌قفلِ واقعی نمایش داده نمیشه (دقیقاً
        // مثلِ مشکلِ شناخته‌شده‌ی اپ‌هایی مثل Truecaller که رو صفحه‌ی باز کار می‌کنن ولی
        // رو قفل غیب میشن) - پس دقیقاً تو حالتی که مهم‌تر بود (صفحه قفله) داشتیم راهی
        // رو انتخاب می‌کردیم که اصلاً کار نمی‌کرد.
        //
        // الان تصمیم بر اساسِ وضعیتِ واقعیِ قفل گرفته میشه:
        // - صفحه قفله → همیشه QuickReplyPopupActivity (fullScreenIntent) - تنها راهی
        //   که واقعاً رو صفحه‌قفل نمایش داده میشه (با تمِ غیرترنسلوسنتِ Popup)
        // - صفحه بازه + پرمیشنِ overlay داده شده → Overlay، چون fullScreenIntent از
        //   اندروید ۱۰ به بعد وقتی صفحه باز و اپ پس‌زمینه‌ست خودکار اجرا نمیشه
        // - صفحه بازه + پرمیشنِ overlay داده نشده → دیگه معنی نداره از نوتیفِ حداقلیِ
        //   fullScreenIntent استفاده کنیم (که تو این حالت خودکار اجرا نمیشه و فقط یه
        //   نوتیفِ بی‌دکمه می‌مونه) - همون نوتیفِ کاملِ معمولی (با دکمه‌ها) بهتره
        if (AppSettings.isPopupInsteadOfNotificationEnabled(context)) {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            val popupOnLock = AppSettings.isPopupOnLockEnabled(context)
            val popupWhenUnlocked = AppSettings.isPopupWhenUnlockedEnabled(context)

            when {
                // قفل + پاپ‌آپ قفل فعال + پاپ‌آپ از قبل باز
                isLocked && popupOnLock && QuickReplyPopupActivity.isActive ->
                    launchLockedPopupDirectly(context, sender, threadId, messageId, fullBody, receivedTimestamp)
                // قفل + پاپ‌آپ قفل فعال
                isLocked && popupOnLock ->
                    showFullScreenPopupNotification(context, sender, displayName, fullBody, threadId, messageId, receivedAtMillis = receivedTimestamp)
                // صفحه باز + پاپ‌آپ unlocked فعال + overlay
                !isLocked && popupWhenUnlocked && Settings.canDrawOverlays(context) ->
                    PopupOverlayService.show(
                        context = context,
                        threadId = threadId,
                        messageId = messageId,
                        address = sender,
                        body = fullBody,
                        date = receivedTimestamp
                    )
                // در بقیه حالت‌ها (قفل با پاپ‌آپ خاموش، یا unlocked با پاپ‌آپ خاموش،
                // یا unlocked بدون پرمیشن overlay) → نوتیف معمولی
                else ->
                    showNotification(context, sender, displayName, fullBody, threadId, messageId, quickAddTargetGroupName, contactPhotoUri)
            }
        } else {
            showNotification(context, sender, displayName, fullBody, threadId, messageId, quickAddTargetGroupName, contactPhotoUri)
        }
    }

    /**
     * وقتی پاپ‌آپِ حالتِ قفل از قبل زنده‌ست، پیامِ تازه مستقیم بهش تحویل داده
     * میشه (نه از مسیرِ notify()) - نگاهِ [QuickReplyPopupActivity] بالای همین
     * فایل رو برای توضیحِ کاملِ چرایی‌اش ببین. صدا/ویبره هم دستی پخش میشه، چون
     * این مسیر برخلافِ showFullScreenPopupNotification هیچ‌وقت notify() صدا
     * نمی‌زنه.
     */
    private fun launchLockedPopupDirectly(
        context: Context,
        sender: String,
        threadId: Long,
        messageId: Long,
        body: String,
        date: Long
    ) {
        val popupIntent = Intent(context, QuickReplyPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(QuickReplyPopupActivity.EXTRA_THREAD_ID, threadId)
            putExtra(QuickReplyPopupActivity.EXTRA_MESSAGE_ID, messageId)
            putExtra(QuickReplyPopupActivity.EXTRA_ADDRESS, sender)
            putExtra(QuickReplyPopupActivity.EXTRA_BODY, body)
            putExtra(QuickReplyPopupActivity.EXTRA_DATE, date)
        }
        try {
            context.startActivity(popupIntent)
            SmsAlertSoundPlayer.playFromSmsChannel(context)
        } catch (e: Exception) {
            // اگه به هر دلیلی (محدودیتِ OEM و ...) نشد، مسیرِ notify()+fullScreenIntent
            // به‌عنوانِ بک‌آپ کافیه - حداقل یه نوتیف/پاپ‌آپِ اولیه نشون داده میشه
            val displayName = ContactsCache.getName(context, sender) ?: sender
            showFullScreenPopupNotification(context, sender, displayName, body, threadId, messageId, receivedAtMillis = date)
        }
    }

    /**
     * یه نوتیفِ حداقلی (فقط برای اینکه fullScreenIntent مجاز باشه به اجرا دربیاد) که
     * به‌جای نمایشِ خودش، مستقیم QuickReplyPopupActivity رو باز می‌کنه. contentIntent
     * هم به همون اکتیویتی اشاره می‌کنه تا اگه heads-up توسطِ سیستم به‌جای پاپ‌آپِ
     * تمام‌صفحه نشون داده شد (مثلاً وقتی گوشی درحالِ استفاده‌ست)، تپ روی خودِ نوتیف هم
     * بازش کنه.
     */
    private fun showFullScreenPopupNotification(
        context: Context,
        sender: String,
        displayName: String,
        body: String,
        threadId: Long,
        messageId: Long,
        receivedAtMillis: Long
    ) {
        // کانال بی‌صدای مخصوص fullScreenIntent (ساخته‌شده در SmsApplication) تا
        // سیستم خودش صدا نزند؛ صدای واقعی از sms_channel خوانده و دستی پخش می‌شود.
        val popupChannelId = "sms_popup_channel"
        val notificationId = sender.hashCode()

        val popupIntent = Intent(context, QuickReplyPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(QuickReplyPopupActivity.EXTRA_THREAD_ID, threadId)
            putExtra(QuickReplyPopupActivity.EXTRA_MESSAGE_ID, messageId)
            putExtra(QuickReplyPopupActivity.EXTRA_ADDRESS, sender)
            putExtra(QuickReplyPopupActivity.EXTRA_BODY, body)
            putExtra(QuickReplyPopupActivity.EXTRA_DATE, receivedAtMillis)
            putExtra(QuickReplyPopupActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val popupPendingIntent = PendingIntent.getActivity(
            context, notificationId, popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // نکته‌ی مهم (صدا + fullScreenIntent روی قفل):
        // - setSilent روی sms_channel → بعضی OEMها پاپ‌آپ را قطع می‌کنند.
        // - فقط notify روی sms_channel بدون play دستی → اولین پیام اغلب بی‌صدا
        //   (چون fullScreenIntent Activity را می‌گیرد و صدای نوتیف نمی‌آید).
        // - play دستی + صدای کانال باهم → دو بار صدا.
        // راه‌حل: نوتیف روی کانال جدا (sms_popup_channel، بدون صدا) برای fullScreenIntent،
        // و یک‌بار SmsAlertSoundPlayer با صدای sms_channel.
        val notificationBuilder = NotificationCompat.Builder(context, popupChannelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(displayName)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(popupPendingIntent)
            .setFullScreenIntent(popupPendingIntent, true)

        if (AppSettings.isMarkReadOnNotificationDismissEnabled(context)) {
            notificationBuilder.setDeleteIntent(buildDismissMarkReadPendingIntent(context, threadId, notificationId))
        }

        val notification = notificationBuilder.build()

        // فقط یک‌بار صدا از پخش‌کننده مرکزی (کانال نوتیف بی‌صداست)
        SmsAlertSoundPlayer.playFromSmsChannel(context)

        NotificationManagerCompat.from(context).apply {
            try {
                notify(notificationId, notification)
            } catch (e: SecurityException) {
                // پرمیشن نوتیفیکیشن داده نشده
            }
        }
    }

    private fun showNotification(
        context: Context,
        sender: String,
        displayName: String,
        body: String,
        threadId: Long,
        messageId: Long,
        quickAddTargetGroupName: String?,
        contactPhotoUri: String?
    ) {
        val channelId = "sms_channel"
        val notificationId = sender.hashCode()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            putExtra(MainActivity.EXTRA_ADDRESS, sender)
            putExtra(MainActivity.EXTRA_DISPLAY_NAME, displayName)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // اگه فرستنده مخاطبِ شناخته‌شده‌ای با عکسِ پروفایل باشه، همون عکس به‌عنوانِ
        // largeIcon نوتیف نشون داده میشه؛ وگرنه (مخاطب ناشناسه یا عکس نداره) دقیقاً
        // رفتارِ قبلی ادامه پیدا می‌کنه: آیکنِ خودِ اپ.
        val largeIcon = loadContactPhotoBitmap(context, contactPhotoUri) ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_message)
            .setContentTitle(displayName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(displayName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        // اگه کاربر از تنظیمات «خوانده‌شدن با بیرون‌انداختنِ نوتیف» رو فعال کرده باشه،
        // سوایپ‌کردنِ (بیرون‌انداختنِ دستیِ) خودِ نوتیف هم دقیقاً همون کاری رو بکنه که
        // دکمه‌ی «خوانده شد» می‌کنه. توجه: setDeleteIntent فقط با سوایپِ کاربر یا «پاک‌کردنِ
        // همه» صدا زده میشه - نه با تپ‌کردن روی نوتیف (autoCancel) و نه با زدنِ دکمه‌های
        // روی نوتیف (که خودِ اپ مستقیم cancel می‌کنه).
        if (AppSettings.isMarkReadOnNotificationDismissEnabled(context)) {
            builder.setDeleteIntent(buildDismissMarkReadPendingIntent(context, threadId, notificationId))
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

    /**
     * خوندنِ عکسِ پروفایلِ مخاطب از رویِ content:// URI که ContactsCache برگردونده.
     * چون این یه IO بلاکینگه، فقط از داخلِ کوروتینِ روی Dispatchers.IO صدا زده میشه
     * (همون‌جایی که کلِ showNotification از قبل ازش صدا زده میشه). هر خطایی (مخاطب
     * حذف شده، عکس در دسترس نیست و ...) فقط null برمی‌گردونه تا fallback به آیکنِ
     * اپ انجام بشه، نه کرش.
     */
    private fun loadContactPhotoBitmap(context: Context, photoUri: String?): Bitmap? {
        if (photoUri.isNullOrBlank()) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
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

    /**
     * دقیقاً همون Intent/اکشنِ دکمه‌ی «خوانده شد» (ACTION_MARK_READ)، ولی برای
     * setDeleteIntent - یعنی وقتی کاربر خودِ نوتیف رو با سوایپ بیرون می‌ندازه. requestCode
     * جدا (notificationId * 10 + 8) داره تا PendingIntentِ دکمه‌ی «خوانده شد» رو بازنویسی نکنه.
     */
    private fun buildDismissMarkReadPendingIntent(context: Context, threadId: Long, notificationId: Int): PendingIntent {
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            data = Uri.parse("smsapp://dismiss-mark-read/$threadId")
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context, notificationId * 10 + 8, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

    /**
     * از extrasِ intentِ SMS_DELIVER، subscriptionId سیم‌کارتی که پیام روش رسیده
     * رو درمیاره. OEMها کلیدهای مختلفی می‌ذارن؛ این تابع چند مسیر رایج رو چک می‌کنه.
     */
    private fun resolveIncomingSubscriptionId(context: Context, intent: Intent): Int? {
        // مسیر استاندارد (بیشتر دستگاه‌ها)
        var subId = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            subId = intent.getIntExtra("subscription_id", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subId = intent.getIntExtra(
                    SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                )
            }
        }
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) return subId

        // بعضی دستگاه‌ها فقط slot می‌دن → تبدیل به subscriptionId
        var slot = intent.getIntExtra("slot", -1)
        if (slot < 0) slot = intent.getIntExtra("simId", -1)
        if (slot < 0) slot = intent.getIntExtra("phone", -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (slot < 0) {
                slot = intent.getIntExtra(SubscriptionManager.EXTRA_SLOT_INDEX, -1)
            }
        }
        if (slot >= 0) {
            try {
                val sm = context.getSystemService(SubscriptionManager::class.java) ?: return null
                val info = sm.getActiveSubscriptionInfoForSimSlotIndex(slot)
                if (info != null) return info.subscriptionId
            } catch (_: SecurityException) {
                // READ_PHONE_STATE نیست
            }
        }
        return null
    }
}