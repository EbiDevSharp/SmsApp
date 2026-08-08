package com.petro.smsapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalContext
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.ThemeMode
import kotlin.math.hypot

private val Blue = Color(0xFF2F7BFF)
private val BlueDark = Color(0xFF0B5CFF)

private val AppLightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,

    secondary = BlueDark,
    onSecondary = Color.White,

    background = Color(0xFFF7F8FA),
    onBackground = Color.Black,

    surface = Color.White,
    onSurface = Color.Black,

    surfaceVariant = Color(0xFFEDEFF2),
    onSurfaceVariant = Color(0xFF424242),

    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color.Black,

    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color.Black,

    error = Color(0xFFB3261E),
    onError = Color.White
)

private val AppDarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,

    secondary = BlueDark,
    onSecondary = Color.White,

    background = Color(0xFF121212),
    onBackground = Color.White,

    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,

    surfaceVariant = Color(0xFF2A2A2C),
    onSurfaceVariant = Color.White,

    primaryContainer = Color(0xFF1D3F73),
    onPrimaryContainer = Color.White,

    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color.White,

    error = Color(0xFFFFB4AB),
    onError = Color.Black
)

/** رنگ پس‌زمینهٔ تم برای overlay انیمیشن دایره‌ای */
internal fun themeBackgroundColor(dark: Boolean): Color =
    if (dark) AppDarkColors.background else AppLightColors.background

/**
 * کنترلر انیمیشن reveal تم (شبیه تلگرام).
 * بدون recreate و بدون bitmap — فقط یک Canvas سبک با سوراخ دایره‌ای.
 *
 * منطق:
 * 1) تم جدید همان اول اعمال می‌شود (محتوای واقعی زیر overlay با رنگ‌های جدید است)
 * 2) روی کل صفحه رنگ پس‌زمینهٔ تمِ قبلی کشیده می‌شود با یک «سوراخ» دایره‌ای در حال بزرگ شدن
 * 3) داخل دایره محتوای واقعی زیرش دیده می‌شود؛ بیرون هنوز رنگ تم قدیم است
 */
object ThemeRevealController {
    data class Request(
        val center: Offset,
        /** رنگ پس‌زمینهٔ تمِ قبلی — بیرون دایره با این رنگ پوشانده می‌شود */
        val oldBackground: Color,
        val applyMode: ThemeMode
    )

    var pending by mutableStateOf<Request?>(null)
        private set

    fun request(center: Offset, oldBackground: Color, applyMode: ThemeMode) {
        // اگر انیمیشن قبلی هنوز تمام نشده، درخواست جدید را نادیده بگیر
        if (pending != null) return
        pending = Request(center, oldBackground, applyMode)
    }

    internal fun clear() {
        pending = null
    }
}

@Composable
fun SmsAppTheme(
    content: @Composable () -> Unit
) {
    val settings by AppSettings.state.collectAsState()
    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) AppDarkColors else AppLightColors
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
            ThemeCircularRevealOverlay()
        }
    }
}

/**
 * انیمیشن دایره‌ای reveal:
 * تم همان لحظه عوض می‌شود؛ overlay فقط رنگ تم قدیم را بیرونِ دایره نگه می‌دارد
 * تا داخل دایره محتوای واقعی (با تم جدید) پیدا باشد.
 */
@Composable
private fun ThemeCircularRevealOverlay() {
    val request = ThemeRevealController.pending ?: return
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }

    LaunchedEffect(request) {
        // اول تم جدید را اعمال کن تا زیر سوراخ، UI واقعی با رنگ‌های جدید باشد
        AppSettings.setThemeMode(context, request.applyMode)
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
        ThemeRevealController.clear()
    }

    Canvas(Modifier.fillMaxSize()) {
        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 1.05f
        val radius = maxRadius * progress.value

        // مستطیل کامل + دایره به‌عنوان سوراخ (EvenOdd) → داخل دایره شفاف است و زیرش دیده می‌شود
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
            addOval(
                Rect(
                    left = request.center.x - radius,
                    top = request.center.y - radius,
                    right = request.center.x + radius,
                    bottom = request.center.y + radius
                )
            )
        }
        drawPath(path = path, color = request.oldBackground)
    }
}

/**
 * محاسبهٔ حالت تاریک مؤثر برای یک ThemeMode.
 */
fun ThemeMode.isEffectivelyDark(isSystemDark: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemDark
}
