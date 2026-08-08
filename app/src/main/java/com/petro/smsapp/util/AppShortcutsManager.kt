package com.petro.smsapp.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.petro.smsapp.MainActivity
import com.petro.smsapp.R
import com.petro.smsapp.data.AppContainer
import com.petro.smsapp.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدیریت شورتکات‌های پویای لانچر (لانگ‌کلیک روی آیکون اپ).
 * شورتکات‌ها بر اساس تنظیمات کاربر و لیست مخاطبین انتخاب‌شده ساخته/حذف می‌شوند.
 */
object AppShortcutsManager {

    const val ID_NEW_MESSAGE = "shortcut_new_message"
    const val ID_SETTINGS = "shortcut_settings"
    const val ID_CONTACT_PREFIX = "shortcut_contact_"

    const val ACTION_SHORTCUT = "com.petro.smsapp.action.SHORTCUT"
    const val EXTRA_SHORTCUT_TYPE = "extra_shortcut_type"
    const val TYPE_NEW_MESSAGE = "new_message"
    const val TYPE_SETTINGS = "settings"
    const val TYPE_CONTACT = "contact"
    const val EXTRA_ADDRESS = "extra_shortcut_address"
    const val EXTRA_DISPLAY_NAME = "extra_shortcut_display_name"

    /** پس‌زمینه دایره‌ای آیکن شورتکات - آبی متریال، روی تم روشن و تیره خوانا است */
    private const val SHORTCUT_BG_COLOR = 0xFF1A73E8.toInt()

    /**
     * بازسازی کامل شورتکات‌های پویا. روی IO صدا زده شود.
     * محدودیت سیستم (معمولاً ۴–۵) رعایت می‌شود: اولویت با پیام‌جدید و تنظیمات،
     * باقی ظرفیت برای مخاطبین (جدیدترین‌ها اول).
     */
    suspend fun updateShortcuts(context: Context) {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            try {
                val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(app).coerceAtLeast(1)
                val list = mutableListOf<ShortcutInfoCompat>()

                val newMsgOn = AppSettings.state.value.shortcutNewMessageEnabled
                val settingsOn = AppSettings.state.value.shortcutSettingsEnabled
                val contactsOn = AppSettings.state.value.shortcutContactsEnabled

                val iconMessage = createShortcutIcon(app, R.drawable.ic_message)
                val iconSettings = createShortcutIcon(app, R.drawable.ic_settings)

                if (newMsgOn) {
                    list += ShortcutInfoCompat.Builder(app, ID_NEW_MESSAGE)
                        .setShortLabel("پیام جدید")
                        .setLongLabel("پیام جدید")
                        .setIcon(iconMessage)
                        .setIntent(
                            Intent(app, MainActivity::class.java).apply {
                                action = ACTION_SHORTCUT
                                putExtra(EXTRA_SHORTCUT_TYPE, TYPE_NEW_MESSAGE)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        .setRank(0)
                        .build()
                }

                if (settingsOn) {
                    list += ShortcutInfoCompat.Builder(app, ID_SETTINGS)
                        .setShortLabel("تنظیمات")
                        .setLongLabel("تنظیمات")
                        .setIcon(iconSettings)
                        .setIntent(
                            Intent(app, MainActivity::class.java).apply {
                                action = ACTION_SHORTCUT
                                putExtra(EXTRA_SHORTCUT_TYPE, TYPE_SETTINGS)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        .setRank(1)
                        .build()
                }

                if (contactsOn) {
                    val remaining = (max - list.size).coerceAtLeast(0)
                    if (remaining > 0) {
                        val contacts = AppContainer.shortcutContactRepository(app).getAllOnce()
                            .take(remaining)
                        contacts.forEachIndexed { index, c ->
                            val id = ID_CONTACT_PREFIX + c.normalizedAddress
                            val label = c.displayName.ifBlank { c.address }
                            list += ShortcutInfoCompat.Builder(app, id)
                                .setShortLabel(label.take(25))
                                .setLongLabel(label)
                                .setIcon(iconMessage)
                                .setIntent(
                                    Intent(app, MainActivity::class.java).apply {
                                        action = ACTION_SHORTCUT
                                        putExtra(EXTRA_SHORTCUT_TYPE, TYPE_CONTACT)
                                        putExtra(EXTRA_ADDRESS, c.address)
                                        putExtra(EXTRA_DISPLAY_NAME, c.displayName)
                                        data = Uri.parse("smsapp://shortcut/contact/${Uri.encode(c.address)}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                .setRank(10 + index)
                                .build()
                        }
                    }
                }

                ShortcutManagerCompat.removeAllDynamicShortcuts(app)
                if (list.isNotEmpty()) {
                    ShortcutManagerCompat.setDynamicShortcuts(app, list.take(max))
                }
                Unit
            } catch (e: Exception) {
                android.util.Log.w("AppShortcutsManager", "updateShortcuts failed", e)
            }
        }
    }

    /**
     * آیکن شورتکات با پس‌زمینه رنگی یکدست + علامت سفید.
     * وکتورهای معمولی با tint سفید روی لانچر اغلب فقط «دایره سفید خالی» دیده می‌شوند.
     * با Adaptive Bitmap هم روی تم روشن و هم تیره خوانا می‌ماند.
     */
    private fun createShortcutIcon(context: Context, glyphRes: Int): IconCompat {
        val density = context.resources.displayMetrics.density
        // 108dp برای adaptive icon
        val size = (108f * density).toInt().coerceAtLeast(108)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SHORTCUT_BG_COLOR
            style = Paint.Style.FILL
        }
        val cx = size / 2f
        canvas.drawCircle(cx, cx, cx, bgPaint)

        val glyph: Drawable? = ContextCompat.getDrawable(context, glyphRes)?.mutate()
        if (glyph != null) {
            glyph.setTint(Color.WHITE)
            // safe zone حدود ۲۲٪ از هر طرف در adaptive icon
            val inset = (size * 0.25f).toInt()
            glyph.setBounds(inset, inset, size - inset, size - inset)
            glyph.draw(canvas)
        }

        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }
}
