package app.foqos.android.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import app.foqos.android.data.db.ProfileEntity
import app.foqos.android.qr.QrCodeGenerator
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.util.DeepLinks

/**
 * The physical side of a profile: the QR code to print, and writing the same link to an NFC tag.
 * Both carry `https://foqos.app/profile/<id>`, the format the iOS app uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileShareScreen(
    viewModel: FoqosViewModel,
    profileId: String,
    onClose: () -> Unit,
    onArmTagWrite: (String?) -> Unit,
    isArmedForTagWrite: Boolean,
) {
    var profile by remember { mutableStateOf<ProfileEntity?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(profileId) {
        profile = viewModel.profile(profileId)
        qr = QrCodeGenerator.generate(DeepLinks.profileUrl(profileId))
    }

    val current = profile

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
                Text("QR code", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Print it and put it somewhere that makes stopping deliberate.",
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

            FoqosCard {
                Text("NFC tag", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isArmedForTagWrite) {
                        "Hold a tag against the back of the phone now."
                    } else {
                        "Writes the same link to an NTAG213 or similar tag. A tag written by " +
                            "the iOS app works here too."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isArmedForTagWrite) {
                    OutlinedButton(
                        onClick = { onArmTagWrite(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text("Cancel") }
                } else {
                    Button(
                        onClick = { onArmTagWrite(DeepLinks.profileUrl(profileId)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) { Text("Write to a tag") }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Scanning this tag or code starts the profile when nothing is running, and " +
                        "stops it when the profile's rules allow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
