package ma.jmal.timeblocks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    const val ACTION_TASK = "ma.jmal.timeblocks.action.TASK"
    const val ACTION_TIME_UP = "ma.jmal.timeblocks.action.TIME_UP"
    const val EXTRA_TASK_ID = "taskId"
    private const val REQ_TIME_UP = 999_999

    /** Next trigger time after [now], or null if a weekly task has no days selected. */
    fun nextTrigger(t: TaskItem, now: Long = System.currentTimeMillis()): Long? {
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, t.hour)
            set(Calendar.MINUTE, t.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            val c = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            if (c.timeInMillis <= now) continue
            if (t.repeat != TaskItem.REPEAT_WEEKLY) return c.timeInMillis
            val dow = c.get(Calendar.DAY_OF_WEEK)
            if (t.daysMask and (1 shl (dow - 1)) != 0) return c.timeInMillis
        }
        return null
    }

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun taskPi(c: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        c, id.toInt(),
        Intent(c, AlarmReceiver::class.java).setAction(ACTION_TASK).putExtra(EXTRA_TASK_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun timeUpPi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, REQ_TIME_UP,
        Intent(c, AlarmReceiver::class.java).setAction(ACTION_TIME_UP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    private fun setExact(c: Context, at: Long, pi: PendingIntent) {
        val am = am(c)
        if (canExact(c)) {
            val show = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun schedule(c: Context, t: TaskItem) {
        cancel(c, t.id)
        if (!t.enabled) return
        val at = nextTrigger(t) ?: return
        setExact(c, at, taskPi(c, t.id))
    }

    fun cancel(c: Context, id: Long) = am(c).cancel(taskPi(c, id))

    fun scheduleTimeUp(c: Context, at: Long) = setExact(c, at, timeUpPi(c))

    fun cancelTimeUp(c: Context) = am(c).cancel(timeUpPi(c))

    suspend fun rescheduleAll(c: Context) {
        AppDb.get(c).dao().enabledTasks().forEach { schedule(c, it) }
    }
}
