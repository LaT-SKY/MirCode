package com.mircode.app.ui.screens.status

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.model.FileStatus
import com.mircode.app.data.model.StatusResult
import com.mircode.app.ui.components.ConflictResolutionBanner
import com.mircode.app.ui.components.ReviewProgressBar
import com.mircode.app.ui.theme.ReviewedGreen
import com.mircode.app.ui.theme.RevokedOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitStatusScreen(
    apiClient: MirCodeApiClient,
    status: StatusResult,
    onRefresh: () -> Unit,
    onOpenFileDiff: (file: String, staged: Boolean) -> Unit,
    onOpenFileEditor: (file: String) -> Unit,
    onLocateFile: (file: String) -> Unit,
    onOpenConflictResolver: () -> Unit,
    onSwitchWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var showCommitSheet by remember { mutableStateOf(false) }
    var commitMessage by remember { mutableStateOf("") }
    var isAmend by remember { mutableStateOf(false) }
    var isCommitting by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            actionMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = status.branch.ifEmpty { "Detached HEAD" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = status.workspace.substringAfterLast('/'),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSwitchWorkspace) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "Switch Workspace")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            if (status.stagedFiles.isNotEmpty() || isAmend) {
                ExtendedFloatingActionButton(
                    onClick = { showCommitSheet = true },
                    icon = { Icon(Icons.Default.Check, null) },
                    text = { Text("提交 (${status.stagedFiles.size})") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Conflict banner
            item {
                ConflictResolutionBanner(
                    conflictCount = status.conflictedFiles.size,
                    onResolveClick = onOpenConflictResolver
                )
            }

            // 2. Review Progress Bar
            item {
                ReviewProgressBar(
                    reviewedCount = status.totalReviewed,
                    totalCount = status.totalChanged
                )
            }

            // 3. Staged Files Section
            if (status.stagedFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "已暂存的更改 (Staged)",
                        count = status.stagedFiles.size,
                        actionText = "取消全暂存",
                        onAction = {
                            scope.launch {
                                apiClient.unstageFiles(emptyList())
                                onRefresh()
                            }
                        }
                    )
                }

                items(status.stagedFiles) { file ->
                    FileStatusItem(
                        file = file,
                        staged = true,
                        onClick = { onOpenFileDiff(file.path, true) },
                        onEdit = { onOpenFileEditor(file.path) },
                        onLocateInTree = { onLocateFile(file.path) },
                        onToggleStage = {
                            scope.launch {
                                apiClient.unstageFiles(listOf(file.path))
                                onRefresh()
                            }
                        },
                        onToggleReview = {
                            scope.launch {
                                apiClient.toggleReview(file.path, !file.reviewed)
                                onRefresh()
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            // 4. Unstaged Files Section
            if (status.unstagedFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "未暂存的更改 (Changes)",
                        count = status.unstagedFiles.size,
                        actionText = "全部暂存",
                        onAction = {
                            scope.launch {
                                apiClient.stageFiles(emptyList())
                                onRefresh()
                            }
                        }
                    )
                }

                items(status.unstagedFiles) { file ->
                    FileStatusItem(
                        file = file,
                        staged = false,
                        onClick = { onOpenFileDiff(file.path, false) },
                        onEdit = { onOpenFileEditor(file.path) },
                        onLocateInTree = { onLocateFile(file.path) },
                        onToggleStage = {
                            scope.launch {
                                apiClient.stageFiles(listOf(file.path))
                                onRefresh()
                            }
                        },
                        onToggleReview = {
                            scope.launch {
                                apiClient.toggleReview(file.path, !file.reviewed)
                                onRefresh()
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            // 5. Untracked Files Section
            if (status.untrackedFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "未跟踪文件 (Untracked)",
                        count = status.untrackedFiles.size,
                        actionText = "全部暂存",
                        onAction = {
                            scope.launch {
                                apiClient.stageFiles(emptyList())
                                onRefresh()
                            }
                        }
                    )
                }

                items(status.untrackedFiles) { file ->
                    FileStatusItem(
                        file = file,
                        staged = false,
                        onClick = { onOpenFileDiff(file.path, false) },
                        onEdit = { onOpenFileEditor(file.path) },
                        onLocateInTree = { onLocateFile(file.path) },
                        onToggleStage = {
                            scope.launch {
                                apiClient.stageFiles(listOf(file.path))
                                onRefresh()
                            }
                        },
                        onToggleReview = {
                            scope.launch {
                                apiClient.toggleReview(file.path, !file.reviewed)
                                onRefresh()
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            if (status.clean) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ReviewedGreen,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "工作区十分整洁",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "无待暂存或未提交的代码变更",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Commit Bottom Sheet
        if (showCommitSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCommitSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "提交代码变更 (Git Commit)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("提交信息 (Commit Message)") },
                        placeholder = { Text("feat: 描述您在此提交中的变更...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isAmend,
                            onCheckedChange = { isAmend = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Amend (合并到上次提交)")
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                isCommitting = true
                                apiClient.commit(commitMessage, isAmend)
                                    .onSuccess {
                                        actionMessage = "提交成功！"
                                        showCommitSheet = false
                                        commitMessage = ""
                                        onRefresh()
                                    }
                                    .onFailure {
                                        actionMessage = "提交失败: ${it.message}"
                                    }
                                isCommitting = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isCommitting && (commitMessage.isNotBlank() || isAmend),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCommitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("确认提交", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Badge(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(count.toString(), fontSize = 11.sp, modifier = Modifier.padding(2.dp))
            }
        }

        TextButton(onClick = onAction) {
            Text(actionText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun FileStatusItem(
    file: FileStatus,
    staged: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onLocateInTree: () -> Unit,
    onToggleStage: () -> Unit,
    onToggleReview: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        leadingContent = {
            IconButton(onClick = onToggleReview) {
                if (file.reviewed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Reviewed",
                        tint = ReviewedGreen
                    )
                } else if (file.modifiedSinceReview) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Modified since review",
                        tint = RevokedOrange
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Not reviewed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = file.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Column {
                if (file.path.contains('/')) {
                    Text(
                        text = file.path.substringBeforeLast('/'),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (file.modifiedSinceReview) {
                        Badge(
                            containerColor = RevokedOrange.copy(alpha = 0.15f),
                            contentColor = RevokedOrange
                        ) {
                            Text("审查后被改动", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (file.additions > 0) {
                        Text(
                            text = "+${file.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ReviewedGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (file.deletions > 0) {
                        Text(
                            text = "-${file.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLocateInTree) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "在文件树中定位",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Code", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onToggleStage) {
                    Icon(
                        imageVector = if (staged) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                        contentDescription = if (staged) "Unstage" else "Stage",
                        tint = if (staged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}
