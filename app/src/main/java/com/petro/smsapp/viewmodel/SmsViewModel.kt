package com.petro.smsapp.viewmodel

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.ContactsRepository
import com.petro.smsapp.data.Conversation
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SimRepository
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.data.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // threadId مکالمه‌ای که الان روی صفحه چت بازه، تا وقتی پیامک جدید میاد بدونیم کدوم thread رو دوباره لود کنیم
    private var openThreadId: Long? = null

    /**
     * وقتی پیامکی وارد یا ارسال میشه (چه از این اپ، چه از بیرون)، سیستم به این آبزرور خبر میده
     * و بدون نیاز به بستن/بازکردن اپ، لیست مکالمات و مکالمه‌ی بازشده به‌روز میشن.
     */
    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadConversations()
            openThreadId?.let { refreshMessages(it) }
        }
    }

    init {
        application.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true, // notifyForDescendants: تغییرات inbox/sent/... که زیرشاخه‌ی content://sms هستن رو هم پوشش میده
            smsObserver
        )
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            refreshMessages(threadId)
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
            withContext(Dispatchers.IO) { repository.deleteMessage(messageId) }
            refreshMessages(threadId)
            loadConversations()
        }
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
}

data class NewConversationTarget(
    val threadId: Long,
    val address: String,
    val displayName: String
)
