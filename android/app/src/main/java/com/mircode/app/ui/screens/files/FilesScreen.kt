package com.mircode.app.ui.screens.files

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.model.BrowseEntry
import com.mircode.app.data.model.BrowseResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    apiClient: MirCodeApiClient,
    workspacePath: String,
    onOpenFile: (String) -> Unit,
    highlightFilePath: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(workspacePath) }
    var parentPath by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadDir(target: String?) {
        scope.launch {
            isLoading = true
            errorMessage = null
            apiClient.browseFs(target)
                .onSuccess { res: BrowseResult ->
                    currentPath = res.currentPath
                    parentPath = res.parentPath.takeIf { it.isNotEmpty() && it != res.currentPath }
                    entries = res.entries
                    isLoading = false
                }
                .onFailure {
                    errorMessage = it.message ?: "Failed to read directory"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(workspacePath, highlightFilePath) {
        if (!highlightFilePath.isNullOrBlank()) {
            val parentDir = if (highlightFilePath.contains('/')) {
                highlightFilePath.substringBeforeLast('/')
            } else workspacePath
            loadDir(parentDir)
        } else {
            loadDir(workspacePath.ifEmpty { null })
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Path Header Bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (parentPath != null && currentPath != workspacePath) {
                    IconButton(
                        onClick = { loadDir(parentPath) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go to Parent")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = currentPath.replace(workspacePath, "."),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { loadDir(currentPath) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "❌ $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { loadDir(currentPath) }) {
                        Text("重试")
                    }
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "该目录下无文件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries) { item ->
                        val isHighlighted = highlightFilePath != null &&
                                (item.path == highlightFilePath || item.path.endsWith(highlightFilePath))

                        FileTreeItem(
                            entry = item,
                            isHighlighted = isHighlighted,
                            onClick = {
                                if (item.isDir) {
                                    loadDir(item.path)
                                } else {
                                    onOpenFile(item.path)
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTreeItem(
    entry: BrowseEntry,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when {
            entry.isDir -> Icons.Default.Folder to MaterialTheme.colorScheme.primary
            entry.name.endsWith(".kt") || entry.name.endsWith(".java") -> Icons.Default.Code to Color(0xFF7F52FF)
            entry.name.endsWith(".go") -> Icons.Default.Code to Color(0xFF00ADD8)
            entry.name.endsWith(".json") || entry.name.endsWith(".xml") || entry.name.endsWith(".yaml") -> Icons.Default.Description to Color(0xFFE28935)
            entry.name.endsWith(".md") || entry.name.endsWith(".txt") -> Icons.Default.Article to MaterialTheme.colorScheme.secondary
            else -> Icons.Default.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDir) {
                Text(
                    text = formatFileSize(entry.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (entry.isDir) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
