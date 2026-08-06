package com.petro.smsapp.service

import android.app.Service
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.petro.smsapp.ui.MessageEntry
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
 *
 * نکته‌ی مهم (رفعِ باگ): قبلاً هر پیامِ جدید - چه از همون مخاطبِ بازِ پاپ‌آپ، چه از یه
 * مخاطبِ دیگه - باعث می‌شد پنجره کامل حذف و یه ComposeView کاملاً تازه ساخته بشه.
 * چون کلِ state (پاسخِ درحالِ تایپ، تاریخچه‌ی پیام‌های همون گفتگو) داخلِ خودِ اون
 * Composeای بود که نابود می‌شد، عملاً با هر پیامِ تازه از هر مخاطبی، پاپ‌آپ می‌بست و
 * از نو با همون پیامِ جدید ظاهر می‌شد.
 *
 * الان به‌جاش یه صفِ [sessions] (یکی به‌ازای هر مخاطب) نگه داشته میشه که خارج از
 * Composeای که نمایشش میده زندگی می‌کنه؛ خودِ پنجره فقط یه‌بار (وقتی صف از خالی به یه
 * آیتم می‌رسه) ساخته میشه:
 * - پیامِ جدید از همون مخاطبی که پاپ‌آپش الان بازه → فقط به تاریخچه‌ی همون Session
 *   اضافه میشه، بدونِ اینکه پنجره یا پاسخِ درحالِ تایپ دست بخوره.
 * - پیامِ جدید از یه مخاطبِ دیگه → یه Session تازه به صف اضافه میشه ولی پاپ‌آپِ فعلی
 *   دست‌نخورده می‌مونه؛ کاربر با چیپِ «صف» بالای پاپ‌آپ می‌تونه بینشون سوییچ کنه.
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

    /**
     * یه گفتگوی درحالِ نمایش/انتظار توی پاپ‌آپ. همه‌ی فیلدهای قابلِ‌تغییرش state
     * هستن تا Compose خودش با تغییرشون دوباره کامپوز بشه؛ چون Session بیرونِ خودِ
     * Compose (توی خودِ Service) زندگی می‌کنه، عوض‌شدنِ محتوای این‌ها هرگز باعثِ
     * ساخته‌شدنِ دوباره‌ی ComposeView نمیشه.
     */
    private class Session(
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

    // صفِ مکالمه‌ها - اولیش همون چیزیه که الان روی پاپ‌آپ نشون داده میشه
    private val sessions = mutableStateListOf<Session>()
    private var activeIndex by mutableStateOf(0)

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

        handleIncomingMessage(threadId, messageId, address, body, date)
        return START_NOT_STICKY
    }

    /**
     * پیامِ تازه‌رسیده رو یا به Sessionِ موجودِ همون مخاطب اضافه می‌کنه (پنجره دست‌نخورده)
     * یا (اگه مخاطب تازه‌ست) یه Session جدید به ته صف اضافه می‌کنه.
     */
    /**
     * صدا/ویبره‌ی پیامکِ تازه‌رسیده رو دستی پخش می‌کنه - چون این Service (برخلافِ
     * SmsDeliverReceiver.showNotification/showFullScreenPopupNotification) هیچ‌وقت
     * notify() صدا نمی‌زنه، پس صدا/ویبرهٔ خودِ notification channel هم هیچ‌وقت
     * خودکار پخش نمیشه. سعیمون اینه که همون تنظیماتِ "sms_channel" (صدا/ویبره‌ای که
     * کاربر از تنظیماتِ سیستم براش انتخاب کرده) رو رعایت کنیم، نه یه صدای ثابت.
     */
    private fun playIncomingMessageAlert() {
        try {
            val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getSystemService(NotificationManager::class.java)?.getNotificationChannel(SMS_CHANNEL_ID)
            } else null

            val soundUri: Uri? = channel?.sound
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
            if (soundUri != null && audioManager?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                RingtoneManager.getRingtone(this, soundUri)?.play()
            }

            val shouldVibrate = channel?.shouldVibrate() ?: true
            if (shouldVibrate) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // پخشِ صدا/ویبره روی این دستگاه با مشکل مواجه شد - نباید مانعِ نمایشِ خودِ پاپ‌آپ بشه
        }
    }

    private fun handleIncomingMessage(threadId: Long, messageId: Long, address: String, body: String, date: Long) {
        // نکته‌ی مهم (رفعِ باگ): این مسیر (Overlay) برخلافِ نوتیفِ معمولی، هیچ‌وقت
        // NotificationManagerCompat.notify() رو صدا نمی‌زنه - پس اگه اینجا صدا/ویبره
        // رو دستی پخش نکنیم، پیامکِ تازه‌رسیده کاملاً بی‌صدا میاد (برخلافِ حالتِ
        // نوتیفِ معمولی که صدا/ویبره‌ی خودِ channel رو داره)
        playIncomingMessageAlert()

        val existing = sessions.firstOrNull { it.address == address || (threadId != -1L && it.threadId == threadId) }

        if (existing != null) {
            existing.messages.add(MessageEntry(text = body, isOutgoing = false, timestampMillis = date))
            existing.messageId = messageId
            existing.lastMessageAtMillis = date
            // اگه این Session همون چیزیه که الان نشون داده میشه، Compose خودش با
            // تغییرِ messages/lastMessageAtMillis بازسازی میشه. اگه Session دیگه‌ای
            // فعاله، این یکی توی صف به‌روز می‌مونه تا کاربر بعداً سراغش بره.
            return
        }

        val newSession = Session(
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

        val wasEmpty = sessions.isEmpty()
        sessions.add(newSession)

        if (wasEmpty) {
            // اولین مکالمه‌ست - تازه اینجاست که واقعاً باید پنجره ساخته بشه
            activeIndex = 0
            createOverlayWindow()
        }
        // اگه از قبل یه پاپ‌آپ باز بود، همونجوری که هست می‌مونه؛ کاربر با فلش‌های
        // ناوبریِ بالای پاپ‌آپ (اگه بخواد) می‌تونه بره سراغِ همین Session تازه
    }

    /**
     * تاریخچه‌ی قبلیِ همین مکالمه (از خودِ اپ، نه فقط پیام‌هایی که تا الان تو همین
     * پاپ‌آپ رد و بدل شده) رو از دیتابیس می‌خونه و جلوی پیامِ تازه‌رسیده اضافه می‌کنه.
     *
     * عمداً خودکار صدا زده نمیشه - فقط با تپِ کاربر رویِ دکمه‌ی «نمایشِ پیام‌های
     * قبلی» تو خودِ QuickReplyPopupScreen. اینجوری پاپ‌آپ همیشه فوری (بدونِ منتظر
     * موندن برایِ یه کوئریِ IO) ظاهر میشه، و کوئری فقط وقتی واقعاً لازمه اجرا میشه.
     */
    private fun loadHistoryForSession(session: Session) {
        if (session.historyLoaded) return
        session.historyLoaded = true

        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                repository.getMessagesForThread(session.threadId)
            }
            val historyEntries = history
                .filter { it.id != session.messageId }
                .takeLast(HISTORY_LIMIT)
                .map { MessageEntry(text = it.body, isOutgoing = it.isOutgoing, timestampMillis = it.date) }

            // اگه کاربر تا اون موقع همین Session رو بسته باشه (X یا یکی از اکشن‌ها)،
            // دیگه چیزی برای اضافه‌کردن نیست
            if (historyEntries.isNotEmpty() && sessions.contains(session)) {
                session.messages.addAll(0, historyEntries)
            }
        }
    }

    /** پنجره‌ی Overlay و ComposeView رو فقط یه‌بار می‌سازه؛ محتواش از سشنِ فعال می‌خونه (reactive). */
    private fun createOverlayWindow() {
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
                            OverlayPopupHost()
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
            sessions.clear()
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

    /** فقط Sessionِ فعال رو از صف برمی‌داره؛ اگه صف خالی شد، کلِ پنجره هم بسته میشه. */
    private fun closeActiveSession() {
        if (activeIndex !in sessions.indices) return
        sessions.removeAt(activeIndex)
        if (sessions.isEmpty()) {
            removeOverlayView()
            stopSelf()
        } else if (activeIndex >= sessions.size) {
            activeIndex = sessions.size - 1
        }
    }

    /** کلِ پاپ‌آپ (همه‌ی صف) رو می‌بنده - برای وقتی کاربر داره میره داخلِ خودِ اپ. */
    private fun closeEverything() {
        sessions.clear()
        removeOverlayView()
        stopSelf()
    }

    private fun switchToNextSession() {
        if (sessions.size <= 1) return
        activeIndex = (activeIndex + 1) % sessions.size
    }

    private fun switchToPreviousSession() {
        if (sessions.size <= 1) return
        activeIndex = (activeIndex - 1 + sessions.size) % sessions.size
    }

    override fun onDestroy() {
        removeOverlayView()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @androidx.compose.runtime.Composable
    private fun OverlayPopupHost() {
        // حالتِ گذرا: بینِ closeActiveSession() و اثرگذاشتنِ stopSelf() ممکنه یه فریم
        // با صفِ خالی کامپوز بشه؛ همینجا بی‌خیالش می‌شیم تا کرش نکنه
        val session = sessions.getOrNull(activeIndex) ?: return

        fun openThread() {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_THREAD_ID, session.threadId)
                putExtra(MainActivity.EXTRA_ADDRESS, session.address)
                putExtra(MainActivity.EXTRA_DISPLAY_NAME, session.displayName)
            }
            startActivity(openIntent)
            closeEverything()
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
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${session.address}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(dialIntent)
            } catch (e: Exception) {
                // اپِ تماسی پیدا نشد
            }
            closeActiveSession()
        }

        fun sendReply(text: String) {
            val address = session.address
            session.messages.add(MessageEntry(text = text, isOutgoing = true, timestampMillis = System.currentTimeMillis()))
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { repository.sendSms(address, text) }
                DataChangeSignal.notifyChanged()
                // دیگه بعدِ فرستادنِ پاسخ پنجره بسته نمیشه - شاید کاربر بخواد دوباره
                // پیام بده؛ QuickReplyPopupScreen خودش تاریخچه‌ی رد و بدل‌شده‌ها رو نشون
                // میده. بستن فقط با دکمه‌ی × یا بقیه‌ی اکشن‌ها (باز کردن/حذف/...) اتفاق می‌افته
            }
        }

        // نکته: برخلافِ نسخه‌ی Activity، اینجا از GroupPickerSheet (که خودش یه
        // Dialog/پنجره‌ی جداست) استفاده نمی‌کنیم - چون یه Dialog داخلِ پنجره‌ی
        // Overlayِ یه Service (بدون توکنِ پنجره‌ی یه Activity) می‌تونه کرش کنه. برای
        // اکشن‌های گروه‌محور (افزودن به گروه) دقیقاً هم‌رفتارِ نوتیفِ معمولیِ قدیمی،
        // اپ رو باز می‌کنیم تا از همونجا مدیریت بشه.
        fun addToGroupTargetFast() {
            val address = session.address
            val displayName = session.displayName
            val threadId = session.threadId
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
                closeActiveSession()
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
            totalSessions = sessions.size,
            currentSessionPosition = activeIndex + 1,
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

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_DATE = "extra_date"

        // تعدادِ پیامِ قبلیِ همین گفتگو که موقعِ باز شدنِ پاپ‌آپ به‌عنوانِ سیاق‌وسباق
        // (context) جلوی پیامِ تازه نشون داده میشه - زیاد نه، چون پاپ‌آپ جای محدودیه
        private const val HISTORY_LIMIT = 5

        // همون channel id که SmsApplication می‌سازدش و SmsDeliverReceiver برای نوتیفِ
        // معمولی ازش استفاده می‌کنه - اینجا فقط برای خوندنِ صدا/ویبره‌ی انتخابیِ کاربر لازمه
        private const val SMS_CHANNEL_ID = "sms_channel"

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