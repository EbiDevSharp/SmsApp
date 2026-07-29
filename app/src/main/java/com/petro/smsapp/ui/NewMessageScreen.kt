package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.util.DateFormatter

@Composable
fun NewMessageScreen(
    contacts: List<ContactInfo>,
    sims: List<SimInfo>,
    pickedContact: ContactInfo?,
    onPickedContactConsumed: () -> Unit,
    onPickFromContactsClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSend: (address: String, displayName: String, body: String, subscriptionId: Int?) -> Unit,
    onScheduleSend: (address: String, displayName: String, body: String, subscriptionId: Int?, scheduledAt: Long) -> Unit,
    onLeaveWithDraft: (address: String, displayName: String, body: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var messageBody by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }
    var scheduledAt by remember { mutableStateOf<Long?>(null) }

    val latestContact = rememberUpdatedState(selectedContact)
    val latestBody = rememberUpdatedState(messageBody)
    val latestOnLeave = rememberUpdatedState(onLeaveWithDraft)
    DisposableEffect(Unit) {
        onDispose {
            val contact = latestContact.value
            if (contact != null) {
                latestOnLeave.value(contact.phoneNumber, contact.name, latestBody.value)
            }
        }
    }

    LaunchedEffect(pickedContact) {
        if (pickedContact != null) {
            selectedContact = pickedContact
            onPickedContactConsumed()
        }
    }

    LaunchedEffect(sims) {
        if (selectedSimId == null && sims.isNotEmpty()) {
            selectedSimId = sims.first().subscriptionId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیام جدید") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        },
        bottomBar = {
            if (selectedContact != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    SimSelector(
                        sims = sims,
                        selectedSubscriptionId = selectedSimId,
                        onSelect = { selectedSimId = it }
                    )
                    MessageInputBar(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        onSendClick = {
                            if (messageBody.isNotBlank()) {
                                val at = scheduledAt
                                if (at != null) {
                                    onScheduleSend(
                                        selectedContact!!.phoneNumber,
                                        selectedContact!!.name,
                                        messageBody,
                                        selectedSimId,
                                        at
                                    )
                                } else {
                                    onSend(
                                        selectedContact!!.phoneNumber,
                                        selectedContact!!.name,
                                        messageBody,
                                        selectedSimId
                                    )
                                }
                                messageBody = ""
                                scheduledAt = null
                            }
                        },
                        scheduledAt = scheduledAt,
                        onScheduledAtChange = { scheduledAt = it },
                        placeholder = "متن پیام"
                    )
                }
            }
        }
    ) { padding ->
        if (selectedContact == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchChange(it)
                    },
                    label = { Text("جستجوی نام یا شماره") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                    leadingIcon = {
                        IconButton(onClick = onPickFromContactsClick) {
                            Icon(Icons.Filled.Person, contentDescription = "انتخاب از مخاطبین گوشی")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }) {
                    TextButton(onClick = {
                        selectedContact = ContactInfo(contactId = -1, name = searchQuery, phoneNumber = searchQuery)
                    }) {
                        Text("ارسال به شماره: ${searchQuery}")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(contacts, key = { it.contactId to it.phoneNumber }) { contact ->
                        ContactRow(contact, onClick = { selectedContact = contact })
                        Divider()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(name = selectedContact!!.name)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("به:", style = MaterialTheme.typography.labelMedium)
                        Text(selectedContact!!.name, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { selectedContact = null }) {
                        Text("تغییر")
                    }
                }
                Divider()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "اولین پیامت رو بنویس",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(name = contact.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ContactAvatar(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}