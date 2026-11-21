package ma.jmal.timeblocks

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF9EC3FF))
        else -> lightColorScheme(primary = Color(0xFF1E5BA8))
    }
    MaterialTheme(colorScheme = scheme) {
        // The whole UI is Arabic, so always lay it out right-to-left.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** Let an alarm screen appear over the lock screen and wake the display. */
fun Activity.showOverLockScreen() {
    if (Build.VERSION.SDK_INT >= 27) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

/** Preset duration buttons plus a custom-minutes field. Calls [onPick] with seconds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPicker(onPick: (Int) -> Unit, presets: List<Int>? = null) {
    val ctx = LocalContext.current
    val list = presets ?: remember { Prefs.presets(ctx) }
    var custom by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            list.forEach { m ->
                FilledTonalButton(onClick = { onPick(m * 60) }) {
                    Text(minutesLabel(m), fontSize = 16.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom,
                onValueChange = { v -> custom = v.filter { it.isDigit() }.take(4) },
                label = { Text("مدة مخصصة (دقائق)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = (custom.toIntOrNull() ?: 0) > 0,
                onClick = { custom.toIntOrNull()?.let { onPick(it * 60) } },
            ) { Text("ابدأ") }
        }
    }
}

fun minutesLabel(m: Int) = when {
    m == 1 -> "دقيقة"
    m == 2 -> "دقيقتان"
    m >= 60 && m % 60 == 0 -> if (m == 60) "ساعة" else "${m / 60} س"
    else -> "$m د"
}

/** Chips for choosing a background sound. [withDefault] adds a "default from settings" choice (""). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmbientPicker(selected: String, onSelect: (String) -> Unit, withDefault: Boolean = false) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (withDefault) {
            FilterChip(selected = selected.isEmpty(), onClick = { onSelect("") }, label = { Text("الافتراضي") })
        }
        Sounds.AMBIENT.forEach { o ->
            FilterChip(selected = selected == o.id, onClick = { onSelect(o.id) }, label = { Text(o.label) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TASK_COLORS.forEachIndexed { i, col ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(col)
                    .border(if (i == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { if (i == selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Live card for the running block, with pause / resume / finish / cancel and the background sound. */
@Composable
fun ActiveTimerCard(s: TimerState) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSounds by remember { mutableStateOf(false) }
    LaunchedEffect(s) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val remainingMs = (if (s.isPaused) s.endAt - s.pausedAt else s.endAt - now).coerceAtLeast(0)
            val total = (s.endAt - s.startedAt - s.pausedTotal).coerceAtLeast(1)
            val progress = (remainingMs.toFloat() / total).coerceIn(0f, 1f)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                CircularProgressIndicator(
                    progress = { if (s.ringing) 0f else progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (s.ringing) "⌛" else formatClock(remainingMs / 1000),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            s.ringing -> "انتهى الوقت!"
                            s.isPaused -> "متوقف مؤقتًا"
                            else -> "من ${formatDuration(s.plannedSec)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showSounds = !showSounds }) {
                Text("الصوت: ${Sounds.ambientLabel(s.ambient)} ${if (showSounds) "▲" else "▼"}")
            }
            if (showSounds) {
                AmbientPicker(selected = s.ambient, onSelect = {
                    TimerService.send(ctx, TimerService.ACTION_AMBIENT, ambient = it)
                })
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    s.ringing -> {
                        Button(onClick = { TimerService.send(ctx, TimerService.ACTION_DONE) }) { Text("تمّت ✓") }
                        OutlinedButton(onClick = { TimerService.send(ctx, TimerService.ACTION_EXTEND, 5 * 60) }) { Text("+5 د") }
                    }
                    s.isPaused -> {
                        Button(onClick = { TimerService.send(ctx, TimerService.ACTION_RESUME) }) { Text("متابعة") }
                        OutlinedButton(onClick = { TimerService.send(ctx, TimerService.ACTION_DONE) }) { Text("إنهاء") }
                    }
                    else -> {
                        OutlinedButton(onClick = { TimerService.send(ctx, TimerService.ACTION_PAUSE) }) { Text("إيقاف مؤقت") }
                        Button(onClick = { TimerService.send(ctx, TimerService.ACTION_DONE) }) { Text("إنهاء مبكر") }
                    }
                }
                TextButton(onClick = { TimerService.send(ctx, TimerService.ACTION_CANCEL) }) { Text("إلغاء") }
            }
        }
    }
}
