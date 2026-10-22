package com.mircode.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.ui.theme.*

sealed interface DiffRow {
    data class Header(val text: String) : DiffRow
    data class Context(val oldLine: Int, val newLine: Int, val text: String) : DiffRow
    data class Add(val newLine: Int, val text: String) : DiffRow
    data class Delete(val oldLine: Int, val text: String) : DiffRow
    data class CollapsedGroup(
        val id: String,
        val count: Int,
        val rows: List<Context>
    ) : DiffRow
}

fun parseDiffWithFolding(
    diffText: String,
    contextWindow: Int = 3,
    foldingThreshold: Int = 6
): List<DiffRow> {
    val rawLines = diffText.lines()
    val parsedRows = mutableListOf<DiffRow>()

    var oldLineNum = 1
    var newLineNum = 1

    var currentContextBlock = mutableListOf<DiffRow.Context>()

    fun flushContextBlock() {
        if (currentContextBlock.isEmpty()) return

        if (currentContextBlock.size > foldingThreshold) {
            val head = currentContextBlock.take(contextWindow)
            val tail = currentContextBlock.takeLast(contextWindow)
            val middle = currentContextBlock.drop(contextWindow).dropLast(contextWindow)

            parsedRows.addAll(head)
            if (middle.isNotEmpty()) {
                val id = "collapsed_${middle.first().oldLine}_${middle.last().oldLine}"
                parsedRows.add(DiffRow.CollapsedGroup(id, middle.size, middle))
            }
            parsedRows.addAll(tail)
        } else {
            parsedRows.addAll(currentContextBlock)
        }
        currentContextBlock = mutableListOf()
    }

    for (line in rawLines) {
        when {
            line.startsWith("@@") -> {
                flushContextBlock()
                // Parse @@ -oldStart,oldCount +newStart,newCount @@
                val match = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""").find(line)
                if (match != null) {
                    oldLineNum = match.groupValues[1].toIntOrNull() ?: 1
                    newLineNum = match.groupValues[2].toIntOrNull() ?: 1
                }
                parsedRows.add(DiffRow.Header(line))
            }
            line.startsWith("---") || line.startsWith("+++") || line.startsWith("diff ") -> {
                flushContextBlock()
                parsedRows.add(DiffRow.Header(line))
            }
            line.startsWith("+") -> {
                flushContextBlock()
                parsedRows.add(DiffRow.Add(newLineNum++, line.substring(1)))
            }
            line.startsWith("-") -> {
                flushContextBlock()
                parsedRows.add(DiffRow.Delete(oldLineNum++, line.substring(1)))
            }
            else -> {
                // Context line (starts with ' ' or normal text)
                val cleanText = if (line.startsWith(" ")) line.substring(1) else line
                currentContextBlock.add(DiffRow.Context(oldLineNum++, newLineNum++, cleanText))
            }
        }
    }
    flushContextBlock()
    return parsedRows
}

@Composable
fun DiffHunkView(
    diffText: String,
    modifier: Modifier = Modifier,
    contextWindow: Int = 3,
    foldingThreshold: Int = 6
) {
    val dark = isSystemInDarkTheme()
    val addBg = if (dark) DiffAddDark else DiffAddLight
    val addText = if (dark) DiffAddTextDark else DiffAddTextLight

    val delBg = if (dark) DiffDeleteDark else DiffDeleteLight
    val delText = if (dark) DiffDeleteTextDark else DiffDeleteTextLight

    val headerBg = if (dark) DiffHeaderDark else DiffHeaderLight

    val rows = remember(diffText, contextWindow, foldingThreshold) {
        parseDiffWithFolding(diffText, contextWindow, foldingThreshold)
    }

    // Keep track of expanded collapsed groups
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        rows.forEach { row ->
            when (row) {
                is DiffRow.Header -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = row.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 15.sp
                        )
                    }
                }
                is DiffRow.Add -> {
                    DiffLineItem(
                        oldNo = "",
                        newNo = "${row.newLine}",
                        prefix = "+",
                        text = row.text,
                        bgColor = addBg,
                        textColor = addText
                    )
                }
                is DiffRow.Delete -> {
                    DiffLineItem(
                        oldNo = "${row.oldLine}",
                        newNo = "",
                        prefix = "-",
                        text = row.text,
                        bgColor = delBg,
                        textColor = delText
                    )
                }
                is DiffRow.Context -> {
                    DiffLineItem(
                        oldNo = "${row.oldLine}",
                        newNo = "${row.newLine}",
                        prefix = " ",
                        text = row.text,
                        bgColor = MaterialTheme.colorScheme.surface,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                is DiffRow.CollapsedGroup -> {
                    val isExpanded = expandedGroups[row.id] == true
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedGroups[row.id] = !isExpanded },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isExpanded) "收起未修改代码 (${row.count} 行)" else "展开 ${row.count} 行未修改代码",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (isExpanded) {
                            row.rows.forEach { ctx ->
                                DiffLineItem(
                                    oldNo = "${ctx.oldLine}",
                                    newNo = "${ctx.newLine}",
                                    prefix = " ",
                                    text = ctx.text,
                                    bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLineItem(
    oldNo: String,
    newNo: String,
    prefix: String,
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = oldNo.padStart(4),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = newNo.padStart(4),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = prefix,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.width(12.dp)
        )
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = textColor,
            lineHeight = 15.sp
        )
    }
}
