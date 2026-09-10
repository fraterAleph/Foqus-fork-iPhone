package app.foqos.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import app.foqos.android.blocking.vpn.DomainBlockerVpnService
import app.foqos.android.data.db.ProfileEntity
import app.foqos.android.data.model.StrategyData
import app.foqos.android.session.UnlockRules
import app.foqos.android.strategy.Strategies
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.Routes
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.ui.components.SectionTitle
import app.foqos.android.ui.components.ToggleRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    viewModel: FoqosViewModel,
    profileId: String,
    onClose: () -> Unit,
    onShare: (String) -> Unit,
) {
    var profile by remember { mutableStateOf<ProfileEntity?>(null) }
    var strategyData by remember { mutableStateOf(StrategyData()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showStrategyPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newDomain by remember { mutableStateOf("") }
    val context = LocalContext.current
    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            viewModel.show("Website blocking needs the VPN permission to run its DNS filter.")
        }
    }

    LaunchedEffect(profileId) {
        val loaded = if (profileId == Routes.NEW_PROFILE) {
            viewModel.newProfileDraft()
        } else {
            viewModel.profile(profileId) ?: viewModel.newProfileDraft()
        }
        profile = loaded
        strategyData = viewModel.decodeStrategyData(loaded)
    }

    val current = profile ?: return
    val strategy = Strategies.byId(current.strategyId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (profileId == Routes.NEW_PROFILE) "New profile" else "Edit profile")
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val prepared = current.copy(
                            name = current.name.trim().ifBlank { "Focus" },
                            strategyData = viewModel.encodeStrategyData(strategyData),
                        )
                        viewModel.saveProfile(prepared) { onClose() }
                    }) { Text("Save") }
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
            item {
                OutlinedTextField(
                    value = current.name,
                    onValueChange = { profile = current.copy(name = it) },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { SectionTitle("How it starts and stops") }
            item {
                FoqosCard(onClick = { showStrategyPicker = true }) {
                    Text(strategy.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strategy.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                FoqosCard {
                    ToggleRow(
                        title = "Unlock only with an NFC tag",
                        description = "The session ends when you scan the linked tag, and no " +
                            "other way: no Stop button, no break, no emergency unblock, and no " +
                            "QR code.",
                        checked = current.nfcOnlyUnlock,
                        onCheckedChange = { enabled ->
                            profile = current.copy(
                                nfcOnlyUnlock = enabled,
                                // An NFC-only profile on a QR strategy could never be stopped by
                                // the token its own strategy asks for, so move it onto NFC.
                                strategyId = if (enabled && !strategy.usesNfc) {
                                    Strategies.nfc.id
                                } else {
                                    current.strategyId
                                },
                            )
                        },
                    )

                    if (current.nfcOnlyUnlock) {
                        val linkedKeys = UnlockRules.hardNfcKeys(current.physicalUnblockItems)
                        Text(
                            if (linkedKeys.isEmpty()) {
                                "No tag linked yet. The profile cannot start until you link one " +
                                    "on its Tag / QR screen."
                            } else {
                                "${linkedKeys.size} tag(s) linked. Keep one somewhere you have " +
                                    "to walk to."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (linkedKeys.isEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "A profile cannot be edited while its session runs, so this cannot " +
                                "be switched off from inside a block.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (strategy.hasTimer) {
                item {
                    DurationCard(
                        title = "Session length",
                        seconds = strategyData.timerSeconds,
                        onChange = { strategyData = strategyData.copy(timerSeconds = it) },
                        maxMinutes = 240,
                    )
                }
            }

            if (strategy.hasPauseMode) {
                item {
                    DurationCard(
                        title = "Pause length",
                        seconds = strategyData.pauseSeconds,
                        onChange = { strategyData = strategyData.copy(pauseSeconds = it) },
                        maxMinutes = 60,
                    )
                }
            }

            if (strategy.hasSoftUnblock) {
                item {
                    FoqosCard {
                        Text("Temporary access", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Short openings you can spend without ending the session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Openings per session: ${strategyData.softUnblockCount}",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Slider(
                            value = strategyData.softUnblockCount.toFloat(),
                            onValueChange = {
                                strategyData = strategyData.copy(softUnblockCount = it.toInt())
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                        )
                        Text(
                            "Each opening lasts ${strategyData.softUnblockSeconds / 60} min"
                        )
                        Slider(
                            value = (strategyData.softUnblockSeconds / 60).toFloat(),
                            onValueChange = {
                                strategyData = strategyData.copy(
                                    softUnblockSeconds = it.toLong() * 60
                                )
                            },
                            valueRange = 1f..30f,
                        )
                    }
                }
            }

            item { SectionTitle("Apps") }
            item {
                FoqosCard(onClick = { showAppPicker = true }) {
                    Text(
                        if (current.enableAllowMode) "Allowed apps" else "Blocked apps",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${current.blockedPackages.size} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                FoqosCard {
                    ToggleRow(
                        title = "Allow only these apps",
                        description = "Block everything except the apps you picked.",
                        checked = current.enableAllowMode,
                        onCheckedChange = { profile = current.copy(enableAllowMode = it) },
                    )
                }
            }

            item { SectionTitle("Websites") }
            item {
                FoqosCard {
                    ToggleRow(
                        title = "Block websites",
                        description = "Runs a local DNS filter. Only one VPN can be active at a " +
                            "time, and apps with their own secure DNS bypass it.",
                        checked = current.enableDomainBlocking,
                        onCheckedChange = { enabled ->
                            profile = current.copy(enableDomainBlocking = enabled)
                            if (enabled) {
                                DomainBlockerVpnService.consentIntent(context)
                                    ?.let(vpnConsentLauncher::launch)
                            }
                        },
                    )

                    if (current.enableDomainBlocking) {
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newDomain,
                                onValueChange = { newDomain = it },
                                label = { Text("example.com") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                val domain = newDomain.trim().lowercase()
                                    .removePrefix("https://")
                                    .removePrefix("http://")
                                    .substringBefore('/')
                                if (domain.isNotEmpty() && domain !in current.domains) {
                                    profile = current.copy(domains = current.domains + domain)
                                }
                                newDomain = ""
                            }) { Text("Add") }
                        }

                        current.domains.forEach { domain ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(domain, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    profile = current.copy(domains = current.domains - domain)
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove $domain")
                                }
                            }
                        }

                        ToggleRow(
                            title = "Allow only these domains",
                            checked = current.enableAllowModeDomains,
                            onCheckedChange = {
                                profile = current.copy(enableAllowModeDomains = it)
                            },
                        )
                    }
                }
            }

            item { SectionTitle("Breaks and escapes") }
            item {
                FoqosCard {
                    ToggleRow(
                        title = "Allow breaks",
                        description = when {
                            current.nfcOnlyUnlock ->
                                "Off while unlocking is NFC-only: a break is a way out of a " +
                                    "session that does not need the tag."
                            !strategy.allowsTimedBreaks ->
                                "This strategy uses pauses instead of breaks."
                            else -> "Pause blocking for a limited amount of time."
                        },
                        checked = current.breaksAllowed,
                        enabled = strategy.allowsTimedBreaks && !current.nfcOnlyUnlock,
                        onCheckedChange = { profile = current.copy(enableBreaks = it) },
                    )

                    if (current.breaksAllowed && strategy.allowsTimedBreaks) {
                        Text("Break allowance: ${current.breakTimeInMinutes} min")
                        Slider(
                            value = current.breakTimeInMinutes.toFloat(),
                            onValueChange = {
                                profile = current.copy(breakTimeInMinutes = it.toInt())
                            },
                            valueRange = 1f..60f,
                        )
                        ToggleRow(
                            title = "Split across several breaks",
                            checked = current.allowMultipleBreaks,
                            onCheckedChange = {
                                profile = current.copy(allowMultipleBreaks = it)
                            },
                        )
                    }

                    if (!current.nfcOnlyUnlock) {
                        ToggleRow(
                            title = "Emergency unblock",
                            description = "Spend one of your limited emergency unblocks to end a " +
                                "session early.",
                            checked = current.enableEmergencyUnblock,
                            onCheckedChange = {
                                profile = current.copy(enableEmergencyUnblock = it)
                            },
                        )

                        ToggleRow(
                            title = "Strict mode",
                            description = "Refuse every stop that does not come from a saved tag " +
                                "or code.",
                            checked = current.enableStrictMode,
                            onCheckedChange = { profile = current.copy(enableStrictMode = it) },
                        )
                    }
                }
            }

            if (current.physicalUnblockItems.isNotEmpty()) {
                item { SectionTitle("Physical unlock") }
                items(current.physicalUnblockItems, key = { it.id }) { unblockItem ->
                    FoqosCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(unblockItem.label.ifBlank { unblockItem.kind.name })
                                Text(
                                    unblockItem.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                profile = current.copy(
                                    physicalUnblockItems =
                                        current.physicalUnblockItems - unblockItem
                                )
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
                        }
                    }
                }
            }

            if (profileId != Routes.NEW_PROFILE) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onShare(current.id) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Tag / QR") }
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            selected = current.blockedPackages.toSet(),
            allowMode = current.enableAllowMode,
            onDismiss = { showAppPicker = false },
            onConfirm = { selection ->
                profile = current.copy(blockedPackages = selection.toList())
                showAppPicker = false
            },
        )
    }

    if (showStrategyPicker) {
        StrategyPickerDialog(
            selectedId = current.strategyId,
            onDismiss = { showStrategyPicker = false },
            onSelect = { chosen ->
                profile = current.copy(strategyId = chosen.id)
                showStrategyPicker = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"${current.name}\"?") },
            text = { Text("Its session history is deleted with it. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteProfile(current)
                    onClose()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DurationCard(
    title: String,
    seconds: Long,
    onChange: (Long) -> Unit,
    maxMinutes: Int,
) {
    val minutes = (seconds / 60).toInt().coerceAtLeast(1)
    FoqosCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = minutes.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { onChange(it.coerceIn(1, maxMinutes) * 60L) }
                },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
        }
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onChange(it.toLong() * 60) },
            valueRange = 1f..maxMinutes.toFloat(),
        )
    }
}
