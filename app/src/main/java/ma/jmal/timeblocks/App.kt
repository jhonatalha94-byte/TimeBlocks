package ma.jmal.timeblocks

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifs.createChannels(this)
        TimerStore.init(this)
    }

    companion object {
        /** App-wide scope for short DB writes that must outlive a screen or a receiver. */
        val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
