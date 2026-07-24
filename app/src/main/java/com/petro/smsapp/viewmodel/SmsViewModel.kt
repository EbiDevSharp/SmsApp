package com.petro.smsapp.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petro.smsapp.ActiveThreadTracker
import com.petro.smsapp.data.BlockStore
import com.petro.smsapp.data.BlockedMessageEntry
import com.petro.smsapp.data.BlockedNumber
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.ContactsRepository
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.data.FavoriteStore
import com.petro.smsapp.data.PrivateMessageEntry
import com.petro.smsapp.data.PrivateNumber
import com.petro.smsapp.data.PrivateStore
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SimRepository
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.data.SmsRepository
import com.petro.smsapp.data.TrashedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val contactsRepository = ContactsRepository(application)
    private val simRepository = SimRepository(application)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contacts: StateFlow<List<ContactInfo>> = _contacts.asStateFlow()

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    // مخاطبی که کاربر از اپ مخاطبین سیستم (نه لیست داخلی) انتخاب کرده
    private val _pickedContact = MutableStateFlow<ContactInfo?>(null)
    val pickedContact: StateFlow<ContactInfo?> = _pickedContact.asStateFlow()

    // اطلاعات مکالمه‌ای که بعد از "پیام جدید" ساخته/پیدا شده، تا Navigation بتونه با اطلاعات کامل بره صفحه چت
    private val _newConversationTarget = MutableStateFlow<NewConversationTarget?>(null)
    val newConversationTarget: StateFlow<NewConversationTarget?> = _newConversationTarget.asStateFlow()

    // متن پیامی که کاربر برای باز کردن توی صفحه‌ی «نوت پیام» انتخاب کرده (دابل‌کلیک یا از منو)
    private val _noteText = MutableStateFlow<String?>(null)
    val noteText: StateFlow<String?> = _noteText.asStateFlow()

    // لیست کامل پیام‌های فیوریت‌شده - برای صفحه‌ی «علاقه‌مندی‌ها»
    private val _favorites = MutableStateFlow<List<FavoriteMessage>>(emptyList())
    val favorites: StateFlow<List<FavoriteMessage>> = _favorites.asStateFlow()

    // فقط id پیام‌های فیوریت‌شده - برای اینکه توی صفحه‌ی چت سریع بشه چک کرد پیامی فیوریته یا نه
    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // لیست پیام‌های توی سطل زباله - برای صفحه‌ی «سطل زباله»
    private val _trash = MutableStateFlow<List<TrashedMessage>>(emptyList())
    val trash: StateFlow<List<TrashedMessage>> = _trash.asStateFlow()

    // لیست شماره‌های بلاک‌شده - برای صفحه‌ی «شماره‌های بلاک‌شده»
    private val _blockedNumbers = MutableStateFlow<List<BlockedNumber>>(emptyList())
    val blockedNumbers: StateFlow<List<BlockedNumber>> = _blockedNumbers.asStateFlow()

    // همه‌ی پیام‌های thread های بلاک‌شده با هم - برای صفحه‌ی «پیامک‌های بلاک‌شده»
    private val _blockedMessages = MutableStateFlow<List<BlockedMessageEntry>>(emptyList())
    val blockedMessages: StateFlow<List<BlockedMessageEntry>> = _blockedMessages.asStateFlow()

    // لیست شماره‌های خصوصی - برای صفحه‌ی «شماره‌های خصوصی»
    private val _privateNumbers = MutableStateFlow<List<PrivateNumber>>(emptyList())
    val privateNumbers: StateFlow<List<PrivateNumber>> = _privateNumbers.asStateFlow()

    // همه‌ی پیام‌های thread های خصوصی با هم - برای صفحه‌ی «پیامک‌های خصوصی»
    private val _privateMessages = MutableStateFlow<List<PrivateMessageEntry>>(emptyList())
    val privateMessages: StateFlow<List<PrivateMessageEntry>> = _privateMessages.asStateFlow()

    // آیا کاربر همین الان (توی همین session) رمز بخش خصوصی رو درست وارد کرده - با خروج
    // کامل از بخش خصوصی (دکمه‌ی برگشتِ خودِ هاب) دوباره false میشه، پس دفعه‌ی بعد رمز می‌خواد
    private val _privateUnlocked = MutableStateFlow(false)
    val privateUnlocked: StateFlow<Boolean> = _privateUnlocked.asStateFlow()

    // پیام یک‌بارمصرف برای اطلاع‌رسانی به کاربر (مثلاً «این پیام قفله و قابل حذف نیست»)
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    // threadId مکالمه‌ای که الان روی صفحه چت بازه، تا وقتی پیامک جدید میاد بدونیم کدوم thread رو دوباره لود کنیم
    private var openThreadId: Long? = null

    private var observerDebounceJob: Job? = null

    /**
     * وقتی پیامکی وارد یا ارسال میشه (چه از این اپ، چه از بیرون)، سیستم به این آبزرور خبر میده
     * و بدون نیاز به بستن/بازکردن اپ، لیست مکالمات و مکالمه‌ی بازشده به‌روز میشن.
     *
     * یه نکته‌ی مهم: تغییرات content provider فقط «پیام جدید» نیستن؛ ارسال، دلیوری،
     * خوانده‌شدن، حذف و تغییر STATUS هم همین observer رو صدا می‌زنن. برای یه اتفاق منطقی
     * واحد (مثلاً ارسال یه پیام که چند میلی‌ثانیه بعد وضعیتش به COMPLETE آپدیت میشه) ممکنه
     * onChange چندبار پشت‌سرهم صدا زده بشه. به‌جای اینکه هر بار جدا loadConversations کامل
     * رو صدا بزنیم (که برای هر پیامک/دلیوری یه Query سنگین به کل جدول sms میزنه)، چند
     * میلی‌ثانیه صبر می‌کنیم و اگه توی این فاصله دوباره onChange اومد، تایمر رو ریست می‌کنیم -
     * یعنی فقط وقتی تغییرات «آروم» گرفتن، یه بار لود انجام میشه.
     */
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerDebounceJob?.cancel()
            observerDebounceJob = viewModelScope.launch {
                delay(150)
                loadConversations()
                openThreadId?.let { refreshMessages(it) }
            }
        }
    }

    init {
        application.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true, // notifyForDescendants: تغییرات inbox/sent/... که زیرشاخه‌ی content://sms هستن رو هم پوشش میده
            smsObserver
        )

        // تغییراتی که از بیرون ViewModel میان (مثل بلاک‌کردن از روی دکمه‌ی خودِ نوتیف) فقط
        // SharedPreferences رو عوض می‌کنن، نه SMS Provider - پس smsObserver بالا خبردار نمیشه.
        // این signal همون نقش رو براشون بازی می‌کنه.
        viewModelScope.launch {
            DataChangeSignal.tick.drop(1).collect {
                loadConversations()
                loadBlockedNumbers()
                loadBlockedMessages()
                loadPrivateNumbers()
                loadPrivateMessages()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(smsObserver)
    }

    fun loadConversations() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getConversations() }
            _conversations.value = result
        }
    }

    fun loadThread(threadId: Long) {
        openThreadId = threadId
        ActiveThreadTracker.activeThreadId = threadId
        viewModelScope.launch {
            // اول پیام‌ها لود و روی صفحه نمایش داده میشن، بعد خوانده‌شده علامت می‌خورن.
            // اگه برعکس بود (که قبلاً بود) و لود با خطا مواجه می‌شد، پیامی که کاربر
            // واقعاً ندیده بود به‌اشتباه خوانده‌شده ثبت می‌شد.
            val result = withContext(Dispatchers.IO) { repository.getMessagesForThread(threadId) }
            _messages.value = result

            val marked = withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            if (!marked) {
                // اگه اپ الان پیش‌فرض پیامک نباشه، markThreadAsRead سایلنت رد میشه و بج خوانده‌نشده
                // هیچ‌وقت پاک نمیشه؛ این پیام دقیقاً همین حالت رو به کاربر نشون میده
                _operationMessage.value = "اپ الان پیش‌فرض پیامک نیست، برای همین علامت «خوانده‌شده» ثبت نشد."
            } else {
                loadConversations()
            }
        }
    }

    /**
     * فقط خوندن پیام‌ها بدون markAsRead - این تابع رو observer صدا می‌زنه.
     * مهم: markThreadAsRead خودش یه update روی content provider هست که دوباره onChange رو صدا می‌زنه؛
     * اگه از loadThread استفاده می‌کردیم اینجا، یه حلقه‌ی بی‌نهایت درست می‌شد.
     */
    private fun refreshMessages(threadId: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForThread(threadId) }
            _messages.value = result

            // اگه هنوز داخل همین مکالمه‌ایم و پیام واردی خونده‌نشده‌ای اومده، خودکار خونده‌شده علامت بزن.
            // این خودش یه update دیگه روی content provider هست که onChange رو دوباره صدا می‌زنه،
            // ولی چون دفعه‌ی بعد هیچ پیام خونده‌نشده‌ای پیدا نمیشه، شرط زیر false میشه و حلقه خودش تموم میشه.
            if (openThreadId == threadId && result.any { !it.isOutgoing && !it.isRead }) {
                withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            }
        }
    }

    /** وقتی از صفحه چت خارج میشیم، دیگه لازم نیست observer اون thread رو رفرش کنه */
    fun clearOpenThread() {
        openThreadId = null
        ActiveThreadTracker.activeThreadId = null
    }

    /**
     * وقتی اپ میره بک‌گراند (Activity.onPause)، حتی اگه هنوز روی صفحه‌ی چت باشیم، دیگه
     * کاربر واقعاً «داره می‌بینه» حساب نمیشه - پس نوتیف پیام‌های جدید باید دوباره نشون داده بشه.
     */
    fun onAppBackgrounded() {
        ActiveThreadTracker.activeThreadId = null
    }

    /** وقتی اپ برمی‌گرده فورگراند (Activity.onResume) و هنوز همون thread بازه، دوباره ساکت کن */
    fun onAppForegrounded() {
        ActiveThreadTracker.activeThreadId = openThreadId
    }

    fun loadSims() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { simRepository.getActiveSims() }
            _sims.value = result
        }
    }

    fun sendMessage(address: String, body: String, threadId: Long, subscriptionId: Int?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.sendSms(address, body, subscriptionId) }
            loadThread(threadId)
            loadConversations()
        }
    }

    /** حذف یه پیام مشخص از داخل مکالمه (بعد از تائید کاربر توی MessageActionsSheet) */
    fun deleteMessage(threadId: Long, messageId: Long) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteMessage(messageId) }
            if (!deleted) {
                // پیام فیوریت‌شده بود و رد شد (نقش قفل) - فقط به کاربر اطلاع میدیم
                _operationMessage.value = "این پیام به علاقه‌مندی‌ها اضافه شده و قفله. برای حذف، اول از علاقه‌مندی‌ها بردارش."
                return@launch
            }
            refreshMessages(threadId)
            loadConversations()
        }
    }

    /** پیامی که به کاربر نشون داده شده رو مصرف می‌کنه (مثلاً بعد از نمایش Snackbar) */
    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

    /**
     * حذف دسته‌جمعی چند پیام با هم - از حالت «انتخاب چندتایی» داخل صفحه‌ی چت یک مخاطب
     * (بعد از تائید کاربر توی دیالوگ حذف).
     */
    fun deleteMessages(threadId: Long, messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteMessages(messageIds) }
            val base = if (result.movedToTrash) "پیام‌های انتخاب‌شده به سطل زباله منتقل شدن" else "پیام‌های انتخاب‌شده حذف شدن"
            _operationMessage.value = if (result.blockedFavoriteCount > 0) {
                "$base (${result.blockedFavoriteCount} پیام فیوریت‌شده به‌خاطر قفل بودن دست‌نخورده موند)"
            } else {
                base
            }
            refreshMessages(threadId)
            loadConversations()
        }
    }

    /**
     * حذف دسته‌جمعی از داخل «پیامک‌های بلاک‌شده» - برخلاف deleteMessages بالا که مال یه
     * مکالمه‌ی مشخصه، اینجا پیام‌های انتخاب‌شده ممکنه از چند شماره‌ی بلاک‌شده‌ی مختلف باشن،
     * پس به‌جای refreshMessages(threadId)، کل لیست بلاک‌شده‌ها رو دوباره لود می‌کنیم.
     */
    fun deleteBlockedMessages(messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteMessages(messageIds) }
            val base = if (result.movedToTrash) "پیام‌های انتخاب‌شده به سطل زباله منتقل شدن" else "پیام‌های انتخاب‌شده حذف شدن"
            _operationMessage.value = if (result.blockedFavoriteCount > 0) {
                "$base (${result.blockedFavoriteCount} پیام فیوریت‌شده به‌خاطر قفل بودن دست‌نخورده موند)"
            } else {
                base
            }
            loadBlockedMessages()
        }
    }

    /**
     * حذف دسته‌جمعی چند مکالمه با هم - از حالت «انتخاب چندتایی» توی صفحه‌ی اصلی لیست پیام‌ها
     * (بعد از تائید کاربر توی دیالوگ حذف). نتیجه رو به‌صورت یه پیام کوتاه به کاربر نشون میده:
     * اینکه به سطل زباله رفتن یا واقعاً حذف شدن، و اگه چندتا پیام فیوریت (قفل) بودن که دست
     * نخوردن، اونم اضافه میشه.
     */
    fun deleteConversations(threadIds: Set<Long>) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteThreads(threadIds) }
            val base = if (result.movedToTrash) {
                "مکالمه‌های انتخاب‌شده به سطل زباله منتقل شدن"
            } else {
                "مکالمه‌های انتخاب‌شده حذف شدن"
            }
            _operationMessage.value = if (result.blockedFavoriteCount > 0) {
                "$base (${result.blockedFavoriteCount} پیام فیوریت‌شده به‌خاطر قفل بودن دست‌نخورده موند)"
            } else {
                base
            }
            loadConversations()
            openThreadId?.let { refreshMessages(it) }
        }
    }

    /** لود کردن لیست فیوریت‌ها - برای صفحه‌ی «علاقه‌مندی‌ها» */
    fun loadFavorites() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { FavoriteStore.getAllFavorites(getApplication<Application>()) }
            _favorites.value = result
            _favoriteIds.value = result.map { it.messageId }.toSet()
        }
    }

    /**
     * فیوریت‌کردن/برداشتنِ فیوریت یه پیام با یک کلیک (از منوی اکشن پیام).
     * تا وقتی پیامی فیوریته، SmsRepository.deleteMessage اجازه‌ی حذفش رو نمی‌ده.
     */
    fun toggleFavorite(message: SmsMessage, contactDisplayName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (FavoriteStore.isFavorite(getApplication<Application>(), message.id)) {
                    FavoriteStore.removeFavorite(getApplication<Application>(), message.id)
                } else {
                    FavoriteStore.addFavorite(
                        getApplication<Application>(),
                        FavoriteMessage(
                            messageId = message.id,
                            threadId = message.threadId,
                            address = message.address,
                            displayName = contactDisplayName,
                            body = message.body,
                            date = message.date
                        )
                    )
                }
            }
            loadFavorites()
        }
    }

    /** برداشتن فیوریت از داخل خود صفحه‌ی «علاقه‌مندی‌ها» (بدون نیاز به SmsMessage کامل) */
    fun removeFavorite(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { FavoriteStore.removeFavorite(getApplication<Application>(), messageId) }
            loadFavorites()
        }
    }

    /** لود کردن لیست پیام‌های سطل زباله - برای صفحه‌ی «سطل زباله» */
    fun loadTrash() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getTrashedMessages() }
            _trash.value = result
        }
    }

    /** بازگردوندن یه پیام از سطل زباله - چون خودِ ردیف پاک نشده، دوباره تو لیست/چت نمایش داده میشه */
    fun restoreFromTrash(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.restoreFromTrash(messageId) }
            loadTrash()
            loadConversations()
            openThreadId?.let { refreshMessages(it) }
        }
    }

    /** حذف همیشگی از داخل صفحه‌ی سطل زباله (بعد از تائید کاربر) - این دیگه واقعاً فیزیکیه */
    fun permanentlyDeleteFromTrash(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.permanentlyDelete(messageId) }
            loadTrash()
        }
    }

    /**
     * بلاک‌کردن دسته‌جمعی چند مکالمه - از حالت «انتخاب چندتایی» توی لیست اصلی پیام‌ها،
     * گزینه‌ی «بلاک کردن». هر مکالمه‌ی انتخاب‌شده به BlockStore اضافه میشه (که خودش باعث
     * میشه دفعه‌ی بعد getConversations دیگه نشونش نده)، و لیست اصلی + شمارنده‌های بلاک
     * دوباره لود میشن.
     *
     * یه شماره‌ی خصوصی نمی‌تونه هم‌زمان بلاک هم بشه (طبق درخواست کاربر) - قبل از بلاک‌کردن
     * چک می‌کنیم؛ اگه از قبل خصوصی بود، رد میشه و توی پیام نتیجه بهش اشاره میشه.
     */
    fun blockConversations(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val blocked = mutableListOf<Conversation>()
            var privateSkipped = 0
            var alreadyBlockedSkipped = 0
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    if (PrivateStore.isAddressPrivate(app, conversation.address)) {
                        privateSkipped++
                        return@forEach
                    }
                    val newlyBlocked = BlockStore.blockNumber(
                        app,
                        conversation.threadId,
                        conversation.address,
                        conversation.displayName
                    )
                    if (!newlyBlocked) {
                        alreadyBlockedSkipped++
                        return@forEach
                    }
                    // اگه نوتیف این شماره الان روی صفحه‌ست، بلافاصله پاک بشه - قبلاً صبر می‌کرد
                    // تا یه اتفاق دیگه (رفتن به چت دیگه) خودش پاکش کنه، که کند به‌نظر می‌رسید
                    NotificationManagerCompat.from(app).cancel(conversation.address.hashCode())
                    blocked.add(conversation)
                }
            }
            val base = when {
                blocked.size == 1 -> "${blocked.first().displayName} بلاک شد"
                blocked.isNotEmpty() -> "${blocked.size} مخاطب بلاک شدن"
                else -> "هیچ مخاطب جدیدی بلاک نشد"
            }
            val notes = mutableListOf<String>()
            if (privateSkipped > 0) notes.add("$privateSkipped مخاطب چون خصوصی بودن رد شدن")
            if (alreadyBlockedSkipped > 0) notes.add("$alreadyBlockedSkipped مخاطب از قبل بلاک بودن")
            _operationMessage.value = if (notes.isNotEmpty()) "$base (${notes.joinToString("، ")})" else base

            loadConversations()
            loadBlockedNumbers()
            loadBlockedMessages()
        }
    }

    /**
     * بلاک کردن مستقیم یه شماره از صفحه‌ی «افزودن شماره‌ی بلاک» - برخلاف blockConversations
     * که از لیست مکالمات موجود میاد، این شماره ممکنه اصلاً قبلاً باهاش مکالمه‌ای نداشته
     * باشیم؛ برای همین اول getOrCreateThreadId یه thread براش می‌سازه (یا اگه از قبل بود،
     * همون رو برمی‌گردونه).
     */
    fun blockNumber(address: String, displayName: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (withContext(Dispatchers.IO) { BlockStore.isAddressBlocked(app, address) }) {
                _operationMessage.value = "این شماره از قبل بلاک بود"
                return@launch
            }
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            val isPrivate = withContext(Dispatchers.IO) { PrivateStore.isAddressPrivate(app, address) }
            if (isPrivate) {
                _operationMessage.value = "این شماره خصوصیه - اول باید از بخش خصوصی خارجش کنی"
                return@launch
            }
            withContext(Dispatchers.IO) {
                BlockStore.blockNumber(app, threadId, address, displayName)
            }
            NotificationManagerCompat.from(app).cancel(address.hashCode())
            _operationMessage.value = "$displayName بلاک شد"
            loadConversations()
            loadBlockedNumbers()
            loadBlockedMessages()
        }
    }

    /** آنبلاک‌کردن یه شماره از داخل صفحه‌ی «شماره‌های بلاک‌شده» - دوباره تو لیست اصلی برمی‌گرده */
    fun unblockNumber(threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { BlockStore.unblockThread(getApplication(), threadId) }
            loadBlockedNumbers()
            loadBlockedMessages()
            loadConversations()
        }
    }

    /** لود کردن لیست شماره‌های بلاک‌شده - برای صفحه‌ی «شماره‌های بلاک‌شده» و بج شمارنده */
    fun loadBlockedNumbers() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { BlockStore.getAllBlockedNumbers(getApplication()) }
            _blockedNumbers.value = result
        }
    }

    /** لود کردن همه‌ی پیام‌های بلاک‌شده - برای صفحه‌ی «پیامک‌های بلاک‌شده» و بج شمارنده */
    fun loadBlockedMessages() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForBlockedThreads() }
            _blockedMessages.value = result
        }
    }

    /**
     * خصوصی‌کردن دسته‌جمعی چند مکالمه - دقیقاً مثل blockConversations ولی با PrivateStore.
     * یه شماره‌ی بلاک‌شده نمی‌تونه هم‌زمان خصوصی هم بشه - قبل از خصوصی‌کردن چک میشه.
     */
    fun makeConversationsPrivate(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val madePrivate = mutableListOf<Conversation>()
            var blockedSkipped = 0
            var alreadyPrivateSkipped = 0
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    if (BlockStore.isAddressBlocked(app, conversation.address)) {
                        blockedSkipped++
                        return@forEach
                    }
                    val newlyPrivate = PrivateStore.makePrivate(
                        app,
                        conversation.threadId,
                        conversation.address,
                        conversation.displayName
                    )
                    if (!newlyPrivate) {
                        alreadyPrivateSkipped++
                        return@forEach
                    }
                    NotificationManagerCompat.from(app).cancel(conversation.address.hashCode())
                    madePrivate.add(conversation)
                }
            }
            val base = when {
                madePrivate.size == 1 -> "${madePrivate.first().displayName} خصوصی شد"
                madePrivate.isNotEmpty() -> "${madePrivate.size} مخاطب خصوصی شدن"
                else -> "هیچ مخاطب جدیدی خصوصی نشد"
            }
            val notes = mutableListOf<String>()
            if (blockedSkipped > 0) notes.add("$blockedSkipped مخاطب چون بلاک بودن رد شدن")
            if (alreadyPrivateSkipped > 0) notes.add("$alreadyPrivateSkipped مخاطب از قبل خصوصی بودن")
            _operationMessage.value = if (notes.isNotEmpty()) "$base (${notes.joinToString("، ")})" else base

            loadConversations()
            loadPrivateNumbers()
            loadPrivateMessages()
        }
    }

    /** خارج کردن یه شماره از حالت خصوصی از داخل صفحه‌ی «شماره‌های خصوصی» - دوباره تو لیست اصلی برمی‌گرده */
    fun removePrivate(threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PrivateStore.removePrivate(getApplication(), threadId) }
            loadPrivateNumbers()
            loadPrivateMessages()
            loadConversations()
        }
    }

    /** لود کردن لیست شماره‌های خصوصی - برای صفحه‌ی «شماره‌های خصوصی» و بج شمارنده */
    fun loadPrivateNumbers() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { PrivateStore.getAllPrivateNumbers(getApplication()) }
            _privateNumbers.value = result
        }
    }

    /** لود کردن همه‌ی پیام‌های خصوصی - برای صفحه‌ی «پیامک‌های خصوصی» و بج شمارنده */
    fun loadPrivateMessages() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForPrivateThreads() }
            _privateMessages.value = result
        }
    }

    /** آیا از قبل رمزی برای بخش خصوصی ساخته شده - PrivatePinScreen بر اساس این تصمیم می‌گیره اول‌بار رمز بسازه یا بخواد */
    fun hasPrivatePin(): Boolean = PrivateStore.hasPin(getApplication())

    /** ذخیره‌ی رمز جدید (هش‌شده) - فقط اولین بار که کاربر وارد بخش خصوصی میشه صدا زده میشه */
    fun setPrivatePin(pin: String) {
        PrivateStore.setPin(getApplication(), pin)
    }

    /** مقایسه‌ی رمز واردشده با رمز ذخیره‌شده - عملیات سریع و سبکه، نیازی به coroutine نداره */
    fun verifyPrivatePin(pin: String): Boolean = PrivateStore.verifyPin(getApplication(), pin)

    /** وقتی رمز درست وارد شد (یا تازه ساخته شد) - برای همین session بخش خصوصی باز می‌مونه */
    fun unlockPrivate() {
        _privateUnlocked.value = true
    }

    /** با خروج کامل از بخش خصوصی (دکمه‌ی برگشتِ هاب)، دوباره قفل میشه تا دفعه‌ی بعد رمز بخواد */
    fun lockPrivate() {
        _privateUnlocked.value = false
    }

    /** حذف کامل رمز از صفحه‌ی تنظیمات رمز - بعدش خودکار قفل میشه (دفعه‌ی بعد باید رمز جدید بسازه) */
    fun removePrivatePin() {
        PrivateStore.removePin(getApplication())
        lockPrivate()
    }

    fun searchContacts(query: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { contactsRepository.searchContacts(query) }
            _contacts.value = result
        }
    }

    /** موقع ورود به صفحه «پیام جدید»، thread قبلی که باز بوده رو فراموش کن */
    fun prepareNewMessage() {
        clearOpenThread()
        searchContacts("")
    }

    fun setPickedContact(contact: ContactInfo) {
        _pickedContact.value = contact
    }

    fun consumePickedContact() {
        _pickedContact.value = null
    }

    /**
     * برای صفحه "پیام جدید": پیام رو می‌فرسته، thread رو پیدا/می‌سازه
     * و اطلاعاتش رو توی newConversationTarget می‌ذاره تا Navigation با آدرس درست بره صفحه چت
     */
    fun sendNewMessage(address: String, displayName: String, body: String, subscriptionId: Int?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.sendSms(address, body, subscriptionId) }
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            loadThread(threadId) // قبل از navigate لود میشه تا صفحه چت از همون لحظه‌ی اول پیام رو نشون بده
            loadConversations()
            _newConversationTarget.value = NewConversationTarget(threadId, address, displayName)
        }
    }

    fun consumeNewConversationTarget() {
        _newConversationTarget.value = null
    }

    /** وقتی کاربر روی نوتیف پیامک کلیک می‌کنه، مستقیم بره صفحه چت همون مخاطب (لود پیام‌ها رو
     *  خودِ LaunchedEffect(newTarget) توی AppNavigation انجام میده) */
    fun openThreadFromNotification(threadId: Long, address: String, displayName: String) {
        _newConversationTarget.value = NewConversationTarget(threadId, address, displayName)
    }

    /** باز کردن متن یه پیام توی صفحه‌ی نوت (از دابل‌کلیک روی حباب پیام یا از منوی کلیک روی پیام) */
    fun openNote(text: String) {
        _noteText.value = text
    }

    /** وقتی از صفحه‌ی نوت با دکمه‌ی برگشت خارج میشیم */
    fun consumeNote() {
        _noteText.value = null
    }
}

data class NewConversationTarget(
    val threadId: Long,
    val address: String,
    val displayName: String
)
