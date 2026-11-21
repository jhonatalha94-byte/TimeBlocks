package ma.jmal.timeblocks

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the single running time block: shows the countdown
 * notification, and rings + vibrates when the time is up.
 * The actual "time up" moment comes from an exact AlarmManager alarm, so it fires
 * even if Android kills this service in the meantime.
 */
class TimerService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_EXTEND = "extend"
        const val ACTION_DONE = "done"
        const val ACTION_CANCEL = "cancel"
        const val ACTION_RING = "ring"
        const val ACTION_REFRESH = "refresh"

        const val EXTRA_TITLE = "title"
        const val EXTRA_SEC = "sec"

        private const val RING_LIMIT_MS = 2 * 60_000L

        fun intent(c: Context, action: String, sec: Int = 0, title: String? = null, taskId: Long = -1L) =
            Intent(c, TimerService::class.java)
                .setAction(action)
                .putExtra(EXTRA_SEC, sec)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(Scheduler.EXTRA_TASK_ID, taskId)

        fun send(c: Context, action: String, sec: Int = 0, title: String? = null, taskId: Long = -1L) {
            ContextCompat.startForegroundService(c, intent(c, action, sec, title, taskId))
        }

        fun pending(
            c: Context, action: String, req: Int, sec: Int = 0, title: String? = null, taskId: Long = -1L,
        ): PendingIntent = PendingIntent.getForegroundService(
            c, req, intent(c, action, sec, title, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStopRing = Runnable { stopRing() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        var s = TimerStore.load(this)

        when (i?.action) {
            ACTION_START -> {
                val sec = i.getIntExtra(EXTRA_SEC, 0).coerceAtLeast(1)
                val title = i.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "مهمة"
                val taskId = i.getLongExtra(Scheduler.EXTRA_TASK_ID, -1L)
                if (taskId >= 0) Notifs.nm(this).cancel(Notifs.reminderId(taskId))
                stopRing()
                Notifs.nm(this).cancel(Notifs.ID_TIME_UP)
                s = TimerState(title, sec, now, now + sec * 1000L)
                Scheduler.scheduleTimeUp(this, s.endAt)
            }

            ACTION_PAUSE -> if (s != null && !s.isPaused && !s.ringing) {
                s = s.copy(pausedAt = now)
                Scheduler.cancelTimeUp(this)
            }

            ACTION_RESUME -> if (s != null && s.isPaused) {
                val d = now - s.pausedAt
                s = s.copy(endAt = s.endAt + d, pausedTotal = s.pausedTotal + d, pausedAt = 0L)
                Scheduler.scheduleTimeUp(this, s.endAt)
            }

            ACTION_EXTEND -> if (s != null) {
                val ms = i.getIntExtra(EXTRA_SEC, 0).coerceAtLeast(1) * 1000L
                stopRing()
                Notifs.nm(this).cancel(Notifs.ID_TIME_UP)
                s = if (s.isPaused) {
                    s.copy(endAt = s.endAt + ms, plannedSec = s.plannedSec + (ms / 1000).toInt())
                } else {
                    s.copy(
                        endAt = maxOf(s.endAt, now) + ms,
                        plannedSec = s.plannedSec + (ms / 1000).toInt(),
                        ringing = false,
                    ).also { Scheduler.scheduleTimeUp(this, it.endAt) }
                }
            }

            ACTION_RING -> if (s != null && !s.isPaused) {
                s = s.copy(ringing = true)
                startRing()
                Notifs.nm(this).notify(Notifs.ID_TIME_UP, Notifs.timeUp(this, s))
            }

            ACTION_DONE -> if (s != null) {
                val done = s
                App.io.launch {
                    AppDb.get(applicationContext).dao().insertLog(
                        LogEntry(
                            title = done.title,
                            plannedSec = done.plannedSec,
                            actualSec = done.actualSec(now),
                            startedAt = done.startedAt,
                            finishedAt = now,
                        ),
                    )
                }
                s = null
            }

            ACTION_CANCEL -> s = null
        }

        TimerStore.save(this, s)

        // Started with startForegroundService → must always call startForeground first.
        val shown = s ?: TimerState("…", 0, now, now)
        goForeground(Notifs.running(this, shown))

        if (s == null) {
            stopRing()
            Scheduler.cancelTimeUp(this)
            Notifs.nm(this).cancel(Notifs.ID_TIME_UP)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if (s.ringing && ringtone?.isPlaying != true && i?.action == ACTION_REFRESH) {
            Notifs.nm(this).notify(Notifs.ID_TIME_UP, Notifs.timeUp(this, s))
        }
        return START_NOT_STICKY
    }

    private fun goForeground(n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifs.ID_TIMER, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifs.ID_TIMER, n)
        }
    }

    private fun startRing() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        handler.removeCallbacks(autoStopRing)
        handler.postDelayed(autoStopRing, RING_LIMIT_MS)
    }

    private fun stopRing() {
        handler.removeCallbacks(autoStopRing)
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopRing()
        super.onDestroy()
    }
}
