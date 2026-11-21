@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ma.jmal.timeblocks

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        App.io.launch { Scheduler.rescheduleAll(applicationContext) }
        setContent { AppTheme { MainScreen() } }
    }
}

private val AR = Locale.forLanguageTag("ar-MA-u-nu-latn")

/** Calendar.DAY_OF_WEEK → Arabic name, listed Monday first. */
private val DAYS = listOf(
    Calendar.MONDAY to "الاثنين", Calendar.TUESDAY to "الثلاثاء", Calendar.WEDNESDAY to "الأربعاء",
    Calendar.THURSDAY to "الخميس", Calendar.FRIDAY to "الجمعة", Calendar.SATURDAY to "السبت",
    Calendar.SUNDAY to "الأحد",
)

private fun repeatText(t: TaskItem): String = when (t.repeat) {
    TaskItem.REPEAT_DAILY -> "يوميًا"
    TaskItem.REPEAT_WEEKLY -> DAYS.filter { t.daysMask and (1 shl (it.first - 1)) != 0 }
        .joinToString("، ") { it.second }
    else -> if (t.enabled) "مرة واحدة" else "مرة واحدة (انتهت)"
}

private fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val timer by TimerStore.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showQuick by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var startFor by remember { mutableStateOf<TaskItem?>(null) }

    // Re-check permissions every time the user comes back from Settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("مكعبات الوقت", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Notifications, null) }, label = { Text("المهام") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("السجل") },
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    SmallFloatingActionButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "مهمة مجدولة")
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { showQuick = true },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("مهمة فورية") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PermissionBanners(resumeTick)
            timer?.let { ActiveTimerCard(it) }
            when (tab) {
                0 -> TasksTab(onStart = { startFor = it })
                else -> LogTab()
            }
        }
    }

    if (showQuick) QuickTaskDialog(onDismiss = { showQuick = false })
    if (showAdd) AddTaskDialog(onDismiss = { showAdd = false })
    startFor?.let { t ->
        AlertDialog(
            onDismissRequest = { startFor = null },
            title = { Text(t.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DurationPicker(onPick = { sec ->
                        TimerService.send(ctx, TimerService.ACTION_START, sec, t.title)
                        startFor = null
                    })
                }
            },
            confirmButton = { TextButton(onClick = { startFor = null }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun TasksTab(onStart: (TaskItem) -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val flow = remember { dao.tasksFlow() }
    val tasks by flow.collectAsState(initial = emptyList())

    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "لا توجد مهام مجدولة بعد.\nاضغط + لإضافة مهمة بوقت محدد،\nأو «مهمة فورية» لتبدأ الآن.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 140.dp)) {
        items(tasks, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${hm(t.hour, t.minute)} • ${repeatText(t)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onStart(t) }) { Icon(Icons.Filled.PlayArrow, "ابدأ الآن") }
                    Switch(
                        checked = t.enabled,
                        onCheckedChange = { on ->
                            val u = t.copy(enabled = on)
                            App.io.launch { dao.update(u); Scheduler.schedule(ctx, u) }
                        },
                    )
                    IconButton(onClick = {
                        App.io.launch { Scheduler.cancel(ctx, t.id); dao.delete(t) }
                    }) { Icon(Icons.Filled.Delete, "حذف") }
                }
            }
        }
    }
}

@Composable
private fun LogTab() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }
    val (from, to) = remember(dayOffset) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        val start = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        start to c.timeInMillis
    }
    val flow = remember(from) { dao.logFlow(from, to) }
    val entries by flow.collectAsState(initial = emptyList())
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", AR) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", AR) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { dayOffset-- }) { Text("→ السابق") }
            Text(
                when (dayOffset) { 0 -> "اليوم"; -1 -> "أمس"; else -> dayFmt.format(Date(from)) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { dayOffset++ }, enabled = dayOffset < 0) { Text("التالي ←") }
        }
        val total = entries.sumOf { it.actualSec }
        val planned = entries.sumOf { it.plannedSec }
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("${entries.size} مهام منجزة", fontWeight = FontWeight.Bold)
                Text("الوقت الفعلي: ${formatDuration(total)} • المخطط: ${formatDuration(planned)}")
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
            items(entries, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${timeFmt.format(Date(e.startedAt))} ← ${timeFmt.format(Date(e.finishedAt))}")
                            val diff = e.actualSec - e.plannedSec
                            val note = when {
                                diff > 30 -> " (+${formatDuration(diff)})"
                                diff < -30 -> " (أسرع بـ ${formatDuration(-diff)})"
                                else -> ""
                            }
                            Text("فعلي: ${formatDuration(e.actualSec)} • مخطط: ${formatDuration(e.plannedSec)}$note")
                        }
                        IconButton(onClick = { App.io.launch { dao.deleteLog(e) } }) {
                            Icon(Icons.Filled.Delete, "حذف")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTaskDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مهمة فورية") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("ماذا ستفعل؟") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                DurationPicker(onPick = { sec ->
                    TimerService.send(ctx, TimerService.ACTION_START, sec, title.ifBlank { "مهمة فورية" })
                    onDismiss()
                })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

@Composable
private fun AddTaskDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var title by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf((now.get(Calendar.MINUTE) + 5) % 60) }
    var repeat by remember { mutableIntStateOf(TaskItem.REPEAT_ONCE) }
    var mask by remember { mutableIntStateOf(1 shl (now.get(Calendar.DAY_OF_WEEK) - 1)) }
    val valid = title.isNotBlank() && (repeat != TaskItem.REPEAT_WEEKLY || mask != 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مهمة مجدولة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("اسم المهمة") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("الوقت: ${hm(hour, minute)}", style = MaterialTheme.typography.titleMedium) }
                Text("التكرار")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        TaskItem.REPEAT_ONCE to "مرة واحدة",
                        TaskItem.REPEAT_DAILY to "يوميًا",
                        TaskItem.REPEAT_WEEKLY to "أسبوعيًا",
                    ).forEach { (v, label) ->
                        FilterChip(selected = repeat == v, onClick = { repeat = v }, label = { Text(label) })
                    }
                }
                if (repeat == TaskItem.REPEAT_WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DAYS.forEach { (dow, name) ->
                            val bit = 1 shl (dow - 1)
                            FilterChip(
                                selected = mask and bit != 0,
                                onClick = { mask = mask xor bit },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val t = TaskItem(
                    title = title.trim(), hour = hour, minute = minute, repeat = repeat,
                    daysMask = if (repeat == TaskItem.REPEAT_WEEKLY) mask else 0,
                )
                App.io.launch {
                    val dao = AppDb.get(ctx).dao()
                    val id = dao.insert(t)
                    Scheduler.schedule(ctx, t.copy(id = id))
                }
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun Context.openSettings(action: String, withPackage: Boolean = true) {
    val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (withPackage) {
        if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        else i.data = Uri.parse("package:$packageName")
    }
    try {
        startActivity(i)
    } catch (_: Exception) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Warnings for anything that would stop reminders from showing on time. */
@Composable
private fun PermissionBanners(resumeTick: Int) {
    val ctx = LocalContext.current
    if (resumeTick < 0) return // reading it re-evaluates the checks after returning from Settings

    val needNotif = Build.VERSION.SDK_INT >= 33 &&
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val needFullScreen = Build.VERSION.SDK_INT >= 34 && !Notifs.nm(ctx).canUseFullScreenIntent()
    val needExact = !Scheduler.canExact(ctx)
    val needBattery = !ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    if (needNotif) Banner("الإشعارات معطّلة — لن تصلك التذكيرات.") {
        ctx.openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }
    if (needFullScreen) Banner("اسمح بالتنبيه بملء الشاشة ليظهر التذكير فوق شاشة القفل.") {
        ctx.openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    }
    if (needExact && Build.VERSION.SDK_INT >= 31) Banner("اسمح بالمنبّهات الدقيقة لتصل التذكيرات في وقتها.") {
        ctx.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    }
    if (needBattery) Banner("أزل التطبيق من «تحسين البطارية» حتى لا يوقفه الهاتف.") {
        ctx.openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, withPackage = false)
    }
}

@Composable
private fun Banner(text: String, onFix: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onFix) { Text("فتح الإعدادات") }
        }
    }
}
