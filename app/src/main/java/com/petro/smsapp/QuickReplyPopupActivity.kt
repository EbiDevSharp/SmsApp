package com.petro.smsapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import android.provider.Telephony
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.data.SimRepository
import com.petro.smsapp.data.SmsRepository
import com.petro.smsapp.ui.GroupPickerSheet
import com.petro.smsapp.ui.MessageEntry
import com.petro.smsapp.ui.QuickReplyPopupAction
import com.petro.smsapp.ui.QuickReplyPopupScreen
import com.petro.smsapp.ui.SmsAppTheme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * اکتیویتیِ میزبانِ پاپ‌آپِ پاسخِ سریعِ حالتِ قفل - جایگزینِ نمایشِ نوتیفِ معمولی
 * وقتی کاربر از تنظیمات فعالش کرده. با فلگ‌های showWhenLocked/turnScreenOn روی
 * صفحه/صفحه‌قفل نمایش داده میشه.
 *
 * نکته‌ی مهم (رفعِ باگ): قبلاً هر پیامِ تازه‌ی SmsDeliverReceiver یه notify() با
 * fullScreenIntent جدید می‌ساخت، ولی چون notificationId بر اساسِ
 * sender.hashCode() بود، پیامِ دومِ همون فرستنده روی همون ID فقط «آپدیت» حساب
 * می‌شد - و fullScreenIntent طبقِ رفتارِ خودِ اندروید فقط موقعِ اولین notify()
 * واقعاً یه Activity باز می‌کنه، نه موقعِ آپدیت‌کردنِ یه نوتیفِ موجود. نتیجه: پیامِ
 * دوم دیگه پاپ‌آپ رو باز نمی‌کرد و فقط مثلِ یه نوتیفِ معمولیِ بی‌دکمه تو نوارِ
 * بالا می‌موند.
 *
 * الان دقیقاً هم‌الگوی PopupOverlayService (که همین مشکل رو برای حالتِ Overlay
 * از قبل درست کرده بود): یه صفِ [PopupSessionQueue] بیرونِ خودِ Compose نگه داشته
 * میشه - و این صف دیگه مخصوصِ همین اکتیویتی هم نیست؛ دقیقاً همون صفیه که
 * PopupOverlayService (حالتِ باز) هم ازش استفاده می‌کنه، پس اگه وضعیتِ قفل
 * درست وسطِ کار عوض بشه، پیام‌ها بینِ این دو تا گم/ناهماهنگ نمیشن. با
 * launchMode="singleTop" (تو AndroidManifest) + پیاده‌سازیِ onNewIntent، وقتی این
 * پاپ‌آپ از قبل روی صفحه بازه، پیامِ تازه دیگه Activity جدید نمی‌سازه - همون
 * نمونه‌ی زنده onNewIntent می‌گیره و پیام رو به صف اضافه می‌کنه (یا اگه از همون
 * فرستنده‌ست، به تاریخچه‌ی همون Session، یا اگه فرستنده‌ی تازه‌ست، یه Session
 * جدید به تهِ صف که کاربر با فلش‌های ناوبریِ بالای پاپ‌آپ می‌تونه بینشون سوییچ کنه).
 *
 * نکته‌ی مهم (رفعِ باگِ دوم): notify()+fullScreenIntent فقط دفعه‌ی اولِ پستِ یه
 * نوتیف واقعاً یه Activity رو باز می‌کنه؛ notify()های بعدی (چه آپدیتِ همون ID چه
 * یه ID تازه) وقتی این اکتیویتی از قبل روی صفحه/فورگراند باشه دیگه هیچ‌وقت
 * startActivity/onNewIntent رو صدا نمی‌زنن - نتیجه دقیقاً همون چیزی بود که کاربر
 * می‌دید: پیامِ دوم فقط یه نوتیفِ ساکت می‌موند. برای همین SmsDeliverReceiver الان
 * وقتی این اکتیویتی از قبل زنده‌ست ([isActive])، به‌جای notify()، مستقیم
 * startActivity صدا می‌زنه - چون اپ همون لحظه یه پنجره‌ی قابل‌مشاهده داره، این
 * کار از محدودیتِ استارتِ اکتیویتی از پس‌زمینه مستثناست و دقیقاً onNewIntent رو
 * تریگر می‌کنه.
 */
class QuickReplyPopupActivity : ComponentActivity() {

    private val repository by lazy { SmsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        setShowOnLockScreenFlags()
        handleIntent(intent)

        setContent {
            SmsAppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PopupHost()
                    }
                }
            }
        }
    }

    /**
     * چون launchMode="singleTop" ئه، وقتی این Activity از قبل بالای تاسک باشه،
     * سیستم اینجا (نه onCreate) پیامِ جدید رو تحویل میده.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) activeInstance = null
        // فقط وقتی واقعاً داره بسته میشه (نه موقعِ چرخشِ صفحه یا بازسازیِ سیستمی)
        // صف رو خالی کن؛ وگرنه دفعه‌ی بعد که پاپ‌آپ باز میشه پیام‌های قدیمی رو
        // دوباره نشون میده
        if (isFinishing) {
            PopupSessionQueue.clear()
        }
    }

    /**
     * پیامِ تازه‌رسیده رو یا به Sessionِ موجودِ همون مخاطب اضافه می‌کنه (پنجره
     * دست‌نخورده می‌مونه) یا (اگه فرستنده‌ی تازه‌ست) یه Session جدید به تهِ صف
     * اضافه می‌کنه - دقیقاً هم‌قاعده‌ی PopupOverlayService.handleIncomingMessage.
     */
    private fun handleIntent(intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val date = intent.getLongExtra(EXTRA_DATE, System.currentTimeMillis())
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (notificationId != -1) {
            NotificationManagerCompat.from(this).cancel(notificationId)
        }

        PopupSessionQueue.addIncomingMessage(
            threadId = threadId,
            messageId = messageId,
            address = address,
            body = body,
            date = date,
            displayName = ContactsCache.getName(this, address) ?: address,
            photoUri = ContactsCache.getPhotoUri(this, address),
            isKnownContact = ContactsCache.getName(this, address) != null
        )
        // اگه از قبل یه پاپ‌آپ باز بود، همونجوری که هست می‌مونه؛ کاربر با فلش‌های
        // ناوبریِ بالای پاپ‌آپ (اگه بخواد) می‌تونه بره سراغِ همین Session تازه
    }

    /**
     * از اندروید ۸.۱ (API 27) به بعد از متدهای رسمیِ setShowWhenLocked/setTurnScreenOn
     * استفاده می‌کنیم؛ برای نسخه‌های قدیمی‌تر همون فلگ‌های کلاسیکِ WindowManager.
     */
    private fun setShowOnLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun loadHistoryForSession(session: PopupSessionQueue.Session) {
        if (session.historyLoaded) return
        session.historyLoaded = true
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) { repository.getMessagesForThread(session.threadId) }
            val historyEntries = history
                .filter { it.id != session.messageId }
                .takeLast(5)
                .map { MessageEntry(text = it.body, isOutgoing = it.isOutgoing, timestampMillis = it.date) }
            // اگه کاربر تا اون موقع همین Session رو بسته باشه، دیگه چیزی برای
            // اضافه‌کردن نیست
            if (historyEntries.isNotEmpty() && PopupSessionQueue.sessions.contains(session)) {
                session.messages.addAll(0, historyEntries)
            }
        }
    }

    /** فقط Sessionِ فعال رو از صف برمی‌داره؛ اگه صف خالی شد، کلِ اکتیویتی بسته میشه. */
    private fun closeActiveSession() {
        if (PopupSessionQueue.closeActiveSession()) {
            finish()
        }
    }

    /** بستن کل صف پاپ‌آپ با یک بار زدن × (نه یکی‌یکی برای هر مخاطب). */
    private fun closeEverything() {
        PopupSessionQueue.clear()
        finish()
    }

    private fun switchToNextSession() = PopupSessionQueue.switchToNext()

    private fun switchToPreviousSession() = PopupSessionQueue.switchToPrevious()

    @Composable
    private fun PopupHost() {
        val session = PopupSessionQueue.sessions.getOrNull(PopupSessionQueue.activeIndex)
        if (session == null) {
            // حالتِ گذرا: صف خالیه (مثلاً همه‌ی سشن‌ها همین الان بسته شدن) -
            // چیزی نشون نده و فوراً ببند
            LaunchedEffect(Unit) { finish() }
            return
        }

        var groupPickerGroups by remember { mutableStateOf<List<FilterGroupSummary>?>(null) }

        fun openThread() {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_THREAD_ID, session.threadId)
                putExtra(MainActivity.EXTRA_ADDRESS, session.address)
                putExtra(MainActivity.EXTRA_DISPLAY_NAME, session.displayName)
            }
            startActivity(openIntent)
            // بازکردنِ ترد یعنی کاربر داره میره داخلِ خودِ اپ - کلِ صف (نه فقط
            // Sessionِ فعال) بسته میشه، عیناً هم‌رفتارِ closeEverything تو
            // PopupOverlayService
            PopupSessionQueue.clear()
            finish()
        }

        fun markRead() {
            val threadId = session.threadId
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
                DataChangeSignal.notifyChanged()
                closeActiveSession()
            }
        }

        fun deleteThisMessage() {
            val messageId = session.messageId
            lifecycleScope.launch {
                if (messageId != -1L) withContext(Dispatchers.IO) { repository.deleteMessage(messageId) }
                DataChangeSignal.notifyChanged()
                closeActiveSession()
            }
        }

        fun callSender() {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${session.address}")))
            } catch (e: Exception) {
                // اپِ تماسی پیدا نشد
            }
            closeActiveSession()
        }

        fun sendReply(text: String, subscriptionId: Int?) {
            val now = System.currentTimeMillis()
            session.messages.add(
                MessageEntry(
                    text = text,
                    isOutgoing = true,
                    timestampMillis = now,
                    type = Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                    status = Telephony.Sms.STATUS_PENDING
                )
            )
            val entryIndex = session.messages.lastIndex
            lifecycleScope.launch {
                val messageId = withContext(Dispatchers.IO) {
                    repository.sendSms(session.address, text, subscriptionId)
                }
                if (entryIndex in session.messages.indices) {
                    val prev = session.messages[entryIndex]
                    session.messages[entryIndex] = prev.copy(
                        messageId = messageId ?: -1L,
                        type = if (messageId != null) Telephony.Sms.MESSAGE_TYPE_SENT
                        else Telephony.Sms.MESSAGE_TYPE_FAILED,
                        status = if (messageId != null) Telephony.Sms.STATUS_PENDING
                        else Telephony.Sms.STATUS_FAILED
                    )
                }
                DataChangeSignal.notifyChanged()
                // دیگه بعدِ فرستادنِ پاسخ پنجره بسته نمیشه؛ تیک ارسال/دلیوری روی حباب نشون داده میشه
            }
        }

        fun openGroupPicker() {
            lifecycleScope.launch {
                val filterGroupRepository = AppContainer.filterGroupRepository(this@QuickReplyPopupActivity)
                groupPickerGroups = withContext(Dispatchers.IO) {
                    filterGroupRepository.observeNotificationPickerGroups().first()
                }
            }
        }

        fun addToGroup(groupId: Long) {
            lifecycleScope.launch {
                val filterGroupRepository = AppContainer.filterGroupRepository(this@QuickReplyPopupActivity)
                withContext(Dispatchers.IO) {
                    val added = filterGroupRepository.addNumber(groupId, session.address, session.displayName)
                    if (added) {
                        repository.applyGroupToExistingThreadMessages(groupId, session.threadId, session.address)
                    }
                }
                DataChangeSignal.notifyChanged()
                groupPickerGroups = null
                closeActiveSession()
            }
        }

        fun createGroupAndAdd(name: String) {
            lifecycleScope.launch {
                val filterGroupRepository = AppContainer.filterGroupRepository(this@QuickReplyPopupActivity)
                val groupId = withContext(Dispatchers.IO) {
                    filterGroupRepository.createGroup(
                        name = name,
                        hideFromMainList = false,
                        showNotifications = true,
                        blockNonContacts = false
                    )
                }
                addToGroup(groupId)
            }
        }

        val currentGroups = groupPickerGroups
        if (currentGroups != null) {
            GroupPickerSheet(
                targetLabel = session.displayName,
                groups = currentGroups,
                onPick = { groupId -> addToGroup(groupId) },
                onCreateAndPick = { name -> createGroupAndAdd(name) },
                onDismiss = { groupPickerGroups = null }
            )
        }

        val enabledActionSettings = remember {
            AppSettings.getNotificationActionSettings(this@QuickReplyPopupActivity).filter { it.enabled }
        }

        // اسم گروه هدف «افزودن سریع» - فقط اسم گروه روی دکمه (مثل نوتیف معمولی)
        var quickAddTargetGroupName by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            quickAddTargetGroupName = withContext(Dispatchers.IO) {
                try {
                    val repo = AppContainer.filterGroupRepository(this@QuickReplyPopupActivity)
                    repo.getQuickAddTargetGroupId()?.let { repo.getGroup(it)?.name }
                } catch (_: Exception) {
                    null
                }
            }
        }

        val sims = remember { SimRepository(this@QuickReplyPopupActivity).getActiveSims() }
        var selectedSubscriptionId by remember {
            mutableStateOf(sims.firstOrNull()?.subscriptionId)
        }

        val allActions = remember(enabledActionSettings, session.isKnownContact, quickAddTargetGroupName) {
            buildList {
                // «باز کردن» حذف شد: با تپ روی خود پیام برنامه باز می‌شه
                enabledActionSettings.forEach { setting ->
                    add(
                        when (setting.type) {
                            NotificationActionType.MARK_READ -> QuickReplyPopupAction(
                                setting.type, "خوانده شد", Icons.Filled.Check
                            ) { markRead() }
                            NotificationActionType.DELETE -> QuickReplyPopupAction(
                                setting.type, "حذف", Icons.Filled.Delete
                            ) { deleteThisMessage() }
                            NotificationActionType.REPLY -> QuickReplyPopupAction(
                                setting.type, "پاسخ", Icons.Filled.Reply
                            ) { /* داخلِ خودِ QuickReplyPopupScreen مدیریت میشه */ }
                            NotificationActionType.BLOCK -> QuickReplyPopupAction(
                                setting.type, "افزودن", Icons.Filled.Folder
                            ) { openGroupPicker() }
                            NotificationActionType.QUICK_ADD_GROUP -> QuickReplyPopupAction(
                                setting.type,
                                quickAddTargetGroupName ?: "افزودن سریع به گروه",
                                Icons.Filled.GroupAdd
                            ) {
                                lifecycleScope.launch {
                                    val filterGroupRepository = AppContainer.filterGroupRepository(this@QuickReplyPopupActivity)
                                    val targetId = withContext(Dispatchers.IO) { filterGroupRepository.getQuickAddTargetGroupId() }
                                    if (targetId != null) addToGroup(targetId) else closeActiveSession()
                                }
                            }
                            NotificationActionType.CALL -> QuickReplyPopupAction(
                                setting.type, "تماس", Icons.Filled.Call
                            ) { callSender() }
                        }
                    )
                }
            }
        }

        // دقیقاً هم‌قاعده‌ی سقفِ ۳ دکمه‌ی نوتیفِ معمولی (SmsDeliverReceiver)
        val primary = allActions.take(3)
        val overflow = allActions.drop(3)

        QuickReplyPopupScreen(
            senderDisplayName = session.displayName,
            senderAddress = session.address,
            isKnownContact = session.isKnownContact,
            photoUri = session.photoUri,
            messages = session.messages,
            replyText = session.replyText,
            onReplyTextChange = { session.replyText = it },
            receivedAtMillis = session.lastMessageAtMillis,
            primaryActions = primary,
            overflowActions = overflow,
            // ناوبریِ صف - وقتی همزمان چند مکالمه تو صفِ پاپ‌آپ منتظرن؛
            // totalSessions<=۱ یعنی صف خالیه و کلِ ناوبری مخفی میشه
            totalSessions = PopupSessionQueue.sessions.size,
            currentSessionPosition = PopupSessionQueue.activeIndex + 1,
            onSwitchToPrevious = { switchToPreviousSession() },
            onSwitchToNext = { switchToNextSession() },
            showHistoryButton = !session.historyLoaded,
            onLoadHistory = { loadHistoryForSession(session) },
            onOpenThread = { openThread() },
            onCallSender = { callSender() },
            onSendReply = { text -> sendReply(text, selectedSubscriptionId) },
            // دکمه × کل صف را می‌بندد (نه فقط سشن فعال)
            onClose = { closeEverything() },
            sims = sims,
            selectedSubscriptionId = selectedSubscriptionId,
            onSimSelect = { selectedSubscriptionId = it }
        )

        // به‌روزرسانی تیک ارسال/دلیوری از Telephony
        LaunchedEffect(
            session.messages.size,
            session.messages.map { "${it.messageId}:${it.status}:${it.type}" }.joinToString()
        ) {
            while (true) {
                val pending = session.messages.mapIndexedNotNull { index, entry ->
                    if (entry.isOutgoing && entry.messageId > 0L && !entry.isDelivered && !entry.isFailed) {
                        index to entry.messageId
                    } else null
                }
                if (pending.isEmpty()) break
                kotlinx.coroutines.delay(1500)
                pending.forEach { (index, id) ->
                    val pair = withContext(Dispatchers.IO) { repository.getMessageTypeAndStatus(id) }
                    if (pair != null && index in session.messages.indices) {
                        val (type, status) = pair
                        val prev = session.messages[index]
                        if (prev.type != type || prev.status != status) {
                            session.messages[index] = prev.copy(type = type, status = status)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        // نمونه‌ی زنده‌ی فعلی (اگه وجود داشته باشه) - SmsDeliverReceiver با
        // چک‌کردنِ [isActive] تشخیص میده که آیا این پاپ‌آپ همین الان روی صفحه‌ست
        // یا نه، تا تصمیم بگیره پیامِ تازه رو مستقیم startActivity کنه (onNewIntent
        // رو تریگر می‌کنه) یا از مسیرِ notify()+fullScreenIntent (که برای بازکردنِ
        // اولیه لازمه) بره.
        @Volatile
        private var activeInstance: QuickReplyPopupActivity? = null

        val isActive: Boolean get() = activeInstance != null
    }
}