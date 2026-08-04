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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.petro.smsapp.util.MessageLinkifier

/**
 * جایگزینِ Textِ ساده‌ی متنِ حبابِ پیام - لینک‌ها (با/بدون http) و رشته‌های عددی رو
 * آبی/زیرخط‌دار نشون میده. تپ روی خودِ لینک/عدد یه شیتِ کوچیک با اکشن‌های کپی/
 * اشتراک‌گذاری (و برای لینک، بازکردن) باز می‌کنه.
 *
 * تپ/دابل‌تپ/لانگ‌کلیک روی بقیه‌ی متن دست‌نخورده به بیرون (حبابِ پیام - MessageActionsSheet،
 * بازکردنِ نوت، حالتِ انتخاب) می‌رسه، چون این Composable فقط رویداد «up»ِ تپ‌هایی که
 * واقعاً روی یه لینک/عدد باشن رو consume می‌کنه؛ بقیه‌ی رویدادها دست‌نخورده می‌مونن تا
 * combinedClickable خودِ Surfaceِ حباب (توی ThreadScreen) طبقِ روالِ قبلی پردازششون کنه.
 *
 * وقتی enabled=false باشه (مثلاً توی حالتِ «انتخاب چندتایی») هیچ رهگیری‌ای انجام
 * نمیشه و همه‌ی تپ‌ها عیناً به بیرون میرن - چون تو اون حالت باید تپ روی هرجای حباب،
 * حتی روی یه لینک، فقط انتخاب/عدمِ‌انتخاب کنه.
 */
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
    // هایلایتِ لینک/عدد کنتراستِ بهتری داره؛ حبابِ دریافتی خاکستری/متنِ تیره‌ست، پس
    // یه آبیِ استانداردِ لینک روش خوب دیده میشه.
    val highlightColor = if (isOutgoing) Color(0xFFBBDEFB) else Color(0xFF1565C0)

    val annotated = remember(text, matches, highlightColor) {
        buildAnnotatedString {
            append(text)
            matches.forEach { match ->
                addStyle(
                    SpanStyle(color = highlightColor, textDecoration = TextDecoration.Underline),
                    match.start,
                    match.end
                )
            }
        }
    }

    val current = actionTarget
    if (current != null) {
        LinkifyActionsSheet(match = current, onDismiss = { actionTarget = null })
    }

    val finalModifier = if (enabled && matches.isNotEmpty()) {
        modifier.pointerInput(matches) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if (up != null) {
                    val result = layoutResult
                    if (result != null) {
                        val offset = result.getOffsetForPosition(up.position)
                        val match = matches.firstOrNull { offset >= it.start && offset < it.end }
                        if (match != null) {
                            up.consume()
                            actionTarget = match
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = match.value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (match.type == MessageLinkifier.MatchType.URL) {
                LinkifyMenuRow(Icons.Filled.OpenInNew, "باز کردن") { openLink() }
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
