package app.foqos.android.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.foqos.android.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ServiceLocator.sessionController(appContext).tick()
            } finally {
                pending.finish()
            }
        }
    }
}
