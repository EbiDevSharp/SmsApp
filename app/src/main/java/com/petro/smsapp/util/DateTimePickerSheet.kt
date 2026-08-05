package com.petro.smsapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.util.JalaliCalendar
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

private enum class PickerStage {
    MAIN, CALENDAR, TIME
}

private enum class QuickTab {
    CUSTOM, THIS_WEEK, TOMORROW, TODAY
}

private val gregorianMonthNamesFaPicker = arrayOf(
    "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن",
    "جولای", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
)

// ترتیب استاندارد هفته جلالی: شنبه تا جمعه
private val weekDayShortLabels = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")
private val weekDayFullLabels = arrayOf(
    "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
)

fun String.toPersianDigits(): String {
    var result = this
    val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    for (i in englishDigits.indices) {
        result = result.replace(englishDigits[i], persianDigits[i])
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerSheet(
    title: String,
    initialMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
    // وقتی false باشه، انتخابِ زمانِ گذشته مجازه و دکمه‌ی تأیید غیرفعال/هشدار نمی‌شه.
    // پیش‌فرض true چون این شیت اصلی‌ترین کاربردش زمان‌بندیِ ارسالِ پیامه که باید تو آینده باشه؛
    // ولی برای مواردی مثل فیلترِ بازه‌ی زمانیِ گذشته (که ذاتاً گذشته انتخاب می‌شه) باید false پاس بشه.
    restrictPast: Boolean = true
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by AppSettings.state.collectAsState()

    // تقویم قابل‌سوئیچ داخل شیت (از تنظیمات شروع می‌شود)
    var isJalali by remember { mutableStateOf(settings.calendarType == CalendarType.JALALI) }

    var stage by remember { mutableStateOf(PickerStage.MAIN) }
    var quickTab by remember { mutableStateOf(QuickTab.CUSTOM) }

    val initialCal = remember { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    val initialParts = remember {
        if (isJalali) {
            val j = JalaliCalendar.toJalali(
                initialCal.get(Calendar.YEAR),
                initialCal.get(Calendar.MONTH) + 1,
                initialCal.get(Calendar.DAY_OF_MONTH)
            )
            Triple(j.year, j.month, j.day)
        } else {
            Triple(
                initialCal.get(Calendar.YEAR),
                initialCal.get(Calendar.MONTH) + 1,
                initialCal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    var year by remember { mutableStateOf(initialParts.first) }
    var month by remember { mutableStateOf(initialParts.second) }
    var day by remember { mutableStateOf(initialParts.third) }
    var hour by remember { mutableStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initialCal.get(Calendar.MINUTE)) }

    var viewYear by remember { mutableStateOf(year) }
    var viewMonth by remember { mutableStateOf(month) }
    var tempDay by remember { mutableStateOf(day) }
    var tempHour by remember { mutableStateOf(hour) }
    var tempMinute by remember { mutableStateOf(minute) }

    fun millisFor(y: Int, m: Int, d: Int, h: Int, min: Int): Long {
        val gymd = if (isJalali) JalaliCalendar.toGregorian(y, m, d) else JalaliCalendar.YMD(y, m, d)
        return Calendar.getInstance().apply {
            clear()
            set(gymd.year, gymd.month - 1, gymd.day, h, min, 0)
        }.timeInMillis
    }

    fun ymdFromMillis(millis: Long): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return if (isJalali) {
            val j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            Triple(j.year, j.month, j.day)
        } else {
            Triple(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    val selectedMillis = millisFor(year, month, day, hour, minute)
    val isPast = restrictPast && selectedMillis <= System.currentTimeMillis()

    fun applyQuickTab(tab: QuickTab) {
        quickTab = tab
        val offsetDays = when (tab) {
            QuickTab.TODAY -> 0
            QuickTab.TOMORROW -> 1
            QuickTab.THIS_WEEK -> 7
            QuickTab.CUSTOM -> return
        }
        val base = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        val (y, m, d) = ymdFromMillis(base.timeInMillis)
        year = y; month = m; day = d
    }

    fun addOffsetToSelection(field: Int, amount: Int) {
        val base = Calendar.getInstance().apply {
            timeInMillis = selectedMillis
            add(field, amount)
        }
        val (y, m, d) = ymdFromMillis(base.timeInMillis)
        year = y; month = m; day = d
        hour = base.get(Calendar.HOUR_OF_DAY)
        minute = base.get(Calendar.MINUTE)
        quickTab = QuickTab.CUSTOM
    }

    /** سوئیچ جلالی ↔ میلادی + تبدیل تاریخ انتخاب‌شده */
    fun toggleCalendar() {
        val (newY, newM, newD) = if (isJalali) {
            // جلالی → میلادی
            val g = JalaliCalendar.toGregorian(year, month, day)
            Triple(g.year, g.month, g.day)
        } else {
            // میلادی → جلالی
            val j = JalaliCalendar.toJalali(year, month, day)
            Triple(j.year, j.month, j.day)
        }
        val nextIsJalali = !isJalali
        val maxDay = daysInMonthFor(newY, newM, nextIsJalali)
        isJalali = nextIsJalali
        year = newY
        month = newM
        day = newD.coerceAtMost(maxDay)
        quickTab = QuickTab.CUSTOM
    }

    BackHandler(enabled = stage != PickerStage.MAIN) {
        stage = PickerStage.MAIN
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            when (stage) {
                PickerStage.MAIN -> MainStage(
                    title = title, year = year, month = month, day = day,
                    hour = hour, minute = minute, isJalali = isJalali,
                    quickTab = quickTab, isPast = isPast,
                    onQuickTabSelect = { applyQuickTab(it) },
                    onOpenCalendar = {
                        viewYear = year; viewMonth = month; tempDay = day
                        stage = PickerStage.CALENDAR
                    },
                    onOpenTime = {
                        tempHour = hour; tempMinute = minute
                        stage = PickerStage.TIME
                    },
                    onQuickOffset = { field, amount -> addOffsetToSelection(field, amount) },
                    onToggleCalendar = { toggleCalendar() },
                    onCancel = onDismiss,
                    onConfirm = { onConfirm(selectedMillis) }
                )
                PickerStage.CALENDAR -> CalendarStage(
                    isJalali = isJalali, viewYear = viewYear, viewMonth = viewMonth, tempDay = tempDay,
                    onViewChange = { y, m -> viewYear = y; viewMonth = m },
                    onDaySelect = { tempDay = it },
                    onQuickJump = { y, m, d -> viewYear = y; viewMonth = m; tempDay = d },
                    onClose = { stage = PickerStage.MAIN },
                    onConfirmDate = {
                        year = viewYear; month = viewMonth; day = tempDay
                        quickTab = QuickTab.CUSTOM
                        stage = PickerStage.MAIN
                    }
                )
                PickerStage.TIME -> TimeStage(
                    initialHour = tempHour, initialMinute = tempMinute,
                    onClose = { stage = PickerStage.MAIN },
                    onBack = { stage = PickerStage.MAIN },
                    onConfirmTime = { h, m ->
                        hour = h; minute = m
                        quickTab = QuickTab.CUSTOM
                        stage = PickerStage.MAIN
                    }
                )
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* MAIN STAGE                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun MainStage(
    title: String, year: Int, month: Int, day: Int, hour: Int, minute: Int,
    isJalali: Boolean, quickTab: QuickTab, isPast: Boolean,
    onQuickTabSelect: (QuickTab) -> Unit, onOpenCalendar: () -> Unit, onOpenTime: () -> Unit,
    onQuickOffset: (field: Int, amount: Int) -> Unit,
    onToggleCalendar: () -> Unit,
    onCancel: () -> Unit, onConfirm: () -> Unit
) {
    val monthName = if (isJalali) JalaliCalendar.monthNames[month - 1] else gregorianMonthNamesFaPicker[month - 1]
    val weekdayIndex = weekdayIndexFor(year, month, day, isJalali)
    val weekdayName = weekDayFullLabels[weekdayIndex]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    ) {
        SheetHeader(title = title, onClose = onCancel)

        Spacer(modifier = Modifier.height(12.dp))

        // سوئیچ تقویم جلالی / میلادی
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomChip(
                text = "جلالی",
                selected = isJalali,
                modifier = Modifier.weight(1f)
            ) {
                if (!isJalali) onToggleCalendar()
            }
            CustomChip(
                text = "میلادی",
                selected = !isJalali,
                modifier = Modifier.weight(1f)
            ) {
                if (isJalali) onToggleCalendar()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomChip(text = "امروز", selected = quickTab == QuickTab.TODAY, modifier = Modifier.weight(1f)) { onQuickTabSelect(QuickTab.TODAY) }
            CustomChip(text = "فردا", selected = quickTab == QuickTab.TOMORROW, modifier = Modifier.weight(1f)) { onQuickTabSelect(QuickTab.TOMORROW) }
            CustomChip(text = "این هفته", selected = quickTab == QuickTab.THIS_WEEK, modifier = Modifier.weight(1f)) { onQuickTabSelect(QuickTab.THIS_WEEK) }
            CustomChip(text = "سفارشی", selected = quickTab == QuickTab.CUSTOM, modifier = Modifier.weight(1f)) {
                onQuickTabSelect(QuickTab.CUSTOM)
                onOpenCalendar()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        PickerCard(
            icon = Icons.Filled.CalendarMonth,
            value = "$weekdayName، $day $monthName $year".toPersianDigits(),
            onClick = onOpenCalendar
        )

        Spacer(modifier = Modifier.height(10.dp))

        PickerCard(
            icon = Icons.Filled.Schedule,
            value = "%02d:%02d".format(hour, minute).toPersianDigits(),
            onClick = onOpenTime
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionDivider(text = "پیشنهاد سریع")

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomChip(text = "+ ۲ روز".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { onQuickOffset(Calendar.DAY_OF_YEAR, 2) }
            CustomChip(text = "+ ۱ روز".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { onQuickOffset(Calendar.DAY_OF_YEAR, 1) }
            CustomChip(text = "+ ۲ ساعت".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { onQuickOffset(Calendar.HOUR_OF_DAY, 2) }
            CustomChip(text = "+ ۶ ساعت".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { onQuickOffset(Calendar.HOUR_OF_DAY, 6) }
        }

        if (isPast) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "این زمان گذشته: لطفاً زمان دیگری انتخاب کنید".toPersianDigits(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text("انصراف", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                enabled = !isPast,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text("تأیید", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* CALENDAR STAGE                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun CalendarStage(
    isJalali: Boolean, viewYear: Int, viewMonth: Int, tempDay: Int,
    onViewChange: (year: Int, month: Int) -> Unit, onDaySelect: (Int) -> Unit,
    onQuickJump: (year: Int, month: Int, day: Int) -> Unit, onClose: () -> Unit, onConfirmDate: () -> Unit
) {
    val monthName = if (isJalali) JalaliCalendar.monthNames[viewMonth - 1] else gregorianMonthNamesFaPicker[viewMonth - 1]
    val daysInMonth = daysInMonthFor(viewYear, viewMonth, isJalali)
    val leading = weekdayIndexFor(viewYear, viewMonth, 1, isJalali)
    val weekdayIndex = weekdayIndexFor(viewYear, viewMonth, tempDay, isJalali)
    val weekdayName = weekDayFullLabels[weekdayIndex]

    fun changeMonth(delta: Int) {
        var m = viewMonth + delta
        var y = viewYear
        if (m > 12) { m = 1; y++ }
        if (m < 1) { m = 12; y-- }
        onViewChange(y, m)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    ) {
        SheetHeader(title = "انتخاب تاریخ", onClose = onClose)

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { changeMonth(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "ماه قبل", tint = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$monthName $viewYear".toPersianDigits(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { changeMonth(-1) }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "ماه بعد", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            weekDayShortLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val totalCells = leading + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leading + 1

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val isSelected = dayNumber == tempDay
                            Surface(
                                onClick = { onDaySelect(dayNumber) },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = dayNumber.toString().toPersianDigits(),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "$weekdayName، $tempDay $monthName $viewYear".toPersianDigits(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionDivider(text = "رفتن سریع")

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomChip(text = "امروز", selected = false, modifier = Modifier.weight(1f)) { jumpDays(isJalali, 0, onQuickJump) }
            CustomChip(text = "فردا", selected = false, modifier = Modifier.weight(1f)) { jumpDays(isJalali, 1, onQuickJump) }
            CustomChip(text = "این هفته", selected = false, modifier = Modifier.weight(1f)) { jumpDays(isJalali, 7, onQuickJump) }
            CustomChip(text = "این ماه", selected = false, modifier = Modifier.weight(1f)) { jumpDays(isJalali, 30, onQuickJump) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onConfirmDate,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Text("تأیید تاریخ", fontWeight = FontWeight.Bold)
        }
    }
}

/* -------------------------------------------------------------------------- */
/* TIME STAGE                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun TimeStage(
    initialHour: Int, initialMinute: Int,
    onClose: () -> Unit, onBack: () -> Unit,
    onConfirmTime: (hour: Int, minute: Int) -> Unit
) {
    var use24h by remember { mutableStateOf(true) }
    var hour by remember { mutableStateOf(initialHour) }
    var minute by remember { mutableStateOf(initialMinute) }

    val scope = rememberCoroutineScope()
    val hourListState = rememberLazyListState()
    val minuteListState = rememberLazyListState()

    fun applyOffsetMinutes(amount: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            add(Calendar.MINUTE, amount)
        }
        hour = cal.get(Calendar.HOUR_OF_DAY)
        minute = cal.get(Calendar.MINUTE)
        scope.launch {
            hourListState.animateScrollToItem(hour)
            minuteListState.animateScrollToItem(minute)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    ) {
        SheetHeader(title = "انتخاب ساعت", onClose = onClose)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomChip(text = "ساعت ۱۲", selected = !use24h, modifier = Modifier.weight(1f)) { use24h = false }
            CustomChip(text = "ساعت ۲۴", selected = use24h, modifier = Modifier.weight(1f)) { use24h = true }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                label = "دقیقه", count = 60, listState = minuteListState, selectedIndex = minute,
                onSelectedChange = { minute = it },
                displayFor = { "%02d".format(it).toPersianDigits() }
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            WheelColumn(
                label = "ساعت", count = 24, listState = hourListState, selectedIndex = hour,
                onSelectedChange = { hour = it },
                displayFor = {
                    val h = if (use24h) it else if (it % 12 == 0) 12 else it % 12
                    "%02d".format(h).toPersianDigits()
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionDivider(text = "پیشنهاد سریع")

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomChip(text = "اکنون", selected = false, modifier = Modifier.weight(1f)) {
                val now = Calendar.getInstance()
                hour = now.get(Calendar.HOUR_OF_DAY)
                minute = now.get(Calendar.MINUTE)
                scope.launch {
                    hourListState.animateScrollToItem(hour)
                    minuteListState.animateScrollToItem(minute)
                }
            }
            CustomChip(text = "+ ۳۰ دقیقه".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { applyOffsetMinutes(30) }
            CustomChip(text = "+ ۱ ساعت".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { applyOffsetMinutes(60) }
            CustomChip(text = "+ ۲ ساعت".toPersianDigits(), selected = false, modifier = Modifier.weight(1f)) { applyOffsetMinutes(120) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text("بازگشت", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { onConfirmTime(hour, minute) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text("تأیید ساعت", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* WHEEL COLUMN                                                               */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    label: String, count: Int, listState: androidx.compose.foundation.lazy.LazyListState,
    selectedIndex: Int, onSelectedChange: (Int) -> Unit, displayFor: (Int) -> String
) {
    val itemHeight = 40.dp
    val visibleCount = 5
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val flingBehavior = rememberSnapFlingBehavior(listState)

    LaunchedEffect(Unit) { listState.scrollToItem(selectedIndex) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val offsetItems = (listState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            val newIndex = (listState.firstVisibleItemIndex + offsetItems).coerceIn(0, count - 1)
            if (newIndex != selectedIndex) onSelectedChange(newIndex)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(itemHeight * visibleCount)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
            )

            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = itemHeight * (visibleCount / 2)),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                items(count) { index ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayFor(index),
                            fontSize = if (isSelected) 18.sp else 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* UI COMPONENTS                                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "بستن", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun PickerCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CustomChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SectionDivider(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    }
}

/* -------------------------------------------------------------------------- */
/* HELPER FUNCTIONS                                                           */
/* -------------------------------------------------------------------------- */

/**
 * تبدیل روز هفته به ایندکس جلالی (شنبه = 0 ... جمعه = 6)
 * Calendar.DAY_OF_WEEK: یکشنبه=1 ... شنبه=7
 */
private fun weekdayIndexFor(year: Int, month: Int, day: Int, isJalali: Boolean): Int {
    val gymd = if (isJalali) JalaliCalendar.toGregorian(year, month, day) else JalaliCalendar.YMD(year, month, day)
    val cal = Calendar.getInstance().apply {
        clear()
        set(gymd.year, gymd.month - 1, gymd.day)
    }
    // شنبه=7 → 0 ، یکشنبه=1 → 1 ، ... ، جمعه=6 → 6
    return (cal.get(Calendar.DAY_OF_WEEK) % 7)
}

private fun daysInMonthFor(year: Int, month: Int, isJalali: Boolean): Int {
    return if (isJalali) {
        when {
            month <= 6 -> 31
            month <= 11 -> 30
            else -> 29
        }
    } else {
        val tmp = Calendar.getInstance()
        tmp.clear()
        tmp.set(year, month - 1, 1)
        tmp.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}

private fun jumpDays(isJalali: Boolean, offsetDays: Int, onQuickJump: (Int, Int, Int) -> Unit) {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
    val (y, m, d) = if (isJalali) {
        val j = JalaliCalendar.toJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        Triple(j.year, j.month, j.day)
    } else {
        Triple(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
    onQuickJump(y, m, d)
}