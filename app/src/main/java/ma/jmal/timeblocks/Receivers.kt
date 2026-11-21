package ma.jmal.timeblocks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** Fires for a scheduled task reminder, or when the running block's time is up. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Scheduler.ACTION_TIME_UP -> TimerService.send(c, TimerService.ACTION_RING)

            Scheduler.ACTION_TASK -> {
                val id = i.getLongExtra(Scheduler.EXTRA_TASK_ID, -1L)
                if (id < 0) return
                val pending = goAsync()
                App.io.launch {
                    try {
                        val dao = AppDb.get(c).dao()
                        val t = dao.task(id)
                        if (t != null && t.enabled) {
                            Notifs.showReminder(c, t)
                            if (t.repeat == TaskItem.REPEAT_ONCE) dao.update(t.copy(enabled = false))
                            else Scheduler.schedule(c, t)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

/** Alarms are wiped on reboot / time change — put them all back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pending = goAsync()
        App.io.launch {
            try {
                Scheduler.rescheduleAll(c)
                val s = TimerStore.load(c)
                if (s != null && !s.isPaused) {
                    if (s.ringing || s.endAt <= System.currentTimeMillis()) {
                        TimerService.send(c, TimerService.ACTION_RING)
                    } else {
                        Scheduler.scheduleTimeUp(c, s.endAt)
                        TimerService.send(c, TimerService.ACTION_REFRESH)
                    }
                } else if (s != null) {
                    TimerService.send(c, TimerService.ACTION_REFRESH)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
