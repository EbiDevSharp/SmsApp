package com.petro.smsapp.ui

import android.graphics.Color as AndroidColor
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.FavoriteMessage
import com.petro.smsapp.data.MessageTemplate
import com.petro.smsapp.util.DateFormatter
import com.petro.smsapp.util.autoDirection
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val GlassShape = RoundedCornerShape(22.dp)
private val GlassMaxHeight = 340.dp
private val GlassEditorMaxHeight = 460.dp
private const val GlassScrimDim = 0.22f
private const val GlassBackdropBlurRadiusPx = 32f

/**
 * پوسته‌ی مشترک مودال‌های InputBar با ظاهر Frosted Glass.
 * بلور فقط روی لایه‌ی Backdrop؛ خودِ محتوای مودال تار نمی‌شود.
 */
@Composable
private fun FrostedGlassDialog(
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.BottomCenter,
    maxHeight: Dp = GlassMaxHeight,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window.setDimAmount(GlassScrimDim)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(0x33FFFFFF)
                            setRenderEffect(
                                RenderEffect.createBlurEffect(
                                    GlassBackdropBlurRadiusPx,
                                    GlassBackdropBlurRadiusPx,
                                    Shader.TileMode.CLAMP
                                )
                            )
                            isClickable = false
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                contentAlignment = alignment
            ) {
                val glassColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)

                Surface(
                    shape = GlassShape,
                    color = glassColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .border(1.dp, borderColor, GlassShape)
                        .clip(GlassShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {}
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * لانگ‌پرس + نگه‌داشتن. ردیف در composition می‌ماند تا رویدادِ رها شدن قطع نشود.
 */
private fun Modifier.longPressHold(
    key: Any? = Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            waitForUpOrCancellation()
        } == null
        if (longPressed) {
            onHoldStart()
            try {
                waitForUpOrCancellation()
            } finally {
                onHoldEnd()
            }
        }
    }
}

@Composable
private fun FullTextPreviewOverlay(
    text: String,
    title: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(bottom = 4.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.autoDirection(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        val scroll = rememberScrollState()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.autoDirection(),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        Text(
            text = "انگشت را رها کنید تا بسته شود",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 12.dp)
        )
    }
}

/**
 * مودال علاقه‌مندی‌ها.
 * لانگ‌کلیک = پیش‌نمایش تمام‌متن (overlay داخل همان کارت)؛ رها کردن انگشت = بستن.
 */
@Composable
fun FavoritePickerSheet(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AppContainer.favoriteRepository(context) }
    val favorites by repo.observeFavorites().collectAsState(initial = emptyList())
    var previewBody by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf<String?>(null) }

    FrostedGlassDialog(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = GlassMaxHeight)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "علاقه‌مندی‌ها",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن", modifier = Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                if (favorites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "هنوز پیام علاقه‌مندی‌ای ندارید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(favorites, key = { it.messageId }) { fav ->
                            FavoritePickerRow(
                                favorite = fav,
                                onClick = {
                                    onSelect(fav.body)
                                    onDismiss()
                                },
                                onLongPressHoldStart = {
                                    previewTitle = fav.displayName.ifBlank { fav.address }
                                    previewBody = fav.body
                                },
                                onLongPressHoldEnd = {
                                    previewBody = null
                                    previewTitle = null
                                }
                            )
                        }
                    }
                }
            }

            // overlay روی لیست؛ ردیف زیرش در composition می‌ماند تا up را بگیرد
            val body = previewBody
            if (body != null) {
                FullTextPreviewOverlay(
                    text = body,
                    title = previewTitle,
                    // pointerInput خالی: hit-test می‌شود ولی رویداد را consume نمی‌کند
                    // تا waitForUpOrCancellation ردیف زیر همچنان up را ببیند (Pass.Final)
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(body) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    if (event.changes.all { !it.pressed }) {
                                        previewBody = null
                                        previewTitle = null
                                        break
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun FavoritePickerRow(
    favorite: FavoriteMessage,
    onClick: () -> Unit,
    onLongPressHoldStart: () -> Unit,
    onLongPressHoldEnd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .longPressHold(
                key = favorite.messageId,
                onHoldStart = onLongPressHoldStart,
                onHoldEnd = onLongPressHoldEnd
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = favorite.displayName.ifBlank { favorite.address },
                style = MaterialTheme.typography.labelLarge.autoDirection(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = DateFormatter.formatFull(favorite.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = favorite.body,
            style = MaterialTheme.typography.bodyMedium.autoDirection(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * مودال تمپلیت‌ها + لانگ‌کلیک پیش‌نمایش تمام‌متن.
 */
@Composable
fun TemplateManagerSheet(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { AppContainer.templateRepository(context) }
    val scope = rememberCoroutineScope()
    val templates by repo.observeTemplates().collectAsState(initial = emptyList())

    var editorTarget by remember { mutableStateOf<MessageTemplate?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MessageTemplate?>(null) }
    var previewBody by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf<String?>(null) }

    if (showCreate || editorTarget != null) {
        TemplateEditorGlass(
            initial = editorTarget,
            onDismiss = {
                showCreate = false
                editorTarget = null
            },
            onSave = { title, body ->
                scope.launch {
                    val existing = editorTarget
                    if (existing != null) {
                        repo.update(existing.id, title, body)
                    } else {
                        repo.add(title, body)
                    }
                    showCreate = false
                    editorTarget = null
                }
            }
        )
    }

    pendingDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف تمپلیت") },
            text = { Text("تمپلیت «${t.title}» حذف شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.delete(t.id)
                        pendingDelete = null
                    }
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("انصراف") }
            }
        )
    }

    FrostedGlassDialog(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = GlassMaxHeight)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تمپلیت‌ها",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                    )
                    IconButton(
                        onClick = { showCreate = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "ساخت تمپلیت", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن", modifier = Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                if (templates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "هنوز تمپلیتی ندارید",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            FilledTonalButton(onClick = { showCreate = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ساخت تمپلیت")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(templates, key = { it.id }) { template ->
                            TemplateRow(
                                template = template,
                                onClick = {
                                    onSelect(template.body)
                                    onDismiss()
                                },
                                onEdit = { editorTarget = template },
                                onDelete = { pendingDelete = template },
                                onLongPressHoldStart = {
                                    previewTitle = template.title.ifBlank { "تمپلیت" }
                                    previewBody = template.body
                                },
                                onLongPressHoldEnd = {
                                    previewBody = null
                                    previewTitle = null
                                }
                            )
                        }
                    }
                }
            }

            val body = previewBody
            if (body != null) {
                FullTextPreviewOverlay(
                    text = body,
                    title = previewTitle,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(body) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    if (event.changes.all { !it.pressed }) {
                                        previewBody = null
                                        previewTitle = null
                                        break
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: MessageTemplate,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongPressHoldStart: () -> Unit,
    onLongPressHoldEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressHold(
                key = template.id,
                onHoldStart = onLongPressHoldStart,
                onHoldEnd = onLongPressHoldEnd
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.title.ifBlank { "بدون عنوان" },
                style = MaterialTheme.typography.titleSmall.autoDirection(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = template.body,
                style = MaterialTheme.typography.bodyMedium.autoDirection(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "ویرایش",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "حذف",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

/**
 * ساخت / ویرایش تمپلیت با ظاهر Frosted Glass.
 * فیلدهای ورودی با [autoDirection] از Textstyleextensions.
 */
@Composable
private fun TemplateEditorGlass(
    initial: MessageTemplate?,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var body by remember(initial) { mutableStateOf(initial?.body ?: "") }
    val fieldStyle = LocalTextStyle.current.autoDirection()

    FrostedGlassDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxHeight = GlassEditorMaxHeight
    ) {
        Text(
            text = if (initial == null) "تمپلیت جدید" else "ویرایش تمپلیت",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان") },
                singleLine = true,
                textStyle = fieldStyle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("متن") },
                minLines = 4,
                maxLines = 8,
                textStyle = fieldStyle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("انصراف") }
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = {
                    if (body.isNotBlank()) onSave(title, body)
                },
                enabled = body.isNotBlank()
            ) { Text("ذخیره") }
        }
    }
}
