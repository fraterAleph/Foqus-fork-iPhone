package app.foqos.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.foqos.android.BuildConfig
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.ui.components.SectionTitle
import app.foqos.android.ui.components.ToggleRow
import app.foqos.android.util.Permissions
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FoqosViewModel,
    onClose: () -> Unit,
    onOpenDeviceOwnerGuide: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    var accessibility by remember { mutableStateOf(false) }
    var usageStats by remember { mutableStateOf(false) }
    var overlays by remember { mutableStateOf(false) }
    var exactAlarms by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibility = Permissions.isAccessibilityEnabled(context)
            usageStats = Permissions.hasUsageStatsAccess(context)
            overlays = Permissions.canDrawOverlays(context)
            exactAlarms = Permissions.canScheduleExactAlarms(context)
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle("Permissions") }

            item {
                PermissionCard(
                    title = "Accessibility service",
                    granted = accessibility,
                    body = "Lets Foqos notice when a blocked app opens and cover it. Without it, " +
                        "blocking falls back to polling and reacts a second or two later.",
                    actionLabel = "Open settings",
                    onAction = { context.startActivity(Permissions.accessibilitySettingsIntent()) },
                )
            }

            item {
                PermissionCard(
                    title = "Usage access",
                    granted = usageStats,
                    body = "The fallback blocker for devices where the accessibility service is " +
                        "off. Recommended even when accessibility is on.",
                    actionLabel = "Open settings",
                    onAction = { context.startActivity(Permissions.usageAccessSettingsIntent()) },
                )
            }

            item {
                PermissionCard(
                    title = "Display over other apps",
                    granted = overlays,
                    body = "Some manufacturers block launching the shield from the background " +
                        "unless this is granted.",
                    actionLabel = "Open settings",
                    onAction = { context.startActivity(Permissions.overlaySettingsIntent(context)) },
                )
            }

            item {
                PermissionCard(
                    title = "Exact alarms",
                    granted = exactAlarms,
                    body = "Ends timed sessions on time even in Doze. Without it, a timer can " +
                        "run a few minutes long.",
                    actionLabel = "Open settings",
                    onAction = {
                        Permissions.exactAlarmSettingsIntent(context)?.let(context::startActivity)
                    },
                )
            }

            item {
                FoqosCard {
                    Text("Battery optimisation", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Exclude Foqos so the system does not stop a running session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { context.startActivity(Permissions.batteryOptimizationIntent()) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Open settings") }
                }
            }

            item { SectionTitle("Escape hatches") }

            item {
                FoqosCard {
                    Text("Emergency unblocks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${settings.emergencyUnblocksRemaining} left. They refill every " +
                            "${settings.emergencyResetPeriodWeeks} weeks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = settings.emergencyResetPeriodWeeks.toFloat(),
                        onValueChange = { viewModel.setEmergencyResetWeeks(it.toInt()) },
                        valueRange = 1f..12f,
                        steps = 10,
                    )
                }
            }

            item {
                FoqosCard {
                    ToggleRow(
                        title = "Restore blocking after a reboot",
                        description = "A running session is put back in place when the phone " +
                            "starts again.",
                        checked = settings.keepShieldOnReboot,
                        onCheckedChange = { viewModel.setKeepShieldOnReboot(it) },
                    )
                }
            }

            item { SectionTitle("Unbypassable blocking") }

            item {
                FoqosCard {
                    Text(
                        if (viewModel.engine.isDeviceOwner) {
                            "Device owner mode is active"
                        } else {
                            "Device owner mode is off"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (viewModel.engine.isDeviceOwner) {
                            "Blocked apps will not launch at all during a session, and Foqos " +
                                "cannot be uninstalled or bypassed through safe mode."
                        } else {
                            "The only blocking on Android that cannot be switched off in " +
                                "Settings. Setting it up needs a computer and a factory reset, " +
                                "so it suits a spare phone rather than your main one."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onOpenDeviceOwnerGuide,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(
                            if (viewModel.engine.isDeviceOwner) {
                                "How to turn it off"
                            } else {
                                "How to set it up"
                            }
                        )
                    }
                }
            }

            item { SectionTitle("About") }
            item {
                FoqosCard {
                    Text("Foqos for Android ${BuildConfig.VERSION_NAME}")
                    Text(
                        "An unofficial Android fork of Foqos by Ali Waseem, MIT licensed. " +
                            "Everything stays on this device: no account, no analytics, no " +
                            "network calls except the DNS lookups the website filter forwards.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    FoqosCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (granted) "On" else "Off",
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (!granted) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                Text(actionLabel)
            }
        }
    }
}
