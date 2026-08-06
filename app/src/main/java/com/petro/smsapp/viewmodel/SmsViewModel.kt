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
import com.petro.smsapp.data.AlarmScheduler
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.ContactsRepository
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.data.FilterGroupSummary
import com.petro.smsapp.data.FilteredMessageEntry
import com.petro.smsapp.data.MessageGroupMember
import com.petro.smsapp.data.MessageGroupSummary
import com.petro.smsapp.data.PatternType
import com.petro.smsapp.data.PrivateMessageEntry
import com.petro.smsapp.data.PrivateNumber
import com.petro.smsapp.data.PrivatePinDataStore
import com.petro.smsapp.data.ScheduledMessage
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SimRepository
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.data.SmsRepository
import com.petro.smsapp.data.TrashedMessage
import com.petro.smsapp.data.repository.FilterGroupRepository
import com.petro.smsapp.data.repository.MessageGroupRepository
import com.petro.smsapp.data.repository.PinRepository
import com.petro.smsapp.data.repository.PrivateRepository
import com.petro.smsapp.data.repository.FavoriteRepository
import com.petro.smsapp.data.repository.ScheduledMessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsRepository(application)
    private val contactsRepository = ContactsRepository(application)
    private val simRepository = SimRepository(application)

    private val favoriteRepository: FavoriteRepository = AppContainer.favoriteRepository(application)
    private val privateRepository: PrivateRepository = AppContainer.privateRepository(application)
    private val pinRepository: PinRepository = AppContainer.pinRepository(application)
    private val scheduledMessageRepository: ScheduledMessageRepository = AppContainer.scheduledMessageRepository(application)
    private val messageGroupRepository: MessageGroupRepository = AppContainer.messageGroupRepository(application)
    private val filterGroupRepository: FilterGroupRepository = AppContainer.filterGroupRepository(application)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _scheduledMessages = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val scheduledMessages: StateFlow<List<ScheduledMessage>> = _scheduledMessages.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contacts: StateFlow<List<ContactInfo>> = _contacts.asStateFlow()

    private val _allContactsForPicker = MutableStateFlow<List<ContactInfo>>(emptyList())
    val allContactsForPicker: StateFlow<List<ContactInfo>> = _allContactsForPicker.asStateFlow()

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    private val _pickedContact = MutableStateFlow<ContactInfo?>(null)
    val pickedContact: StateFlow<ContactInfo?> = _pickedContact.asStateFlow()

    private val _pickedContactsBatch = MutableStateFlow<List<ContactInfo>?>(null)
    val pickedContactsBatch: StateFlow<List<ContactInfo>?> = _pickedContactsBatch.asStateFlow()

    private val _newConversationTarget = MutableStateFlow<NewConversationTarget?>(null)
    val newConversationTarget: StateFlow<NewConversationTarget?> = _newConversationTarget.asStateFlow()

    private val _noteText = MutableStateFlow<String?>(null)
    val noteText: StateFlow<String?> = _noteText.asStateFlow()

    // ---- لیست‌های کاملاً Room-based - reactive، بدون نیاز به load دستی ----

    val favorites: StateFlow<List<FavoriteMessage>> =
        favoriteRepository.observeFavorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<Long>> =
        favoriteRepository.observeFavoriteIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val pinnedMessageIds: StateFlow<Set<Long>> =
        pinRepository.observePinnedMessageIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val pinnedMessageThreadIds: StateFlow<Set<Long>> =
        pinRepository.observePinnedMessageThreadIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoriteThreadIds: StateFlow<Set<Long>> =
        favorites.map { list -> list.map { it.threadId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val privateNumbers: StateFlow<List<PrivateNumber>> =
        privateRepository.observePrivateNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allScheduledMessages: StateFlow<List<ScheduledMessage>> =
        scheduledMessageRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupSummaries: StateFlow<List<MessageGroupSummary>> =
        messageGroupRepository.observeGroupSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** خلاصه‌ی همه‌ی گروه‌های فیلتر (شماره/کلمه/الگو/تنظیمات) - جایگزینِ عمومیِ بلاک، برای هابِ صفحه‌ی «گروه‌ها» */
    val filterGroupSummaries: StateFlow<List<FilterGroupSummary>> =
        filterGroupRepository.observeGroupSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * فقط گروه‌هایی که کاربر گفته تویِ شیتِ انتخابِ گروهِ دکمه‌ی نوتیفیکیشن هم نشون داده
     * بشن (FilterGroup.showInNotificationPicker) - برای شیتِ بازشده از دکمه‌ی «افزودن به
     * گروه»ِ روی نوتیف (quickGroupPickTarget). شیتِ سویپ/منویِ داخلِ لیست همچنان از
     * filterGroupSummaries کامل استفاده می‌کنه.
     */
    val notificationPickerGroups: StateFlow<List<FilterGroupSummary>> =
        filterGroupRepository.observeNotificationPickerGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- لیست‌های ترکیبیِ Telephony + Room - هم‌چنان trigger-based ----

    private val _trash = MutableStateFlow<List<TrashedMessage>>(emptyList())
    val trash: StateFlow<List<TrashedMessage>> = _trash.asStateFlow()

    private val _filterGroupMessages = MutableStateFlow<List<FilteredMessageEntry>>(emptyList())
    val filterGroupMessages: StateFlow<List<FilteredMessageEntry>> = _filterGroupMessages.asStateFlow()

    private val _privateMessages = MutableStateFlow<List<PrivateMessageEntry>>(emptyList())
    val privateMessages: StateFlow<List<PrivateMessageEntry>> = _privateMessages.asStateFlow()

    private val _privateUnlocked = MutableStateFlow(false)
    val privateUnlocked: StateFlow<Boolean> = _privateUnlocked.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    /** وقتی از سویپ/منویِ لیستِ مکالمات «افزودن به گروه» زده میشه، لیستِ مکالمه‌های هدف اینجا می‌شینه تا AppNavigation یه شیتِ انتخابِ گروه نشون بده */
    private val _pendingGroupPickTargets = MutableStateFlow<List<Conversation>?>(null)
    val pendingGroupPickTargets: StateFlow<List<Conversation>?> = _pendingGroupPickTargets.asStateFlow()

    /** وقتی دکمه‌ی روی نوتیف زده میشه، اپ باز میشه و این پر میشه (تک‌شماره‌ای، نه لیستِ مکالمه) */
    private val _quickGroupPickTarget = MutableStateFlow<QuickGroupPickTarget?>(null)
    val quickGroupPickTarget: StateFlow<QuickGroupPickTarget?> = _quickGroupPickTarget.asStateFlow()

    private var openThreadId: Long? = null

    private var observerDebounceJob: Job? = null

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
            true,
            smsObserver
        )

        viewModelScope.launch {
            DataChangeSignal.tick.drop(1).collect {
                loadConversations()
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
        loadScheduledMessages(threadId)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForThread(threadId) }
            _messages.value = result
            _draftText.value = withContext(Dispatchers.IO) { repository.getDraftText(threadId) }

            val marked = withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            if (!marked) {
                _operationMessage.value = "اپ الان پیش‌فرض پیامک نیست، برای همین علامت «خوانده‌شده» ثبت نشد."
            } else {
                loadConversations()
            }
        }
    }

    private fun refreshMessages(threadId: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForThread(threadId) }
            _messages.value = result

            if (openThreadId == threadId && result.any { !it.isOutgoing && !it.isRead }) {
                withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            }
        }
    }

    fun clearOpenThread() {
        openThreadId = null
        ActiveThreadTracker.activeThreadId = null
        _draftText.value = ""
    }

    fun onAppBackgrounded() {
        ActiveThreadTracker.activeThreadId = null
    }

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

    fun saveDraft(threadId: Long, address: String, body: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.saveDraft(threadId, address, body) }
            loadConversations()
        }
    }

    fun saveDraftForNewConversation(address: String, displayName: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            if (threadId == 0L) return@launch
            withContext(Dispatchers.IO) { repository.saveDraft(threadId, address, body) }
            loadConversations()
        }
    }

    fun resendMessage(message: SmsMessage) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteMessage(message.id) }
            if (!deleted) {
                _operationMessage.value = "این پیام به علاقه‌مندی‌ها اضافه شده و قفله. برای ارسال دوباره، اول از علاقه‌مندی‌ها بردارش."
                return@launch
            }
            withContext(Dispatchers.IO) {
                repository.sendSms(message.address, message.body, message.subscriptionId.takeIf { it >= 0 })
            }
            loadThread(message.threadId)
            loadConversations()
        }
    }

    fun deleteMessage(threadId: Long, messageId: Long) {
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) { repository.deleteMessage(messageId) }
            if (!deleted) {
                _operationMessage.value = "این پیام به علاقه‌مندی‌ها اضافه شده و قفله. برای حذف، اول از علاقه‌مندی‌ها بردارش."
                return@launch
            }
            refreshMessages(threadId)
            loadConversations()
        }
    }

    fun consumeOperationMessage() {
        _operationMessage.value = null
    }

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

    fun deletePrivateMessages(messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteMessages(messageIds) }
            val base = if (result.movedToTrash) "پیام‌های انتخاب‌شده به سطل زباله منتقل شدن" else "پیام‌های انتخاب‌شده حذف شدن"
            _operationMessage.value = if (result.blockedFavoriteCount > 0) {
                "$base (${result.blockedFavoriteCount} پیام فیوریت‌شده به‌خاطر قفل بودن دست‌نخورده موند)"
            } else {
                base
            }
            loadPrivateMessages()
        }
    }

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

    fun toggleFavorite(message: SmsMessage, contactDisplayName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                favoriteRepository.toggleFavorite(
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
    }

    fun removeFavorite(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { favoriteRepository.removeFavorite(messageId) }
        }
    }

    fun togglePinMessage(message: SmsMessage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { pinRepository.togglePinMessage(message.threadId, message.id) }
        }
    }

    fun pinConversations(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val allPinned = conversations.all { it.isPinned }
            var pinnedCount = 0
            var limitSkipped = 0
            val maxAllowed = AppSettings.getMaxPinnedConversations(getApplication())
            withContext(Dispatchers.IO) {
                if (allPinned) {
                    conversations.forEach { pinRepository.unpinThread(it.threadId) }
                } else {
                    conversations.filter { !it.isPinned }.forEach { conversation ->
                        if (pinRepository.getPinnedCount() >= maxAllowed) {
                            limitSkipped++
                            return@forEach
                        }
                        pinRepository.pinThread(conversation.threadId)
                        pinnedCount++
                    }
                }
            }
            _operationMessage.value = when {
                allPinned -> "پین ${conversations.size} مکالمه برداشته شد"
                limitSkipped > 0 && pinnedCount > 0 -> "$pinnedCount مکالمه پین شد (حداکثر تعداد پین پر شد، $limitSkipped مکالمه دیگه پین نشد)"
                limitSkipped > 0 -> "حداکثر تعداد پین ($maxAllowed مکالمه) پره"
                else -> "$pinnedCount مکالمه پین شد"
            }
            loadConversations()
        }
    }

    fun loadTrash() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getTrashedMessages() }
            _trash.value = result
        }
    }

    fun restoreFromTrash(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.restoreFromTrash(messageId) }
            loadTrash()
            loadConversations()
            openThreadId?.let { refreshMessages(it) }
        }
    }

    fun restoreMultipleFromTrash(messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                messageIds.forEach { repository.restoreFromTrash(it) }
            }
            loadTrash()
            loadConversations()
            openThreadId?.let { refreshMessages(it) }
        }
    }

    fun permanentlyDeleteFromTrash(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.permanentlyDelete(messageId) }
            loadTrash()
        }
    }

    fun permanentlyDeleteMultipleFromTrash(messageIds: Set<Long>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                messageIds.forEach { repository.permanentlyDelete(it) }
            }
            loadTrash()
        }
    }

    // ==================================================================
    // گروهِ فیلتر - جایگزینِ عمومیِ بخشِ قبلیِ «بلاک»
    // ==================================================================

    fun createFilterGroup(
        name: String,
        hideFromMainList: Boolean,
        showNotifications: Boolean,
        blockNonContacts: Boolean,
        showInNotificationPicker: Boolean = true
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                filterGroupRepository.createGroup(trimmed, hideFromMainList, showNotifications, blockNonContacts, showInNotificationPicker)
            }
            _operationMessage.value = "گروهِ «$trimmed» ساخته شد"
        }
    }

    fun updateFilterGroup(
        id: Long,
        name: String,
        hideFromMainList: Boolean,
        showNotifications: Boolean,
        blockNonContacts: Boolean,
        showInNotificationPicker: Boolean
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                filterGroupRepository.updateGroup(id, trimmed, hideFromMainList, showNotifications, blockNonContacts, showInNotificationPicker)
            }
            loadConversations()
        }
    }

    fun deleteFilterGroup(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { filterGroupRepository.deleteGroup(id) }
            loadConversations()
        }
    }

    /** بعد از درگ‌اند‌دراپِ لیستِ گروه‌ها صدا زده میشه - orderedGroupIds ترتیبِ نهاییِ کاربره */
    fun reorderFilterGroups(orderedGroupIds: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { filterGroupRepository.reorderGroups(orderedGroupIds) }
        }
    }

    /** سوییچِ گلوبالِ «دریافتِ نوتیف» تویِ هابِ گروه‌ها - رویِ همه‌ی گروه‌های موجود یه‌جا اعمال میشه */
    fun setAllFilterGroupsShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { filterGroupRepository.setAllGroupsShowNotifications(enabled) }
        }
    }

    /** سوییچِ گلوبالِ «نمایش در لیستِ پیام‌ها» تویِ هابِ گروه‌ها - رویِ همه‌ی گروه‌های موجود یه‌جا اعمال میشه */
    fun setAllFilterGroupsShowInMainList(show: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { filterGroupRepository.setAllGroupsShowInMainList(show) }
            loadConversations()
        }
    }

    /**
     * تنظیم/برداشتنِ هدفِ دکمه‌ی «افزودن سریع به گروه»ِ روی نوتیف - از داخلِ صفحه‌ی
     * تنظیماتِ خودِ گروه (FilterGroupDetailScreen) صدا زده میشه. groupId=null یعنی
     * هدف کلاً برداشته بشه. چون همیشه حداکثر یه گروه می‌تونه هدف باشه، ست‌کردنِ یه
     * گروهِ جدید خودکار بقیه رو خاموش می‌کنه (منطقش توی Repository/Dao ئه).
     * نیازی به loadConversations یا هیچ refreshِ دستی نیست چون filterGroupSummaries
     * از قبل یه Flow روی جدولِ filter_groups ئه و خودش با تغییرِ همین ستون آپدیت میشه.
     */
    fun setQuickAddTargetGroup(groupId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { filterGroupRepository.setQuickAddTargetGroup(groupId) }
            _operationMessage.value = if (groupId != null) {
                "این گروه، هدفِ «افزودن سریع به گروه» شد"
            } else {
                "هدفِ «افزودن سریع به گروه» برداشته شد"
            }
        }
    }

    fun addNumberToFilterGroup(groupId: Long, address: String, displayName: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            val isPrivate = withContext(Dispatchers.IO) { privateRepository.isAddressPrivate(address) }
            if (isPrivate) {
                _operationMessage.value = "این شماره خصوصیه - اول باید از بخش خصوصی خارجش کنی"
                return@launch
            }
            val added = withContext(Dispatchers.IO) { filterGroupRepository.addNumber(groupId, address, displayName) }
            if (added) {
                // پیام‌های از قبل موجودِ همین شماره هم به این گروه وصل بشن، وگرنه
                // تنظیماتِ گروه (مثلاً مخفی از لیستِ اصلی) فقط رویِ پیام‌های بعدی اثر می‌کرد
                withContext(Dispatchers.IO) {
                    val threadId = repository.getOrCreateThreadId(address)
                    repository.applyGroupToExistingThreadMessages(groupId, threadId, address)
                }
            }
            _operationMessage.value = if (added) "$displayName اضافه شد" else "این شماره از قبل تو این گروه بود"
            loadConversations()
        }
    }

    fun removeNumberFromFilterGroup(groupId: Long, address: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                filterGroupRepository.removeNumber(groupId, address)
                // ردِ matchِ پیام‌های قبلیِ این شماره تو این گروه هم پاک بشه تا اگه گروه
                // مخفی‌شون کرده بود، دوباره برگردن به لیستِ اصلیِ مکالمات
                repository.removeGroupFromExistingThreadMessages(groupId, address)
            }
            loadConversations()
        }
    }

    fun addFilterGroupKeyword(groupId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { filterGroupRepository.addKeyword(groupId, text) }
            _operationMessage.value = if (added) "کلمه‌ی «${text.trim()}» اضافه شد" else "این کلمه از قبل تو این گروه بود"
        }
    }

    fun removeFilterGroupKeyword(id: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { filterGroupRepository.removeKeyword(id) } }
    }

    fun addFilterGroupPattern(groupId: Long, type: PatternType, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { filterGroupRepository.addPattern(groupId, type, value) }
            _operationMessage.value = if (added) "الگوی «${value.trim()}» اضافه شد" else "این الگو از قبل تو این گروه بود"
        }
    }

    fun removeFilterGroupPattern(id: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { filterGroupRepository.removePattern(id) } }
    }

    fun observeFilterGroupNumbers(groupId: Long) = filterGroupRepository.observeNumbersForGroup(groupId)
    fun observeFilterGroupKeywords(groupId: Long) = filterGroupRepository.observeKeywordsForGroup(groupId)
    fun observeFilterGroupPatterns(groupId: Long) = filterGroupRepository.observePatternsForGroup(groupId)

    fun loadFilterGroupMessages(groupId: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForFilterGroup(groupId) }
            _filterGroupMessages.value = result
        }
    }

    fun deleteFilterGroupMessages(messageIds: Set<Long>, groupId: Long) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.deleteMessages(messageIds) }
            val base = if (result.movedToTrash) "پیام‌های انتخاب‌شده به سطل زباله منتقل شدن" else "پیام‌های انتخاب‌شده حذف شدن"
            _operationMessage.value = if (result.blockedFavoriteCount > 0) {
                "$base (${result.blockedFavoriteCount} پیام فیوریت‌شده به‌خاطر قفل بودن دست‌نخورده موند)"
            } else {
                base
            }
            loadFilterGroupMessages(groupId)
        }
    }

    fun requestAddConversationsToGroup(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        _pendingGroupPickTargets.value = conversations
    }

    fun consumeGroupPickTargets() {
        _pendingGroupPickTargets.value = null
    }

    /** ساختِ یه گروهِ تازه (با تنظیماتِ پیش‌فرضِ ساده) + افزودنِ فوریِ مکالمه‌های هدف بهش - برای فرمِ «گروهِ جدید» داخلِ GroupPickerSheet */
    fun createFilterGroupAndAddConversations(name: String, conversations: List<Conversation>) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            consumeGroupPickTargets()
            return
        }
        viewModelScope.launch {
            val groupId = withContext(Dispatchers.IO) {
                filterGroupRepository.createGroup(trimmed, hideFromMainList = false, showNotifications = true, blockNonContacts = false)
            }
            addConversationsToGroup(groupId, conversations)
        }
    }

    /** نسخه‌ی تک‌شماره‌ایِ بالا - برای فرمِ «گروهِ جدید» موقعِ افزودنِ سریع از نوتیف */
    fun createFilterGroupAndAddAddress(name: String, address: String, displayName: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            consumeQuickGroupPick()
            return
        }
        viewModelScope.launch {
            val groupId = withContext(Dispatchers.IO) {
                filterGroupRepository.createGroup(trimmed, hideFromMainList = false, showNotifications = true, blockNonContacts = false)
            }
            addAddressToGroupQuick(groupId, address, displayName)
        }
    }

    fun addConversationsToGroup(groupId: Long, conversations: List<Conversation>) {
        if (conversations.isEmpty()) {
            consumeGroupPickTargets()
            return
        }
        viewModelScope.launch {
            val app = getApplication<Application>()
            var added = 0
            var privateSkipped = 0
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    if (privateRepository.isAddressPrivate(conversation.address)) {
                        privateSkipped++
                        return@forEach
                    }
                    val wasAdded = filterGroupRepository.addNumber(groupId, conversation.address, conversation.displayName)
                    if (wasAdded) {
                        // این thread از قبل تو لیستِ اصلی بود - پیام‌های موجودش هم به این گروه وصل بشن
                        repository.applyGroupToExistingThreadMessages(groupId, conversation.threadId, conversation.address)
                        NotificationManagerCompat.from(app).cancel(conversation.address.hashCode())
                        added++
                    }
                }
            }
            val base = when {
                added == 1 -> "۱ مخاطب به گروه اضافه شد"
                added > 1 -> "$added مخاطب به گروه اضافه شدن"
                else -> "هیچ مخاطبِ جدیدی اضافه نشد (احتمالاً از قبل تو این گروه بودن)"
            }
            _operationMessage.value = if (privateSkipped > 0) "$base ($privateSkipped مخاطب چون خصوصی بودن رد شدن)" else base
            loadConversations()
            consumeGroupPickTargets()
        }
    }

    fun requestQuickGroupPick(address: String, displayName: String) {
        _quickGroupPickTarget.value = QuickGroupPickTarget(address, displayName)
    }

    fun consumeQuickGroupPick() {
        _quickGroupPickTarget.value = null
    }

    fun addAddressToGroupQuick(groupId: Long, address: String, displayName: String) {
        viewModelScope.launch {
            val isPrivate = withContext(Dispatchers.IO) { privateRepository.isAddressPrivate(address) }
            if (isPrivate) {
                _operationMessage.value = "این شماره خصوصیه - اول باید از بخش خصوصی خارجش کنی"
                consumeQuickGroupPick()
                return@launch
            }
            val added = withContext(Dispatchers.IO) { filterGroupRepository.addNumber(groupId, address, displayName) }
            if (added) {
                withContext(Dispatchers.IO) {
                    val threadId = repository.getOrCreateThreadId(address)
                    repository.applyGroupToExistingThreadMessages(groupId, threadId, address)
                }
            }
            _operationMessage.value = if (added) "$displayName به گروه اضافه شد" else "این شماره از قبل تو این گروه بود"
            loadConversations()
            consumeQuickGroupPick()
        }
    }

    // ==================================================================
    // خصوصی
    // ==================================================================

    fun makeConversationsPrivate(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val madePrivate = mutableListOf<Conversation>()
            var alreadyPrivateSkipped = 0
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    val newlyPrivate = privateRepository.makePrivate(
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
            _operationMessage.value = if (alreadyPrivateSkipped > 0) "$base ($alreadyPrivateSkipped مخاطب از قبل خصوصی بودن)" else base

            loadConversations()
            loadPrivateMessages()
        }
    }

    fun makePrivateNumber(address: String, displayName: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (withContext(Dispatchers.IO) { privateRepository.isAddressPrivate(address) }) {
                _operationMessage.value = "این شماره از قبل خصوصی بود"
                return@launch
            }
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            withContext(Dispatchers.IO) { privateRepository.makePrivate(threadId, address, displayName) }
            NotificationManagerCompat.from(app).cancel(address.hashCode())
            _operationMessage.value = "$displayName خصوصی شد"
            loadConversations()
            loadPrivateMessages()
        }
    }

    fun removePrivate(threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { privateRepository.removePrivate(threadId) }
            loadPrivateMessages()
            loadConversations()
        }
    }

    fun loadPrivateMessages() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForPrivateThreads() }
            _privateMessages.value = result
        }
    }

    suspend fun hasPrivatePin(): Boolean =
        withContext(Dispatchers.IO) { PrivatePinDataStore.hasPin(getApplication()) }

    suspend fun setPrivatePin(pin: String) {
        withContext(Dispatchers.IO) { PrivatePinDataStore.setPin(getApplication(), pin) }
    }

    suspend fun verifyPrivatePin(pin: String): Boolean =
        withContext(Dispatchers.IO) { PrivatePinDataStore.verifyPin(getApplication(), pin) }

    fun unlockPrivate() {
        _privateUnlocked.value = true
    }

    fun lockPrivate() {
        _privateUnlocked.value = false
    }

    fun removePrivatePin() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { PrivatePinDataStore.removePin(getApplication()) }
            lockPrivate()
        }
    }

    fun searchContacts(query: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { contactsRepository.searchContacts(query) }
            _contacts.value = result
        }
    }

    fun loadAllContactsForPicker() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { contactsRepository.getAllContacts() }
            _allContactsForPicker.value = result
        }
    }

    fun setPickedContactsBatch(selected: List<ContactInfo>) {
        _pickedContactsBatch.value = selected
    }

    fun consumePickedContactsBatch() {
        _pickedContactsBatch.value = null
    }

    fun prepareNewMessage() {
        clearOpenThread()
        _contacts.value = emptyList()
    }

    fun setPickedContact(contact: ContactInfo) {
        _pickedContact.value = contact
    }

    fun consumePickedContact() {
        _pickedContact.value = null
    }

    fun sendNewMessage(address: String, displayName: String, body: String, subscriptionId: Int?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.sendSms(address, body, subscriptionId) }
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            loadThread(threadId)
            loadConversations()
            _newConversationTarget.value = NewConversationTarget(threadId, address, displayName)
        }
    }

    fun sendNewMessageToMultiple(recipients: List<Pair<String, String>>, body: String, subscriptionId: Int?) {
        if (recipients.isEmpty() || body.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                recipients.forEach { (address, _) ->
                    repository.sendSms(address, body, subscriptionId)
                }
            }
            loadConversations()
        }
    }

    fun scheduleMessage(address: String, displayName: String, body: String, subscriptionId: Int?, scheduledAt: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            val message = ScheduledMessage(
                id = System.currentTimeMillis(),
                threadId = threadId,
                address = address,
                displayName = displayName,
                body = body,
                scheduledAt = scheduledAt,
                subscriptionId = subscriptionId
            )
            withContext(Dispatchers.IO) {
                scheduledMessageRepository.save(message)
                AlarmScheduler.schedule(app, message)
            }
            loadThread(threadId)
            loadConversations()
            _newConversationTarget.value = NewConversationTarget(threadId, address, displayName)
        }
    }

    fun scheduleMessageToMultiple(
        recipients: List<Pair<String, String>>,
        body: String,
        subscriptionId: Int?,
        scheduledAt: Long
    ) {
        if (recipients.isEmpty() || body.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                recipients.forEachIndexed { index, (address, displayName) ->
                    val threadId = repository.getOrCreateThreadId(address)
                    val message = ScheduledMessage(
                        id = System.currentTimeMillis() + index,
                        threadId = threadId,
                        address = address,
                        displayName = displayName,
                        body = body,
                        scheduledAt = scheduledAt,
                        subscriptionId = subscriptionId
                    )
                    scheduledMessageRepository.save(message)
                    AlarmScheduler.schedule(app, message)
                }
            }
            loadConversations()
        }
    }

    fun loadScheduledMessages(threadId: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { scheduledMessageRepository.getForThreadOnce(threadId) }
            _scheduledMessages.value = result
        }
    }

    fun updateScheduledTime(id: Long, threadId: Long, newScheduledAt: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                val existing = scheduledMessageRepository.get(id) ?: return@withContext
                AlarmScheduler.cancel(app, id)
                val updated = existing.copy(scheduledAt = newScheduledAt)
                scheduledMessageRepository.save(updated)
                AlarmScheduler.schedule(app, updated)
            }
            loadScheduledMessages(threadId)
        }
    }

    fun sendScheduledNow(id: Long, threadId: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                val message = scheduledMessageRepository.get(id) ?: return@withContext
                AlarmScheduler.cancel(app, id)
                repository.sendSms(message.address, message.body, message.subscriptionId)
                scheduledMessageRepository.remove(id)
            }
            refreshMessages(threadId)
            loadScheduledMessages(threadId)
            loadConversations()
        }
    }

    fun cancelScheduledMessage(id: Long, threadId: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                AlarmScheduler.cancel(app, id)
                scheduledMessageRepository.remove(id)
            }
            loadScheduledMessages(threadId)
        }
    }

    fun updateScheduledTimeGlobal(id: Long, newScheduledAt: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                val existing = scheduledMessageRepository.get(id) ?: return@withContext
                AlarmScheduler.cancel(app, id)
                val updated = existing.copy(scheduledAt = newScheduledAt)
                scheduledMessageRepository.save(updated)
                AlarmScheduler.schedule(app, updated)
            }
        }
    }

    fun sendScheduledNowGlobal(id: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                val message = scheduledMessageRepository.get(id) ?: return@withContext
                AlarmScheduler.cancel(app, id)
                repository.sendSms(message.address, message.body, message.subscriptionId)
                scheduledMessageRepository.remove(id)
            }
            loadConversations()
            openThreadId?.let { refreshMessages(it) }
        }
    }

    fun cancelScheduledMessageGlobal(id: Long) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            withContext(Dispatchers.IO) {
                AlarmScheduler.cancel(app, id)
                scheduledMessageRepository.remove(id)
            }
        }
    }

    suspend fun getGroupMembers(groupId: Long): List<MessageGroupMember> =
        withContext(Dispatchers.IO) { messageGroupRepository.getGroupMembers(groupId) }

    fun saveMessageGroup(name: String, members: List<Pair<String, String>>) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || members.isEmpty()) return
        val isDuplicate = groupSummaries.value.any { it.name.equals(trimmedName, ignoreCase = true) }
        if (isDuplicate) {
            _operationMessage.value = "یه گروهِ دیگه از قبل اسمِ «$trimmedName» رو داره - یه اسمِ دیگه انتخاب کن"
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                messageGroupRepository.saveGroup(trimmedName, members.map { MessageGroupMember(it.first, it.second) })
            }
            _operationMessage.value = "گروه «$trimmedName» ذخیره شد"
        }
    }

    fun deleteMessageGroup(groupId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { messageGroupRepository.deleteGroup(groupId) }
        }
    }

    fun renameMessageGroup(groupId: Long, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        val isDuplicate = groupSummaries.value.any { it.id != groupId && it.name.equals(trimmedName, ignoreCase = true) }
        if (isDuplicate) {
            _operationMessage.value = "یه گروهِ دیگه از قبل اسمِ «$trimmedName» رو داره - یه اسمِ دیگه انتخاب کن"
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { messageGroupRepository.renameGroup(groupId, trimmedName) }
            _operationMessage.value = "اسمِ گروه به «$trimmedName» تغییر کرد"
        }
    }

    fun updateMessageGroupMembers(groupId: Long, members: List<Pair<String, String>>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                messageGroupRepository.replaceMembers(groupId, members.map { MessageGroupMember(it.first, it.second) })
            }
        }
    }

    fun consumeNewConversationTarget() {
        _newConversationTarget.value = null
    }

    fun openThreadFromNotification(threadId: Long, address: String, displayName: String) {
        _newConversationTarget.value = NewConversationTarget(threadId, address, displayName)
    }

    fun openNote(text: String) {
        _noteText.value = text
    }

    fun consumeNote() {
        _noteText.value = null
    }

    fun markThreadReadFromSwipe(threadId: Long) {
        viewModelScope.launch {
            val marked = withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            if (marked) {
                loadConversations()
            } else {
                _operationMessage.value = "اپ الان پیش‌فرض پیامک نیست، برای همین علامت «خوانده‌شده» ثبت نشد."
            }
        }
    }

    fun markThreadUnreadFromSwipe(threadId: Long) {
        viewModelScope.launch {
            val marked = withContext(Dispatchers.IO) { repository.markThreadAsUnread(threadId) }
            if (marked) {
                loadConversations()
            } else {
                _operationMessage.value = "اپ الان پیش‌فرض پیامک نیست، برای همین علامت «ناخوانده» ثبت نشد."
            }
        }
    }
}

data class NewConversationTarget(
    val threadId: Long,
    val address: String,
    val displayName: String
)

/** هدفِ تک‌شماره‌ایِ درخواستِ سریعِ «افزودن به گروه» (از دکمه‌ی روی نوتیف) */
data class QuickGroupPickTarget(
    val address: String,
    val displayName: String
)
