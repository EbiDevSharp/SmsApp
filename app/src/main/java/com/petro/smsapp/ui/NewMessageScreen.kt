package com.petro.smsapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
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
import com.petro.smsapp.data.AppSettings
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
 *    جستجو کنی. تپِ دوباره روی یه مخاطبِ انتخاب‌شده هم از انتخاب درش میاره.
 *    بعد از انتخاب، متنِ جستجو پاک می‌شه تا تکراری/گیج‌کننده نباشه.
 *
 * ۳) دکمه‌ی «انتخاب از مخاطبین گوشی» یه صفحه‌ی داخلیِ اپ (ContactPickerScreen) با
 *    چک‌باکس و بدونِ محدودیتِ تعداد باز می‌کنه.
 *
 * ۴) گروه‌های پیامکیِ ذخیره‌شده (Settings -> «گروه‌های پیامکی»):
 *    - با انتخابِ بیش از یک مخاطب، «ذخیره به‌عنوان گروه» ظاهر میشه. دیالوگِ ساختِ
 *      گروه حالا خودِ لیستِ گروه‌های موجود رو هم (برای جلوگیری از سردرگمی/نامِ تکراری)
 *      نشون میده و تا وقتی اسمِ واردشده تکراری یا خالی باشه، دکمه‌ی «ذخیره» غیرفعاله.
 *    - آیکنِ «گروه‌ها» بالای صفحه لیستِ گروه‌های ذخیره‌شده رو نشون میده - تپ روی خودِ
 *      ردیف = بارگذاریِ اعضا توی انتخابِ فعلی؛ دکمه‌ی مدادِ کنارش = باز شدنِ صفحه‌ی
 *      «ویرایشِ گروه» (تغییرِ اسم + حذفِ تک‌تکِ اعضا + حذفِ کاملِ گروه).
 *
 * ۵) ارسال: تکی یا گروهی، دقیقاً مثل قبل.
 *
 * ۶) ذخیره‌ی پیش‌نویس (فقط با یک گیرنده) - دو مسیرِ خروج (ترکِ داخلِ اپ + ترکِ کاملِ اپ) پوشش داده میشه.
 *
 * ۷) انتخابِ سیم‌کارت داخلِ خودِ MessageInputBar ئه.
 *
 * ۸) وقتی کاربر دستی یه شماره تایپ می‌کنه، داخلِ همون کادرِ جستجو یه آیکنِ تیک (✓)
 *    ظاهر می‌شه؛ با زدنِ تیک، شماره به لیستِ انتخاب‌شده‌ها (چیپ) اضافه و متنِ تایپ‌شده
 *    از باکس پاک می‌شه. دیگه دکمه‌ی جداگانه‌ی «ارسال به شماره: ...» زیر کادر نیست.
 *
 * ۹) نوارِ ورودیِ پیام (MessageInputBar) از لحظه‌ی باز شدنِ صفحه «پیام جدید» نمایش
 *    داده می‌شه تا ظاهرِ صفحه مثل یه پیامِ جدید واقعی باشه (ارسال تا وقتی گیرنده‌ای
 *    انتخاب نشده غیرفعال می‌مونه).
 */
@OptIn(ExperimentalFoundationApi::class)
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
    onRenameGroup: (groupId: Long, newName: String) -> Unit = { _, _ -> },
    onUpdateGroupMembers: (groupId: Long, members: List<Pair<String, String>>) -> Unit = { _, _ -> },
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
    var editingGroup by remember { mutableStateOf<MessageGroupSummary?>(null) }
    val scope = rememberCoroutineScope()
    val settings by AppSettings.state.collectAsState()

    /** فقط اضافه می‌کنه (اگه از قبل نبود) - برای مخاطبِ دستی، مخاطبِ گروه، و اعضای بارگذاری‌شده از گروه */
    fun addContactIfAbsent(contact: ContactInfo) {
        if (selectedContacts.none { it.phoneNumber == contact.phoneNumber }) {
            selectedContacts = selectedContacts + contact
        }
    }

    /** بعد از افزودن/تایید یه مخاطب یا شماره‌ی دستی، باکسِ جستجو (و نتیجه‌ی کوئریِ ViewModel) پاک بشه */
    fun clearSearch() {
        searchQuery = ""
        onSearchChange("")
    }

    /** toggle واقعی - برای تپ روی یه ردیفِ لیستِ جستجو: اگه بود حذفش کن، نبود اضافه‌ش کن.
     * بعد از انتخاب (اضافه شدن)، متنِ جستجو پاک می‌شه تا تکراری داخل باکس نمونه. */
    fun toggleContact(contact: ContactInfo) {
        val already = selectedContacts.any { it.phoneNumber == contact.phoneNumber }
        selectedContacts = if (already) {
            selectedContacts.filter { it.phoneNumber != contact.phoneNumber }
        } else {
            selectedContacts + contact
        }
        // فقط وقتی اضافه می‌کنیم پاک کن (حذف دوباره نیازی به پاک کردن نداره)
        if (!already) {
            clearSearch()
        }
    }

    fun removeContact(contact: ContactInfo) {
        selectedContacts = selectedContacts.filter { it.phoneNumber != contact.phoneNumber }
    }

    /** افزودن شماره‌ی دستی از داخل کادر جستجو (با تیک) و پاک کردن متن */
    fun confirmManualNumber() {
        val q = searchQuery.trim()
        if (q.isBlank() || !q.any { it.isDigit() }) return
        val manualEntry = ContactInfo(contactId = -1, name = q, phoneNumber = q)
        addContactIfAbsent(manualEntry)
        clearSearch()
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
            onEdit = { group ->
                showGroupsSheet = false
                editingGroup = group
            },
            onDismiss = { showGroupsSheet = false }
        )
    }

    val currentEditingGroup = editingGroup
    if (currentEditingGroup != null) {
        GroupEditSheet(
            group = currentEditingGroup,
            existingGroupNames = groupSummaries.filter { it.id != currentEditingGroup.id }.map { it.name },
            onLoadMembers = { onLoadGroupMembers(currentEditingGroup.id) },
            onRename = { newName -> onRenameGroup(currentEditingGroup.id, newName) },
            onUpdateMembers = { members -> onUpdateGroupMembers(currentEditingGroup.id, members) },
            onDelete = {
                onDeleteGroup(currentEditingGroup.id)
                editingGroup = null
            },
            onDismiss = { editingGroup = null }
        )
    }

    if (showSaveGroupDialog) {
        SaveGroupDialog(
            existingGroupNames = groupSummaries.map { it.name },
            nameInput = groupNameInput,
            onNameChange = { groupNameInput = it },
            onConfirm = {
                onSaveGroup(groupNameInput.trim(), selectedContacts.map { it.phoneNumber to it.name })
                groupNameInput = ""
                showSaveGroupDialog = false
            },
            onDismiss = {
                groupNameInput = ""
                showSaveGroupDialog = false
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
            // همیشه نوار ورودی پیام نشون داده می‌شه تا صفحه از اول شبیه پیام جدید باشه.
            // performSend خودش اگه گیرنده‌ای نباشه هیچی نمی‌فرسته.
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
                    onSimSelect = { selectedSimId = it },
                    sendDelaySeconds = settings.sendDelaySeconds
                )
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
            // اگر متن شبیه شماره باشه، آیکن تیک داخل کادر ظاهر می‌شه برای تأیید.
            ContactChipsSearchField(
                selectedContacts = selectedContacts,
                searchQuery = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    onSearchChange(it)
                },
                onRemoveContact = { contact -> removeContact(contact) },
                onOpenContactPicker = onOpenContactPicker,
                onConfirmManualNumber = { confirmManualNumber() },
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

            // دکمه‌ی جداگانه‌ی «ارسال به شماره» حذف شد؛
            // حالا تیک تأیید داخل خودِ کادر جستجو (ContactChipsSearchField) هست.

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
 * دیالوگِ ساختِ گروهِ جدید. قبلاً فقط یه فیلدِ متنیِ خالی بود و کاربر هیچ ایده‌ای
 * نداشت چه گروه‌هایی از قبل وجود دارن - الان لیستِ اسمِ گروه‌های موجود هم زیرِ فیلد
 * نشون داده میشه (اگه گروهی وجود داشته باشه)، و اگه اسمِ واردشده (بعد از trim، بدونِ
 * حساسیت به بزرگ/کوچیکیِ حروف) با یکی از همون‌ها یکی باشه، پیغامِ خطا نشون داده
 * میشه و دکمه‌ی «ذخیره» غیرفعال می‌مونه.
 */
@Composable
private fun SaveGroupDialog(
    existingGroupNames: List<String>,
    nameInput: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val trimmed = nameInput.trim()
    val isDuplicate = trimmed.isNotEmpty() && existingGroupNames.any { it.equals(trimmed, ignoreCase = true) }
    val canConfirm = trimmed.isNotEmpty() && !isDuplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ذخیره به‌عنوان گروه") },
        text = {
            Column {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = onNameChange,
                    label = { Text("اسم گروه") },
                    singleLine = true,
                    isError = isDuplicate,
                    textStyle = LocalTextStyle.current.autoDirection(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isDuplicate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "یه گروهِ دیگه از قبل همین اسم رو داره",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (existingGroupNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "گروه‌های موجود:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.heightIn(max = 140.dp)) {
                        existingGroupNames.forEach { name ->
                            Text(
                                text = "• $name",
                                style = MaterialTheme.typography.bodySmall.autoDirection(),
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) { Text("ذخیره") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/**
 * کادرِ جستجو + چیپ‌های مخاطبینِ انتخاب‌شده، همه داخلِ یه کادرِ واحد (شبیهِ فیلدهای
 * "To" تو اپ‌های ایمیل).
 *
 * اگر متن تایپ‌شده شبیه شماره باشه (حداقل یک رقم داشته باشه)، یه آیکن تیک (✓)
 * سمتِ راستِ کادر ظاهر می‌شه؛ با زدنِ تیک، شماره تأیید و به چیپ‌ها اضافه می‌شه
 * و متن از باکس پاک می‌شه.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ContactChipsSearchField(
    selectedContacts: List<ContactInfo>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onRemoveContact: (ContactInfo) -> Unit,
    onOpenContactPicker: () -> Unit,
    onConfirmManualNumber: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val showConfirmTick = searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }

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

        // تیک تأیید شماره دستی — داخل همون کادر، کنار متن
        if (showConfirmTick) {
            IconButton(
                onClick = onConfirmManualNumber,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "تأیید شماره و افزودن",
                    tint = MaterialTheme.colorScheme.primary
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

/**
 * لیستِ گروه‌های ذخیره‌شده. تپ روی خودِ ردیف = اعضاش به انتخابِ فعلی اضافه میشن.
 * دو دکمه‌ی جدا کنارِ هر ردیف: مداد (ویرایش) و سطل‌زباله (حذفِ مستقیم با تائید).
 */
@Composable
private fun GroupsPickerSheet(
    groups: List<MessageGroupSummary>,
    onPick: (MessageGroupSummary) -> Unit,
    onEdit: (MessageGroupSummary) -> Unit,
    onDelete: (MessageGroupSummary) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var pendingDelete by remember { mutableStateOf<MessageGroupSummary?>(null) }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف گروه") },
            text = { Text("گروهِ «${toDelete.name}» حذف بشه؟ خودِ مخاطبین حذف نمیشن، فقط این گروه از بین میره.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(toDelete)
                    pendingDelete = null
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("انصراف") }
            }
        )
    }

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
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Groups, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge.autoDirection())
                            Text(
                                "${group.memberCount} عضو",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { onEdit(group) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "ویرایش گروه")
                        }
                        IconButton(onClick = { pendingDelete = group }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف گروه")
                        }
                    }
                }
            }
        }
    }
}

/**
 * صفحه‌ی «ویرایشِ گروه» - تغییرِ اسم (با چکِ زنده‌ی تکراری‌نبودن) + حذفِ تک‌تکِ
 * اعضا (هر حذف بلافاصله ذخیره میشه، بدونِ نیاز به یه دکمه‌ی «ذخیره»ی جدا برای
 * اعضا) + حذفِ کاملِ گروه (با تائید).
 */
@Composable
private fun GroupEditSheet(
    group: MessageGroupSummary,
    existingGroupNames: List<String>,
    onLoadMembers: suspend () -> List<MessageGroupMember>,
    onRename: (newName: String) -> Unit,
    onUpdateMembers: (List<Pair<String, String>>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var nameInput by remember { mutableStateOf(group.name) }
    var members by remember { mutableStateOf<List<MessageGroupMember>?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(group.id) {
        members = onLoadMembers()
    }

    val trimmedName = nameInput.trim()
    val isDuplicate = trimmedName.isNotEmpty() && existingGroupNames.any { it.equals(trimmedName, ignoreCase = true) }
    val nameChanged = trimmedName.isNotEmpty() && trimmedName != group.name
    val canSaveName = nameChanged && !isDuplicate

    fun removeMember(member: MessageGroupMember) {
        val updated = (members ?: emptyList()).filter { it.address != member.address }
        members = updated
        onUpdateMembers(updated.map { it.address to it.displayName })
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف گروه") },
            text = { Text("گروهِ «${group.name}» حذف بشه؟ خودِ مخاطبین حذف نمیشن، فقط این گروه از بین میره.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("انصراف") }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("ویرایش گروه", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("اسم گروه") },
                    singleLine = true,
                    isError = isDuplicate,
                    textStyle = LocalTextStyle.current.autoDirection(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onRename(trimmedName) },
                    enabled = canSaveName
                ) { Text("ذخیره") }
            }
            if (isDuplicate) {
                Text(
                    "یه گروهِ دیگه از قبل همین اسم رو داره",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "اعضای گروه (${members?.size ?: group.memberCount})",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))

            when (val currentMembers = members) {
                null -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                else -> {
                    if (currentMembers.isEmpty()) {
                        Text("این گروه دیگه هیچ عضوی نداره", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    } else {
                        val membersScrollState = rememberScrollState()
                        Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(membersScrollState)) {
                            currentMembers.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(member.displayName, style = MaterialTheme.typography.bodyMedium.autoDirection())
                                        Text(
                                            member.address,
                                            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                                            color = Color.Gray
                                        )
                                    }
                                    IconButton(onClick = { removeMember(member) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "حذفِ ${member.displayName} از گروه")
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حذفِ کاملِ گروه")
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