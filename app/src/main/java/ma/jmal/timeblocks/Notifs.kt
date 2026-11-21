package ma.jmal.timeblocks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

object Notifs {
    const val CH_REMINDER = "reminders"
    const val CH_TIMER = "timer"
    const val CH_TIME_UP = "time_up"

    const val ID_TIMER = 1
    const val ID_TIME_UP = 2
    fun reminderId(taskId: Long) = 1000 + taskId.toInt()

    /** Quick durations offered as notification buttons on a reminder. */
    private val QUICK = listOf(5 * 60, 10 * 60, 25 * 60)

    fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    fun createChannels(c: Context) {
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val reminder = NotificationChannel(CH_REMINDER, "تذكير بالمهام", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), alarmAttrs)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val timer = NotificationChannel(CH_TIMER, "العدّ التنازلي", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Sound and vibration for time-up are played by TimerService (looping), not by the channel.
        val timeUp = NotificationChannel(CH_TIME_UP, "انتهاء الوقت", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm(c).createNotificationChannels(listOf(reminder, timer, timeUp))
    }

    private fun activityPi(c: Context, req: Int, intent: Intent) = PendingIntent.getActivity(
        c, req, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun showReminder(c: Context, t: TaskItem) {
        val open = Intent(c, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(ReminderActivity.EXTRA_TITLE, t.title)
            .putExtra(Scheduler.EXTRA_TASK_ID, t.id)
        val pi = activityPi(c, reminderId(t.id), open)
        val b = NotificationCompat.Builder(c, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("حان وقت: ${t.title}")
            .setContentText("اختر مدة لتبدأ مكعب الوقت")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
        QUICK.forEachIndexed { i, sec ->
            b.addAction(
                0, formatDuration(sec),
                TimerService.pending(c, TimerService.ACTION_START, (t.id * 10 + i).toInt() + 50_000, sec, t.title, t.id),
            )
        }
        nm(c).notify(reminderId(t.id), b.build())
    }

    fun running(c: Context, s: TimerState): Notification {
        val open = activityPi(c, 1, Intent(c, MainActivity::class.java))
        val b = NotificationCompat.Builder(c, CH_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(s.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
        when {
            s.ringing -> b.setContentText("انتهى الوقت!")
            s.isPaused -> {
                b.setContentText("متوقف مؤقتًا — باقي ${formatClock(s.remainingMs() / 1000)}")
                b.addAction(0, "متابعة", TimerService.pending(c, TimerService.ACTION_RESUME, 11))
            }
            else -> {
                b.setContentText("المدة: ${formatDuration(s.plannedSec)}")
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
                    .setWhen(s.endAt)
                b.addAction(0, "إيقاف مؤقت", TimerService.pending(c, TimerService.ACTION_PAUSE, 12))
            }
        }
        b.addAction(0, "إنهاء الآن", TimerService.pending(c, TimerService.ACTION_DONE, 13))
        return b.build()
    }

    fun timeUp(c: Context, s: TimerState): Notification {
        val open = Intent(c, TimeUpActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pi = activityPi(c, 2, open)
        return NotificationCompat.Builder(c, CH_TIME_UP)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("انتهى الوقت: ${s.title}")
            .setContentText("هل أنجزت المهمة؟")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .addAction(0, "تمّت ✓", TimerService.pending(c, TimerService.ACTION_DONE, 21))
            .addAction(0, "+5 د", TimerService.pending(c, TimerService.ACTION_EXTEND, 22, 5 * 60))
            .addAction(0, "+10 د", TimerService.pending(c, TimerService.ACTION_EXTEND, 23, 10 * 60))
            .build()
    }
}
