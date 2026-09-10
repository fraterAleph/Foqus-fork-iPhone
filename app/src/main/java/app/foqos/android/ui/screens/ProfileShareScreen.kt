package app.foqos.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import app.foqos.android.qr.QrCodeGenerator
import app.foqos.android.session.UnlockRules
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.TagAction
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.util.DeepLinks
import kotlinx.coroutines.flow.flowOf

/**
 * The physical side of a profile: the tag that unlocks it, and the printable code that starts it.
 *
 * The distinction matters and the screen is built around it. A key is a tag's hardware UID, which
 * cannot be reproduced from a photo. A profile link — whether on a tag or in a QR code — is just
 * text, so anyone who reads it once can print their own copy; it can start a session, never end
 * one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileShareScreen(
    viewModel: FoqosViewModel,
    profileId: String,
    onClose: () -> Unit,
    pendingTagAction: TagAction?,
    onArmTagAction: (TagAction?) -> Unit,
) {
    val profileFlow = remember(profileId) {
        if (profileId.isBlank()) flowOf(null) else viewModel.observeProfile(profileId)
    }
    val profile by profileFlow.collectAsState(initial = null)
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(profileId) {
        qr = QrCodeGenerator.generate(DeepLinks.profileUrl(profileId))
    }

    val current = profile
    val keys = current?.physicalUnblockItems.orEmpty()
    val hardKeys = UnlockRules.hardNfcKeys(keys)
    val awaitingLink = pendingTagAction is TagAction.Link
    val awaitingWrite = pendingTagAction is TagAction.Write

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "Profile") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FoqosCard {
                Text("NFC key", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        awaitingLink -> "Hold a tag against the back of the phone now."
                        hardKeys.isEmpty() && current?.nfcOnlyUnlock == true ->
                            "This profile unlocks only by NFC and has no tag yet, so it cannot " +
                                "be started. Link one to fix that."
                        hardKeys.isEmpty() -> "No tag is linked yet."
                        else -> "Scanning a linked tag ends the session. Nothing else does."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                keys.forEach { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(key.label.ifBlank { key.kind.name })
                            Text(
                                if (UnlockRules.isClonableLink(key.value)) {
                                    "Profile link — can be copied, does not lock"
                                } else {
                                    "${key.kind.name} · ${key.value}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.unlinkKey(profileId, key.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove key")
                        }
                    }
                }

                if (awaitingLink) {
                    OutlinedButton(
                        onClick = { onArmTagAction(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text("Cancel") }
                } else {
                    Button(
                        onClick = { onArmTagAction(TagAction.Link(profileId)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text(if (keys.isEmpty()) "Link a tag" else "Link another tag") }
                }
            }

            FoqosCard {
                Text("Write a link to a tag", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (awaitingWrite) {
                        "Hold a tag against the back of the phone now."
                    } else {
                        "Optional. Writes this profile's link onto a tag so it also works with " +
                            "the iOS app. A written link is only text: anyone who scans the tag " +
                            "can reprint it as a QR code, so it starts sessions but never ends " +
                            "one. Link the tag above to make it a key."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (awaitingWrite) {
                    OutlinedButton(
                        onClick = { onArmTagAction(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text("Cancel") }
                } else {
                    TextButton(
                        onClick = {
                            onArmTagAction(
                                TagAction.Write(profileId, DeepLinks.profileUrl(profileId))
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text("Write link to a tag") }
                }
            }

            FoqosCard {
                Text("QR code", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (current?.nfcOnlyUnlock == true) {
                        "Starts this profile. It cannot stop it — NFC-only unlocking is on."
                    } else {
                        "Print it and put it somewhere that makes stopping deliberate."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                qr?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "QR code for this profile",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(top = 12.dp),
                    )
                }
                Text(
                    DeepLinks.profileUrl(profileId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.padding(bottom = 24.dp)) {}
        }
    }
}
