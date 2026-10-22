package com.mircode.app.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.api.MirCodeWebSocketClient
import com.mircode.app.data.model.StatusResult
import com.mircode.app.data.model.WorkspaceEntry
import com.mircode.app.data.repository.WorkspaceRepository
import com.mircode.app.ui.screens.branches.BranchesScreen
import com.mircode.app.ui.screens.files.FilesScreen
import com.mircode.app.ui.screens.status.GitStatusScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    apiClient: MirCodeApiClient,
    wsClient: MirCodeWebSocketClient,
    workspaceRepository: WorkspaceRepository,
    onOpenFileDiff: (file: String, staged: Boolean) -> Unit,
    onOpenFileEditor: (file: String) -> Unit,
    onSwitchWorkspace: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Review, 1: Files, 2: Branches
    var locateFilePath by remember { mutableStateOf<String?>(null) }
    var showWorkspaceSheet by remember { mutableStateOf(false) }

    val status by workspaceRepository.status.collectAsState()
    val savedWorkspaces by workspaceRepository.workspaces.collectAsState()

    fun refreshAll() {
        scope.launch {
            workspaceRepository.refreshStatus(apiClient)
            workspaceRepository.refreshWorkspaces(apiClient)
        }
    }

    LaunchedEffect(Unit) {
        if (status == null) {
            refreshAll()
        } else {
            // Silently background refresh
            scope.launch {
                workspaceRepository.refreshStatus(apiClient)
                workspaceRepository.refreshWorkspaces(apiClient)
            }
        }

        // Listen to WebSocket events from companion
        wsClient.events.collectLatest { msg ->
            when (msg.type) {
                "FILE_CHANGED", "GIT_STATUS_UPDATED", "REVIEW_INVALIDATED", "WORKSPACE_SWITCHED" -> {
                    workspaceRepository.refreshStatus(apiClient)
                    workspaceRepository.refreshWorkspaces(apiClient)
                }
            }
        }
    }

    val currentStatus = status ?: StatusResult()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .clickable { showWorkspaceSheet = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentStatus.workspace.substringAfterLast('/').ifEmpty { "工作区" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch Workspace",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = currentStatus.branch.ifEmpty { "MirCode" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = "Review & Changes") },
                    label = { Text("审查 & 变更") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Files") },
                    label = { Text("文件") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AltRoute, contentDescription = "Branches & Sync") },
                    label = { Text("分支 & 工具") }
                )
            }
        }
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> GitStatusScreen(
                    apiClient = apiClient,
                    status = currentStatus,
                    onRefresh = { refreshAll() },
                    onOpenFileDiff = onOpenFileDiff,
                    onOpenFileEditor = onOpenFileEditor,
                    onLocateFile = { file ->
                        locateFilePath = file
                        selectedTab = 1
                    },
                    onOpenConflictResolver = { selectedTab = 2 },
                    onSwitchWorkspace = { showWorkspaceSheet = true },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> FilesScreen(
                    apiClient = apiClient,
                    workspacePath = currentStatus.workspace,
                    onOpenFile = onOpenFileEditor,
                    highlightFilePath = locateFilePath,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> BranchesScreen(
                    apiClient = apiClient,
                    status = currentStatus,
                    onRefresh = { refreshAll() },
                    onOpenEditor = onOpenFileEditor,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Workspace Quick Switcher Sheet
        if (showWorkspaceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showWorkspaceSheet = false },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "远程工作区切换",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            showWorkspaceSheet = false
                            onSwitchWorkspace()
                        }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Browse folders")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (savedWorkspaces.isEmpty()) {
                        Text(
                            text = "暂无保存的历史工作区，点击右上角浏览文件夹添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(savedWorkspaces) { ws ->
                                val isCurrent = ws.path == currentStatus.workspace
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                apiClient.activateWorkspace(ws.path)
                                                refreshAll()
                                                showWorkspaceSheet = false
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (ws.isGit) Icons.Default.Source else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = ws.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (isCurrent) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                        Text("当前", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                                    }
                                                }
                                            }
                                            Text(
                                                text = ws.path,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    apiClient.deleteWorkspace(ws.path)
                                                    workspaceRepository.refreshWorkspaces(apiClient)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            showWorkspaceSheet = false
                            onSwitchWorkspace()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("浏览电脑文件夹添加新工作区")
                    }
                }
            }
        }
    }
}
