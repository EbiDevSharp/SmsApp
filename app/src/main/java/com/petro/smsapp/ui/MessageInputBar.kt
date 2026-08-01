package com.petro.smsapp.ui

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
import androidx.compose.ui.graphics.Color
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
 * اضافه میشن. دکمه‌ی ارسال هم یه دایره‌ی پر با آیکنِ پیکان (Send) شبیه Google Messages ئه.
 *
 * اگه یه زمان انتخاب شده باشه، یه چیپ بالای کادر نشونش میده (کلیک روش = ویرایش زمان،
 * ضربدر کنارش = لغوِ زمان‌بندی و برگشت به ارسال فوری). خودِ تصمیم اینکه ارسال فوری بشه
 * یا زمان‌بندی‌شده، با صداکننده‌ست (توی onSendClick مقدار فعلیِ scheduledAt رو خودش چک می‌کنه).
 *
 * یه ردیفِ کوچیکِ «تعداد کاراکترِ باقی‌مونده/تعداد پیامک» (مثلاً 160/1) هم بالای کادرِ
 * متن، سمتِ چپ (چون همیشه چپ‌به‌راست نشون داده میشه) اضافه شده - از همون
 * SmsSegmentCalculator/SmsSegmentIndicator که زیرِ هر حباب پیام هم استفاده میشه، پس
 * منطقش دقیقاً یکیه و اگه بعداً بخواد عوض بشه فقط یه‌جا لازمه تغییر کنه.
 *
 * انتخابِ سیم‌کارت: قبلاً یه ردیفِ جدا (SimSelector) بالای این نوار نشون داده می‌شد.
 * الان به‌جاش یه دکمه‌ی کوچیکِ دایره‌ای (شماره‌ی سیمِ فعال: ۱ یا ۲) خودِ همین نوار،
 * کنارِ دکمه‌ی ارسال، نشون داده میشه - با کلیک روش یه منوی کشویی برای تعویضِ سیم باز
 * میشه. فقط وقتی گوشی حداقل دو سیم‌کارتِ فعال داشته باشه نشون داده میشه.
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
    onSimSelect: (Int) -> Unit = {}
) {
    var showAttachMenu by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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
        DateTimePickerDialog(
            initialMillis = scheduledAt ?: System.currentTimeMillis(),
            onConfirm = {
                onScheduledAtChange(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    Column(modifier = modifier) {
        if (scheduledAt != null) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { showTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ارسال در ${DateFormatter.formatFull(scheduledAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = { onScheduledAtChange(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = "لغو زمان‌بندی", modifier = Modifier.size(18.dp))
                }
            }
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
                    onClick = onSendClick,
                    enabled = value.isNotBlank(),
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
                if (value.isNotEmpty()) {
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

            if (sims.size >= 2) {
                Spacer(modifier = Modifier.width(6.dp))
                SimQuickSelectButton(
                    sims = sims,
                    selectedSubscriptionId = selectedSubscriptionId,
                    onSelect = onSimSelect
                )
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
                shape = RoundedCornerShape(22.dp),
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)
            )
            Spacer(modifier = Modifier.width(4.dp))
            // آخرین آیتم -> توی چیدمانِ راست‌به‌چپ سمتِ چپِ کادر می‌شینه
            IconButton(onClick = { showAttachMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = "افزودن")
            }
        }
    }
}

/**
 * دکمه‌ی کوچیکِ دایره‌ای داخلِ نوارِ ارسال که شماره‌ی سیمِ فعال (بر اساسِ slotIndex، نه
 * subscriptionId که یه عددِ سیستمیِ بی‌معنی برای کاربره) رو نشون میده. کلیک روش یه
 * DropdownMenu با اسمِ هر سیم (displayName) باز می‌کنه.
 */
@Composable
private fun SimQuickSelectButton(
    sims: List<SimInfo>,
    selectedSubscriptionId: Int?,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSim = sims.find { it.subscriptionId == selectedSubscriptionId } ?: sims.firstOrNull()
    val label = selectedSim?.let { (it.slotIndex + 1).toString() } ?: "?"

    Box {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier
                .size(38.dp)
                .clickable { expanded = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun AttachMenuSheet(onDismiss: () -> Unit, onScheduleClick: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScheduleClick)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text("زمان‌بندی ارسال", style = MaterialTheme.typography.bodyLarge)
            }
            // آیتم‌های بعدیِ منوی «+» (پیوست عکس/فایل و ...) اینجا اضافه میشن
        }
    }
}
