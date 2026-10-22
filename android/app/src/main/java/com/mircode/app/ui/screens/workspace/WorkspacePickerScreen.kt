package com.mircode.app.ui.screens.workspace

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
import com.mircode.app.data.model.BrowseEntry
import com.mircode.app.data.model.BrowseResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacePickerScreen(
    apiClient: MirCodeApiClient,
    onWorkspaceSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf("") }
    var parentPath by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadPath(path: String?) {
        scope.launch {
            isLoading = true
            errorMessage = null
            apiClient.browseFs(path)
                .onSuccess { result: BrowseResult ->
                    currentPath = result.currentPath
                    parentPath = result.parentPath.takeIf { it.isNotEmpty() && it != result.currentPath }
                    entries = result.entries.filter { it.isDir }
                    isLoading = false
                }
                .onFailure {
                    errorMessage = it.message ?: "Failed to load directory"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(Unit) {
        loadPath(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择电脑文件夹工作区") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadPath(currentPath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "当前路径:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                apiClient.activateWorkspace(currentPath)
                                onWorkspaceSelected(currentPath)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("设为工作区")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "❌ $errorMessage", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { loadPath(currentPath) }) {
                        Text("重试")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (parentPath != null) {
                        item {
                            ListItem(
                                headlineContent = { Text(".. (返回上一层目录)", fontWeight = FontWeight.Medium) },
                                leadingContent = {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.clickable { loadPath(parentPath) }
                            )
                            HorizontalDivider()
                        }
                    }

                    items(entries) { entry ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.name, fontWeight = FontWeight.Normal)
                                    if (entry.isGitRepo) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text("Git 仓库", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                        }
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    text = entry.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (entry.isGitRepo) Icons.Default.Source else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (entry.isGitRepo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            },
                            trailingContent = {
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.clickable { loadPath(entry.path) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
