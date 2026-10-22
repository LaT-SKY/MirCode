package com.mircode.app.ui.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.api.MirCodeWebSocketClient
import com.mircode.app.data.model.ConnectionProfile
import com.mircode.app.ui.screens.connection.ConnectionScreen
import com.mircode.app.ui.screens.dashboard.DashboardScreen
import com.mircode.app.ui.screens.diff.DiffScreen
import com.mircode.app.ui.screens.editor.EditorScreen
import com.mircode.app.ui.screens.workspace.WorkspacePickerScreen
import kotlinx.coroutines.launch

@Composable
fun MirCodeNavGraph(
    initialProfile: ConnectionProfile?,
    wsClient: MirCodeWebSocketClient
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    var activeProfile by remember { mutableStateOf(initialProfile) }
    var apiClient by remember {
        mutableStateOf(
            activeProfile?.let { MirCodeApiClient(it) }
        )
    }

    NavHost(
        navController = navController,
        startDestination = if (activeProfile != null) "dashboard" else "connection"
    ) {
        composable("connection") {
            ConnectionScreen(
                initialProfile = activeProfile,
                onConnect = { profile ->
                    activeProfile = profile
                    val client = MirCodeApiClient(profile)
                    apiClient = client
                    scope.launch {
                        client.verifyAuth()
                            .onSuccess {
                                wsClient.connect(profile)
                                navController.navigate("dashboard") {
                                    popUpTo("connection") { inclusive = true }
                                }
                            }
                    }
                }
            )
        }

        composable("workspace_picker") {
            apiClient?.let { client ->
                WorkspacePickerScreen(
                    apiClient = client,
                    onWorkspaceSelected = {
                        navController.navigate("dashboard") {
                            popUpTo("workspace_picker") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable("dashboard") {
            apiClient?.let { client ->
                DashboardScreen(
                    apiClient = client,
                    wsClient = wsClient,
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
                    }
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
