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
 * اکتیویتیِ میزبانِ پاپ‌آپِ پاسخِ سریع - جایگزینِ نمایشِ نوتیفِ معمولی وقتی کاربر از
 * تنظیمات فعالش کرده. با فلگ‌های showWhenLocked/turnScreenOn (یا معادلِ قدیمی‌ترشون
 * روی نسخه‌های زیرِ ۲۷) روی صفحه/صفحه‌قفل نمایش داده میشه - دقیقاً هم‌قاعده‌ی الگوی
 * نوتیفِ تماسِ ورودی. خودش fullScreenIntentِ یه نوتیفِ حداقلیه (SmsDeliverReceiver
 * می‌سازدش)، نه یه Activity معمولی.
 *
 * تمِ این اکتیویتی (Theme.SmsApp.Popup در themes.xml) شفافه تا پس‌زمینه‌ی واقعیِ
 * صفحه (یا صفحه‌قفل) پشتِ کارتِ پاپ‌آپ دیده بشه.
 */
class QuickReplyPopupActivity : ComponentActivity() {

    private val repository by lazy { SmsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowOnLockScreenFlags()

        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: ""
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val date = intent.getLongExtra(EXTRA_DATE, System.currentTimeMillis())
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (notificationId != -1) {
            NotificationManagerCompat.from(this).cancel(notificationId)
        }

        val displayName = ContactsCache.getName(this, address) ?: address
        val photoUri = ContactsCache.getPhotoUri(this, address)
        val isKnownContact = ContactsCache.getName(this, address) != null

        setContent {
            SmsAppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PopupHost(
                            threadId = threadId,
                            messageId = messageId,
                            address = address,
                            displayName = displayName,
                            photoUri = photoUri,
                            isKnownContact = isKnownContact,
                            body = body,
                            date = date
                        )
                    }
                }
            }
        }
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

    @Composable
    private fun PopupHost(
        threadId: Long,
        messageId: Long,
        address: String,
        displayName: String,
        photoUri: String?,
        isKnownContact: Boolean,
        body: String,
        date: Long
    ) {
        var visible by remember { mutableStateOf(true) }
        var groupPickerGroups by remember { mutableStateOf<List<FilterGroupSummary>?>(null) }
        // این Activity (برخلافِ نسخه‌ی Overlay Service) فقط یه مکالمه رو در لحظه
        // میزبانی می‌کنه، پس تاریخچه/پاسخِ درحالِ تایپ همینجا (به‌جای صفی از Sessionها)
        // نگه داشته میشه - نیازی به queueCount/سوییچ نیست
        val messages = remember { mutableStateListOf(MessageEntry(text = body, isOutgoing = false, timestampMillis = date)) }
        var replyText by remember { mutableStateOf("") }

        // تاریخچه‌ی قبلیِ همین گفتگو - دقیقاً هم‌قاعده‌ی نسخه‌ی Overlay Service
        LaunchedEffect(threadId) {
            val history = withContext(Dispatchers.IO) { repository.getMessagesForThread(threadId) }
            val historyEntries = history
                .filter { it.id != messageId }
                .takeLast(5)
                .map { MessageEntry(text = it.body, isOutgoing = it.isOutgoing, timestampMillis = it.date) }
            if (historyEntries.isNotEmpty()) {
                messages.addAll(0, historyEntries)
            }
        }

        fun closeSelf() {
            visible = false
            finish()
        }

        fun openThread() {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                putExtra(MainActivity.EXTRA_ADDRESS, address)
                putExtra(MainActivity.EXTRA_DISPLAY_NAME, displayName)
            }
            startActivity(openIntent)
            closeSelf()
        }

        fun markRead() {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
                DataChangeSignal.notifyChanged()
                closeSelf()
            }
        }

        fun deleteThisMessage() {
            lifecycleScope.launch {
                if (messageId != -1L) withContext(Dispatchers.IO) { repository.deleteMessage(messageId) }
                DataChangeSignal.notifyChanged()
                closeSelf()
            }
        }

        fun callSender() {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address")))
            } catch (e: Exception) {
                // اپِ تماسی پیدا نشد
            }
            closeSelf()
        }

        fun sendReply(text: String) {
            messages.add(MessageEntry(text = text, isOutgoing = true, timestampMillis = System.currentTimeMillis()))
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.sendSms(address, text) }
                DataChangeSignal.notifyChanged()
                // دیگه بعدِ فرستادنِ پاسخ پنجره بسته نمیشه - شاید کاربر بخواد دوباره
                // پیام بده؛ QuickReplyPopupScreen خودش تاریخچه‌ی رد و بدل‌شده‌ها رو نشون
                // میده. بستن فقط با دکمه‌ی × یا بقیه‌ی اکشن‌ها (باز کردن/حذف/...) اتفاق می‌افته
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
                    val added = filterGroupRepository.addNumber(groupId, address, displayName)
                    if (added) {
                        repository.applyGroupToExistingThreadMessages(groupId, threadId, address)
                    }
                }
                DataChangeSignal.notifyChanged()
                groupPickerGroups = null
                closeSelf()
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
                targetLabel = displayName,
                groups = currentGroups,
                onPick = { groupId -> addToGroup(groupId) },
                onCreateAndPick = { name -> createGroupAndAdd(name) },
                onDismiss = { groupPickerGroups = null }
            )
        }

        if (visible) {
            val enabledActionSettings = remember {
                AppSettings.getNotificationActionSettings(this@QuickReplyPopupActivity)
                    .filter { it.enabled }
            }

            val allActions = remember(enabledActionSettings, isKnownContact) {
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
                                        if (targetId != null) addToGroup(targetId) else closeSelf()
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
                senderDisplayName = displayName,
                senderAddress = address,
                isKnownContact = isKnownContact,
                photoUri = photoUri,
                messages = messages,
                replyText = replyText,
                onReplyTextChange = { replyText = it },
                receivedAtMillis = date,
                primaryActions = primary,
                overflowActions = overflow,
                onOpenThread = { openThread() },
                onCallSender = { callSender() },
                onSendReply = { text -> sendReply(text) },
                onClose = { closeSelf() }
            )
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