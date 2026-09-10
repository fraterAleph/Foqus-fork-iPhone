package app.foqos.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FoqosApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // The process can be recreated while a session is running (low memory, an update, a
        // reboot); put the block back before any UI shows.
        scope.launch {
            ServiceLocator.sessionController(this@FoqosApplication).restoreAfterRestart()
        }
    }
}
