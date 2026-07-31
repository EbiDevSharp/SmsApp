package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

/**
 * صفحه‌ی «انتخاب چندتاییِ مخاطبین» - جایگزینِ Intent سیستمیِ ACTION_PICK فقط برای
 * صفحه‌ی «پیام جدید»، چون اون Intent سیستمی محدودیتِ خودِ اندرویده و همیشه فقط
 * اجازه‌ی انتخابِ یک مخاطب رو میده. اینجا کاربر می‌تونه با تیک‌زدن، هرچندتا مخاطب
 * که بخواد انتخاب کنه و با دکمه‌ی «تأیید» یه‌جا همه‌شون رو برگردونه.
 *
 * کل مخاطبین گوشی همینجا (توسطِ صداکننده) یه‌بار لود میشه - چون این صفحه با یه
 * اکشنِ صریح از طرفِ کاربر باز میشه (نه خودکار موقع باز شدنِ «پیام جدید»)، لود کردنِ
 * کاملِ لیست منطقیه؛ فیلترِ جستجو همینجا و به‌صورت محلی (بدون کوئریِ اضافه به
 * ContentResolver) روی همون لیستِ یه‌بار-خوانده‌شده انجام میشه.
 */
@Composable
fun ContactPickerScreen(
    contacts: List<ContactInfo>,
    onConfirm: (List<ContactInfo>) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNumbers by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectedNumbers.isEmpty()) "انتخاب مخاطبین" else "${selectedNumbers.size} مخاطب انتخاب شده")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن")
                    }
                },
                actions = {
                    TextButton(
                        enabled = selectedNumbers.isNotEmpty(),
                        onClick = {
                            onConfirm(contacts.filter { selectedNumbers.contains(it.phoneNumber) })
                        }
                    ) {
                        Text("تأیید")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("جستجوی نام یا شماره") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (contacts.isEmpty()) "مخاطبی پیدا نشد" else "چیزی پیدا نشد",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.contactId to it.phoneNumber }) { contact ->
                        val isSelected = selectedNumbers.contains(contact.phoneNumber)
                        PickerContactRow(
                            contact = contact,
                            isSelected = isSelected,
                            onClick = {
                                selectedNumbers = if (isSelected) {
                                    selectedNumbers - contact.phoneNumber
                                } else {
                                    selectedNumbers + contact.phoneNumber
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerContactRow(contact: ContactInfo, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onClick() })
        Spacer(modifier = Modifier.width(4.dp))
        PickerAvatar(name = contact.name)
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

@Composable
private fun PickerAvatar(name: String) {
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
