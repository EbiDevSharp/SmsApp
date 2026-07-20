package com.petro.smsapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.ContactsRepository
import com.petro.smsapp.data.Conversation
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

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contacts: StateFlow<List<ContactInfo>> = _contacts.asStateFlow()

    // اطلاعات مکالمه‌ای که بعد از "پیام جدید" ساخته/پیدا شده، تا Navigation بتونه با اطلاعات کامل بره صفحه چت
    private val _newConversationTarget = MutableStateFlow<NewConversationTarget?>(null)
    val newConversationTarget: StateFlow<NewConversationTarget?> = _newConversationTarget.asStateFlow()

    fun loadConversations() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.getConversations() }
            _conversations.value = result
        }
    }

    fun loadThread(threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.markThreadAsRead(threadId) }
            val result = withContext(Dispatchers.IO) { repository.getMessagesForThread(threadId) }
            _messages.value = result
        }
    }

    fun sendMessage(address: String, body: String, threadId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.sendSms(address, body) }
            loadThread(threadId)
            loadConversations()
        }
    }

    fun searchContacts(query: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { contactsRepository.searchContacts(query) }
            _contacts.value = result
        }
    }

    /**
     * برای صفحه "پیام جدید": پیام رو می‌فرسته، thread رو پیدا/می‌سازه
     * و اطلاعاتش رو توی newConversationTarget می‌ذاره تا Navigation با آدرس درست بره صفحه چت
     */
    fun sendNewMessage(address: String, displayName: String, body: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.sendSms(address, body) }
            val threadId = withContext(Dispatchers.IO) { repository.getOrCreateThreadId(address) }
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
