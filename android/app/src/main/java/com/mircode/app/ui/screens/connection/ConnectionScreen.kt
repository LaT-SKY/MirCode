package com.mircode.app.ui.screens.connection

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.model.ConnectionProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    initialProfile: ConnectionProfile?,
    onConnect: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 6-digit code, 1: LAN Direct

    // 6-digit Pair code states
    var pairCode by remember { mutableStateOf(initialProfile?.pairCode ?: "") }
    var relayToken by remember { mutableStateOf(initialProfile?.token ?: "") }
    var relayUrl by remember { mutableStateOf(initialProfile?.relayUrl ?: "") }

    // Direct LAN states
    var host by remember { mutableStateOf(initialProfile?.host ?: "192.168.1.100") }
    var port by remember { mutableStateOf((initialProfile?.port ?: 18980).toString()) }
    var directToken by remember { mutableStateOf(initialProfile?.token ?: "") }
    var profileName by remember { mutableStateOf(initialProfile?.name ?: "我的开发机") }

    var isConnecting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接电脑工作区", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "MirCode 移动端代码工作区",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "免配置异地穿透 · 闭环代码审查 · 全套 Git 协作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("🚀 6 位配对码 (异地免配置)", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("🌐 局域网直连", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedTab == 0) {
                // 1. Zero-config Pair code tab
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "请输入电脑终端打印的 6 位配对码",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pairCode,
                            onValueChange = {
                                val filtered = it.filter { c -> c.isDigit() || c == '-' }
                                if (filtered.length <= 7) pairCode = filtered
                            },
                            placeholder = { Text("例如 839-102", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = relayToken,
                            onValueChange = { relayToken = it },
                            label = { Text("安全令牌 Token (终端输出)") },
                            leadingIcon = { Icon(Icons.Default.Key, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("自定义中继服务器 (选填)") },
                            placeholder = { Text("默认: https://relay.mircode.dev") },
                            leadingIcon = { Icon(Icons.Default.CloudQueue, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val finalRelay = if (relayUrl.isNotBlank()) relayUrl.trim() else "https://relay.mircode.dev"
                        val profile = ConnectionProfile(
                            name = "云端开发机 (${pairCode})",
                            token = relayToken.trim(),
                            connectMode = "relay",
                            pairCode = pairCode.trim(),
                            relayUrl = finalRelay
                        )
                        isConnecting = true
                        onConnect(profile)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = pairCode.isNotBlank()
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("正在穿透连接...")
                    } else {
                        Icon(Icons.Default.CloudSync, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("一键免配置异地连接", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // 2. Direct LAN tab
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("设备名称 / 备注") },
                    leadingIcon = { Icon(Icons.Default.Badge, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("内网 IP 地址") },
                    leadingIcon = { Icon(Icons.Default.Lan, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("端口号") },
                    leadingIcon = { Icon(Icons.Default.Numbers, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = directToken,
                    onValueChange = { directToken = it },
                    label = { Text("安全令牌 Token") },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val p = port.toIntOrNull() ?: 18980
                        val profile = ConnectionProfile(
                            name = profileName,
                            host = host.trim(),
                            port = p,
                            token = directToken.trim(),
                            connectMode = "direct"
                        )
                        isConnecting = true
                        onConnect(profile)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Link, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("直连内网工作区", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 电脑端免配置使用提示",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. 在电脑项目目录直接运行：mircode .\n2. 控制台将自动生成 6 位配对码与二维码\n3. 支持 Tailcat P2P 隧道与公共中继，异地随时直通",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
