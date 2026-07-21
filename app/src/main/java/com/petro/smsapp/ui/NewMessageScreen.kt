package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.SimInfo

@Composable
fun NewMessageScreen(
    contacts: List<ContactInfo>,
    sims: List<SimInfo>,
    pickedContact: ContactInfo?,
    onPickedContactConsumed: () -> Unit,
    onPickFromContactsClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSend: (address: String, displayName: String, body: String, subscriptionId: Int?) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var messageBody by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }

    // وقتی کاربر از اپ مخاطبین سیستم یه مخاطب انتخاب کرد، همون رو به‌عنوان گیرنده ثبت کن
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
            // فقط وقتی گیرنده مشخص شده، نوار ارسال رو نشون بده - همیشه پایین صفحه و بالای کیبورده
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageBody,
                            onValueChange = { messageBody = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("متن پیام") },
                            maxLines = 5
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (messageBody.isNotBlank()) {
                                    onSend(
                                        selectedContact!!.phoneNumber,
                                        selectedContact!!.name,
                                        messageBody,
                                        selectedSimId
                                    )
                                }
                            },
                            enabled = messageBody.isNotBlank()
                        ) {
                            Text("ارسال")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (selectedContact == null) {
                // مرحله ۱: انتخاب گیرنده
                OutlinedButton(
                    onClick = onPickFromContactsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("انتخاب از مخاطبین گوشی")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchChange(it)
                    },
                    label = { Text("جستجوی نام یا شماره") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                // اگه چیزی که تایپ شده مثل شماره باشه، امکان ارسال مستقیم به همون شماره
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
            } else {
                // مرحله ۲: گیرنده مشخصه، فقط هدر رو نشون بده - ورودی پیام توی bottomBar هست
                Row(verticalAlignment = Alignment.CenterVertically) {
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
