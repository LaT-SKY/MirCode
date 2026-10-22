package com.mircode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.ui.theme.*

@Composable
fun DiffHunkView(
    diffText: String,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val lines = diffText.lines()

    val addBg = if (dark) DiffAddDark else DiffAddLight
    val addText = if (dark) DiffAddTextDark else DiffAddTextLight

    val delBg = if (dark) DiffDeleteDark else DiffDeleteLight
    val delText = if (dark) DiffDeleteTextDark else DiffDeleteTextLight

    val headerBg = if (dark) DiffHeaderDark else DiffHeaderLight

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val (bgColor, textColor) = when {
                line.startsWith("+") && !line.startsWith("+++") -> addBg to addText
                line.startsWith("-") && !line.startsWith("---") -> delBg to delText
                line.startsWith("@@") -> headerBg to MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format("%3d", index + 1),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(32.dp)
                )

                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = textColor,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
