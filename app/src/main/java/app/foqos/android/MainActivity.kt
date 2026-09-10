package app.foqos.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.foqos.android.nfc.NfcTools
import app.foqos.android.session.TokenSource
import app.foqos.android.ui.FoqosApp
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.theme.FoqosTheme
import app.foqos.android.util.DeepLinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FoqosViewModel by viewModels()

    /** Set while the user is on the "write this profile to a tag" screen. */
    private var pendingTagWriteUrl: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FoqosTheme {
                FoqosApp(
                    viewModel = viewModel,
                    onArmTagWrite = { url -> pendingTagWriteUrl = url },
                    isArmedForTagWrite = pendingTagWriteUrl != null,
                )
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        NfcTools.enableForegroundDispatch(this)
        // Deadlines can pass while the app is in the background.
        lifecycleScope.launch { ServiceLocator.sessionController(applicationContext).tick() }
    }

    override fun onPause() {
        NfcTools.disableForegroundDispatch(this)
        super.onPause()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val tag = NfcTools.tagFrom(intent)
        val armedUrl = pendingTagWriteUrl

        if (tag != null && armedUrl != null) {
            pendingTagWriteUrl = null
            when (val result = NfcTools.write(tag, armedUrl)) {
                is NfcTools.WriteResult.Success ->
                    viewModel.show("Tag written. Scan it to toggle this profile.")
                is NfcTools.WriteResult.Failure -> viewModel.show(result.message)
            }
            return
        }

        NfcTools.tokenFrom(intent)?.let { token ->
            viewModel.onTokenScanned(token, TokenSource.NFC)
            return
        }

        val data = intent.data?.toString()
        if (intent.action == Intent.ACTION_VIEW && DeepLinks.profileIdFrom(data) != null) {
            viewModel.onTokenScanned(data!!, TokenSource.DEEP_LINK)
        }
    }
}
