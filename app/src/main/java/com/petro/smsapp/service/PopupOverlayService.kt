package com.petro.smsapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Reply
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.petro.smsapp.ActiveThreadTracker
import com.petro.smsapp.MainActivity
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.data.SmsRepository
import com.petro.smsapp.ui.QuickReplyPopupAction
import com.petro.smsapp.ui.QuickReplyPopupScreen
import com.petro.smsapp.ui.SmsAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * سرویسِ نمایشِ پاپ‌آپِ پیامک به‌صورتِ یه پنجره‌ی شناور (Overlay Window) به‌جای یه
 * Activity معمولی.
 *
 * چرا این لازمه: از اندروید ۱۰ (API 29) به بعد، fullScreenIntentِ یه نوتیف فقط وقتی
 * صفحه قفله خودکار به یه Activity تبدیل میشه. اگه صفحه باز باشه و اپِ ما فورگراند
 * نباشه، سیستم fullScreenIntent رو اجرا نمی‌کنه و فقط یه heads-up notification معمولی
 * (بدون هیچ دکمه‌ای، چون خودِ نوتیفِ زیرینِ ما حداقلیه) نشون میده - این یه محدودیتِ
 * عمدیِ خودِ پلتفرمه، نه باگ.
 *
 * راه‌حل: به‌جای تکیه‌کردن به fullScreenIntent، وقتی کاربر پرمیشنِ «نمایش روی
 * برنامه‌های دیگر» (SYSTEM_ALERT_WINDOW / Settings.canDrawOverlays) رو داده باشه،
 * مستقیم یه پنجره‌ی TYPE_APPLICATION_OVERLAY با WindowManager اضافه می‌کنیم - این
 * پنجره صرفِ‌نظر از اینکه اپ فورگراند/بک‌گراند یا صفحه قفل/باز باشه، بالای همه‌چیز
 * نمایش داده میشه (دقیقاً هم‌قاعده‌ی پاپ‌آپِ تماسِ ورودیِ اپ‌هایی مثل مسنجر/ترو‌کالر).
 *
 * چون این یه Service معمولیه نه یه Activity، برای میزبانیِ Compose باید خودمون
 * LifecycleOwner / ViewModelStoreOwner / SavedStateRegistryOwner رو دستی پیاده‌سازی
 * کنیم (چیزی که ComponentActivity به‌صورتِ رایگان میده).
 */
class PopupOverlayService :
    Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val repository by lazy { SmsRepository(this) }
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: ""
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val date = intent.getLongExtra(EXTRA_DATE, System.currentTimeMillis())

        showOverlay(threadId, messageId, address, body, date)
        return START_NOT_STICKY
    }

    private fun showOverlay(threadId: Long, messageId: Long, address: String, body: String, date: Long) {
        // اگه یه پاپ‌آپ از قبل روی صفحه‌ست (مثلاً پیامِ دومی همون لحظه رسیده)، اول
        // اون رو برمی‌داریم و پاپ‌آپِ جدید (آخرین پیام) رو جایگزینش می‌کنیم
        removeOverlayView()

        val displayName = ContactsCache.getName(this, address) ?: address
        val photoUri = ContactsCache.getPhotoUri(this, address)
        val isKnownContact = ContactsCache.getName(this, address) != null

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // پنجره focusable می‌مونه (پرچمِ FLAG_NOT_FOCUSABLE عمداً ست نشده) تا فیلدِ
            // پاسخِ سریع بتونه کیبورد رو بالا بیاره
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PopupOverlayService)
            setViewTreeViewModelStoreOwner(this@PopupOverlayService)
            setViewTreeSavedStateRegistryOwner(this@PopupOverlayService)
            setContent {
                SmsAppTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            OverlayPopupHost(
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

        overlayView = view
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            wm.addView(view, params)
        } catch (e: Exception) {
            // بعضی OEMها با وجودِ گرفتنِ پرمیشن هم ممکنه اجازه‌ی اضافه‌شدنِ پنجره رو
            // ندن؛ نوتیفِ معمولی (که همیشه جدا از این سرویس هم فرستاده میشه) به‌عنوانِ
            // بک‌آپ کافیه
            overlayView = null
            stopSelf()
        }
    }

    private fun removeOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // پنجره از قبل حذف شده بود
            }
        }
        overlayView = null
    }

    private fun closeOverlay() {
        removeOverlayView()
        stopSelf()
    }

    override fun onDestroy() {
        removeOverlayView()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @androidx.compose.runtime.Composable
    private fun OverlayPopupHost(
        threadId: Long,
        messageId: Long,
        address: String,
        displayName: String,
        photoUri: String?,
        isKnownContact: Boolean,
        body: String,
        date: Long
    ) {
        fun openThread() {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
                putExtra(MainActivity.EXTRA_ADDRESS, address)
                putExtra(MainActivity.EXTRA_DISPLAY_NAME, displayName)
            }
            startActivity(openIntent)
            closeOverlay()
        }

        fun markRead() {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
                DataChangeSignal.notifyChanged()
                closeOverlay()
            }
        }

        fun deleteThisMessage() {
            lifecycleScope.launch {
                if (messageId != -1L) withContext(Dispatchers.IO) { repository.deleteMessage(messageId) }
                DataChangeSignal.notifyChanged()
                closeOverlay()
            }
        }

        fun callSender() {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$address")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(dialIntent)
            } catch (e: Exception) {
                // اپِ تماسی پیدا نشد
            }
            closeOverlay()
        }

        fun sendReply(text: String) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.sendSms(address, text) }
                DataChangeSignal.notifyChanged()
                closeOverlay()
            }
        }

        // نکته: برخلافِ نسخه‌ی Activity، اینجا از GroupPickerSheet (که خودش یه
        // Dialog/پنجره‌ی جداست) استفاده نمی‌کنیم - چون یه Dialog داخلِ پنجره‌ی
        // Overlayِ یه Service (بدون توکنِ پنجره‌ی یه Activity) می‌تونه کرش کنه. برای
        // اکشن‌های گروه‌محور (افزودن به گروه) دقیقاً هم‌رفتارِ نوتیفِ معمولیِ قدیمی،
        // اپ رو باز می‌کنیم تا از همونجا مدیریت بشه.
        fun addToGroupTargetFast() {
            lifecycleScope.launch {
                val filterGroupRepository = AppContainer.filterGroupRepository(this@PopupOverlayService)
                val targetId = withContext(Dispatchers.IO) { filterGroupRepository.getQuickAddTargetGroupId() }
                if (targetId != null) {
                    withContext(Dispatchers.IO) {
                        val added = filterGroupRepository.addNumber(targetId, address, displayName)
                        if (added) {
                            repository.applyGroupToExistingThreadMessages(targetId, threadId, address)
                        }
                    }
                    DataChangeSignal.notifyChanged()
                }
                closeOverlay()
            }
        }

        val enabledActionSettings = AppSettings.getNotificationActionSettings(this@PopupOverlayService)
            .filter { it.enabled }

        val allActions = buildList {
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
                        ) { openThread() }
                        NotificationActionType.QUICK_ADD_GROUP -> QuickReplyPopupAction(
                            setting.type, "افزودن سریع", Icons.Filled.GroupAdd
                        ) { addToGroupTargetFast() }
                        NotificationActionType.CALL -> QuickReplyPopupAction(
                            setting.type, "تماس", Icons.Filled.Call
                        ) { callSender() }
                    }
                )
            }
        }

        val primary = allActions.take(3)
        val overflow = allActions.drop(3)

        QuickReplyPopupScreen(
            senderDisplayName = displayName,
            senderAddress = address,
            isKnownContact = isKnownContact,
            photoUri = photoUri,
            messageBody = body,
            receivedAtMillis = date,
            primaryActions = primary,
            overflowActions = overflow,
            onOpenThread = { openThread() },
            onSendReply = { text -> sendReply(text) },
            onClose = { closeOverlay() }
        )
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_DATE = "extra_date"

        fun show(
            context: Context,
            threadId: Long,
            messageId: Long,
            address: String,
            body: String,
            date: Long
        ) {
            val intent = Intent(context, PopupOverlayService::class.java).apply {
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_MESSAGE_ID, messageId)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_DATE, date)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // اگه به هر دلیلی (محدودیتِ OEM و ...) استارت نشد، نوتیفِ معمولی که
                // جدا از این سرویس همیشه فرستاده میشه به‌عنوانِ بک‌آپ کافیه
            }
        }
    }
}
