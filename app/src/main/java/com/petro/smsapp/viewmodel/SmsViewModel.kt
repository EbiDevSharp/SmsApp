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
import com.petro.smsapp.data.BlockKeyword
import com.petro.smsapp.data.BlockPattern
import com.petro.smsapp.data.BlockPatternType
import com.petro.smsapp.data.BlockedMessageEntry
import com.petro.smsapp.data.BlockedNumber
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.ContactsRepository
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.DataChangeSignal
import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.data.MessageGroupMember
import com.petro.smsapp.data.MessageGroupSummary
import com.petro.smsapp.data.PrivateMessageEntry
import com.petro.smsapp.data.PrivateNumber
import com.petro.smsapp.data.PrivatePinDataStore
import com.petro.smsapp.data.ScheduledMessage
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SimRepository
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.data.SmsRepository
import com.petro.smsapp.data.TrashedMessage
import com.petro.smsapp.data.repository.BlockRepository
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
    private val blockRepository: BlockRepository = AppContainer.blockRepository(application)
    private val privateRepository: PrivateRepository = AppContainer.privateRepository(application)
    private val pinRepository: PinRepository = AppContainer.pinRepository(application)
    private val scheduledMessageRepository: ScheduledMessageRepository = AppContainer.scheduledMessageRepository(application)
    private val messageGroupRepository: MessageGroupRepository = AppContainer.messageGroupRepository(application)

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

    /** کل مخاطبینِ گوشی - فقط برای ContactPickerScreen، فقط با درخواستِ صریحِ کاربر لود میشه */
    private val _allContactsForPicker = MutableStateFlow<List<ContactInfo>>(emptyList())
    val allContactsForPicker: StateFlow<List<ContactInfo>> = _allContactsForPicker.asStateFlow()

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims.asStateFlow()

    private val _pickedContact = MutableStateFlow<ContactInfo?>(null)
    val pickedContact: StateFlow<ContactInfo?> = _pickedContact.asStateFlow()

    /** نتیجه‌ی ContactPickerScreen (چندتایی) - جدا از pickedContact (که مالِ Intent سیستمیِ تک‌انتخابیه) */
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

    val blockedNumbers: StateFlow<List<BlockedNumber>> =
        blockRepository.observeBlockedNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockKeywords: StateFlow<List<BlockKeyword>> =
        blockRepository.observeKeywords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockPatterns: StateFlow<List<BlockPattern>> =
        blockRepository.observePatterns().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateNumbers: StateFlow<List<PrivateNumber>> =
        privateRepository.observePrivateNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allScheduledMessages: StateFlow<List<ScheduledMessage>> =
        scheduledMessageRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** خلاصه‌ی گروه‌های پیامکیِ ذخیره‌شده (اسم + تعداد اعضا) - برای نمایش توی صفحه‌ی «پیام جدید» */
    val groupSummaries: StateFlow<List<MessageGroupSummary>> =
        messageGroupRepository.observeGroupSummaries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- لیست‌های ترکیبیِ Telephony + Room - هم‌چنان trigger-based ----

    private val _trash = MutableStateFlow<List<TrashedMessage>>(emptyList())
    val trash: StateFlow<List<TrashedMessage>> = _trash.asStateFlow()

    private val _blockedMessages = MutableStateFlow<List<BlockedMessageEntry>>(emptyList())
    val blockedMessages: StateFlow<List<BlockedMessageEntry>> = _blockedMessages.asStateFlow()

    private val _privateMessages = MutableStateFlow<List<PrivateMessageEntry>>(emptyList())
    val privateMessages: StateFlow<List<PrivateMessageEntry>> = _privateMessages.asStateFlow()

    private val _privateUnlocked = MutableStateFlow(false)
    val privateUnlocked: StateFlow<Boolean> = _privateUnlocked.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

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
                loadBlockedMessages()
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

    @Deprecated("دیگه لازم نیست - favorites/favoriteIds خودشون از Room reactive هستن", ReplaceWith(""))
    fun loadFavorites() { /* no-op: favorites همیشه reactive */ }

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

    @Deprecated("دیگه لازم نیست - pinnedMessageIds خودش از Room reactive هست", ReplaceWith(""))
    fun loadPinnedMessages() { /* no-op */ }

    fun togglePinMessage(message: SmsMessage) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { pinRepository.togglePinMessage(message.id) }
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

    fun permanentlyDeleteFromTrash(messageId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.permanentlyDelete(messageId) }
            loadTrash()
        }
    }

    fun blockConversations(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val blocked = mutableListOf<Conversation>()
            var privateSkipped = 0
            var alreadyBlockedSkipped = 0
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    if (privateRepository.isAddressPrivate(conversation.address)) {
                        privateSkipped++
                        return@forEach
                    }
                    val newlyBlocked = blockRepository.blockNumber(
                        conversation.threadId,
                        conversation.address,
                        conversation.displayName
                    )
                    if (!newlyBlocked) {
                        alreadyBlockedSkipped++
                        return@forEach
                    }
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
            loadBlockedMessages()
        }
    }

    fun blockNumber(address: String, displayName: String) {
        if (address.isBlank()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            if (withContext(Dispatchers.IO) { blockRepository.isAddressBlocked(address) }) {
                _operationMessage.value = "این شماره از قبل بلاک بود"
                return@launch
            }
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
            val isPrivate = withContext(Dispatchers.IO) { privateRepository.isAddressPrivate(address) }
            if (isPrivate) {
                _operationMessage.value = "این شماره خصوصیه - اول باید از بخش خصوصی خارجش کنی"
                return@launch
            }
            withContext(Dispatchers.IO) { blockRepository.blockNumber(threadId, address, displayName) }
            NotificationManagerCompat.from(app).cancel(address.hashCode())
            _operationMessage.value = "$displayName بلاک شد"
            loadConversations()
            loadBlockedMessages()
        }
    }

    fun unblockNumber(threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { blockRepository.unblockThread(threadId) }
            loadBlockedMessages()
            loadConversations()
        }
    }

    @Deprecated("دیگه لازم نیست - blockedNumbers خودش از Room reactive هست", ReplaceWith(""))
    fun loadBlockedNumbers() { /* no-op */ }

    fun loadBlockedMessages() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getMessagesForBlockedThreads() }
            _blockedMessages.value = result
        }
    }

    @Deprecated("دیگه لازم نیست - blockKeywords خودش از Room reactive هست", ReplaceWith(""))
    fun loadBlockKeywords() { /* no-op */ }

    fun addBlockKeyword(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { blockRepository.addKeyword(text) }
            _operationMessage.value = if (added) "کلمه‌ی «${text.trim()}» اضافه شد" else "این کلمه از قبل اضافه شده بود"
        }
    }

    fun removeBlockKeyword(id: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { blockRepository.removeKeyword(id) } }
    }

    @Deprecated("دیگه لازم نیست - blockPatterns خودش از Room reactive هست", ReplaceWith(""))
    fun loadBlockPatterns() { /* no-op */ }

    fun addBlockPattern(type: BlockPatternType, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { blockRepository.addPattern(type, value) }
            _operationMessage.value = if (added) "الگوی «${value.trim()}» اضافه شد" else "این الگو از قبل اضافه شده بود"
        }
    }

    fun removeBlockPattern(id: String) {
        viewModelScope.launch { withContext(Dispatchers.IO) { blockRepository.removePattern(id) } }
    }

    fun makeConversationsPrivate(conversations: List<Conversation>) {
        if (conversations.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val madePrivate = mutableListOf<Conversation>()
            var blockedSkipped = 0
            var alreadyPrivateSkipped = 0
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    if (blockRepository.isAddressBlocked(conversation.address)) {
                        blockedSkipped++
                        return@forEach
                    }
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
            val notes = mutableListOf<String>()
            if (blockedSkipped > 0) notes.add("$blockedSkipped مخاطب چون بلاک بودن رد شدن")
            if (alreadyPrivateSkipped > 0) notes.add("$alreadyPrivateSkipped مخاطب از قبل خصوصی بودن")
            _operationMessage.value = if (notes.isNotEmpty()) "$base (${notes.joinToString("، ")})" else base

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
            val isBlocked = withContext(Dispatchers.IO) { blockRepository.isAddressBlocked(address) }
            if (isBlocked) {
                _operationMessage.value = "این شماره بلاکه - اول باید از بخش بلاک خارجش کنی"
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

    @Deprecated("دیگه لازم نیست - privateNumbers خودش از Room reactive هست", ReplaceWith(""))
    fun loadPrivateNumbers() { /* no-op */ }

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

    /** فقط با درخواستِ صریحِ کاربر (زدنِ دکمه‌ی «انتخاب از مخاطبین») صدا زده میشه - کل مخاطبینِ دارای شماره رو لود می‌کنه */
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
        // قبلاً اینجا searchContacts("") صدا زده می‌شد، یعنی محضِ باز شدنِ صفحه‌ی «پیام
        // جدید» کل مخاطبین گوشی لود می‌شدن. الان تا کاربر واقعاً چیزی تایپ نکنه، لیست
        // خالی می‌مونه (ContactsRepository.searchContacts هم با کوئری خالی چیزی برنمی‌گردونه).
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

    /**
     * ارسال گروهی: وقتی توی «پیام جدید» بیشتر از یک مخاطب انتخاب شده، پیام جدا-جدا
     * (SMS مستقل، نه MMS) به تک‌تک شماره‌ها ارسال میشه - یعنی هر گیرنده یه thread
     * جدا/موجودِ خودش رو می‌گیره، دقیقاً مثل اینکه پیام رو تک‌به‌تک براشون فرستاده باشی.
     * چون اینجا دیگه یه thread واحد برای باز کردن نداریم، برخلاف sendNewMessage،
     * _newConversationTarget ست نمیشه - صفحه‌ی «پیام جدید» بعدش برمی‌گرده به لیست مکالمات.
     */
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

    /** نسخه‌ی گروهیِ scheduleMessage - همون منطق sendNewMessageToMultiple ولی برای پیامِ زمان‌بندی‌شده */
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
                    // id باید بین گیرنده‌های مختلف یکتا باشه؛ چون همه‌شون توی یه coroutine
                    // پشتِ‌سرِهم ذخیره میشن، فقط currentTimeMillis ممکنه برای دوتاشون یکی
                    // دربیاد - برای همین index هم بهش اضافه میشه
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

    /** خوندنِ اعضای یه گروهِ پیامکیِ ذخیره‌شده - فقط وقتی کاربر واقعاً یه گروه رو برای بارگذاری انتخاب کنه صدا زده میشه */
    suspend fun getGroupMembers(groupId: Long): List<MessageGroupMember> =
        withContext(Dispatchers.IO) { messageGroupRepository.getGroupMembers(groupId) }

    /** ذخیره‌ی مخاطبینِ انتخاب‌شده‌ی فعلی (توی «پیام جدید») به‌عنوان یه گروهِ جدید */
    fun saveMessageGroup(name: String, members: List<Pair<String, String>>) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || members.isEmpty()) return
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
}

data class NewConversationTarget(
    val threadId: Long,
    val address: String,
    val displayName: String
)
