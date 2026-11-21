package ma.jmal.timeblocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-screen "time is up": Done, or I need more time. */
class TimeUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            AppTheme {
                val s by TimerStore.state.collectAsState()
                // Closed from the notification or the app → close this screen too.
                LaunchedEffect(s) { if (s == null || s?.ringing == false) finish() }
                var askMore by remember { mutableStateOf(false) }

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("⌛ انتهى الوقت", fontSize = 24.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s?.title ?: "", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(32.dp))
                        if (!askMore) {
                            Button(
                                onClick = { TimerService.send(this@TimeUpActivity, TimerService.ACTION_DONE); finish() },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            ) { Text("تمّت ✓", fontSize = 22.sp) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { askMore = true },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            ) { Text("أحتاج وقتًا إضافيًا", fontSize = 20.sp) }
                        } else {
                            Text("كم دقيقة إضافية؟", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            DurationPicker(
                                presets = listOf(1, 2, 5, 10, 15, 30),
                                onPick = { sec ->
                                    TimerService.send(this@TimeUpActivity, TimerService.ACTION_EXTEND, sec)
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
