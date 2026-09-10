package app.foqos.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import app.foqos.android.ui.FoqosViewModel
import app.foqos.android.ui.components.FoqosCard
import app.foqos.android.ui.components.SectionTitle
import kotlinx.coroutines.delay

private const val SETUP_COMMAND =
    "adb shell dpm set-device-owner app.foqos.android/.blocking.FoqosDeviceAdminReceiver"

private const val REMOVAL_COMMAND =
    "adb shell dpm remove-active-admin app.foqos.android/.blocking.FoqosDeviceAdminReceiver"

/**
 * The setup guide for device owner mode.
 *
 * Written for someone who has never opened a terminal, and deliberately front-loads the cost:
 * this needs a factory reset and a computer, and most people should not do it. Better that they
 * close the screen here than halfway through, with a wiped phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceOwnerScreen(viewModel: FoqosViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var isDeviceOwner by remember { mutableStateOf(viewModel.engine.isDeviceOwner) }

    LaunchedEffect(Unit) {
        while (true) {
            isDeviceOwner = viewModel.engine.isDeviceOwner
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unbypassable blocking") },
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
            item {
                FoqosCard {
                    Text(
                        if (isDeviceOwner) "Device owner is active" else "Device owner is off",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDeviceOwner) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        if (isDeviceOwner) {
                            "While a session runs, blocked apps will not launch at all, Foqos " +
                                "cannot be uninstalled, and the phone cannot boot into safe mode."
                        } else {
                            "Blocking currently relies on the accessibility shield, which can be " +
                                "switched off in Android's settings."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!isDeviceOwner) {
                item { SectionTitle("Read this first") }
                item {
                    FoqosCard {
                        Text(
                            "This is a lot of trouble and most people should skip it.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Android only lets an app become device owner on a phone with no " +
                                "Google account on it yet. That means erasing the phone and " +
                                "setting it up again from scratch. There is no way around this " +
                                "and no app can do it for you.\n\n" +
                                "It makes sense on a spare or second phone kept for focus. On " +
                                "your main phone, it usually is not worth it — an NFC tag left " +
                                "in another room already does most of the work.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item { SectionTitle("You will need") }
                item {
                    FoqosCard {
                        Bullet("A computer (Windows, Mac or Linux)")
                        Bullet("A USB cable that carries data, not only power")
                        Bullet("Android Platform Tools, a free download from Google")
                        Bullet("About 30 minutes, plus the time to set the phone up again")
                    }
                }

                item { SectionTitle("Steps") }
                item {
                    FoqosCard {
                        Step(1, "Back up anything you care about. The next step erases the phone.")
                        Step(
                            2,
                            "Factory reset the phone: Settings → System → Reset → Erase all data.",
                        )
                        Step(
                            3,
                            "Set the phone up again, and when it asks you to sign in to Google, " +
                                "choose Skip. Do not add any account yet — this is the step " +
                                "everything else depends on.",
                        )
                        Step(
                            4,
                            "Turn on developer options: Settings → About phone → tap Build " +
                                "number seven times.",
                        )
                        Step(
                            5,
                            "Turn on USB debugging: Settings → System → Developer options → USB " +
                                "debugging.",
                        )
                        Step(
                            6,
                            "On the computer, download Android Platform Tools from " +
                                "developer.android.com/tools/releases/platform-tools and unzip it.",
                        )
                        Step(
                            7,
                            "Connect the phone by USB. A prompt appears on the phone asking to " +
                                "allow debugging — tap Allow.",
                        )
                        Step(
                            8,
                            "Install Foqos on the phone (copy the APK across, or run " +
                                "\"adb install foqos.apk\" from the unzipped folder).",
                        )
                        Step(
                            9,
                            "In a terminal opened in that folder, run the command below. If it " +
                                "prints \"Success\", you are done.",
                        )
                        Step(
                            10,
                            "Now sign in to Google and set the phone up as usual.",
                        )
                    }
                }

                item { SectionTitle("The command") }
                item {
                    CommandCard(
                        command = SETUP_COMMAND,
                        onCopy = {
                            copyToClipboard(context, "Foqos device owner command", SETUP_COMMAND)
                            viewModel.show("Command copied.")
                        },
                    )
                }

                item {
                    FoqosCard {
                        Text("If it fails", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "\"Not allowed to set the device owner because there are already " +
                                "some accounts on the device\" means step 3 was missed — an " +
                                "account got added. The phone has to be erased again.\n\n" +
                                "\"device unauthorized\" means the Allow prompt in step 7 was " +
                                "not accepted. Unplug, plug back in, and watch the phone screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionTitle("Turning it off again") }
            item {
                FoqosCard {
                    Text(
                        "Device owner cannot be removed from inside the app — if it could, it " +
                            "would be just another way out of a block. It takes the computer " +
                            "again, with the same USB debugging setup:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                CommandCard(
                    command = REMOVAL_COMMAND,
                    onCopy = {
                        copyToClipboard(context, "Foqos device owner removal", REMOVAL_COMMAND)
                        viewModel.show("Command copied.")
                    },
                )
            }
            item {
                FoqosCard {
                    Text(
                        "A factory reset also clears it. Foqos never blocks factory reset: that " +
                            "is the one restriction that could leave a phone with no way back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Column(modifier = Modifier.padding(bottom = 32.dp)) {} }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("•  ", color = MaterialTheme.colorScheme.primary)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CommandCard(command: String, onCopy: () -> Unit) {
    FoqosCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                command,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onCopy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) { Text("Copy command") }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
