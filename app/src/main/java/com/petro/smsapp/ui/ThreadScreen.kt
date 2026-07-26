package com.petro.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petro.smsapp.data.ScheduledMessage
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.data.SmsMessage
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.PhoneNumberUtils
import kotlinx.coroutines.launch

/**
 * یه ردیف واحد برای LazyColumn صفحه‌ی چت - یا یه پیام واقعی، یا یه پیام زمان‌بندی‌شده‌ی
 * هنوز-در-انتظار (که هنوز SmsMessage واقعی نیست چون ارسال نشده). برای اینکه هردو تو یه
 * لیستِ زمانی درست کنار هم مرتب بشن (پیام‌های زمان‌بندی‌شده معمولاً چون زمانشون تو
 * آینده‌ست، ته لیست/پایین صفحه می‌شینن).
 */
private sealed class ThreadListItem {
    abstract val sortDate: Long
    data class Real(val message: SmsMessage) : ThreadListItem() {
        override val sortDate get() = message.date
    }
    data class Pending(val scheduled: ScheduledMessage) : ThreadListItem() {
        override val sortDate get() = scheduled.scheduledAt
    }
}

/**
 * صفحه‌ی چت یک مخاطب. دو تا قابلیت مهم علاوه بر ارسال/دریافت معمولی:
 *
 * ۱) حالت «انتخاب چندتایی» - دقیقاً همون الگوی لیست مکالمات: لانگ‌کلیک روی یه پیام وارد
 *    حالت انتخاب میشه، تک‌کلیک بعدی روی هر پیام دیگه فقط انتخاب/عدم‌انتخابش می‌کنه (دیگه
 *    منوی اکشن پیام باز نمیشه)، نوار بالا با تعداد انتخاب‌شده + انتخاب‌همه + حذف عوض میشه.
 *
 * ۲) زوم فونت با دو انگشت (Pinch) - این یه Scale واقعی روی کل صفحه نیست؛ فقط اندازه‌ی فونت
 *    متن پیام‌ها (که حباب‌ها هم چون دور همون متن رو می‌گیرن، خودشون بزرگ/کوچیک میشن) بین
 *    16sp تا 28sp تغییر می‌کنه. کاربر حس می‌کنه صفحه رو زوم کرده، ولی فقط متنه که عوض میشه.
 *
 * ۳) مکالمه‌های بدون شماره‌ی واقعی (Sender ID حروفی مثل اسم اپراتورها) - این‌جور مکالمه‌ها
 *    قابل بازکردن و خوندن هستن (پیام‌های دریافتی‌شون سرجاشونه) ولی چون address واقعاً یه
 *    شماره نیست، کادرِ ارسال/دکمه‌ی ارسال اصلاً نشون داده نمیشه؛ به‌جاش یه پیام توضیحی میاد.
 */
@Composable
fun ThreadScreen(
    displayName: String,
    // آدرس خام مکالمه (ستونِ address توی جدول اس‌ام‌اس) - برای تشخیص اینکه واقعاً شماره‌ست
    // یا صرفاً یه Sender ID حروفی (مثلاً اسم اپراتور) که نمیشه بهش پیام فرستاد
    address: String,
    messages: List<SmsMessage>,
    scheduledMessages: List<ScheduledMessage>,
    sims: List<SimInfo>,
    favoriteIds: Set<Long>,
    // id پیام‌هایی که داخل همین چت پین شدن - برای نشون‌دادن حالت پین‌شده توی منوی کلیک پیام
    pinnedMessageIds: Set<Long> = emptySet(),
    // متن پیش‌نویسِ ذخیره‌شده‌ی همین مکالمه (اگه بود) - برای پرکردن خودکار کادر متن
    initialDraft: String = "",
    onSend: (body: String, subscriptionId: Int?) -> Unit,
    onDeleteMessage: (messageId: Long) -> Unit,
    onDeleteMessages: (Set<Long>) -> Unit,
    onOpenNote: (text: String) -> Unit,
    onToggleFavorite: (message: SmsMessage) -> Unit,
    onTogglePinMessage: (message: SmsMessage) -> Unit = {},
    onResend: (message: SmsMessage) -> Unit,
    onUpdateScheduledTime: (id: Long, newTime: Long) -> Unit,
    onSendScheduledNow: (id: Long) -> Unit,
    onCancelScheduledMessage: (id: Long) -> Unit,
    // موقع خروج از صفحه (هر دلیلی) با متنِ فعلیِ کادر صدا زده میشه - پیاده‌سازی این تابع
    // تصمیم می‌گیره ذخیره کنه (اگه متنی بود) یا پیش‌نویس قبلی رو پاک کنه (اگه خالی بود)
    onLeaveWithDraft: (text: String) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    // initialDraft از دیتابیس async لود میشه، پس اولین باری که این Composable با این پارامتر
    // اجرا میشه معمولاً هنوز "" (مقدار اولیه‌ی StateFlow) هست و متن واقعیِ درفت چند میلی‌ثانیه
    // بعد می‌رسه. قبلاً draftApplied همون بار اول (با مقدار خالی) true می‌شد، پس وقتی متن
    // واقعی می‌رسید دیگه اعمال نمی‌شد - نتیجه‌ش این بود که کادر متن خالی می‌موند، و موقع خروج
    // از صفحه همون مقدار خالی به‌عنوان درفت جدید ذخیره می‌شد که عملاً درفت واقعی رو پاک می‌کرد.
    // الان draftApplied فقط وقتی true میشه که واقعاً یه مقدار غیرخالی رسیده باشه، پس effect
    // دوباره (با کلید جدید) اجرا میشه تا مقدار واقعی برسه.
    var draftApplied by remember { mutableStateOf(false) }
    LaunchedEffect(initialDraft) {
        if (!draftApplied && initialDraft.isNotBlank()) {
            draftApplied = true
            if (input.isBlank()) {
                input = initialDraft
            }
        }
    }
    // موقع خروج از صفحه (به هر دلیلی: برگشت، رفتن سراغ مکالمه‌ی دیگه، بستن اپ) با آخرین
    // متنِ کادر ذخیره میشه - rememberUpdatedState لازمه چون onDispose خودِ لامبدای اولیه رو
    // با مقدار input در همون لحظه‌ی composition اول capture می‌کنه، نه مقدار لحظه‌ی خروج.
    val latestInput = rememberUpdatedState(input)
    val latestOnLeave = rememberUpdatedState(onLeaveWithDraft)
    DisposableEffect(Unit) {
        onDispose { latestOnLeave.value(latestInput.value) }
    }
    // اگه address یه شماره‌ی واقعی نباشه (مثلاً Sender ID حروفیِ اپراتورها)، امکان ارسال
    // پیام به این مکالمه نیست - کادر ارسال اصلاً نشون داده نمیشه
    val canSend = remember(address) { PhoneNumberUtils.isSendableAddress(address) }
    var selectedSimId by remember { mutableStateOf<Int?>(null) }
    var selectedMessage by remember { mutableStateOf<SmsMessage?>(null) }
    var selectedScheduledMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    var editingScheduledMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 1f = فونت پایه (16sp)، 1.75f = حداکثر زوم (28sp)
    var fontScale by remember { mutableStateOf(1f) }
    val selectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // پیام‌های واقعی + پیام‌های زمان‌بندی‌شده‌ی در انتظار (که هنوز SmsMessage واقعی
    // نیستن) با هم ترکیب و بر اساس تاریخ/زمانِ خودشون مرتب میشن - پیام‌های
    // زمان‌بندی‌شده چون زمانشون تو آینده‌ست، معمولاً ته لیست (پایین صفحه) می‌شینن.
    // این محاسبه بالاتر از Scaffold اومده (نه فقط داخل LazyColumn) چون نوار «پیام پین‌شده»
    // بالای صفحه هم برای اسکرول‌کردن به ایندکسِ درست بهش نیاز داره.
    val combinedItems = remember(messages, scheduledMessages) {
        (messages.map { ThreadListItem.Real(it) } + scheduledMessages.map { ThreadListItem.Pending(it) })
            .sortedBy { it.sortDate }
    }

    // پیام‌های پین‌شده‌ی همین مکالمه، از قدیمی به جدید - برای نوارِ آویزونِ بالای چت (شبیه
    // بقیه‌ی اپ‌های پیام‌رسان) که حتی وسط اسکرولِ یه چتِ شلوغ هم گم نمیشه
    val pinnedInThread = remember(messages, pinnedMessageIds) {
        messages.filter { pinnedMessageIds.contains(it.id) }.sortedBy { it.date }
    }
    // ایندکسِ پیامِ پین‌شده‌ای که الان توی نوار نشون داده میشه - دقیقاً رفتار تلگرام: با هر
    // کلیک روی نوار، میره سراغِ همین پیام و بعد ایندکس یکی جلو میره (وقتی به آخری رسید،
    // دوباره از اول شروع میشه) تا کلیکِ بعدی، پیامِ پین‌شده‌ی بعدی رو نشون بده.
    var pinnedBannerIndex by remember(pinnedInThread) { mutableStateOf(0) }
    val currentPinnedMessage = pinnedInThread.getOrNull(pinnedBannerIndex)

    LaunchedEffect(sims) {
        if (selectedSimId == null && sims.isNotEmpty()) {
            selectedSimId = sims.first().subscriptionId
        }
    }

    // اگه بعد از حذف/تغییر لیست، بعضی id های انتخاب‌شده دیگه وجود نداشته باشن، از انتخاب پاک بشن
    LaunchedEffect(messages) {
        val stillExisting = messages.map { it.id }.toSet()
        if (selectedIds.any { it !in stillExisting }) {
            selectedIds = selectedIds.filter { it in stillExisting }.toSet()
        }
    }

    // دکمه‌ی برگشت سیستم: همیشه توسط خودمون مدیریت میشه (نه پیش‌فرض NavHost) -
    // چون قبلاً وقتی selectionMode نبود، این BackHandler غیرفعال بود و برگشت از مسیر
    // پیش‌فرض NavHost رد می‌شد؛ یعنی onBack (که clearOpenThread رو صدا می‌زنه) هیچ‌وقت
    // اجرا نمی‌شد و ActiveThreadTracker/openThreadId رو یه مکالمه‌ی قدیمی گیر می‌کرد -
    // نتیجه‌ش این بود که پیام‌های بعدیِ همون مخاطب نه نوتیف می‌گرفتن نه به‌درستی خونده‌نشده می‌موندن.
    BackHandler(enabled = true) {
        if (selectionMode) {
            selectedIds = emptySet()
        } else {
            onBack()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف پیام‌ها") },
            text = { Text("${selectedIds.size} پیام حذف بشه؟") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteMessages(selectedIds)
                    selectedIds = emptySet()
                }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    val currentSelectedMessage = selectedMessage
    if (currentSelectedMessage != null) {
        MessageActionsSheet(
            message = currentSelectedMessage,
            contactDisplayName = displayName,
            isFavorite = favoriteIds.contains(currentSelectedMessage.id),
            onDismiss = { selectedMessage = null },
            onOpenNote = { onOpenNote(currentSelectedMessage.body) },
            onDeleteConfirmed = {
                onDeleteMessage(currentSelectedMessage.id)
                selectedMessage = null
            },
            onToggleFavorite = { onToggleFavorite(currentSelectedMessage) },
            onResend = { onResend(currentSelectedMessage) },
            isPinned = pinnedMessageIds.contains(currentSelectedMessage.id),
            onTogglePin = { onTogglePinMessage(currentSelectedMessage) }
        )
    }

    val currentSelectedScheduled = selectedScheduledMessage
    if (currentSelectedScheduled != null) {
        ScheduledMessageActionsSheet(
            onDismiss = { selectedScheduledMessage = null },
            onEditTime = { editingScheduledMessage = currentSelectedScheduled },
            onSendNow = {
                onSendScheduledNow(currentSelectedScheduled.id)
                selectedScheduledMessage = null
            },
            onCancelSchedule = {
                onCancelScheduledMessage(currentSelectedScheduled.id)
                selectedScheduledMessage = null
            }
        )
    }

    val currentEditingScheduled = editingScheduledMessage
    if (currentEditingScheduled != null) {
        DateTimePickerDialog(
            initialMillis = currentEditingScheduled.scheduledAt,
            onConfirm = {
                onUpdateScheduledTime(currentEditingScheduled.id, it)
                editingScheduledMessage = null
            },
            onDismiss = { editingScheduledMessage = null }
        )
    }

    Scaffold(
        topBar = {
            Column {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} انتخاب شده") },
                        navigationIcon = {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Filled.Close, contentDescription = "لغو انتخاب")
                            }
                        },
                        actions = {
                            val allSelected = selectedIds.size == messages.size && messages.isNotEmpty()
                            IconButton(onClick = {
                                selectedIds = if (allSelected) emptySet() else messages.map { it.id }.toSet()
                            }) {
                                Icon(
                                    Icons.Filled.SelectAll,
                                    contentDescription = if (allSelected) "از انتخاب دراوردن همه" else "انتخاب همه"
                                )
                            }
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(displayName) },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Text("←") }
                        }
                    )
                    // نوار «پیام پین‌شده» - همیشه بالای صفحه آویزونه (زیرِ TopAppBar، جزوِ
                    // topBar خودِ Scaffold) پس با اسکرول کردن وسط پیام‌های زیاد گم نمیشه.
                    // با کلیک، مستقیم می‌پره روی خودِ پیام پین‌شده.
                    if (currentPinnedMessage != null) {
                        PinnedMessageBanner(
                            message = currentPinnedMessage,
                            currentIndex = pinnedBannerIndex,
                            totalPinnedCount = pinnedInThread.size,
                            onClick = {
                                val index = combinedItems.reversed().indexOfFirst {
                                    it is ThreadListItem.Real && it.message.id == currentPinnedMessage.id
                                }
                                if (index >= 0) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                                // بعد از رفتن سراغِ این یکی، دفعه‌ی بعد که کاربر کلیک کرد، پیامِ
                                // پین‌شده‌ی بعدی رو نشون بده - اگه آخری بود، دوباره از اول
                                pinnedBannerIndex = (pinnedBannerIndex + 1) % pinnedInThread.size
                            },
                            onUnpin = { onTogglePinMessage(currentPinnedMessage) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (!selectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    if (canSend) {
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
                            Button(onClick = {
                                if (input.isNotBlank()) {
                                    onSend(input, selectedSimId)
                                    input = ""
                                }
                            }) {
                                Text("ارسال")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("پیام...") },
                                maxLines = 5,
                                // متن انگلیسی/اعداد از چپ نوشته بشن، حتی داخل کانتینر راست‌به‌چپ
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
                            )
                        }
                    } else {
                        // این مکالمه شماره‌ی واقعی نداره (Sender ID حروفیه، مثل اسم اپراتورها) -
                        // به‌جای کادر ارسال، فقط یه توضیح نشون بده که ارسال به این مخاطب ممکن نیست
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "این مخاطب شماره ندارد و امکان ارسال پیام به آن وجود ندارد",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // زوم فونت با دو انگشت - فقط اندازه‌ی متن عوض میشه، نه واقعاً اسکیل کل صفحه
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        fontScale = (fontScale * zoom).coerceIn(1f, 1.75f)
                    }
                }
        ) {
            // هر بار فضای واقعی در دسترس عوض بشه (باز/بسته شدن کیبورد، چرخش صفحه و ...)
            // دوباره برو آخرین پیام تا زیر کادر ارسال قایم نمونه
            val availableHeight = maxHeight
            LaunchedEffect(availableHeight, combinedItems.size) {
                if (combinedItems.isNotEmpty()) {
                    // چون reverseLayout=true هست، ایندکس ۰ = پایین‌ترین/جدیدترین پیام
                    listState.animateScrollToItem(0)
                }
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                items(
                    combinedItems.reversed(),
                    key = { item ->
                        when (item) {
                            is ThreadListItem.Real -> "m_${item.message.id}"
                            is ThreadListItem.Pending -> "s_${item.scheduled.id}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is ThreadListItem.Real -> {
                            val message = item.message
                            MessageBubble(
                                message = message,
                                isFavorite = favoriteIds.contains(message.id),
                                isPinned = pinnedMessageIds.contains(message.id),
                                selectionMode = selectionMode,
                                isSelected = selectedIds.contains(message.id),
                                fontScale = fontScale,
                                onResend = { onResend(message) },
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds = if (selectedIds.contains(message.id)) {
                                            selectedIds - message.id
                                        } else {
                                            selectedIds + message.id
                                        }
                                    } else {
                                        selectedMessage = message
                                    }
                                },
                                onDoubleClick = {
                                    if (!selectionMode) onOpenNote(message.body)
                                },
                                onLongClick = {
                                    if (!selectionMode) selectedIds = setOf(message.id)
                                }
                            )
                        }
                        is ThreadListItem.Pending -> {
                            PendingScheduledBubble(
                                scheduled = item.scheduled,
                                onClick = { if (!selectionMode) selectedScheduledMessage = item.scheduled }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * نوار «پیام پین‌شده» - زیرِ TopAppBar آویزون می‌مونه (نه داخلِ لیستِ پیام‌ها)، پس با
 * اسکرول‌کردنِ چتِ شلوغ گم نمیشه. کلیک روش می‌بره سراغِ خودِ پیام، دکمه‌ی کنارش هم
 * مستقیم آنپینش می‌کنه بدون نیاز به رفتن سراغِ منوی پیام.
 */
@Composable
private fun PinnedMessageBanner(
    message: SmsMessage,
    currentIndex: Int,
    totalPinnedCount: Int,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    Surface(
        color = Color(0xFFFFF3E0),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // وقتی بیش از یه پیام پین باشه، به‌جای آیکنِ ساده، یه ردیف خط‌های کوچیک (شبیه
            // نشانگرِ داستان/پیشرفتِ تلگرام) نشون میده که کدوم پیامِ پین‌شده الان انتخابه
            if (totalPinnedCount > 1) {
                Row(modifier = Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(totalPinnedCount) { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .width(3.dp)
                                .height(if (i == currentIndex) 18.dp else 10.dp)
                                .background(
                                    color = if (i == currentIndex) Color(0xFFFFA000) else Color(0xFFFFCC80),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = Color(0xFFFFA000),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (totalPinnedCount > 1) "پیام پین‌شده (${currentIndex + 1} از $totalPinnedCount)" else "پیام پین‌شده",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(Icons.Filled.Close, contentDescription = "برداشتن پین", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: SmsMessage,
    isFavorite: Boolean,
    isPinned: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    fontScale: Float,
    onResend: () -> Unit,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    // پیام‌های ارسالیِ ناموفق زمینه‌ی قرمز کم‌رنگ می‌گیرن تا همون نگاه اول معلوم باشه مشکل داشتن
    val bubbleColor = when {
        message.isFailed -> Color(0xFFFFCDD2)
        message.isOutgoing -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFE5E5EA)
    }
    val textColor = when {
        message.isFailed -> Color(0xFFB71C1C)
        message.isOutgoing -> Color.White
        else -> Color.Black
    }
    val fontSize = (16 * fontScale).sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionCheck(isSelected = isSelected)
            Spacer(modifier = Modifier.width(4.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
        ) {
            Box(contentAlignment = alignment, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(16.dp),
                    // پیام پین‌شده یه حاشیه‌ی نازکِ رنگی می‌گیره تا همون نگاه اول از بقیه‌ی
                    // پیام‌ها متمایز باشه (علاوه بر آیکن پین زیرِ پیام)
                    border = if (isPinned) BorderStroke(1.5.dp, Color(0xFFFFA000)) else null,
                    modifier = Modifier
                        .padding(4.dp)
                        // تک‌کلیک: برای پیام ناموفق مستقیم دوباره می‌فرسته، وگرنه منوی اکشن پیام
                        // (یا انتخاب، توی حالت انتخاب) - دابل‌کلیک: نوت - لانگ‌کلیک: ورود به حالت انتخاب
                        .combinedClickable(
                            onClick = if (message.isFailed && !selectionMode) onResend else onClick,
                            onDoubleClick = onDoubleClick,
                            onLongClick = onLongClick
                        )
                ) {
                    Text(
                        text = message.body,
                        color = textColor,
                        fontSize = fontSize,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = DateFormatter.formatSmart(message.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                // وضعیت ارسال - فقط برای پیام‌های ارسالی، دقیقاً همون‌جایی که قبلاً فقط
                // تیکِ دلیوری بود؛ حالا هر ۴ حالت یه آیکن مشخص همینجا نشون میدن:
                // در صف/در حال ارسال -> ساعت خاکستری، ارسال‌شده (بدون گزارش دلیوری) -> یه تیک،
                // تحویل داده‌شده -> دو تیک سبز، ناموفق -> آیکن خطا (با کلیک، دوباره می‌فرسته)
                if (message.isOutgoing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when {
                        message.isFailed -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "ارسال نشد - برای ارسال دوباره بزن",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(14.dp)
                                    .combinedClickable(onClick = onResend, onLongClick = {})
                            )
                        }
                        message.isSending || message.isQueued -> {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = if (message.isQueued) "در صف ارسال" else "در حال ارسال",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        message.isDelivered -> {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "تحویل داده شد",
                                tint = Color(0xFF34A853),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> {
                            // ارسال‌شده ولی هنوز گزارش دلیوری نرسیده (یا اصلاً درخواستش نکردیم)
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "ارسال شد",
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                // نشان ستاره: پیام فیوریت‌شده (قفل‌شده در برابر حذف)
                if (isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "فیوریت‌شده",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(14.dp)
                    )
                }
                // نشان پین: پیام پین‌شده‌ی داخل همین چت
                if (isPinned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "پین‌شده",
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/** آواتار جایگزین توی حالت انتخاب: دایره‌ی خالی وقتی انتخاب نشده، دایره‌ی رنگی با تیک وقتی انتخاب شده */
@Composable
private fun SelectionCheck(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "انتخاب‌شده",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * حباب مجازی یه پیام زمان‌بندی‌شده‌ی هنوز-در-انتظار - شکلش شبیه حباب پیام ارسالی معمولیه
 * (چون قراره از طرف ما فرستاده بشه)، با یه خط کوچیک بالای متن که ساعت ارسالش رو نشون میده.
 * تک‌کلیک روش منوی «ویرایش زمان / اکنون ارسال شود / لغو زمان‌بندی» رو باز می‌کنه.
 */
@Composable
private fun PendingScheduledBubble(scheduled: ScheduledMessage, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable(onClick = onClick)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Alarm,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ارسال در ${DateFormatter.formatFull(scheduled.scheduledAt)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = Color.White.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = scheduled.body, color = Color.White)
                    }
                }
            }
        }
    }
}