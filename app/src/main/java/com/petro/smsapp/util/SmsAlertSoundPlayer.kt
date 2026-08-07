package com.petro.smsapp.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

/**
 * پخش‌کننده‌ی مرکزیِ صدای هشدارِ پیامکِ تازه‌رسیده - مشترک بینِ SmsDeliverReceiver
 * (پاپ‌آپِ صفحه‌قفل) و PopupOverlayService (پاپ‌آپِ Overlay).
 *
 * نکته‌ی مهم (رفعِ باگ): قبلاً هر مسیر مستقل یه Ringtone جدید می‌ساخت و play()
 * می‌کرد. وقتی چند پیامک نزدیک به هم می‌رسیدن (حتی از فرستنده‌های مختلف، یکی از
 * مسیرِ Overlay و یکی از مسیرِ صفحه‌قفل)، چند Ringtone هم‌زمان روی هم پخش
 * می‌شدن و صدا قاطی/چندصدایی می‌شد.
 *
 * الان همیشه فقط یک Ringtone در آنِ واحد فعاله (چه از قفل بیاد چه از Overlay) -
 * قبل از پخشِ صدای تازه، صدای قبلی (اگه هنوز داشت پخش می‌شد) متوقف میشه.
 */
object SmsAlertSoundPlayer {

    @Volatile
    private var activeRingtone: Ringtone? = null

    /**
     * @param context کانتکستِ صداکننده (Receiver یا Service)
     * @param channelSoundUri صدایی که از خودِ NotificationChannel خونده شده (اگه
     * موجود بود)؛ اگه null بود، از صدای پیش‌فرضِ نوتیفِ سیستم استفاده میشه.
     */
    @Synchronized
    fun play(context: Context, channelSoundUri: Uri? = null) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

            val soundUri = channelSoundUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return

            // صدای قبلی (اگه هنوز پخش می‌شد) رو متوقف کن تا روی صدای جدید سوار نشه
            activeRingtone?.let { previous ->
                try {
                    if (previous.isPlaying) previous.stop()
                } catch (_: Exception) {
                    // مشکلی در توقفِ صدای قبلی پیش اومد - نباید مانعِ پخشِ صدای جدید بشه
                }
            }

            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            if (ringtone == null) {
                activeRingtone = null
                return
            }
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            activeRingtone = ringtone
            ringtone.play()
        } catch (_: Exception) {
            // خطای پخش صدا نباید مانع نمایش پاپ‌آپ/نوتیف بشه
        }
    }
}
