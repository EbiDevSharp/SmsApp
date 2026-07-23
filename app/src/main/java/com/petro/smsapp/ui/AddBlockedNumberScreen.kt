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

/**
 * صفحه‌ی «افزودن شماره‌ی بلاک» - دقیقاً همون الگوی مرحله‌ی اول «پیام جدید»: جستجو
 * تو مخاطبین گوشی + دکمه‌ی انتخاب از لیست مخاطبین + امکان وارد کردن دستی یه شماره
 * که اصلاً تو مخاطبین نیست. تفاوتش با «پیام جدید» اینه که به‌جای رفتن به صفحه‌ی چت،
 * انتخاب (چه از مخاطبین چه دستی) بلافاصله همون شماره رو بلاک می‌کنه و برمی‌گرده عقب.
 */
@Composable
fun AddBlockedNumberScreen(
    contacts: List<ContactInfo>,
    pickedContact: ContactInfo?,
    onPickedContactConsumed: () -> Unit,
    onPickFromContactsClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onBlockNumber: (address: String, displayName: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(pickedContact) {
        if (pickedContact != null) {
            onBlockNumber(pickedContact.phoneNumber, pickedContact.name)
            onPickedContactConsumed()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افزودن شماره‌ی بلاک") },
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
                    IconButton(onClick = onPickFromContactsClick) {
                        Icon(Icons.Filled.Person, contentDescription = "انتخاب از مخاطبین گوشی")
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // اگه شماره‌ای که تایپ شده اصلاً تو مخاطبین نباشه، همینجا دستی قابل بلاک‌کردنه
            if (searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }) {
                TextButton(onClick = {
                    onBlockNumber(searchQuery, searchQuery)
                    onBack()
                }) {
                    Text("بلاک کردن شماره: $searchQuery")
                }
            }

            if (contacts.isEmpty() && searchQuery.isBlank()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("مخاطبی پیدا نشد", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(contacts, key = { it.contactId to it.phoneNumber }) { contact ->
                        AddBlockContactRow(
                            contact = contact,
                            onClick = { onBlockNumber(contact.phoneNumber, contact.name); onBack() }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AddBlockContactRow(contact: ContactInfo, onClick: () -> Unit) {
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
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
        }
    }
}
