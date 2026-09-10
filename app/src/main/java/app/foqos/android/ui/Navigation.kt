package app.foqos.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.foqos.android.qr.QrScannerScreen
import app.foqos.android.session.TokenSource
import app.foqos.android.ui.screens.HomeScreen
import app.foqos.android.ui.screens.InsightsScreen
import app.foqos.android.ui.screens.IntroScreen
import app.foqos.android.ui.screens.ProfileEditScreen
import app.foqos.android.ui.screens.ProfileShareScreen
import app.foqos.android.ui.screens.SettingsScreen

object Routes {
    const val INTRO = "intro"
    const val HOME = "home"
    const val PROFILE = "profile/{profileId}"
    const val SHARE = "share/{profileId}"
    const val SCAN = "scan"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"

    fun profile(id: String) = "profile/$id"
    fun share(id: String) = "share/$id"
    const val NEW_PROFILE = "new"
}

@Composable
fun FoqosApp(
    viewModel: FoqosViewModel,
    onArmTagWrite: (String?) -> Unit,
    isArmedForTagWrite: Boolean,
) {
    val navController: NavHostController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val introCompleted by viewModel.introCompleted.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showMessage(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // Wait for the stored flag: NavHost pins its start destination on first composition.
        val startDestination = when (introCompleted) {
            null -> null
            true -> Routes.HOME
            false -> Routes.INTRO
        } ?: return@Scaffold

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.INTRO) {
                IntroScreen(
                    onDone = {
                        viewModel.completeIntro()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.INTRO) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel,
                    onCreateProfile = { navController.navigate(Routes.profile(Routes.NEW_PROFILE)) },
                    onEditProfile = { navController.navigate(Routes.profile(it.id)) },
                    onShareProfile = { navController.navigate(Routes.share(it.id)) },
                    onScan = { navController.navigate(Routes.SCAN) },
                    onInsights = { navController.navigate(Routes.INSIGHTS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(
                route = Routes.PROFILE,
                arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
            ) { entry ->
                ProfileEditScreen(
                    viewModel = viewModel,
                    profileId = entry.arguments?.getString("profileId").orEmpty(),
                    onClose = { navController.popBackStack() },
                    onShare = { navController.navigate(Routes.share(it)) },
                )
            }

            composable(
                route = Routes.SHARE,
                arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
            ) { entry ->
                ProfileShareScreen(
                    viewModel = viewModel,
                    profileId = entry.arguments?.getString("profileId").orEmpty(),
                    onClose = {
                        onArmTagWrite(null)
                        navController.popBackStack()
                    },
                    onArmTagWrite = onArmTagWrite,
                    isArmedForTagWrite = isArmedForTagWrite,
                )
            }

            composable(Routes.SCAN) {
                QrScannerScreen(
                    onResult = { value ->
                        viewModel.onTokenScanned(value, TokenSource.QR)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                )
            }

            composable(Routes.INSIGHTS) {
                InsightsScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = viewModel, onClose = { navController.popBackStack() })
            }
        }
    }
}

private suspend fun SnackbarHostState.showMessage(message: String) {
    showSnackbar(message = message, withDismissAction = true)
}
