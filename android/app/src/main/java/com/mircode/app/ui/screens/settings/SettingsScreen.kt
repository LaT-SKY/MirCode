package com.mircode.app.ui.screens.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.model.AppSettings
import com.mircode.app.data.model.ConnectionProfile
import com.mircode.app.data.preferences.DevicePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentProfile: ConnectionProfile?,
    onDisconnect: () -> Unit,
    onSwitchDevice: (ConnectionProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { DevicePreferences(context) }

    var settings by remember { mutableStateOf(prefs.getSettings()) }
    var savedProfiles by remember { mutableStateOf(prefs.getSavedProfiles()) }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        prefs.saveSettings(newSettings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Current Device Card
            item {
                Text(
                    text = "当前连接设备",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Computer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentProfile?.name ?: "未连接",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (currentProfile?.connectMode == "relay") {
                            Text(
                                text = "配对码: ${currentProfile.pairCode ?: "无"}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "中继地址: ${currentProfile.relayUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "直连地址: ${currentProfile?.host}:${currentProfile?.port}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDisconnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("断开当前连接")
                        }
                    }
                }
            }

            // 2. Saved Devices
            item {
                Text(
                    text = "已保存的设备 (${savedProfiles.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            items(savedProfiles) { profile ->
                val isCurrent = profile.id == currentProfile?.id
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("当前", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                                    }
                                }
                            }
                            Text(
                                text = if (profile.connectMode == "relay") "配对码: ${profile.pairCode}" else "${profile.host}:${profile.port}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isCurrent) {
                            FilledTonalButton(
                                onClick = { onSwitchDevice(profile) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("连接", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        IconButton(
                            onClick = {
                                prefs.removeProfile(profile.id)
                                savedProfiles = prefs.getSavedProfiles()
                            }
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Device",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 3. Connection & Startup Preferences
            item {
                Text(
                    text = "启动与连接偏好",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启动时自动连接上次设备", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "开启后启动应用将预检健康状态并自动直连",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoConnect,
                            onCheckedChange = { updateSettings(settings.copy(autoConnect = it)) }
                        )
                    }
                }
            }

            // 4. Editor Preferences
            item {
                Text(
                    text = "编辑器偏好",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("默认开启自动换行", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "文档与长行代码自动软换行呈现",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.defaultWordWrap,
                                onCheckedChange = { updateSettings(settings.copy(defaultWordWrap = it)) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "编辑器字号 (${settings.editorFontSize} sp)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(12, 13, 14, 16).forEach { size ->
                                    FilterChip(
                                        selected = settings.editorFontSize == size,
                                        onClick = { updateSettings(settings.copy(editorFontSize = size)) },
                                        label = {
                                            Text(
                                                text = "$size sp",
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Diff & Review Preferences
            item {
                Text(
                    text = "差异与代码审查偏好",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "差异上下文保留行数",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(3, 5, 8).forEach { lines ->
                                    FilterChip(
                                        selected = settings.diffContextLines == lines,
                                        onClick = { updateSettings(settings.copy(diffContextLines = lines)) },
                                        label = {
                                            Text(
                                                text = "$lines 行",
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "未修改代码折叠阈值",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(5, 8, 12).forEach { threshold ->
                                    FilterChip(
                                        selected = settings.diffFoldingThreshold == threshold,
                                        onClick = { updateSettings(settings.copy(diffFoldingThreshold = threshold)) },
                                        label = {
                                            Text(
                                                text = "> $threshold 行",
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center,
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. About Section
            item {
                Text(
                    text = "关于 MirCode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "MirCode 移动端代码工作区",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v1.1.0 (Build 2026.10)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "基于 Material Design 3 · TailCat P2P · Jetpack Compose 打造\nMIT License 开源许可",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
