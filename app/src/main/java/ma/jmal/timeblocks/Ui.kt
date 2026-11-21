package ma.jmal.timeblocks

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
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

val PRESETS = listOf(1, 2, 5, 10, 15, 25, 30, 45, 60) // minutes

/** Preset duration buttons plus a custom-minutes field. Calls [onPick] with seconds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationPicker(onPick: (Int) -> Unit, presets: List<Int> = PRESETS) {
    var custom by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { m ->
                FilledTonalButton(onClick = { onPick(m * 60) }) {
                    Text(if (m == 1) "دقيقة" else if (m == 2) "دقيقتان" else "$m د", fontSize = 16.sp)
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

/** Live card for the running block, with pause / resume / finish / cancel. */
@Composable
fun ActiveTimerCard(s: TimerState) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val remaining = (if (s.isPaused) s.endAt - s.pausedAt else s.endAt - now).coerceAtLeast(0) / 1000
            Text(
                if (s.ringing) "انتهى الوقت!" else formatClock(remaining),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    s.ringing -> "اختر: تمّت أو وقت إضافي"
                    s.isPaused -> "متوقف مؤقتًا"
                    else -> "من أصل ${formatDuration(s.plannedSec)}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.padding(4.dp))
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
