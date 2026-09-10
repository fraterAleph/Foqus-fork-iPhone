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
import app.foqos.android.ui.TagAction
import app.foqos.android.ui.theme.FoqosTheme
import app.foqos.android.util.DeepLinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FoqosViewModel by viewModels()

    /**
     * What the next tag held to the phone should do, set while the user is on a profile's
     * Tag / QR screen. Null means a scan toggles a session as usual.
     */
    private var pendingTagAction: TagAction? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FoqosTheme {
                FoqosApp(
                    viewModel = viewModel,
                    pendingTagAction = pendingTagAction,
                    onArmTagAction = { action -> pendingTagAction = action },
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
        val action = pendingTagAction

        if (tag != null && action != null) {
            pendingTagAction = null
            when (action) {
                is TagAction.Link -> {
                    val uid = NfcTools.tagIdFrom(intent)
                    if (uid == null) {
                        viewModel.show("Could not read that tag's id. Try holding it again.")
                    } else {
                        viewModel.linkNfcTag(action.profileId, uid)
                    }
                }

                is TagAction.Write -> when (val result = NfcTools.write(tag, action.url)) {
                    is NfcTools.WriteResult.Success ->
                        viewModel.show("Tag written. Scan it to toggle this profile.")
                    is NfcTools.WriteResult.Failure -> viewModel.show(result.message)
                }
            }
            return
        }

        val tokens = NfcTools.tokensFrom(intent)
        if (tokens.isNotEmpty()) {
            viewModel.onTokensScanned(tokens, TokenSource.NFC)
            return
        }

        val data = intent.data?.toString()
        if (intent.action == Intent.ACTION_VIEW && DeepLinks.profileIdFrom(data) != null) {
            viewModel.onTokensScanned(listOf(data!!), TokenSource.DEEP_LINK)
        }
    }
}
