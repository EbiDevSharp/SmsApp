package com.petro.smsapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.SimInfo
import com.petro.smsapp.util.DateFormatter

/**
 * نوار ارسال پیام مشترک بین «پیام جدید» و صفحه‌ی چت.
 *
 * دکمه‌ی «+» چون آخرین آیتمِ Row هست، توی چیدمانِ راست‌به‌چپِ برنامه سمتِ چپِ کادر متن
 * می‌شینه (دقیقاً همونجایی که خواسته شده بود) و با زدنش یه منوی پایین‌صفحه باز میشه؛
 * فعلاً فقط «زمان‌بندی ارسال» توش هست، آیتم‌های بعدی (پیوست عکس/فایل و ...) همینجا
 * اضافه میشن. هرکدوم به‌صورتِ یه چیپِ مربعیِ کوچیک - فعلاً فقط آیکن، بدونِ متن - نشون
 * داده میشن؛ اگه بعداً لازم شد، یه برچسبِ کوچیک زیرِ هرکدوم اضافه میشه. دکمه‌ی ارسال
 * هم یه دایره‌ی پر با آیکنِ پیکان (Send) شبیه Google Messages ئه.
 *
 * چیپِ زمان‌بندی: قبلاً یه ردیفِ جدا بالای کادرِ متن بود (بیرونِ خودِ باکس). الان
 * به‌عنوانِ leadingIcon خودِ OutlinedTextField تعریف شده - یعنی کاملاً داخلِ خودِ
 * باکسِ متن می‌مونه و «بیرون نمی‌پره»، دقیقاً تا لحظه‌ی ارسالِ واقعی. کلیک روی خودِ
 * چیپ = ویرایشِ زمان، دکمه‌ی ضربدرِ کوچیکِ کنارش = لغوِ زمان‌بندی و برگشت به ارسالِ
 * فوری. خودِ متنِ تایپ‌شده و زمانِ انتخابی، تا قبل از زدنِ دکمه‌ی ارسال هیچ‌وقت به‌شکلِ
 * حبابِ پیام نمایش داده نمیشن؛ فقط بعد از ارسالِ واقعی (یا رسیدنِ زمانِ زمان‌بندی)
 * تبدیل به یه پیامِ واقعی/زمان‌بندی‌شده تو لیستِ پیام‌ها میشن.
 *
 * یه ردیفِ کوچیکِ «تعداد کاراکترِ باقی‌مونده/تعداد پیامک» (مثلاً 160/1) هم بالای کادرِ
 * متن، سمتِ چپ (چون همیشه چپ‌به‌راست نشون داده میشه) اضافه شده - از همون
 * SmsSegmentCalculator/SmsSegmentIndicator که زیرِ هر حباب پیام هم استفاده میشه، پس
 * منطقش دقیقاً یکیه و اگه بعداً بخواد عوض بشه فقط یه‌جا لازمه تغییر کنه.
 *
 * انتخابِ سیم‌کارت: به‌عنوانِ trailingIcon خودِ OutlinedTextField تعریف شده - یعنی
 * داخلِ خودِ کادر، گوشه‌ی «انتهای» متن که چون کلِ چیدمانِ برنامه راست‌به‌چپه، دقیقاً
 * همون گوشه‌ی فیزیکیِ چپِ کادر میشه. یه چیپِ کوچیکِ مستطیلی با شماره‌ی سیمِ فعال (۱ یا
 * ۲) نشون داده میشه؛ کلیک روش همون منوی قبلی (DropdownMenu با اسمِ هر سیم) رو باز
 * می‌کنه. فقط وقتی گوشی حداقل دو سیم‌کارتِ فعال داشته باشه نشون داده میشه.
 *
 * زمان‌بندی: قبلاً DateTimePickerDialog (استپرِ ساده) بود؛ الان از DateTimePickerSheet
 * (کامپوننتِ سه‌مرحله‌ایِ «طرح ۲» - تب‌های سریع + گریدِ تقویم + ویل‌پیکرِ ساعت) استفاده
 * می‌کنه.
 *
 * تأخیر ارسال: اگه sendDelaySeconds > 0، با زدنِ ارسال اول PendingMessageBubble نشون
 * داده میشه و بعد از اون ثانیه واقعاً onSendClick صدا زده میشه؛ کنسل متن رو برمی‌گردونه.
 */
@Composable
fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    scheduledAt: Long?,
    onScheduledAtChange: (Long?) -> Unit,
    placeholder: String = "پیام...",
    modifier: Modifier = Modifier,
    sims: List<SimInfo> = emptyList(),
    selectedSubscriptionId: Int? = null,
    onSimSelect: (Int) -> Unit = {},
    sendDelaySeconds: Int = 0
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isPending by remember { mutableStateOf(false) }
    var pendingSnapshot by remember { mutableStateOf("") }

    if (showAttachMenu) {
        AttachMenuSheet(
            onDismiss = { showAttachMenu = false },
            onScheduleClick = {
                showAttachMenu = false
                showTimePicker = true
            }
        )
    }

    if (showTimePicker) {
        DateTimePickerSheet(
            title = "زمان ارسال",
            initialMillis = scheduledAt ?: System.currentTimeMillis(),
            onConfirm = {
                onScheduledAtChange(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isPending && sendDelaySeconds > 0) {
            PendingMessageBubble(
                text = pendingSnapshot,
                delaySeconds = sendDelaySeconds,
                onCancel = {
                    onValueChange(pendingSnapshot)
                    isPending = false
                    pendingSnapshot = ""
                },
                onSendTimeout = {
                    onValueChange(pendingSnapshot)
                    onSendClick()
                    isPending = false
                    pendingSnapshot = ""
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // اول میاد تا توی چیدمان راست‌به‌چپ سمت راستِ کادر بشینه (دقیقاً هم‌جهت با قبل)
            Box(contentAlignment = Alignment.TopCenter) {
                FilledIconButton(
                    onClick = {
                        if (value.isBlank() || isPending) return@FilledIconButton
                        if (sendDelaySeconds > 0 && scheduledAt == null) {
                            pendingSnapshot = value
                            onValueChange("")
                            isPending = true
                        } else {
                            onSendClick()
                        }
                    },
                    enabled = value.isNotBlank() && !isPending,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (scheduledAt != null) "زمان‌بندی ارسال" else "ارسال",
                        tint = Color.White
                    )
                }
                if (value.isNotEmpty() && !isPending) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.offset(y = (-18).dp)
                    ) {
                        SmsSegmentIndicator(
                            text = value,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                placeholder = { Text(placeholder) },
                maxLines = 5,
                enabled = !isPending,
                shape = RoundedCornerShape(22.dp),
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr),
                // چیپِ زمان‌بندی - کاملاً داخلِ خودِ باکسِ متن، تا قبل از ارسال بیرون نمی‌پره
                leadingIcon = {
                    if (scheduledAt != null) {
                        ScheduledInlineChip(
                            scheduledAt = scheduledAt,
                            onClick = { showTimePicker = true },
                            onCancel = { onScheduledAtChange(null) }
                        )
                    }
                },
                // چیپِ کوچیکِ انتخابِ سیم - داخلِ خودِ کادر، گوشه‌ی «انتها»یِ متن که تو
                // چیدمانِ راست‌به‌چپِ برنامه دقیقاً گوشه‌ی فیزیکیِ چپ میشه
                trailingIcon = {
                    if (sims.size >= 2) {
                        SimQuickSelectChip(
                            sims = sims,
                            selectedSubscriptionId = selectedSubscriptionId,
                            onSelect = onSimSelect
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            // آخرین آیتم -> توی چیدمانِ راست‌به‌چپ سمتِ چپِ کادر می‌شینه
            IconButton(onClick = { showAttachMenu = true }, enabled = !isPending) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن")
            }
        }
    }
}

/**
 * چیپِ کوچیکِ زمان‌بندی که به‌عنوانِ leadingIcon داخلِ خودِ OutlinedTextField می‌شینه -
 * یه مربعِ کوچیک با آیکنِ ساعت (فعلاً بدون متن). دکمه‌ی لغو دیگه یه IconButtonِ جدا و
 * پهن نیست - مثلِ چیپ‌های فیلترِ درآور (DrawerFilterAccordion) یه بجِ ریزِ گوشه‌ی
 * بالای خودِ چیپه؛ اینجوری هم مجموعه جمع‌وجورتر میشه هم با پدینگِ اطرافش از لبه‌ی
 * باکسِ متن فاصله می‌گیره (قبلاً کاملاً چسبیده بود).
 */
@Composable
private fun ScheduledInlineChip(
    scheduledAt: Long,
    onClick: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = "ارسال در ${DateFormatter.formatFull(scheduledAt)} - برای ویرایش بزن",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "لغو زمان‌بندی",
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/**
 * چیپِ کوچیکِ مستطیلیِ انتخابِ سیم‌کارت که به‌عنوانِ trailingIcon داخلِ خودِ
 * OutlinedTextField می‌شینه. عددِ نشون‌داده‌شده بر اساسِ slotIndex ئه (نه
 * subscriptionId که یه عددِ سیستمیِ بی‌معنی برای کاربره). کلیک روش همون
 * DropdownMenu با اسمِ هر سیم رو باز می‌کنه.
 */
@Composable
private fun SimQuickSelectChip(
    sims: List<SimInfo>,
    selectedSubscriptionId: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSim = sims.find { it.subscriptionId == selectedSubscriptionId } ?: sims.firstOrNull()
    val label = selectedSim?.let { (it.slotIndex + 1).toString() } ?: "?"

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .padding(end = 4.dp)
                .size(width = 26.dp, height = 22.dp)
                .clickable { expanded = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text(sim.displayName) },
                    onClick = {
                        onSelect(sim.subscriptionId)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * منوی «+» - آیتم‌هاش (فعلاً فقط «زمان‌بندی ارسال») به‌صورتِ چیپ‌های مربعیِ کوچیک
 * کنارِ هم (FlowRow) نشون داده میشن، فعلاً فقط با آیکن و بدونِ متن. اگه بعداً لازم
 * شد، یه برچسبِ کوچیک زیرِ هر چیپ اضافه میشه. آیتم‌های بعدی (پیوست عکس/فایل و ...)
 * دقیقاً با همین الگو به AttachMenuChip های بیشتر تبدیل میشن.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AttachMenuSheet(onDismiss: () -> Unit, onScheduleClick: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AttachMenuChip(
                icon = Icons.Filled.Schedule,
                contentDescription = "زمان‌بندی ارسال",
                onClick = {
                    onDismiss()
                    onScheduleClick()
                }
            )
            // آیتم‌های بعدیِ منوی «+» (پیوست عکس/فایل و ...) همینجا اضافه میشن
        }
    }
}

@Composable
private fun AttachMenuChip(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(56.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
