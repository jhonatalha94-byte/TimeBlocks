package ma.jmal.timeblocks

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color

/** User settings, kept in SharedPreferences. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val DEFAULT_PRESETS = listOf(1, 2, 5, 10, 15, 25, 30, 45, 60)
    val PRESET_CHOICES = listOf(1, 2, 3, 5, 10, 15, 20, 25, 30, 40, 45, 50, 60, 90, 120)

    fun alarm(c: Context): String = p(c).getString("alarm", "alarm_chime") ?: "alarm_chime"
    fun setAlarm(c: Context, v: String) = p(c).edit().putString("alarm", v).apply()

    fun ringMinutes(c: Context): Int = p(c).getInt("ringMin", 2)
    fun setRingMinutes(c: Context, v: Int) = p(c).edit().putInt("ringMin", v).apply()

    fun vibrate(c: Context): Boolean = p(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) = p(c).edit().putBoolean("vibrate", v).apply()

    /** Raise the alarm volume if it is muted, so the ring is actually heard. */
    fun fixVolume(c: Context): Boolean = p(c).getBoolean("fixVolume", true)
    fun setFixVolume(c: Context, v: Boolean) = p(c).edit().putBoolean("fixVolume", v).apply()

    fun ambient(c: Context): String = p(c).getString("ambient", "none") ?: "none"
    fun setAmbient(c: Context, v: String) = p(c).edit().putString("ambient", v).apply()

    /** 0..100 */
    fun ambientVolume(c: Context): Int = p(c).getInt("ambientVol", 60)
    fun setAmbientVolume(c: Context, v: Int) = p(c).edit().putInt("ambientVol", v).apply()

    fun presets(c: Context): List<Int> =
        p(c).getString("presets", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.sorted()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_PRESETS

    fun setPresets(c: Context, v: List<Int>) =
        p(c).edit().putString("presets", v.sorted().joinToString(",")).apply()
}

val TASK_COLORS = listOf(
    Color(0xFF1E5BA8), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFFEF6C00),
    Color(0xFF6A1B9A), Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF5D4037),
)

fun taskColor(i: Int) = TASK_COLORS[i.mod(TASK_COLORS.size)]

data class SoundOpt(val id: String, val label: String, val res: Int)

object Sounds {
    const val SYSTEM = "system"
    const val NONE = "none"
    const val URI_PREFIX = "uri:"

    val ALARMS = listOf(
        SoundOpt("alarm_chime", "جرس 🔔", R.raw.alarm_chime),
        SoundOpt("alarm_classic", "منبّه كلاسيكي ⏰", R.raw.alarm_classic),
        SoundOpt("alarm_digital", "رنين رقمي 📟", R.raw.alarm_digital),
        SoundOpt("alarm_gentle", "نغمة هادئة 🎵", R.raw.alarm_gentle),
        SoundOpt("alarm_gong", "غونغ 🥁", R.raw.alarm_gong),
        SoundOpt(SYSTEM, "منبّه الهاتف 📱", 0),
    )

    val AMBIENT = listOf(
        SoundOpt(NONE, "بدون صوت 🔇", 0),
        SoundOpt("amb_rain", "مطر 🌧️", R.raw.amb_rain),
        SoundOpt("amb_ocean", "أمواج البحر 🌊", R.raw.amb_ocean),
        SoundOpt("amb_fire", "نار المدفأة 🔥", R.raw.amb_fire),
        SoundOpt("amb_clock", "تكتكة الساعة 🕰️", R.raw.amb_clock),
        SoundOpt("amb_white", "ضجيج أبيض ⚪", R.raw.amb_white),
        SoundOpt("amb_brown", "ضجيج بنّي (عميق) 🟤", R.raw.amb_brown),
    )

    fun alarmLabel(id: String) =
        if (id.startsWith(URI_PREFIX)) "رنّة من الهاتف 📱" else ALARMS.firstOrNull { it.id == id }?.label ?: id

    fun ambientLabel(id: String) = AMBIENT.firstOrNull { it.id == id }?.label ?: AMBIENT[0].label

    private fun rawUri(c: Context, res: Int) = Uri.parse("android.resource://${c.packageName}/$res")

    /** Uri for an alarm id; falls back to the bundled chime. */
    fun alarmUri(c: Context, id: String): Uri = when {
        id.startsWith(URI_PREFIX) -> Uri.parse(id.removePrefix(URI_PREFIX))
        id == SYSTEM -> RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: rawUri(c, R.raw.alarm_chime)
        else -> rawUri(c, ALARMS.firstOrNull { it.id == id && it.res != 0 }?.res ?: R.raw.alarm_chime)
    }

    fun ambientUri(c: Context, id: String): Uri? =
        AMBIENT.firstOrNull { it.id == id && it.res != 0 }?.let { rawUri(c, it.res) }

    private fun attrs(alarm: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
        .setContentType(if (alarm) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** Create and start a player, or return null if the sound cannot be played. */
    fun play(c: Context, uri: Uri, alarm: Boolean, loop: Boolean, volume: Float = 1f): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(attrs(alarm))
            setDataSource(c, uri)
            isLooping = loop
            prepare()
            setVolume(volume, volume)
            start()
        }
    } catch (_: Exception) {
        null
    }

    /** Alarm player for the chosen tone; if it fails, the bundled chime. */
    fun playAlarm(c: Context, id: String, loop: Boolean): MediaPlayer? =
        play(c, alarmUri(c, id), alarm = true, loop = loop)
            ?: play(c, rawUri(c, R.raw.alarm_chime), alarm = true, loop = loop)

    fun alarmVolumeMuted(c: Context): Boolean =
        c.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM) == 0

    /** If the alarm stream is muted, set it to ~70 % so the ring is heard. */
    fun ensureAlarmAudible(c: Context) {
        val am = c.getSystemService(AudioManager::class.java)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7f).toInt().coerceAtLeast(1), 0)
            } catch (_: Exception) {
            }
        }
    }

    // ---- Preview in Settings (one at a time, stops by itself) ----
    private var preview: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stopPreview() }

    fun previewAlarm(c: Context, id: String) {
        stopPreview()
        preview = playAlarm(c, id, loop = true)
        handler.postDelayed(stopper, 5_000)
    }

    fun previewAmbient(c: Context, id: String) {
        stopPreview()
        val uri = ambientUri(c, id) ?: return
        preview = play(c, uri, alarm = false, loop = true, volume = Prefs.ambientVolume(c) / 100f)
        handler.postDelayed(stopper, 8_000)
    }

    fun stopPreview() {
        handler.removeCallbacks(stopper)
        try {
            preview?.stop()
        } catch (_: Exception) {
        }
        preview?.release()
        preview = null
    }
}
