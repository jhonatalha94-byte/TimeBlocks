@file:OptIn(ExperimentalLayoutApi::class)

package ma.jmal.timeblocks

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsTab() {
    val ctx = LocalContext.current
    var alarm by remember { mutableStateOf(Prefs.alarm(ctx)) }
    var ringMin by remember { mutableIntStateOf(Prefs.ringMinutes(ctx)) }
    var vibrate by remember { mutableStateOf(Prefs.vibrate(ctx)) }
    var fixVol by remember { mutableStateOf(Prefs.fixVolume(ctx)) }
    var ambient by remember { mutableStateOf(Prefs.ambient(ctx)) }
    var ambVol by remember { mutableFloatStateOf(Prefs.ambientVolume(ctx).toFloat()) }
    var presets by remember { mutableStateOf(Prefs.presets(ctx).toSet()) }
    var muted by remember { mutableStateOf(Sounds.alarmVolumeMuted(ctx)) }

    DisposableEffect(Unit) { onDispose { Sounds.stopPreview() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                alarm = Sounds.URI_PREFIX + uri
                Prefs.setAlarm(ctx, alarm)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Section("🔔 صوت انتهاء الوقت") {
            if (muted) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("صوت المنبّه في هاتفك على الصفر، لذلك تسمع الاهتزاز فقط.", Modifier.weight(1f))
                        TextButton(onClick = {
                            Sounds.ensureAlarmAudible(ctx); muted = Sounds.alarmVolumeMuted(ctx)
                        }) { Text("رفع الصوت") }
                    }
                }
            }
            Sounds.ALARMS.forEach { o ->
                SoundRow(o.label, selected = alarm == o.id, onSelect = {
                    alarm = o.id; Prefs.setAlarm(ctx, o.id); Sounds.previewAlarm(ctx, o.id)
                }, onPreview = { Sounds.previewAlarm(ctx, o.id) })
            }
            if (alarm.startsWith(Sounds.URI_PREFIX)) {
                SoundRow(Sounds.alarmLabel(alarm), selected = true, onSelect = {}, onPreview = {
                    Sounds.previewAlarm(ctx, alarm)
                })
            }
            OutlinedButton(onClick = {
                Sounds.stopPreview()
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر رنّة")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false),
                )
            }, modifier = Modifier.fillMaxWidth()) { Text("📂 اختيار رنّة أخرى من الهاتف…") }
            TextButton(onClick = { Sounds.stopPreview() }) { Text("⏹ إيقاف المعاينة") }

            Text("مدة الرنين")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 5, 10).forEach { m ->
                    FilterChip(selected = ringMin == m, onClick = { ringMin = m; Prefs.setRingMinutes(ctx, m) },
                        label = { Text(minutesLabel(m)) })
                }
            }
            SwitchRow("اهتزاز مع الرنين", vibrate) { vibrate = it; Prefs.setVibrate(ctx, it) }
            SwitchRow("رفع صوت المنبّه تلقائيًا إذا كان صامتًا", fixVol) { fixVol = it; Prefs.setFixVolume(ctx, it) }
        }

        Section("🎧 صوت الخلفية أثناء المهمة") {
            Text(
                "يُشغَّل أثناء العدّ ويتوقف عند الإيقاف المؤقت أو انتهاء الوقت. يمكنك تغييره لكل مهمة، أو أثناء المؤقت.",
                style = MaterialTheme.typography.bodySmall,
            )
            Sounds.AMBIENT.forEach { o ->
                SoundRow(o.label, selected = ambient == o.id, onSelect = {
                    ambient = o.id; Prefs.setAmbient(ctx, o.id)
                    if (o.id == Sounds.NONE) Sounds.stopPreview() else Sounds.previewAmbient(ctx, o.id)
                }, onPreview = if (o.id == Sounds.NONE) null else ({ Sounds.previewAmbient(ctx, o.id) }))
            }
            Text("مستوى الصوت: ${ambVol.toInt()}٪")
            Slider(
                value = ambVol, onValueChange = { ambVol = it }, valueRange = 5f..100f,
                onValueChangeFinished = {
                    Prefs.setAmbientVolume(ctx, ambVol.toInt())
                    // Apply to a running block right away.
                    if (TimerStore.state.value != null) TimerService.send(ctx, TimerService.ACTION_REFRESH)
                },
            )
        }

        Section("⏱️ أزرار المدة") {
            Text("اختر الأزرار التي تظهر عند بدء مهمة:", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Prefs.PRESET_CHOICES.forEach { m ->
                    FilterChip(
                        selected = m in presets,
                        onClick = {
                            val n = if (m in presets) presets - m else presets + m
                            if (n.isNotEmpty()) { presets = n; Prefs.setPresets(ctx, n.toList()) }
                        },
                        label = { Text(minutesLabel(m)) },
                    )
                }
            }
        }
        Text(
            "مكعبات الوقت 1.1 • J@MAL",
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SoundRow(label: String, selected: Boolean, onSelect: () -> Unit, onPreview: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSelect), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, Modifier.weight(1f))
        if (onPreview != null) TextButton(onClick = onPreview) { Text("▶ استماع") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
