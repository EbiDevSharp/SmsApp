package com.petro.smsapp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.util.MessageLinkifier
import com.petro.smsapp.util.autoDirection

/**
 * جایگزینِ Textِ ساده‌ی متنِ حبابِ پیام - لینک‌ها، شماره‌تلفن‌ها و اعدادِ معمولی رو
 * آبی/زیرخط‌دار نشون میده. تپ روی هرکدوم یه شیتِ کوچیک با اکشن‌های مناسب باز می‌کنه:
 * لینک -> باز کردن/کپی/اشتراک‌گذاری، شماره‌تلفن -> تماس/کپی/اشتراک‌گذاری (و اگه شماره
 * جزوِ مخاطبینِ گوشی باشه، اسمش هم بالای شیت نشون داده میشه)، عددِ معمولی -> فقط
 * کپی/اشتراک‌گذاری.
 *
 * نکته‌ی مهمِ جهتِ نمایش: چون پیام‌های فارسی راست‌به‌چپ هستن، وقتی یه شماره/لینک
 * (که همیشه باید چپ‌به‌راست دیده بشه) وسطِ متن یا حتی توی یه خطِ کاملاً مجزا می‌افته،
 * کاراکترهای neutral مثلِ «+» می‌تونن به‌اشتباه از جهتِ راست‌به‌چپِ اطراف پیروی کنن
 * (مثلاً + یه شماره بره سمتِ راستِ شماره به‌جای چپش). برای همین هر match با
 * کاراکترهای ایزوله‌ی یونیکد LRI (U+2066) / PDI (U+2069) پیچیده میشه تا مستقل از
 * جهتِ پاراگرافِ اطراف، همیشه به‌عنوانِ یه بلوکِ چپ‌به‌راست رندر بشه.
 *
 * تپ/دابل‌تپ/لانگ‌کلیک روی بقیه‌ی متن دست‌نخورده به بیرون (حبابِ پیام) می‌رسه، چون این
 * Composable فقط رویداد «up»ِ تپ‌هایی که واقعاً روی یه match باشن رو consume می‌کنه.
 *
 * وقتی enabled=false باشه (مثلاً توی حالتِ «انتخاب چندتایی») هیچ رهگیری‌ای انجام
 * نمیشه و همه‌ی تپ‌ها عیناً به بیرون میرن.
 */

// کاراکترهای ایزوله‌ی جهتِ یونیکد - دورِ هر match می‌پیچن تا مستقل از جهتِ متنِ
// اطراف، همیشه چپ‌به‌راست رندر بشه (بدونِ اینکه جهتِ خودِ پاراگراف رو عوض کنن)
private const val LRI = '\u2066' // Left-to-Right Isolate
private const val PDI = '\u2069' // Pop Directional Isolate

private data class RenderedMatch(
    val renderedStart: Int,
    val renderedEnd: Int,
    val match: MessageLinkifier.LinkifyMatch
)

private fun buildLinkifiedContent(
    text: String,
    matches: List<MessageLinkifier.LinkifyMatch>,
    highlightColor: Color
): Pair<AnnotatedString, List<RenderedMatch>> {
    val renderedMatches = mutableListOf<RenderedMatch>()
    val annotated = buildAnnotatedString {
        var lastIndex = 0
        matches.forEach { match ->
            if (match.start > lastIndex) {
                append(text.substring(lastIndex, match.start))
            }
            val renderedStart = length
            append(LRI)
            append(text.substring(match.start, match.end))
            append(PDI)
            val renderedEnd = length
            addStyle(
                SpanStyle(color = highlightColor, textDecoration = TextDecoration.Underline),
                renderedStart,
                renderedEnd
            )
            renderedMatches.add(RenderedMatch(renderedStart, renderedEnd, match))
            lastIndex = match.end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
    return annotated to renderedMatches
}

@Composable
fun LinkifiedMessageText(
    text: String,
    textColor: Color,
    fontSize: TextUnit,
    isOutgoing: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val matches = remember(text) { MessageLinkifier.findMatches(text) }
    var actionTarget by remember { mutableStateOf<MessageLinkifier.LinkifyMatch?>(null) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // پس‌زمینه‌ی حبابِ ارسالی primary (آبی) رنگه و متنش سفیده - یه آبیِ روشن برای
    // هایلایتِ لینک/شماره/عدد کنتراستِ بهتری داره؛ حبابِ دریافتی خاکستری/متنِ تیره‌ست، پس
    // یه آبیِ استانداردِ لینک روش خوب دیده میشه.
    val highlightColor = if (isOutgoing) Color(0xFFBBDEFB) else Color(0xFF1565C0)

    val (annotated, renderedMatches) = remember(text, matches, highlightColor) {
        buildLinkifiedContent(text, matches, highlightColor)
    }

    val current = actionTarget
    if (current != null) {
        LinkifyActionsSheet(match = current, onDismiss = { actionTarget = null })
    }

    val finalModifier = if (enabled && renderedMatches.isNotEmpty()) {
        modifier.pointerInput(renderedMatches) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if (up != null) {
                    val result = layoutResult
                    if (result != null) {
                        val offset = result.getOffsetForPosition(up.position)
                        val hit = renderedMatches.firstOrNull { offset >= it.renderedStart && offset < it.renderedEnd }
                        if (hit != null) {
                            up.consume()
                            actionTarget = hit.match
                        }
                    }
                }
            }
        }
    } else {
        modifier
    }

    Text(
        text = annotated,
        color = textColor,
        fontSize = fontSize,
        onTextLayout = { layoutResult = it },
        modifier = finalModifier
    )
}

@Composable
private fun LinkifyActionsSheet(
    match: MessageLinkifier.LinkifyMatch,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // فقط برای شماره‌تلفن‌ها معنی داره - اگه این شماره جزوِ مخاطبینِ ذخیره‌شده‌ی گوشی
    // باشه، اسمش رو (از همون کشِ مشترکِ ContactsCache، بدونِ کوئریِ اضافه) می‌گیریم
    val contactName = remember(match.value, match.type) {
        if (match.type == MessageLinkifier.MatchType.PHONE_NUMBER) {
            ContactsCache.getName(context, match.value)
        } else {
            null
        }
    }

    fun shareValue() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, match.value)
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
        onDismiss()
    }

    fun copyValue() {
        clipboardManager.setText(AnnotatedString(match.value))
        onDismiss()
    }

    fun openLink() {
        val hasScheme = match.value.startsWith("http://", ignoreCase = true) ||
                match.value.startsWith("https://", ignoreCase = true)
        val target = if (hasScheme) match.value else "https://${match.value}"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        } catch (e: Exception) {
            // هیچ اپی برای بازکردنِ این لینک روی گوشی پیدا نشد
        }
        onDismiss()
    }

    fun callNumber() {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${match.value}")))
        } catch (e: Exception) {
            // هیچ اپِ تماسی روی گوشی پیدا نشد
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                // اگه این شماره یه مخاطبِ شناخته‌شده باشه، اول اسمش (راست‌به‌چپ/چپ‌به‌راستِ
                // خودکار طبقِ محتوا) و بعدش خودِ شماره (همیشه چپ‌به‌راست) نشون داده میشه
                if (contactName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = contactName,
                            style = MaterialTheme.typography.bodyMedium.autoDirection(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.padding(top = 2.dp))
                }
                Text(
                    text = match.value,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (match.type == MessageLinkifier.MatchType.URL) {
                LinkifyMenuRow(Icons.Filled.OpenInNew, "باز کردن") { openLink() }
            }
            if (match.type == MessageLinkifier.MatchType.PHONE_NUMBER) {
                LinkifyMenuRow(Icons.Filled.Call, "تماس") { callNumber() }
            }
            LinkifyMenuRow(Icons.Filled.ContentCopy, "کپی") { copyValue() }
            LinkifyMenuRow(Icons.Filled.Share, "اشتراک‌گذاری") { shareValue() }
        }
    }
}

@Composable
private fun LinkifyMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}