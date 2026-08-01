package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.MessageGroupMember
import com.petro.smsapp.data.MessageGroupSummary
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.util.autoDirection
import kotlinx.coroutines.launch

/**
 * صفحه‌ی «پیام جدید».
 *
 * ۱) لیستِ نتایجِ جستجو دیگه با باز شدنِ صفحه لود نمیشه؛ فقط با تایپ کردن کاربر
 *    یه کوئری زده میشه.
 *
 * ۲) لیستِ نتایج واقعاً چندانتخابیه: با تپ روی هر مخاطب، تیک می‌خوره و به‌صورتِ
 *    یه چیپِ کوچیک داخلِ همون کادرِ جستجو (کنارِ هم، قبل از متنِ تایپ‌شده) نشون داده
 *    میشه - بدونِ اینکه لیستِ جستجو خالی بشه یا مجبور باشی برای مخاطبِ بعدی از اول
 *    جستجو کنی. تپِ دوباره روی یه مخاطبِ انتخاب‌شده هم از انتخاب درش میاره. قبلاً این
 *    چیپ‌ها یه ردیفِ کاملاً جدا بالای کادرِ جستجو بودن؛ الان دیگه ردیفِ جدا نیست و
 *    خودِ چیپ‌ها (با گوشه‌های گردتر) داخلِ همون کادر می‌شینن.
 *
 * ۳) دکمه‌ی «انتخاب از مخاطبین گوشی» (آیکنِ شخص) دیگه Intent سیستمیِ ACTION_PICK
 *    رو باز نمی‌کنه - چون اون Intent محدودیتِ خودِ اندرویده و همیشه فقط یک مخاطب
 *    برمی‌گردونه. به‌جاش onOpenContactPicker صدا زده میشه که یه صفحه‌ی داخلیِ اپ
 *    (ContactPickerScreen) با چک‌باکس و بدونِ محدودیتِ تعداد باز می‌کنه.
 *
 * ۴) اگه ارسال گروهی (Settings -> «گروه‌های پیامکی») فعال باشه: با انتخابِ بیش از
 *    یک مخاطب، امکانِ «ذخیره به‌عنوان گروه» ظاهر میشه؛ و یه آیکنِ «گروه‌ها» بالای
 *    صفحه لیستِ گروه‌های ذخیره‌شده رو نشون میده - با زدن روی هرکدوم، همه‌ی اعضاش
 *    خودکار به انتخاب اضافه میشن.
 *
 * ۵) ارسال: اگه دقیقاً یک مخاطب انتخاب شده باشه، از مسیرِ تکیِ قبلی (onSend/
 *    onScheduleSend) میره - یعنی بعدش دقیقاً مثلِ قبل میره تو چتِ همون مخاطب. اگه
 *    بیشتر از یکی انتخاب شده باشه، پیام جدا-جدا به تک‌تکشون ارسال میشه
 *    (onSendToMultiple/onScheduleToMultiple) و صفحه برمی‌گرده به لیست مکالمات.
 *
 * ۶) ذخیره‌ی پیش‌نویس (فقط وقتی دقیقاً یک گیرنده انتخاب شده) دو مسیرِ خروج رو پوشش
 *    میده - عیناً هم‌خانواده‌ی همون منطقِ ThreadScreen:
 *    - خروجِ داخلِ اپ (کاربر با دکمه‌ی برگشتِ بالای صفحه یا Navigation از این Composable
 *      خارج میشه) -> DisposableEffect(Unit) با onDispose فوراً صدا زده میشه.
 *    - خروجِ کامل از اپ بدونِ خارج شدن از این صفحه (دکمه‌ی Home، سوییچ به اپِ دیگه،
 *      خاموش‌شدنِ صفحه و ...) -> قبلاً اصلاً پوشش داده نمی‌شد، چون این صفحه فقط اون
 *      DisposableEffect بالا رو داشت (که با ترکِ اپ از این طریق، Composable هنوز از
 *      ترکیب خارج نشده و onDispose صدا زده نمیشه). با گوش‌دادن به رویدادِ ON_STOP
 *      چرخه‌ی عمرِ صفحه (دقیقاً همون الگوی ThreadScreen)، همینجا هم پیش‌نویس ذخیره میشه.
 *
 * ۷) انتخابِ سیم‌کارت دیگه یه ردیفِ جدا (SimSelector) بالای کیبورد نیست - یه دکمه‌ی
 *    کوچیکِ داخلِ خودِ MessageInputBar شده (کنارِ دکمه‌ی ارسال).
 */
@Composable
fun NewMessageScreen(
    contacts: List<ContactInfo>,
    sims: List<SimInfo>,
    pickedContact: ContactInfo?,
    onPickedContactConsumed: () -> Unit,
    pickedContactsBatch: List<ContactInfo>?,
    onPickedContactsBatchConsumed: () -> Unit,
    onOpenContactPicker: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSend: (address: String, displayName: String, body: String, subscriptionId: Int?) -> Unit,
    onScheduleSend: (address: String, displayName: String, body: String, subscriptionId: Int?, scheduledAt: Long) -> Unit,
    onSendToMultiple: (recipients: List<Pair<String, String>>, body: String, subscriptionId: Int?) -> Unit,
    onScheduleToMultiple: (recipients: List<Pair<String, String>>, body: String, subscriptionId: Int?, scheduledAt: Long) -> Unit,
    onLeaveWithDraft: (address: String, displayName: String, body: String) -> Unit,
    groupMessagingEnabled: Boolean = false,
    groupSummaries: List<MessageGroupSummary> = emptyList(),
    onLoadGroupMembers: suspend (groupId: Long) -> List<MessageGroupMember> = { emptyList() },
    onSaveGroup: (name: String, members: List<Pair<String, String>>) -> Unit = { _, _ -> },
    onDeleteGroup: (groupId: Long) -> Unit = {},
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContacts by remember { mutableStateOf(listOf<ContactInfo>()) }
    var messageBody by remember { mutableStateOf("") }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }
    var scheduledAt by remember { mutableStateOf<Long?>(null) }
    var showGroupsSheet by remember { mutableStateOf(false) }
    var showSaveGroupDialog by remember { mutableStateOf(false) }
    var groupNameInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    /** فقط اضافه می‌کنه (اگه از قبل نبود) - برای مخاطبِ دستی، مخاطبِ گروه، و اعضای بارگذاری‌شده از گروه */
    fun addContactIfAbsent(contact: ContactInfo) {
        if (selectedContacts.none { it.phoneNumber == contact.phoneNumber }) {
            selectedContacts = selectedContacts + contact
        }
    }

    /** toggle واقعی - برای تپ روی یه ردیفِ لیستِ جستجو: اگه بود حذفش کن، نبود اضافه‌ش کن */
    fun toggleContact(contact: ContactInfo) {
        selectedContacts = if (selectedContacts.any { it.phoneNumber == contact.phoneNumber }) {
            selectedContacts.filter { it.phoneNumber != contact.phoneNumber }
        } else {
            selectedContacts + contact
        }
    }

    fun removeContact(contact: ContactInfo) {
        selectedContacts = selectedContacts.filter { it.phoneNumber != contact.phoneNumber }
    }

    // ذخیره‌ی پیش‌نویس فقط وقتی معنی داره که دقیقاً یک گیرنده انتخاب شده باشه -
    // برای پیام گروهی (چند گیرنده) اصلاً پیش‌نویسی ذخیره نمیشه
    val latestSelected = rememberUpdatedState(selectedContacts)
    val latestBody = rememberUpdatedState(messageBody)
    val latestOnLeave = rememberUpdatedState(onLeaveWithDraft)

    fun saveDraftIfNeeded() {
        val single = latestSelected.value.singleOrNull()
        if (single != null) {
            latestOnLeave.value(single.phoneNumber, single.name, latestBody.value)
        }
    }

    // حالتِ اول: کاربر از داخلِ اپ به صفحه‌ی دیگه‌ای میره (این Composable کامل از
    // ترکیب خارج میشه) -> onDispose فوراً صدا زده میشه.
    DisposableEffect(Unit) {
        onDispose { saveDraftIfNeeded() }
    }
    // حالتِ دوم (که قبلاً اصلاً پوشش داده نمی‌شد): کاربر بدونِ خارج شدن از این صفحه،
    // کلِ اپ رو ترک می‌کنه (دکمه‌ی Home، سوییچ به اپِ دیگه، خاموش‌شدنِ صفحه و ...).
    // توی این حالت Composable هنوز از ترکیب خارج نشده، پس onDispose بالا صدا زده
    // نمیشه و متنِ تایپ‌شده بدونِ ذخیره از دست می‌رفت. با گوش‌دادن به ON_STOP چرخه‌ی
    // عمرِ صفحه، همینجا هم پیش‌نویس ذخیره میشه.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveDraftIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pickedContact) {
        if (pickedContact != null) {
            addContactIfAbsent(pickedContact)
            onPickedContactConsumed()
        }
    }

    LaunchedEffect(pickedContactsBatch) {
        if (pickedContactsBatch != null) {
            pickedContactsBatch.forEach { addContactIfAbsent(it) }
            onPickedContactsBatchConsumed()
        }
    }

    LaunchedEffect(sims) {
        if (selectedSimId == null && sims.isNotEmpty()) {
            selectedSimId = sims.first().subscriptionId
        }
    }

    fun performSend() {
        if (messageBody.isBlank() || selectedContacts.isEmpty()) return
        val at = scheduledAt
        if (selectedContacts.size == 1) {
            val c = selectedContacts.first()
            if (at != null) {
                onScheduleSend(c.phoneNumber, c.name, messageBody, selectedSimId, at)
            } else {
                onSend(c.phoneNumber, c.name, messageBody, selectedSimId)
            }
        } else {
            val recipients = selectedContacts.map { it.phoneNumber to it.name }
            if (at != null) {
                onScheduleToMultiple(recipients, messageBody, selectedSimId, at)
            } else {
                onSendToMultiple(recipients, messageBody, selectedSimId)
            }
        }
        messageBody = ""
        scheduledAt = null
    }

    if (showGroupsSheet) {
        GroupsPickerSheet(
            groups = groupSummaries,
            onPick = { group ->
                showGroupsSheet = false
                scope.launch {
                    val members = onLoadGroupMembers(group.id)
                    members.forEach { m ->
                        addContactIfAbsent(ContactInfo(contactId = -1, name = m.displayName, phoneNumber = m.address))
                    }
                }
            },
            onDelete = { group -> onDeleteGroup(group.id) },
            onDismiss = { showGroupsSheet = false }
        )
    }

    if (showSaveGroupDialog) {
        AlertDialog(
            onDismissRequest = { showSaveGroupDialog = false },
            title = { Text("ذخیره به‌عنوان گروه") },
            text = {
                OutlinedTextField(
                    value = groupNameInput,
                    onValueChange = { groupNameInput = it },
                    label = { Text("اسم گروه") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupNameInput.isNotBlank()) {
                            onSaveGroup(groupNameInput.trim(), selectedContacts.map { it.phoneNumber to it.name })
                            groupNameInput = ""
                            showSaveGroupDialog = false
                        }
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveGroupDialog = false }) { Text("انصراف") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("پیام جدید") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    if (groupMessagingEnabled) {
                        IconButton(onClick = { showGroupsSheet = true }) {
                            Icon(Icons.Filled.Groups, contentDescription = "گروه‌های ذخیره‌شده")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedContacts.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    MessageInputBar(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        onSendClick = { performSend() },
                        scheduledAt = scheduledAt,
                        onScheduledAtChange = { scheduledAt = it },
                        placeholder = "متن پیام",
                        sims = sims,
                        selectedSubscriptionId = selectedSimId,
                        onSimSelect = { selectedSimId = it }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // چیپ‌های مخاطبینِ انتخاب‌شده دیگه ردیفِ جدا نیستن - داخلِ همین کادرِ
            // جستجو، کنارِ متنِ تایپ‌شده نشون داده میشن (ContactChipsSearchField پایین‌تر).
            ContactChipsSearchField(
                selectedContacts = selectedContacts,
                searchQuery = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchChange(it)
                },
                onRemoveContact = { contact -> removeContact(contact) },
                onOpenContactPicker = onOpenContactPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            if (groupMessagingEnabled && selectedContacts.size > 1) {
                TextButton(
                    onClick = { showSaveGroupDialog = true },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text("ذخیره به‌عنوان گروه")
                }
            }

            if (selectedContacts.isNotEmpty()) {
                Divider()
            }

            if (searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }) {
                val manualEntry = ContactInfo(contactId = -1, name = searchQuery, phoneNumber = searchQuery)
                val alreadyAdded = selectedContacts.any { it.phoneNumber == manualEntry.phoneNumber }
                TextButton(
                    onClick = { toggleContact(manualEntry) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(if (alreadyAdded) "✓ اضافه شد: $searchQuery" else "ارسال به شماره: $searchQuery")
                }
            }

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "برای شروع، نام یا شماره‌ای رو جستجو کن" else "چیزی پیدا نشد",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(contacts, key = { it.contactId to it.phoneNumber }) { contact ->
                        val isSelected = selectedContacts.any { it.phoneNumber == contact.phoneNumber }
                        ContactRow(contact, isSelected = isSelected, onClick = { toggleContact(contact) })
                        Divider()
                    }
                }
            }
        }
    }
}

/**
 * کادرِ جستجو + چیپ‌های مخاطبینِ انتخاب‌شده، همه داخلِ یه کادرِ واحد (شبیهِ فیلدهای
 * "To" تو اپ‌های ایمیل). قبلاً چیپ‌ها یه ردیفِ کاملاً جدا (SelectedContactsRow) بالای
 * این کادر بودن؛ الان همه‌چی تو یه جعبه‌ست: آیکنِ انتخاب از مخاطبین، بعدش چیپ‌های
 * انتخاب‌شده (هرکدوم با ضربدرِ حذف)، بعدش خودِ فیلدِ متنیِ جستجو - همه کنارِ هم و اگه
 * جا نشن به خط بعد می‌رن (FlowRow).
 *
 * چیپ‌ها و متنِ جستجو با autoDirection/ContentOrLtr نمایش داده میشن - یعنی اسم‌های
 * فارسی راست‌به‌چپ می‌مونن و شماره‌ها (چه تو چیپ چه تو متنِ تایپ‌شده) همیشه چپ‌به‌راستن،
 * دقیقاً هم‌قاعده‌ی بقیه‌ی کادرهای متنی/جستجوی برنامه.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ContactChipsSearchField(
    selectedContacts: List<ContactInfo>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onRemoveContact: (ContactInfo) -> Unit,
    onOpenContactPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Row(
        modifier = modifier
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onOpenContactPicker, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Person, contentDescription = "انتخاب از مخاطبین گوشی")
        }

        Spacer(modifier = Modifier.width(4.dp))

        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            selectedContacts.forEach { contact ->
                ContactChip(contact = contact, onRemove = { onRemoveContact(contact) })
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .widthIn(min = 110.dp)
                    .padding(vertical = 4.dp)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = LocalContentColor.current,
                        textDirection = TextDirection.ContentOrLtr
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (selectedContacts.isEmpty()) "جستجوی نام یا شماره" else "افزودنِ مخاطبِ دیگر",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}

/** چیپِ یه مخاطبِ انتخاب‌شده - گوشه‌های گردتر از یه دکمه‌ی معمولی، با دکمه‌ی ضربدرِ حذف کنارش */
@Composable
private fun ContactChip(contact: ContactInfo, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "حذف ${contact.name}",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = contact.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium.autoDirection()
            )
        }
    }
}

/** لیستِ گروه‌های ذخیره‌شده - با تپ روی هرکدوم، اعضاش به انتخاب اضافه میشن؛ دکمه‌ی حذف هم داره */
@Composable
private fun GroupsPickerSheet(
    groups: List<MessageGroupSummary>,
    onPick: (MessageGroupSummary) -> Unit,
    onDelete: (MessageGroupSummary) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "گروه‌های ذخیره‌شده",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (groups.isEmpty()) {
                Text(
                    "هنوز هیچ گروهی ذخیره نشده",
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            } else {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(group) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${group.memberCount} عضو",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { onDelete(group) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف گروه")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactInfo, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(name = contact.name)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge.autoDirection())
            // شماره‌ها همیشه چپ‌به‌راست نشون داده بشن - وگرنه شماره‌هایی که با +98
            // شروع می‌شن توی چیدمانِ راست‌به‌چپِ برنامه برعکس (چپکی) نشون داده می‌شدن
            Text(
                text = contact.phoneNumber,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr)
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "انتخاب‌شده",
                tint = MaterialTheme.colorScheme.primary
            )
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