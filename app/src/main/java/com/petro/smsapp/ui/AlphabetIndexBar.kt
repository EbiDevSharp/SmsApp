package com.petro.smsapp.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * نوارِ کناریِ پرشِ سریع بر اساسِ حرفِ اول (Alphabet Index Bar) - همیشه سمتِ چپِ
 * فیزیکیِ صفحه می‌شینه، مستقل از راست‌چینِ کلیِ برنامه؛ دقیقاً هم‌قاعده‌ی همون ترفندِ
 * CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) که تویِ
 * پس‌زمینه‌ی سویپِ ConversationListScreen هم استفاده شده، تا Start/End همیشه معنیِ
 * فیزیکیِ چپ/راست بدن، نه معنیِ جهتِ متنِ برنامه.
 *
 * فقط حروفی که واقعاً توی لیستِ فعلیِ مکالمات حضور دارن نشون داده میشن (نه کلِ
 * الفبای فارسی+انگلیسی که با هم ۵۹ حرفه و رو صفحه‌ی گوشی جا نمیشه).
 *
 * هم لمسِ تکی هم درگِ عمودی پشتیبانی میشه: با هر تغییرِ موقعیتِ انگشت روی نوار،
 * onLetterChange با حرفِ زیرِ انگشت صدا زده میشه - خودِ اسکرول‌کردنِ لیست به‌عهده‌ی
 * صداکننده‌ست (ConversationListScreen)، این کامپوننت فقط تشخیصِ حرف رو انجام میده.
 *
 * @param bubbleSizeDp اندازه بادکنک حرف جاری (۴۸ تا ۹۶)
 * @param offsetXDp فاصله افقی از لبه چپ فیزیکی (۰ تا ۲۴) — هم فاصله از لبه و هم کمک به جدا شدن از ردیف‌ها
 * @param offsetYDp فاصله عمودی حروف از بالا/پایین (۰ تا ۴۰)
 */
@Composable
fun AlphabetIndexBar(
    letters: List<String>,
    onLetterChange: (String) -> Unit,
    onDragEnd: () -> Unit,
    bubbleSizeDp: Int = 64,
    offsetXDp: Int = 6,
    offsetYDp: Int = 8,
    modifier: Modifier = Modifier
) {
    if (letters.isEmpty()) return

    var activeIndex by remember { mutableStateOf<Int?>(null) }
    var rowHeightPx by remember { mutableStateOf(0f) }

    fun indexForOffsetY(y: Float): Int {
        if (rowHeightPx <= 0f) return 0
        return (y / rowHeightPx).toInt().coerceIn(0, letters.lastIndex)
    }

    val bubbleSize = bubbleSizeDp.coerceIn(48, 96).dp
    val bubbleFontSize = (bubbleSizeDp * 0.4f).coerceIn(18f, 36f).sp
    val hPad = offsetXDp.coerceIn(0, 24).dp
    val vPad = offsetYDp.coerceIn(0, 40).dp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = vPad, horizontal = hPad)
                    .pointerInput(letters) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var idx = indexForOffsetY(down.position.y)
                            activeIndex = idx
                            onLetterChange(letters[idx])
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.pressed }
                                if (change != null) {
                                    val newIdx = indexForOffsetY(change.position.y)
                                    if (newIdx != idx) {
                                        idx = newIdx
                                        activeIndex = idx
                                        onLetterChange(letters[idx])
                                    }
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                            activeIndex = null
                            onDragEnd()
                        }
                    }
            ) {
                letters.forEachIndexed { index, letter ->
                    Text(
                        text = letter,
                        fontSize = 11.sp,
                        color = if (index == activeIndex) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier
                            .padding(vertical = 1.5.dp, horizontal = 4.dp)
                            .onSizeChanged { size -> if (rowHeightPx == 0f) rowHeightPx = size.height.toFloat() }
                    )
                }
            }

            // بادکنکِ بزرگِ نمایشِ حرفِ جاری، وسطِ صفحه، فقط حینِ لمس/درگ
            val currentIndex = activeIndex
            if (currentIndex != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(bubbleSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = letters[currentIndex],
                            color = Color.White,
                            fontSize = bubbleFontSize
                        )
                    }
                }
            }
        }
    }
}
