package ma.jmal.timeblocks

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The single running time block. Persisted so it survives process death and reboots. */
data class TimerState(
    val title: String,
    val plannedSec: Int,
    val startedAt: Long,
    val endAt: Long,
    val pausedAt: Long = 0L,
    val pausedTotal: Long = 0L,
    val ringing: Boolean = false,
) {
    val isPaused get() = pausedAt > 0L

    fun remainingMs(now: Long = System.currentTimeMillis()): Long =
        (if (isPaused) endAt - pausedAt else endAt - now).coerceAtLeast(0L)

    fun actualSec(now: Long = System.currentTimeMillis()): Int {
        val stop = if (isPaused) pausedAt else now
        return ((stop - startedAt - pausedTotal) / 1000L).toInt().coerceAtLeast(0)
    }
}

object TimerStore {
    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state

    private fun prefs(c: Context) = c.getSharedPreferences("timer", Context.MODE_PRIVATE)

    fun init(c: Context) {
        _state.value = load(c)
    }

    fun load(c: Context): TimerState? {
        val p = prefs(c)
        if (!p.getBoolean("active", false)) return null
        return TimerState(
            title = p.getString("title", "") ?: "",
            plannedSec = p.getInt("planned", 0),
            startedAt = p.getLong("started", 0L),
            endAt = p.getLong("end", 0L),
            pausedAt = p.getLong("pausedAt", 0L),
            pausedTotal = p.getLong("pausedTotal", 0L),
            ringing = p.getBoolean("ringing", false),
        )
    }

    fun save(c: Context, s: TimerState?) {
        val e = prefs(c).edit().clear()
        if (s != null) {
            e.putBoolean("active", true)
                .putString("title", s.title)
                .putInt("planned", s.plannedSec)
                .putLong("started", s.startedAt)
                .putLong("end", s.endAt)
                .putLong("pausedAt", s.pausedAt)
                .putLong("pausedTotal", s.pausedTotal)
                .putBoolean("ringing", s.ringing)
        }
        e.apply()
        _state.value = s
    }
}

/** "05:00" or "1:05:00". */
fun formatClock(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** "5 د" / "1 س 20 د" / "40 ث". */
fun formatDuration(sec: Int): String {
    if (sec < 60) return "$sec ث"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return when {
        h > 0 && m > 0 -> "$h س $m د"
        h > 0 -> "$h س"
        else -> "$m د"
    }
}
