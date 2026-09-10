package app.foqos.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.util.Permissions
import kotlinx.coroutines.delay

/**
 * First-run setup. Android cannot grant any of this from inside the app, so the honest thing is
 * to explain what each permission buys and hand the user straight to the right Settings page.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var accessibility by remember { mutableStateOf(false) }
    var usageStats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibility = Permissions.isAccessibilityEnabled(context)
            usageStats = Permissions.hasUsageStatsAccess(context)
            delay(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Foqos", style = MaterialTheme.typography.displaySmall)
        Text(
            "Put friction between you and the apps you keep opening. Profiles decide what is " +
                "blocked; NFC tags, QR codes and timers decide when it stops.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.padding(top = 8.dp))

        FoqosCard {
            Text(
                "1. Accessibility service" + if (accessibility) " ✓" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Android has no system-level app blocker for normal apps. Foqos uses the " +
                    "accessibility service to see which app comes to the front and cover it " +
                    "while a session runs. It reads the package name and nothing else.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { context.startActivity(Permissions.accessibilitySettingsIntent()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text(if (accessibility) "Enabled" else "Enable in Settings") }
        }

        FoqosCard {
            Text(
                "2. Usage access" + if (usageStats) " ✓" else "",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "A fallback so blocking keeps working if the accessibility service is turned " +
                    "off. Optional, but the block is weaker without it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { context.startActivity(Permissions.usageAccessSettingsIntent()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text(if (usageStats) "Granted" else "Grant usage access") }
        }

        FoqosCard {
            Text("3. What this cannot do", style = MaterialTheme.typography.titleMedium)
            Text(
                "A determined user can force-stop Foqos or switch the service off in Settings, " +
                    "and the block goes with it. If you want blocking you cannot walk around, " +
                    "set Foqos as device owner over ADB — Settings explains how.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Start using Foqos") }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    }
}
