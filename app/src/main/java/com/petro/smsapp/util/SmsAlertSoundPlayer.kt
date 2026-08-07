package com.petro.smsapp.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * پخش‌کننده‌ی مرکزی صدای هشدار پیامک - مشترک بین SmsDeliverReceiver
 * (پاپ‌آپ صفحه‌قفل) و PopupOverlayService (پاپ‌آپ Overlay).
 *
 * قوانین:
 * 1) در هر لحظه حداکثر یک پخش فعال است.
 * 2) اگر play() دوباره در کمتر از [DEBOUNCE_MS] صدا زده شود، نادیده گرفته می‌شود
 *    تا صدای دو بار (race بین مسیرها یا دو بار فراخوانی) رخ ندهد.
 * 3) از MediaPlayer به‌جای Ringtone تا روی بعضی OEMها رفتار دوگانه نباشد.
 * 4) اگر کانال نوتیف واقعاً بی‌صدا باشد (sound == null)، چیزی پخش نمی‌شود
 *    (دیگر به پیش‌فرض سیستم fallback نمی‌کنیم).
 */
object SmsAlertSoundPlayer {

    private const val TAG = "SmsAlertSoundPlayer"
    private const val DEBOUNCE_MS = 900L
    private const val SMS_CHANNEL_ID = "sms_channel"

    @Volatile
    private var activePlayer: MediaPlayer? = null

    @Volatile
    private var lastPlayAtMs: Long = 0L

    /**
     * پخش صدای کانال sms_channel (تنظیمات کاربر در سیستم).
     * اگر کانال بی‌صدا باشد یا رینگر عادی نباشد، هیچی پخش نمی‌شود.
     */
    @Synchronized
    fun playFromSmsChannel(context: Context) {
        val soundUri = resolveSmsChannelSound(context) ?: return
        playUri(context, soundUri)
    }

    /**
     * @param channelSoundUri اگر non-null باشد همان پخش می‌شود؛
     * اگر null باشد و [fallbackToDefault] true باشد، صدای پیش‌فرض نوتیف سیستم.
     */
    @Synchronized
    fun play(context: Context, channelSoundUri: Uri? = null, fallbackToDefault: Boolean = true) {
        val soundUri = channelSoundUri
            ?: if (fallbackToDefault) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            } else null
                ?: return
        playUri(context, soundUri)
    }

    private fun resolveSmsChannelSound(context: Context): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel: NotificationChannel? =
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.getNotificationChannel(SMS_CHANNEL_ID)
            if (channel != null) {
                // کانال وجود دارد: اگر sound==null یعنی کاربر/سیستم صدا را خاموش کرده
                return channel.sound
            }
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun playUri(context: Context, soundUri: Uri) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastPlayAtMs < DEBOUNCE_MS) {
                Log.d(TAG, "play debounced (${now - lastPlayAtMs}ms since last)")
                return
            }

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

            stopInternal()

            val appContext = context.applicationContext
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(appContext, soundUri)
                player.setOnCompletionListener { mp ->
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (activePlayer === mp) activePlayer = null
                }
                player.setOnErrorListener { mp, _, _ ->
                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }
                    if (activePlayer === mp) activePlayer = null
                    true
                }
                player.prepare()
                activePlayer = player
                lastPlayAtMs = now
                player.start()
            } catch (e: Exception) {
                try {
                    player.release()
                } catch (_: Exception) {
                }
                if (activePlayer === player) activePlayer = null
                Log.w(TAG, "failed to play alert sound", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "playUri() failed", e)
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        activePlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
        activePlayer = null
    }
}
