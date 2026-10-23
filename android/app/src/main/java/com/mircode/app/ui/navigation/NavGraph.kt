package com.mircode.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.api.MirCodeWebSocketClient
import com.mircode.app.data.model.ConnectionProfile
import com.mircode.app.data.preferences.DevicePreferences
import com.mircode.app.data.repository.WorkspaceRepository
import com.mircode.app.ui.screens.connection.ConnectionScreen
import com.mircode.app.ui.screens.dashboard.DashboardScreen
import com.mircode.app.ui.screens.diff.DiffScreen
import com.mircode.app.ui.screens.editor.EditorScreen
import com.mircode.app.ui.screens.settings.SettingsScreen
import com.mircode.app.ui.screens.workspace.WorkspacePickerScreen
import kotlinx.coroutines.launch

@Composable
fun MirCodeNavGraph(
    initialProfile: ConnectionProfile?,
    wsClient: MirCodeWebSocketClient
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val prefs = remember { DevicePreferences(context) }
    val workspaceRepository = remember { WorkspaceRepository() }

    // Check last profile from preferences if not from intent
    val targetProfile = initialProfile ?: if (prefs.getSettings().autoConnect) prefs.getLastProfile() else null

    var activeProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var apiClient by remember { mutableStateOf<MirCodeApiClient?>(null) }

    val startDest = if (targetProfile != null) "splash" else "connection"

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {
        composable("splash") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            LaunchedEffect(Unit) {
                if (targetProfile != null) {
                    val client = MirCodeApiClient(targetProfile)
                    client.verifyAuth()
                        .onSuccess {
                            activeProfile = targetProfile
                            apiClient = client
                            wsClient.connect(targetProfile)
                            navController.navigate("dashboard") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                        .onFailure {
                            navController.navigate("connection") {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                } else {
                    navController.navigate("connection") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            }
        }

        composable("connection") {
            ConnectionScreen(
                initialProfile = targetProfile ?: activeProfile,
                onConnect = { profile ->
                    activeProfile = profile
                    val client = MirCodeApiClient(profile)
                    apiClient = client
                    wsClient.connect(profile)
                    navController.navigate("dashboard") {
                        popUpTo("connection") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            apiClient?.let { client ->
                DashboardScreen(
                    apiClient = client,
                    wsClient = wsClient,
                    workspaceRepository = workspaceRepository,
                    onOpenFileDiff = { file, staged ->
                        val encoded = java.net.URLEncoder.encode(file, "UTF-8")
                        navController.navigate("diff/$encoded?staged=$staged")
                    },
                    onOpenFileEditor = { file ->
                        val encoded = java.net.URLEncoder.encode(file, "UTF-8")
                        navController.navigate("editor/$encoded")
                    },
                    onSwitchWorkspace = {
                        navController.navigate("workspace_picker")
                    },
                    onOpenSettings = {
                        navController.navigate("settings")
                    }
                )
            }
        }

        composable("settings") {
            SettingsScreen(
                currentProfile = activeProfile,
                onDisconnect = {
                    wsClient.disconnect()
                    workspaceRepository.clear()
                    activeProfile = null
                    apiClient = null
                    navController.navigate("connection") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                },
                onSwitchDevice = { newProfile ->
                    activeProfile = newProfile
                    val client = MirCodeApiClient(newProfile)
                    apiClient = client
                    wsClient.connect(newProfile)
                    workspaceRepository.clear()
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("workspace_picker") {
            apiClient?.let { client ->
                WorkspacePickerScreen(
                    apiClient = client,
                    onWorkspaceSelected = {
                        scope.launch {
                            workspaceRepository.refreshStatus(client)
                            workspaceRepository.refreshWorkspaces(client)
                        }
                        navController.navigate("dashboard") {
                            popUpTo("workspace_picker") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = "diff/{file}?staged={staged}&reviewed={reviewed}&modified={modified}",
            arguments = listOf(
                navArgument("file") { type = NavType.StringType },
                navArgument("staged") { type = NavType.BoolType; defaultValue = false },
                navArgument("reviewed") { type = NavType.BoolType; defaultValue = false },
                navArgument("modified") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val encodedFile = backStackEntry.arguments?.getString("file") ?: ""
            val file = java.net.URLDecoder.decode(encodedFile, "UTF-8")
            val staged = backStackEntry.arguments?.getBoolean("staged") ?: false
            val reviewed = backStackEntry.arguments?.getBoolean("reviewed") ?: false
            val modified = backStackEntry.arguments?.getBoolean("modified") ?: false

            apiClient?.let { client ->
                DiffScreen(
                    apiClient = client,
                    filePath = file,
                    isStaged = staged,
                    initialReviewed = reviewed,
                    modifiedSinceReview = modified,
                    workspaceRepository = workspaceRepository,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { targetFile ->
                        val enc = java.net.URLEncoder.encode(targetFile, "UTF-8")
                        navController.navigate("editor/$enc")
                    }
                )
            }
        }

        composable(
            route = "editor/{file}",
            arguments = listOf(
                navArgument("file") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedFile = backStackEntry.arguments?.getString("file") ?: ""
            val file = java.net.URLDecoder.decode(encodedFile, "UTF-8")

            apiClient?.let { client ->
                EditorScreen(
                    apiClient = client,
                    filePath = file,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
