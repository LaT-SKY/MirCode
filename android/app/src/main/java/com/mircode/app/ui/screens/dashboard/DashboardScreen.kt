package com.mircode.app.ui.screens.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.api.MirCodeWebSocketClient
import com.mircode.app.data.model.StatusResult
import com.mircode.app.ui.screens.branches.BranchesScreen
import com.mircode.app.ui.screens.status.GitStatusScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    apiClient: MirCodeApiClient,
    wsClient: MirCodeWebSocketClient,
    onOpenFileDiff: (file: String, staged: Boolean) -> Unit,
    onOpenFileEditor: (file: String) -> Unit,
    onSwitchWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var gitStatus by remember { mutableStateOf(StatusResult()) }

    fun refreshStatus() {
        scope.launch {
            apiClient.getGitStatus().onSuccess {
                gitStatus = it
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
        // Listen to real-time events from Companion
        wsClient.events.collectLatest { msg ->
            when (msg.type) {
                "FILE_CHANGED", "GIT_STATUS_UPDATED", "REVIEW_INVALIDATED", "WORKSPACE_SWITCHED" -> {
                    refreshStatus()
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = "Changes & Review") },
                    label = { Text("审查 & 变更") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AltRoute, contentDescription = "Branches & Sync") },
                    label = { Text("分支 & 工具") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> GitStatusScreen(
                apiClient = apiClient,
                status = gitStatus,
                onRefresh = { refreshStatus() },
                onOpenFileDiff = onOpenFileDiff,
                onOpenFileEditor = onOpenFileEditor,
                onOpenConflictResolver = { selectedTab = 1 },
                onSwitchWorkspace = onSwitchWorkspace,
                modifier = modifier.padding(padding)
            )
            1 -> BranchesScreen(
                apiClient = apiClient,
                status = gitStatus,
                onRefresh = { refreshStatus() },
                onOpenEditor = onOpenFileEditor,
                modifier = modifier.padding(padding)
            )
        }
    }
}
