package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petro.smsapp.data.ContactsCache

/**
 * آواتارِ مشترکِ نمایشِ مخاطب برای کل برنامه (لیست مکالمات، بالای صفحه‌ی چت و ...).
 *
 * اگه address واقعاً متعلق به یه مخاطبِ ذخیره‌شده تو گوشی باشه *و* اون مخاطب عکسِ
 * پروفایل داشته باشه (ContactsCache.getPhotoUri)، همون عکس به‌شکلِ دایره نشون داده
 * میشه. وگرنه (مخاطب ناشناسه، یا مخاطب هست ولی عکس نداره) دقیقاً همون رفتارِ قبلیِ
 * برنامه ادامه پیدا می‌کنه: یه دایره‌ی رنگی با حرفِ اولِ اسم/شماره.
 *
 * قبلاً هیچ‌جای برنامه - نه لیستِ اصلیِ مکالمات، نه بالای صفحه‌ی چت - عکسِ واقعیِ
 * مخاطب نشون داده نمی‌شد، حتی برای مخاطبینی که تو گوشی عکسِ پروفایل داشتن.
 */
@Composable
fun ContactAvatar(
    name: String,
    address: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
) {
    val context = LocalContext.current
    // address به‌ندرت عوض میشه (هر آیتم مالِ یه مکالمه/مخاطبِ ثابته)، پس فقط با تغییرِ
    // خودِ address دوباره از کش (که خودش O(1) هست) خونده میشه، نه هر recomposition
    val photoUri = remember(address) { ContactsCache.getPhotoUri(context, address) }

    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}