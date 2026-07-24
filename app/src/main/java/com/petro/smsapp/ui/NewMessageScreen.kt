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
    // موقع خروج از این صفحه (هر دلیلی: برگشت فیزیکی، منو، یا بستن اپ) با آدرس/نام/متنِ فعلیِ
    // کادر صدا زده میشه - دقیقاً هم‌خانواده‌ی onLeaveWithDraft توی ThreadScreen. قبلاً این
    // مکانیزم اصلاً اینجا وجود نداشت، پس برای مخاطبی که تا حالا باهاش چت نشده بود، خروج از
    // این صفحه (بدون ارسال) هیچ پیش‌نویسی نمی‌ساخت.
    onLeaveWithDraft: (address: String, displayName: String, body: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var messageBody by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }

    // موقع خروج از صفحه (به هر دلیلی) با آخرین مخاطبِ انتخاب‌شده/متنِ کادر ذخیره میشه -
    // rememberUpdatedState لازمه چون onDispose خودِ لامبدای اولیه رو با مقدارهای همون لحظه‌ی
    // composition اول capture می‌کنه، نه مقدار لحظه‌ی خروج.
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // دکمه ارسال اول میاد تا توی چیدمان راست‌به‌چپ سمت راست کادر بشینه
                        Button(
                            onClick = {
                                if (messageBody.isNotBlank()) {
                                    onSend(
                                        selectedContact!!.phoneNumber,
                                        selectedContact!!.name,
                                        messageBody,
                                        selectedSimId
                                    )
                                    // خالی می‌کنیم تا موقع خروج از صفحه (که بعد از ارسال قطعاً
                                    // اتفاق می‌افته) onDispose متنِ همین‌الان‌فرستاده‌شده رو
                                    // دوباره به‌عنوان پیش‌نویس ذخیره نکنه
                                    messageBody = ""
                                }
                            },
                            enabled = messageBody.isNotBlank()
                        ) {
                            Text("ارسال")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = messageBody,
                            onValueChange = { messageBody = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("متن پیام") },
                            maxLines = 5,
                            // متن انگلیسی/اعداد از چپ نوشته بشن، حتی داخل کانتینر راست‌به‌چپ
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (selectedContact == null) {
            // مرحله ۱: انتخاب گیرنده
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
            // مرحله ۲: هدر گیرنده همیشه بالا ثابت می‌مونه، بدنه‌ی زیرش (برای پیام‌های آینده) قابل اسکرول می‌مونه
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

                // فضای خالی زیر هدر - جای پیام‌هایی که بعداً ارسال میشن (بعد از اولین پیام میره صفحه چت)
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