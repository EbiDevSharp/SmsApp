package com.petro.smsapp.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * یه آیتمِ منوی «+». هرکدوم یه چیپِ مربعی با آیکن بالا و لیبل درست زیرش میشه.
 * جایگاهِ هر آیتم رو خودِ فراخوان با ساختارِ pages مشخص می‌کنه (کدوم صفحه،
 * کدوم ردیف/ستون) - AttachMenuSheet خودش چیزی رو خودکار نمی‌چینه.
 */
data class AttachMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

private const val ATTACH_MENU_COLUMNS = 3
private const val ATTACH_MENU_ROWS = 2
private const val ATTACH_MENU_PAGE_COUNT = 3
private const val ATTACH_MENU_MIDDLE_PAGE = ATTACH_MENU_PAGE_COUNT / 2 // = 1
private val ATTACH_MENU_PAGE_HEIGHT = 168.dp
private val ATTACH_MENU_CHIP_SIZE = 74.dp
private const val ATTACH_MENU_SCRIM_DIM = 0.5f // پشتِ مودال تقریباً ۷۰٪ معلوم بمونه

/**
 * منوی «+» به‌صورتِ یه کامپوننتِ کاملاً جدا و قابلِ‌توسعه.
 *
 * به‌جای ModalBottomSheetِ قبلی، یه Dialogِ شناوره: یه کارتِ گردِ چهارگوشه که هم
 * از پایینِ صفحه هم از دو طرف فاصله می‌گیره. اسکریمِ پشتش دیگه تیره‌ی پیش‌فرضِ
 * دیالوگ نیست - با setDimAmount روی خودِ پنجره‌ی دیالوگ کم شده تا صفحه‌ی پشتش
 * تقریباً ۷۰٪ دیده بشه (فقط یه لایه‌ی نازکِ تیره برای جداشدنِ بصری از پسِ‌زمینه).
 *
 * محتوا سه‌صفحه‌ایه (HorizontalPager) با سه‌تا دایره‌ی نشانگرِ پایین؛ دایره‌ی
 * صفحه‌ی فعال پره، دوتای دیگه فقط یه حلقه‌ی توخالی‌ان. با بازشدنِ منو همیشه
 * صفحه‌ی وسط (ایندکس ۱) پیش‌فرض/فعاله - چون آیتمِ اصلیِ فعلی («زمان‌بندی») هم
 * دقیقاً همونجاست، کاربر مجبور نیست سوایپ کنه تا بهش برسه؛ صفحه‌ی راست و چپ
 * برای آیتم‌های بعدی (پیوست عکس/فایل و ...) خالی و آماده‌ان.
 *
 * چیدمانِ داخلِ هر صفحه از گوشه‌یِ «شروع/بالا» پر میشه، نه وسط - یعنی چون
 * جهتِ برنامه فعلاً راست‌به‌چپه (Start = راست)، چیپِ اول دقیقاً گوشه‌ی
 * راست‌بالای صفحه می‌شینه؛ اگه بعداً برنامه دوزبانه/چپ‌به‌راست شد، همین
 * Arrangement.Start/Alignment.Start خودش می‌چرخه و نیازی به تغییرِ دستی نیست.
 *
 * @param pages لیستِ آیتم‌های هر صفحه، به ترتیب (صفحه‌ی ۰، ۱، ۲)؛ هر صفحه حداکثر
 *   ۶تا چیپ (۳ستون × ۲ردیف) جا می‌ده. برای اضافه‌کردنِ آیتمِ جدید فقط کافیه به
 *   لیستِ صفحه‌ی موردنظر یه AttachMenuItem دیگه اضافه بشه.
 * @param initialPage صفحه‌ای که منو باهاش باز میشه (پیش‌فرض: صفحه‌ی وسط).
 */
@ExperimentalFoundationApi
@Composable
fun AttachMenuSheet(
    pages: List<List<AttachMenuItem>>,
    onDismiss: () -> Unit,
    initialPage: Int = ATTACH_MENU_MIDDLE_PAGE
) {
    val normalizedPages = remember(pages) {
        (0 until ATTACH_MENU_PAGE_COUNT).map { pageIndex -> pages.getOrNull(pageIndex) ?: emptyList() }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, ATTACH_MENU_PAGE_COUNT - 1),
        pageCount = { ATTACH_MENU_PAGE_COUNT }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // پنجره‌ی خودِ Dialog به‌طورِ پیش‌فرض یه پس‌زمینه‌ی ماتِ سفید داره که زیرِ
        // dimAmount می‌شینه و کاملاً می‌پوشوندش - برای همین قبلاً هرچی dimAmount رو
        // کم می‌کردیم بازم سفید می‌موند. اول باید خودِ پس‌زمینه‌ی پنجره transparent
        // بشه تا dimAmount واقعاً روی محتوای پشتش (نه یه لایه‌ی سفیدِ اضافه) اثر کنه.
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window?.setDimAmount(ATTACH_MENU_SCRIM_DIM)
        }

        // چون پنجره کلِ صفحه رو گرفته (usePlatformDefaultWidth = false)، دیگه از
        // دیدِ اندروید «بیرونِ پنجره»ای وجود نداره تا dismissOnClickOutside خودکار
        // کار کنه؛ برای همین خودمون کلیکِ روی زمینه (بیرونِ کارت) رو تشخیص می‌دیم و
        // onDismiss رو صدا می‌زنیم. خودِ کارت با Surface(onClick = {}) یه کلیکِ
        // بی‌اثر رو می‌گیره تا این کلیک به زمینه‌ی پشتش سرایت نکنه و منو بسته نشه.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                onClick = {},
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 20.dp)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        AttachMenuPage(
                            items = normalizedPages[page],
                            onItemClick = { itemClick ->
                                onDismiss()
                                itemClick()
                            }
                        )
                    }
                    AttachMenuPageIndicator(
                        pageCount = ATTACH_MENU_PAGE_COUNT,
                        currentPage = pagerState.currentPage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachMenuPage(
    items: List<AttachMenuItem>,
    onItemClick: (() -> Unit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(ATTACH_MENU_PAGE_HEIGHT)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        items.chunked(ATTACH_MENU_COLUMNS).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                rowItems.forEachIndexed { index, item ->
                    AttachMenuChip(item = item, onClick = { onItemClick(item.onClick) })
                    if (index != rowItems.lastIndex) {
                        Spacer(modifier = Modifier.width(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * چیپِ هر آیتمِ منو - آیکن بالا، لیبل درست زیرِ آیکن، هردو وسط‌چین (خودِ چیپ).
 */
@Composable
private fun AttachMenuChip(item: AttachMenuItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(ATTACH_MENU_CHIP_SIZE)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * سه‌تا دایره‌ی نشانگرِ صفحه - دایره‌ی صفحه‌ی فعال پر، بقیه فقط یه حلقه‌ی
 * توخالی.
 */
@Composable
private fun AttachMenuPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .then(
                        if (isActive) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                        }
                    )
            )
        }
    }
}