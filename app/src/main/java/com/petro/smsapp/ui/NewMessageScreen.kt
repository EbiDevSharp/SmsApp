package com.petro.smsapp.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ContactInfo

@Composable
fun NewMessageScreen(
    contacts: List<ContactInfo>,
    onSearchChange: (String) -> Unit,
    onSend: (address: String, displayName: String, body: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var manualNumber by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیام جدید") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(12.dp)
        ) {
            if (selectedContact == null) {
                // مرحله ۱: انتخاب گیرنده
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchChange(it)
                    },
                    label = { Text("جستجوی نام یا شماره") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // اگه چیزی که تایپ شده مثل شماره باشه، امکان ارسال مستقیم به همون شماره
                if (searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }) {
                    TextButton(onClick = {
                        manualNumber = searchQuery
                        selectedContact = ContactInfo(contactId = -1, name = searchQuery, phoneNumber = searchQuery)
                    }) {
                        Text("ارسال به شماره: $searchQuery")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(contacts, key = { it.contactId to it.phoneNumber }) { contact ->
                        ListItem(
                            headlineContent = { Text(contact.name) },
                            supportingContent = { Text(contact.phoneNumber) },
                            modifier = Modifier.clickableItem { selectedContact = contact }
                        )
                        Divider()
                    }
                }
            } else {
                // مرحله ۲: نوشتن و ارسال پیام
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("به: ", style = MaterialTheme.typography.labelMedium)
                    Text(selectedContact!!.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedContact = null }) {
                        Text("تغییر")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = messageBody,
                    onValueChange = { messageBody = it },
                    label = { Text("متن پیام") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    maxLines = 8
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (messageBody.isNotBlank()) {
                            onSend(selectedContact!!.phoneNumber, selectedContact!!.name, messageBody)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = messageBody.isNotBlank()
                ) {
                    Text("ارسال")
                }
            }
        }
    }
}

// هلپر کوچیک چون Modifier.clickable مستقیم روی ListItem گاهی با ripple تداخل داره
private fun Modifier.clickableItem(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
