package com.mircode.app.ui.screens.branches

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.model.BranchInfo
import com.mircode.app.data.model.StashItem
import com.mircode.app.data.model.StatusResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(
    apiClient: MirCodeApiClient,
    status: StatusResult,
    onRefresh: () -> Unit,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var branches by remember { mutableStateOf<List<BranchInfo>>(emptyList()) }
    var stashes by remember { mutableStateOf<List<StashItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var showStashDialog by remember { mutableStateOf(false) }
    var stashMessage by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    fun loadData() {
        scope.launch {
            isLoading = true
            apiClient.listBranches().onSuccess { branches = it }
            apiClient.listStashes().onSuccess { stashes = it }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            feedbackMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("分支与 Git 工具箱", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { loadData(); onRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Sync actions: Fetch, Pull, Push
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "远程同步操作",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        apiClient.sync("fetch")
                                            .onSuccess { feedbackMessage = "Fetch 成功" }
                                            .onFailure { feedbackMessage = "Fetch 失败: ${it.message}" }
                                        loadData()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudSync, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fetch", maxLines = 1, softWrap = false, fontSize = 13.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        apiClient.sync("pull")
                                            .onSuccess { feedbackMessage = "Pull 完成: $it" }
                                            .onFailure { feedbackMessage = "Pull 失败: ${it.message}" }
                                        loadData()
                                        onRefresh()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pull", maxLines = 1, softWrap = false, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        apiClient.sync("push")
                                            .onSuccess { feedbackMessage = "Push 成功！" }
                                            .onFailure { feedbackMessage = "Push 失败: ${it.message}" }
                                        loadData()
                                        onRefresh()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Push", maxLines = 1, softWrap = false, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 2. Conflict Resolver Section
            if (status.safeConflictedFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "冲突解决 (${status.safeConflictedFiles.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(status.safeConflictedFiles) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            apiClient.resolveConflict(file.path, "ours")
                                            onRefresh()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("采纳本地 (Ours)", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            apiClient.resolveConflict(file.path, "theirs")
                                            onRefresh()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("采纳传入 (Theirs)", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { onOpenEditor(file.path) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("编辑器合并", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Branches section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分支列表 (${branches.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showNewBranchDialog = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建分支")
                    }
                }
            }

            items(branches) { branch ->
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = branch.safeName,
                                fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (branch.isCurrent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("当前", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                }
                            }
                            if (branch.isRemote) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text("远程", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                }
                            }
                        }
                    },
                    supportingContent = {
                        val sha = branch.safeCommitSha.take(7)
                        val msg = branch.safeMessage
                        val display = if (sha.isNotEmpty() && msg.isNotEmpty()) {
                            "$sha - $msg"
                        } else sha.ifEmpty { msg }
                        if (display.isNotEmpty()) {
                            Text(
                                text = display,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.ForkRight,
                            contentDescription = null,
                            tint = if (branch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        if (!branch.isCurrent && !branch.isRemote) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        apiClient.branchAction("checkout", branch.safeName)
                                            .onSuccess {
                                                feedbackMessage = "已切换到分支 ${branch.safeName}"
                                                loadData()
                                                onRefresh()
                                            }
                                            .onFailure { feedbackMessage = "切换失败: ${it.message}" }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("检出", fontSize = 12.sp)
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            // 4. Stashes section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Git 储藏 (Stashes: ${stashes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showStashDialog = true }) {
                        Icon(Icons.Default.Archive, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("存入 Stash")
                    }
                }
            }

            items(stashes) { stash ->
                ListItem(
                    headlineContent = { Text(stash.safeName, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(stash.safeMessage) },
                    trailingContent = {
                        Button(
                            onClick = {
                                scope.launch {
                                    apiClient.stashAction("pop", index = stash.index)
                                        .onSuccess {
                                            feedbackMessage = "已恢复 Stash: ${stash.safeName}"
                                            loadData()
                                            onRefresh()
                                        }
                                        .onFailure { feedbackMessage = "恢复失败: ${it.message}" }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Pop", fontSize = 12.sp)
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // New branch dialog
        if (showNewBranchDialog) {
            AlertDialog(
                onDismissRequest = { showNewBranchDialog = false },
                title = { Text("新建分支") },
                text = {
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        label = { Text("分支名称") },
                        placeholder = { Text("feature/my-new-feature") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                apiClient.branchAction("create", newBranchName)
                                    .onSuccess {
                                        feedbackMessage = "分支 $newBranchName 创建并检出成功"
                                        showNewBranchDialog = false
                                        newBranchName = ""
                                        loadData()
                                        onRefresh()
                                    }
                                    .onFailure { feedbackMessage = "创建失败: ${it.message}" }
                            }
                        },
                        enabled = newBranchName.isNotBlank()
                    ) {
                        Text("创建并检出")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewBranchDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // Stash dialog
        if (showStashDialog) {
            AlertDialog(
                onDismissRequest = { showStashDialog = false },
                title = { Text("存入 Git 储藏 (Stash)") },
                text = {
                    OutlinedTextField(
                        value = stashMessage,
                        onValueChange = { stashMessage = it },
                        label = { Text("储藏备注 (可选)") },
                        placeholder = { Text("WIP on feature...") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                apiClient.stashAction("push", message = stashMessage)
                                    .onSuccess {
                                        feedbackMessage = "已储藏工作区更改"
                                        showStashDialog = false
                                        stashMessage = ""
                                        loadData()
                                        onRefresh()
                                    }
                                    .onFailure { feedbackMessage = "储藏失败: ${it.message}" }
                            }
                        }
                    ) {
                        Text("确认储藏")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStashDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}
