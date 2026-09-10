package app.foqos.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.foqos.android.data.db.ProfileEntity
import app.foqos.android.data.db.SessionWithProfile
import app.foqos.android.session.SessionTimeCalculator
import app.foqos.android.strategy.Strategies
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.components.EmptyState
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.ui.components.SectionTitle
import app.foqos.android.ui.components.TagRow
import app.foqos.android.util.Permissions
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FoqosViewModel,
    onCreateProfile: () -> Unit,
    onEditProfile: (ProfileEntity) -> Unit,
    onShareProfile: (ProfileEntity) -> Unit,
    onScan: () -> Unit,
    onInsights: () -> Unit,
    onSettings: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.activeSession.collectAsState()
    val context = LocalContext.current

    var shieldEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        while (true) {
            shieldEnabled = Permissions.isAccessibilityEnabled(context) ||
                Permissions.hasUsageStatsAccess(context)
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Foqos") },
                actions = {
                    IconButton(onClick = onScan) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan a code")
                    }
                    IconButton(onClick = onInsights) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Insights")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (active == null) {
                FloatingActionButton(onClick = onCreateProfile) {
                    Icon(Icons.Filled.Add, contentDescription = "New profile")
                }
            }
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
            if (!shieldEnabled) {
                item {
                    FoqosCard {
                        Text(
                            "Blocking is not switched on yet",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Foqos needs its accessibility service to cover blocked apps. " +
                                "Without it a session records time but nothing is blocked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                        Button(onClick = {
                            context.startActivity(Permissions.accessibilitySettingsIntent())
                        }) { Text("Open accessibility settings") }
                    }
                }
            }

            active?.let { session ->
                item { ActiveSessionCard(session = session, viewModel = viewModel) }
            }

            item { SectionTitle("Profiles") }

            if (profiles.isEmpty()) {
                item {
                    EmptyState(
                        title = "No profiles yet",
                        description = "Create one for work, study or bedtime, pick the apps it " +
                            "blocks, and choose how it starts and stops.",
                    )
                }
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileRow(
                    profile = profile,
                    isRunning = active?.profile?.id == profile.id,
                    canStart = active == null,
                    onStart = { viewModel.startProfile(profile) },
                    onEdit = { onEditProfile(profile) },
                    onShare = { onShareProfile(profile) },
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun ActiveSessionCard(session: SessionWithProfile, viewModel: FoqosViewModel) {
    val profile = session.profile
    val strategy = Strategies.byId(profile.strategyId)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session.session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remaining = SessionTimeCalculator.remainingTimerMs(session.session, now)
    val elapsed = SessionTimeCalculator.elapsedFocusMs(session.session, now)

    FoqosCard {
        Text(
            text = when {
                session.session.isPauseActive -> "Paused"
                session.session.isBreakActive -> "On a break"
                else -> "Focusing"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(profile.name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (remaining != null) {
                SessionTimeCalculator.formatDuration(remaining) + " left"
            } else {
                SessionTimeCalculator.formatDuration(elapsed)
            },
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Text(
            text = strategy.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (strategy.allowsManualStop || remaining == 0L) {
                Button(
                    onClick = { viewModel.stopActiveSession() },
                    modifier = Modifier.weight(1f),
                ) { Text("Stop") }
            } else {
                OutlinedButton(
                    onClick = { viewModel.stopActiveSession() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            strategy.usesNfc -> "Scan tag to stop"
                            strategy.usesQr -> "Scan code to stop"
                            else -> "Stop"
                        }
                    )
                }
            }

            if (profile.enableBreaks && strategy.allowsTimedBreaks) {
                OutlinedButton(
                    onClick = { viewModel.toggleBreak() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (session.session.isBreakActive) "End break" else "Break")
                }
            }
        }

        if (strategy.hasSoftUnblock) {
            TextButton(onClick = { viewModel.grantTemporaryAccess() }) {
                Text("Open temporary access")
            }
        }

        if (session.session.isPauseActive) {
            TextButton(onClick = { viewModel.resumeFromPause() }) { Text("Resume blocking now") }
        }

        if (profile.enableEmergencyUnblock) {
            TextButton(onClick = { viewModel.emergencyUnblock() }) {
                Text("Emergency unblock", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProfileEntity,
    isRunning: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
    val strategy = Strategies.byId(profile.strategyId)

    FoqosCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name.ifBlank { "Untitled profile" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = buildString {
                        append(strategy.name)
                        append(" · ")
                        append(
                            if (profile.enableAllowMode) {
                                "${profile.blockedPackages.size} allowed"
                            } else {
                                "${profile.blockedPackages.size} blocked"
                            }
                        )
                        if (profile.domains.isNotEmpty()) {
                            append(" · ${profile.domains.size} domains")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TagRow(
                    tags = strategy.tags.map { it.title },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isRunning) {
                    Text(
                        "Running",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (strategy.startsManually) {
                    Button(
                        onClick = onStart,
                        enabled = canStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(strategy.accent),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) { Text("Start") }
                } else {
                    Text(
                        if (strategy.usesNfc) "Scan tag" else "Scan code",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onShare) { Text("Tag / QR") }
            }
        }
    }
}
