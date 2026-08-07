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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.data.NotificationActionType
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
 * از قبل درست کرده بود): یه صفِ [PopupQueue.sessions] بیرونِ خودِ Compose نگه
 * داشته میشه. با launchMode="singleTop" (تو AndroidManifest) + پیاده‌سازیِ
 * onNewIntent، وقتی این پاپ‌آپ از قبل روی صفحه بازه، پیامِ تازه دیگه Activity
 * جدید نمی‌سازه - همون نمونه‌ی زنده onNewIntent می‌گیره و پیام رو به صف اضافه
 * می‌کنه (یا اگه از همون فرستنده‌ست، به تاریخچه‌ی همون Session، یا اگه فرستنده‌ی
 * تازه‌ست، یه Session جدید به تهِ صف که کاربر با فلش‌های ناوبریِ بالای پاپ‌آپ
 * می‌تونه بینشون سوییچ کنه).
 */
class QuickReplyPopupActivity : ComponentActivity() {

    private val repository by lazy { SmsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // فقط وقتی واقعاً داره بسته میشه (نه موقعِ چرخشِ صفحه یا بازسازیِ سیستمی)
        // صف رو خالی کن؛ وگرنه دفعه‌ی بعد که پاپ‌آپ باز میشه پیام‌های قدیمی رو
        // دوباره نشون میده
        if (isFinishing) {
            PopupQueue.sessions.clear()
            PopupQueue.activeIndex = 0
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

        val existing = PopupQueue.sessions.firstOrNull {
            it.address == address || (threadId != -1L && it.threadId == threadId)
        }
        if (existing != null) {
            existing.messages.add(MessageEntry(text = body, isOutgoing = false, timestampMillis = date))
            existing.messageId = messageId
            existing.lastMessageAtMillis = date
            return
        }

        val session = PopupQueue.Session(
            threadId = threadId,
            address = address,
            messageId = messageId,
            displayName = ContactsCache.getName(this, address) ?: address,
            photoUri = ContactsCache.getPhotoUri(this, address),
            isKnownContact = ContactsCache.getName(this, address) != null
        ).apply {
            messages.add(MessageEntry(text = body, isOutgoing = false, timestampMillis = date))
            lastMessageAtMillis = date
        }

        PopupQueue.sessions.add(session)
        if (PopupQueue.sessions.size == 1) {
            PopupQueue.activeIndex = 0
        }
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

    private fun loadHistoryForSession(session: PopupQueue.Session) {
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
            if (historyEntries.isNotEmpty() && PopupQueue.sessions.contains(session)) {
                session.messages.addAll(0, historyEntries)
            }
        }
    }

    /** فقط Sessionِ فعال رو از صف برمی‌داره؛ اگه صف خالی شد، کلِ اکتیویتی بسته میشه. */
    private fun closeActiveSession() {
        val index = PopupQueue.activeIndex
        if (index !in PopupQueue.sessions.indices) return
        PopupQueue.sessions.removeAt(index)
        if (PopupQueue.sessions.isEmpty()) {
            finish()
        } else if (PopupQueue.activeIndex >= PopupQueue.sessions.size) {
            PopupQueue.activeIndex = PopupQueue.sessions.size - 1
        }
    }

    private fun switchToNextSession() {
        if (PopupQueue.sessions.size <= 1) return
        PopupQueue.activeIndex = (PopupQueue.activeIndex + 1) % PopupQueue.sessions.size
    }

    private fun switchToPreviousSession() {
        if (PopupQueue.sessions.size <= 1) return
        PopupQueue.activeIndex = (PopupQueue.activeIndex - 1 + PopupQueue.sessions.size) % PopupQueue.sessions.size
    }

    @Composable
    private fun PopupHost() {
        val session = PopupQueue.sessions.getOrNull(PopupQueue.activeIndex)
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
            PopupQueue.sessions.clear()
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

        fun sendReply(text: String) {
            session.messages.add(MessageEntry(text = text, isOutgoing = true, timestampMillis = System.currentTimeMillis()))
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.sendSms(session.address, text) }
                DataChangeSignal.notifyChanged()
                // دیگه بعدِ فرستادنِ پاسخ پنجره بسته نمیشه - شاید کاربر بخواد دوباره
                // پیام بده؛ QuickReplyPopupScreen خودش تاریخچه‌ی رد و بدل‌شده‌ها رو
                // نشون میده. بستن فقط با دکمه‌ی × یا بقیه‌ی اکشن‌ها اتفاق می‌افته
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

        val allActions = remember(enabledActionSettings, session.isKnownContact) {
            buildList {
                // «باز کردن» همیشه اولین و ثابته - مشابهِ رفتارِ خودِ کلیکِ نوتیفِ
                // معمولی که همیشه ترد رو باز می‌کنه
                add(
                    QuickReplyPopupAction(
                        type = null,
                        label = "باز کردن",
                        icon = Icons.Filled.OpenInNew,
                        onClick = { openThread() }
                    )
                )
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
                                setting.type, "افزودن سریع", Icons.Filled.GroupAdd
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

        // دقیقاً هم‌قاعده‌ی سقفِ ۳ دکمه‌ی نوتیفِ معمولی (SmsDeliverReceiver) -
        // به‌علاوه‌ی «باز کردن» که همیشه جزوِ همون سقفه
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
            totalSessions = PopupQueue.sessions.size,
            currentSessionPosition = PopupQueue.activeIndex + 1,
            onSwitchToPrevious = { switchToPreviousSession() },
            onSwitchToNext = { switchToNextSession() },
            showHistoryButton = !session.historyLoaded,
            onLoadHistory = { loadHistoryForSession(session) },
            onOpenThread = { openThread() },
            onCallSender = { callSender() },
            onSendReply = { text -> sendReply(text) },
            onClose = { closeActiveSession() }
        )
    }

    /**
     * صفِ سراسریِ سشن‌های پاپ‌آپِ حالتِ قفل - بیرونِ خودِ Compose زندگی می‌کنه تا
     * onNewIntent (که هیچ ربطی به هیچ Composableای نداره) بتونه مستقیم بهش پیام
     * اضافه کنه و Compose خودش reactive بازسازی بشه. دقیقاً هم‌الگوی
     * PopupOverlayService.sessions/activeIndex.
     */
    private object PopupQueue {
        val sessions: SnapshotStateList<Session> = mutableStateListOf()
        var activeIndex by mutableStateOf(0)

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
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}