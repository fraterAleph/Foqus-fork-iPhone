package app.foqos.android.blocking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.foqos.android.ServiceLocator
import app.foqos.android.session.SessionResult
import app.foqos.android.session.SessionTimeCalculator
import app.foqos.android.ui.theme.FoqosTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The screen a blocked app gets covered with. The Android answer to the iOS shield extension.
 *
 * It is a normal activity rather than a system overlay so that it participates in the back stack
 * and can be dismissed cleanly; the blocked app stays open behind it but out of reach.
 */
class ShieldActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME).orEmpty()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            FoqosTheme {
                ShieldScreen(
                    appLabel = InstalledAppsProvider.label(this, blockedPackage),
                    profileName = profileName,
                    onDismiss = ::goHome,
                    onBreak = ::takeBreak,
                    onEmergency = ::emergencyUnblock,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun takeBreak() {
        lifecycleScope.launch {
            val result = ServiceLocator.sessionController(applicationContext).startBreak()
            if (result !is SessionResult.Rejected) finish() else goHome()
        }
    }

    private fun emergencyUnblock() {
        lifecycleScope.launch {
            val result = ServiceLocator.sessionController(applicationContext).emergencyUnblock()
            if (result is SessionResult.Stopped) finish() else goHome()
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "blocked_package"
        private const val EXTRA_PROFILE_NAME = "profile_name"
        private const val EXTRA_PROFILE_ID = "profile_id"

        fun intent(
            context: Context,
            blockedPackage: String,
            profileName: String,
            profileId: String,
        ): Intent = Intent(context, ShieldActivity::class.java)
            .putExtra(EXTRA_PACKAGE, blockedPackage)
            .putExtra(EXTRA_PROFILE_NAME, profileName)
            .putExtra(EXTRA_PROFILE_ID, profileId)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
    }
}

@Composable
private fun ShieldScreen(
    appLabel: String,
    profileName: String,
    onDismiss: () -> Unit,
    onBreak: () -> Unit,
    onEmergency: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var subtitle by remember { mutableStateOf("") }
    var breakAvailable by remember { mutableStateOf(false) }
    var emergencyAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessions = ServiceLocator.sessionRepository(context)
        while (true) {
            val active = sessions.getActive()
            if (active == null) {
                onDismiss()
                return@LaunchedEffect
            }
            val remaining = SessionTimeCalculator.remainingTimerMs(active.session)
            subtitle = if (remaining != null) {
                "${SessionTimeCalculator.formatDuration(remaining)} left in ${active.profile.name}"
            } else {
                "${SessionTimeCalculator.formatDuration(
                    SessionTimeCalculator.elapsedFocusMs(active.session)
                )} focused"
            }
            breakAvailable = active.profile.enableBreaks &&
                SessionTimeCalculator.remainingBreakMs(
                    active.session,
                    active.profile.breakTimeInMinutes,
                    active.profile.allowMultipleBreaks,
                ) > 0 && !active.session.isBreakActive
            emergencyAvailable = active.profile.enableEmergencyUnblock
            delay(1000)
        }
    }

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Text(
                    text = appLabel.ifBlank { "This app" } + " is blocked",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = subtitle.ifBlank { profileName },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Back to home screen")
                }

                if (breakAvailable) {
                    TextButton(onClick = onBreak, modifier = Modifier.fillMaxWidth()) {
                        Text("Take a break")
                    }
                }

                if (emergencyAvailable) {
                    TextButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Emergency unblock",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
