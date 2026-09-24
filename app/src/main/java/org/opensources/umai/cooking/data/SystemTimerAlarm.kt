package org.opensources.umai.cooking.data

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.opensources.umai.cooking.domain.TimerAlarm

/**
 * Rings with the alarm sound of the device and vibrates, as an alarm clock
 * would: both follow the alarm volume and settings, and loop until stopped.
 *
 * The device may have been woken up just for this: a wake lock keeps it
 * awake while the alarm rings, so the ringing is also stopped on time.
 */
class SystemTimerAlarm(context: Context) : TimerAlarm {

    private val appContext = context.applicationContext
    private val vibrator: Vibrator = appContext.getSystemService(VibratorManager::class.java).defaultVibrator
    private val wakeLock: PowerManager.WakeLock = appContext.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        .apply { setReferenceCounted(false) }
    private var ringtone: Ringtone? = null

    override fun start(sound: Boolean, vibrate: Boolean) {
        stop()
        wakeLock.acquire(CookingTimerController.MAX_RING_MILLIS + WAKE_LOCK_MARGIN_MILLIS)
        if (vibrate && vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        }
        if (sound) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = uri?.let { RingtoneManager.getRingtone(appContext, it) }?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }
    }

    override fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator.cancel()
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        /** Off, on, off, on, in milliseconds, repeated from the start. */
        val VIBRATION_PATTERN = longArrayOf(0, 600, 400, 600)

        const val WAKE_LOCK_TAG = "umai:cooking-timer-alarm"

        /** Leaves the controller time to stop the alarm itself before the lock lapses. */
        const val WAKE_LOCK_MARGIN_MILLIS = 10_000L
    }
}
