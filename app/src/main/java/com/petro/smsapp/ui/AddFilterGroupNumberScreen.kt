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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.util.autoDirection

@Composable
fun AddFilterGroupNumberScreen(
    groupName: String,
    contacts: List<ContactInfo>,
    pickedContactsBatch: List<ContactInfo>?,
    onPickedContactsBatchConsumed: () -> Unit,
    onOpenContactPicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onAddNumber: (address: String, displayName: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    // برگشت از صفحه‌ی «انتخابِ چندتاییِ مخاطبین» (همون کامپوننتی که تویِ «پیامِ جدید»
    // استفاده میشه) - همه‌ی مخاطب‌های تیک‌خورده یه‌جا به گروه اضافه میشن
    LaunchedEffect(pickedContactsBatch) {
        val batch = pickedContactsBatch
        if (batch != null) {
            batch.forEach { onAddNumber(it.phoneNumber, it.name) }
            onPickedContactsBatchConsumed()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افزودنِ شماره به «$groupName»", style = LocalTextStyle.current.autoDirection()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                }
            )
        }
    ) { padding ->
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
                    IconButton(onClick = onOpenContactPicker) {
                        Icon(Icons.Filled.Person, contentDescription = "انتخابِ چندتاییِ مخاطبین")
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }) {
                TextButton(onClick = {
                    onAddNumber(searchQuery, searchQuery)
                    onBack()
                }) {
                    Text("افزودنِ شماره: $searchQuery")
                }
            }

            if (contacts.isEmpty() && searchQuery.isBlank()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("مخاطبی پیدا نشد", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(contacts, key = { it.contactId to it.phoneNumber }) { contact ->
                        AddFilterGroupContactRow(
                            contact = contact,
                            onClick = { onAddNumber(contact.phoneNumber, contact.name); onBack() }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AddFilterGroupContactRow(contact: ContactInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initial = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge.autoDirection())
            Text(
                text = contact.phoneNumber,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
            )
        }
    }
}
