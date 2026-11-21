package ma.jmal.timeblocks

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
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
        const val ACTION_AMBIENT = "ambient"

        const val EXTRA_TITLE = "title"
        const val EXTRA_SEC = "sec"
        /** Ambient sound id; null or "" = default from settings. */
        const val EXTRA_AMBIENT = "ambient"

        fun intent(
            c: Context, action: String, sec: Int = 0, title: String? = null, taskId: Long = -1L,
            ambient: String? = null,
        ) = Intent(c, TimerService::class.java)
            .setAction(action)
            .putExtra(EXTRA_SEC, sec)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(Scheduler.EXTRA_TASK_ID, taskId)
            .putExtra(EXTRA_AMBIENT, ambient)

        fun send(
            c: Context, action: String, sec: Int = 0, title: String? = null, taskId: Long = -1L,
            ambient: String? = null,
        ) {
            ContextCompat.startForegroundService(c, intent(c, action, sec, title, taskId, ambient))
        }

        fun pending(
            c: Context, action: String, req: Int, sec: Int = 0, title: String? = null, taskId: Long = -1L,
            ambient: String? = null,
        ): PendingIntent = PendingIntent.getForegroundService(
            c, req, intent(c, action, sec, title, taskId, ambient),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private var ringPlayer: MediaPlayer? = null
    private var ambientPlayer: MediaPlayer? = null
    private var ambientId: String? = null
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
                val amb = i.getStringExtra(EXTRA_AMBIENT)?.takeIf { it.isNotBlank() } ?: Prefs.ambient(this)
                s = TimerState(title, sec, now, now + sec * 1000L, ambient = amb)
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

            ACTION_AMBIENT -> if (s != null) {
                s = s.copy(ambient = i.getStringExtra(EXTRA_AMBIENT)?.takeIf { it.isNotBlank() } ?: Sounds.NONE)
            }
        }

        TimerStore.save(this, s)

        // Started with startForegroundService → must always call startForeground first.
        val shown = s ?: TimerState("…", 0, now, now)
        goForeground(Notifs.running(this, shown))

        syncAmbient(s)

        if (s == null) {
            stopRing()
            Scheduler.cancelTimeUp(this)
            Notifs.nm(this).cancel(Notifs.ID_TIME_UP)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if (s.ringing && ringPlayer?.isPlaying != true && i?.action == ACTION_REFRESH) {
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
        if (ringPlayer?.isPlaying == true) return
        if (Prefs.fixVolume(this)) Sounds.ensureAlarmAudible(this)
        ringPlayer = Sounds.playAlarm(this, Prefs.alarm(this), loop = true)
        if (Prefs.vibrate(this)) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        }
        handler.removeCallbacks(autoStopRing)
        handler.postDelayed(autoStopRing, Prefs.ringMinutes(this) * 60_000L)
    }

    private fun stopRing() {
        handler.removeCallbacks(autoStopRing)
        release(ringPlayer)
        ringPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun release(p: MediaPlayer?) {
        if (p == null) return
        try {
            p.stop()
        } catch (_: Exception) {
        }
        p.release()
    }

    /** Background sound plays only while the block is running (not paused, not ringing). */
    private fun syncAmbient(s: TimerState?) {
        val want = s?.takeIf { !it.isPaused && !it.ringing }?.ambient?.takeIf { it != Sounds.NONE }
        if (want == null) {
            release(ambientPlayer); ambientPlayer = null; ambientId = null
            return
        }
        if (want == ambientId && ambientPlayer?.isPlaying == true) {
            val v = Prefs.ambientVolume(this) / 100f
            ambientPlayer?.setVolume(v, v)
            return
        }
        release(ambientPlayer)
        ambientPlayer = Sounds.ambientUri(this, want)?.let {
            Sounds.play(this, it, alarm = false, loop = true, volume = Prefs.ambientVolume(this) / 100f)
        }
        ambientId = want
    }

    override fun onDestroy() {
        stopRing()
        release(ambientPlayer)
        ambientPlayer = null
        super.onDestroy()
    }
}
