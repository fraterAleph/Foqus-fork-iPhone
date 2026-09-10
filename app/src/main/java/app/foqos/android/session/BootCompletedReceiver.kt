package app.foqos.android.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.foqos.android.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A session that was running when the phone rebooted has to be put back in place, otherwise a
 * reboot would be the easiest way around a block.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ServiceLocator.sessionController(appContext).restoreAfterRestart()
            } finally {
                pending.finish()
            }
        }
    }
}
