package com.balltrack.ai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.balltrack.ai.game.RosterRepository
import com.balltrack.ai.ui.MainViewModel
import com.balltrack.ai.ui.screens.FaceDataSettingsScreen
import com.balltrack.ai.ui.screens.GameModeScreen
import com.balltrack.ai.ui.screens.LiveSessionScreen
import com.balltrack.ai.ui.screens.SettingsScreen
import com.balltrack.ai.ui.screens.TeamSetupScreen
import com.balltrack.ai.ui.theme.SportTrackTheme
import com.balltrack.ai.vision.FaceRecognitionManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val accentPrefs = remember { com.balltrack.ai.ui.theme.AccentPreferences(this) }
            var accentColor by remember { mutableStateOf(accentPrefs.accentColor) }

            SportTrackTheme(accentColor = accentColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        viewModel = viewModel,
                        accentPrefs = accentPrefs,
                        onAccentChanged = { accentColor = it }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppRoot(
    viewModel: MainViewModel,
    accentPrefs: com.balltrack.ai.ui.theme.AccentPreferences,
    onAccentChanged: (androidx.compose.ui.graphics.Color) -> Unit
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (!cameraPermission.status.isGranted) {
        CameraPermissionScreen(onRequest = { cameraPermission.launchPermissionRequest() })
        return
    }

    val navController = rememberNavController()
    val context = LocalContext.current
    val faceManager = remember { FaceRecognitionManager(context) }
    val rosterRepository = remember { RosterRepository(context) }

    NavHost(navController = navController, startDestination = "game_mode") {
        composable("game_mode") {
            GameModeScreen(viewModel = viewModel, onStart = { navController.navigate("team_setup") })
        }
        composable("team_setup") {
            TeamSetupScreen(
                rosterRepository = rosterRepository,
                faceManager = faceManager,
                featureFlags = viewModel.featureFlags,
                onStart = { teamA, teamB ->
                    viewModel.setTeams(teamA, teamB)
                    navController.navigate("live_session")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("live_session") {
            LiveSessionScreen(viewModel = viewModel, onNavigateToSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenFaceData = { navController.navigate("face_data") },
                onOpenHistory = { navController.navigate("history") },
                onOpenDeveloperMenu = { navController.navigate("developer_menu") },
                accentPrefs = accentPrefs,
                onAccentChanged = onAccentChanged
            )
        }
        composable("developer_menu") {
            com.balltrack.ai.ui.screens.DeveloperMenuScreen(flagsRepository = viewModel.featureFlags, onBack = { navController.popBackStack() })
        }
        composable("history") {
            com.balltrack.ai.ui.screens.SessionHistoryScreen(db = viewModel.db, onBack = { navController.popBackStack() })
        }
        composable("face_data") {
            FaceDataSettingsScreen(faceManager = faceManager, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun CameraPermissionScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Button(onClick = onRequest) {
            Text("Grant Camera Permission to Start")
        }
    }
}
