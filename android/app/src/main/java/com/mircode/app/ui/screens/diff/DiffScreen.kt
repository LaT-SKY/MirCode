package com.mircode.app.ui.screens.diff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.mircode.app.data.model.DiffResult
import com.mircode.app.ui.components.DiffHunkView
import com.mircode.app.ui.theme.ReviewedGreen
import com.mircode.app.ui.theme.RevokedOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffScreen(
    apiClient: MirCodeApiClient,
    filePath: String,
    isStaged: Boolean,
    initialReviewed: Boolean,
    modifiedSinceReview: Boolean,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var diffContent by remember { mutableStateOf("") }
    var isReviewed by remember { mutableStateOf(initialReviewed) }
    var hasModifiedWarning by remember { mutableStateOf(modifiedSinceReview) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadDiff() {
        scope.launch {
            isLoading = true
            errorMessage = null
            apiClient.getDiff(filePath, isStaged)
                .onSuccess { result: DiffResult ->
                    diffContent = result.diff
                    isLoading = false
                }
                .onFailure {
                    errorMessage = it.message ?: "Failed to load diff"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(filePath, isStaged) {
        loadDiff()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = filePath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isStaged) "已暂存差异 (Staged Diff)" else "工作区差异 (Working Diff)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenEditor(filePath) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit File")
                    }
                    IconButton(onClick = { loadDiff() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch {
                        val targetState = !isReviewed
                        apiClient.toggleReview(filePath, targetState)
                            .onSuccess {
                                isReviewed = it.reviewed
                                hasModifiedWarning = it.modifiedSinceReview
                            }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isReviewed) Icons.Default.CheckCircle else Icons.Default.Check,
                        contentDescription = null
                    )
                },
                text = {
                    Text(if (isReviewed) "已审查 (点击取消)" else "标记为已审查")
                },
                containerColor = if (isReviewed) ReviewedGreen else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Path indicator
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Invalidation Warning Banner
            if (hasModifiedWarning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = RevokedOrange.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RevokedOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "注意：该文件在标记为已审查后再次被修改！请核对最新代码变动后重新确认审查。",
                            style = MaterialTheme.typography.bodySmall,
                            color = RevokedOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
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
                        Button(onClick = { loadDiff() }) {
                            Text("重试")
                        }
                    }
                } else if (diffContent.isBlank()) {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "无差异内容 (文件内容与目标版本相同)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        DiffHunkView(diffText = diffContent)
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
