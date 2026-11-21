package ma.jmal.timeblocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen reminder: task name + duration buttons. */
class ReminderActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "مهمة"
        val taskId = intent.getLongExtra(Scheduler.EXTRA_TASK_ID, -1L)
        if (taskId >= 0) Notifs.nm(this).cancel(Notifs.reminderId(taskId))

        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("⏰ حان الوقت", fontSize = 22.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            title, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("كم من الوقت تحتاج؟", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        DurationPicker(onPick = { sec ->
                            TimerService.send(this@ReminderActivity, TimerService.ACTION_START, sec, title, taskId)
                            finish()
                        })
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { finish() }) { Text("تجاهل") }
                    }
                }
            }
        }
    }
}
