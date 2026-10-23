package com.mircode.app.ui.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.preferences.DevicePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    apiClient: MirCodeApiClient,
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { DevicePreferences(context) }
    val settings = remember { prefs.getSettings() }

    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isWordWrap by remember { mutableStateOf(settings.defaultWordWrap) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val fontSize = settings.editorFontSize.sp
    val lineHeight = (settings.editorFontSize * 1.5).sp

    val hasUnsavedChanges = content != originalContent

    fun loadContent() {
        scope.launch {
            isLoading = true
            apiClient.getFileContent(filePath)
                .onSuccess {
                    content = it
                    originalContent = it
                    isLoading = false
                }
                .onFailure {
                    snackbarMessage = "加载失败: ${it.message}"
                    isLoading = false
                }
        }
    }

    LaunchedEffect(filePath) {
        loadContent()
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = filePath.substringAfterLast('/'),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasUnsavedChanges) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("已修改*", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                }
                            }
                        }
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isWordWrap = !isWordWrap }) {
                        Icon(
                            imageVector = if (isWordWrap) Icons.Default.WrapText else Icons.Default.FormatAlignLeft,
                            contentDescription = "自动换行切换",
                            tint = if (isWordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                apiClient.saveFileContent(filePath, content)
                                    .onSuccess {
                                        originalContent = content
                                        snackbarMessage = "保存成功！已同步至电脑"
                                    }
                                    .onFailure {
                                        snackbarMessage = "保存失败: ${it.message}"
                                    }
                                isSaving = false
                            }
                        },
                        enabled = !isSaving && hasUnsavedChanges
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = "Save",
                                tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Virtual Symbol Bar lifted with soft keyboard
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val symbols = listOf("Tab", "{", "}", "(", ")", "[", "]", ";", "=", "\"", "'", ":", "->", ".", ",")
                    symbols.forEach { sym ->
                        FilledTonalButton(
                            onClick = {
                                val insertText = if (sym == "Tab") "    " else sym
                                content += insertText
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(sym, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
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
            } else {
                val verticalScrollState = rememberScrollState()
                val horizontalScrollState = rememberScrollState()
                val textMeasurer = rememberTextMeasurer()
                val density = LocalDensity.current

                val gutterBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                val gutterDivider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                val lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

                val totalLogicalLines = remember(content) {
                    if (content.isEmpty()) 1 else content.count { it == '\n' } + 1
                }
                val digitCount = totalLogicalLines.toString().length.coerceAtLeast(2)
                val sampleDigit = textMeasurer.measure("8", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize))
                val digitWidthPx = sampleDigit.size.width
                val gutterPaddingStartPx = with(density) { 10.dp.toPx() }
                val gutterPaddingEndPx = with(density) { 8.dp.toPx() }
                val gutterWidthPx = (digitCount * digitWidthPx) + gutterPaddingStartPx + gutterPaddingEndPx
                val gutterWidthDp = with(density) { gutterWidthPx.toDp() }

                val editorHeightDp = with(density) {
                    val h = textLayoutResult?.size?.height ?: 0
                    h.toFloat().toDp() + 16.dp
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                ) {
                    // Line numbers Gutter (Canvas pixel-perfect synchronized with TextLayoutResult)
                    Canvas(
                        modifier = Modifier
                            .width(gutterWidthDp)
                            .height(editorHeightDp.coerceAtLeast(200.dp))
                    ) {
                        drawRect(color = gutterBg)
                        drawLine(
                            color = gutterDivider,
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )

                        val layout = textLayoutResult ?: return@Canvas
                        val paddingTop = with(density) { 8.dp.toPx() }
                        val currentScrollY = verticalScrollState.value.toFloat()
                        val visibleMinY = currentScrollY - 200f
                        val visibleMaxY = currentScrollY + 3000f

                        var logicalLine = 1
                        for (i in 0 until layout.lineCount) {
                            val startOffset = layout.getLineStart(i)
                            val isLogicalStart = (i == 0) || (startOffset > 0 && content.getOrNull(startOffset - 1) == '\n')
                            if (isLogicalStart) {
                                val curLine = logicalLine++
                                val y = layout.getLineTop(i) + paddingTop
                                if (y + 50f >= visibleMinY && y <= visibleMaxY) {
                                    val numLayout = textMeasurer.measure(
                                        text = "$curLine",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = fontSize,
                                            color = lineNumberColor
                                        )
                                    )
                                    val x = size.width - gutterPaddingEndPx - numLayout.size.width
                                    drawText(
                                        textLayoutResult = numLayout,
                                        topLeft = Offset(x, y)
                                    )
                                }
                            }
                        }
                    }

                    // Code Editor Text Area
                    val editorModifier = if (isWordWrap) {
                        Modifier
                            .weight(1f)
                            .padding(8.dp)
                    } else {
                        Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                            .padding(8.dp)
                    }

                    Box(modifier = editorModifier) {
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            onTextLayout = { textLayoutResult = it },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = if (isWordWrap) Modifier.fillMaxWidth() else Modifier.wrapContentSize()
                        )
                    }
                }
            }
        }
    }
}
