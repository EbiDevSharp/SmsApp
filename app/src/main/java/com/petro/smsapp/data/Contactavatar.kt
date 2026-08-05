package com.petro.smsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.petro.smsapp.data.ContactsCache

@Composable
fun ContactAvatar(
    name: String,
    address: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    backgroundColor: Color =
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
) {
    val context = LocalContext.current

    /*
     * وقتی ContactsCache در Background کامل شد،
     * این مقدار تغییر می‌کند و Composable دوباره اجرا می‌شود.
     */
    val cacheVersion by ContactsCache.version.collectAsState()

    /*
     * برای جلوگیری از warning مربوط به unused variable
     * و اینکه Compose dependency آن را متوجه شود.
     */
    @Suppress("UNUSED_VARIABLE")
    val currentVersion = cacheVersion

    /*
     * این بار getPhotoUri فقط از HashMap می‌خواند.
     * دیگر ContentResolver روی Main Thread اجرا نمی‌شود.
     */
    val photoUri = remember(
        address,
        cacheVersion
    ) {
        ContactsCache.getPhotoUri(
            context = context,
            address = address
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {

        /*
         * همیشه fallback زیر عکس وجود دارد.
         */
        InitialCircle(
            name = name,
            size = size,
            backgroundColor = backgroundColor
        )

        if (photoUri != null) {

            var loaded by remember(photoUri) {
                mutableStateOf(false)
            }

            AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,

                modifier = Modifier
                    .matchParentSize()
                    .alpha(
                        if (loaded) {
                            1f
                        } else {
                            0f
                        }
                    ),

                onState = { state ->
                    loaded =
                        state is AsyncImagePainter.State.Success
                }
            )
        }
    }
}

@Composable
private fun InitialCircle(
    name: String,
    size: Dp,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val initial =
        name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}