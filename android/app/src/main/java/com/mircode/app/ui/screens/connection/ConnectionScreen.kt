package com.mircode.app.ui.screens.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mircode.app.data.api.MirCodeApiClient
import com.mircode.app.data.model.ConnectionProfile
import com.mircode.app.data.preferences.DevicePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    initialProfile: ConnectionProfile?,
    onConnect: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { DevicePreferences(context) }

    var savedProfiles by remember { mutableStateOf(prefs.getSavedProfiles()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 6-digit code, 1: LAN Direct

    // 6-digit Pair code states
    var pairCode by remember { mutableStateOf(initialProfile?.pairCode ?: "") }
    var relayToken by remember { mutableStateOf(initialProfile?.token ?: "") }
    var relayUrl by remember { mutableStateOf(initialProfile?.relayUrl ?: "https://relay.mircode.dev") }
    var relayProfileName by remember { mutableStateOf(initialProfile?.name ?: "我的开发机") }

    // Direct LAN states
    var host by remember { mutableStateOf(initialProfile?.host ?: "192.168.1.100") }
    var port by remember { mutableStateOf((initialProfile?.port ?: 18980).toString()) }
    var directToken by remember { mutableStateOf(initialProfile?.token ?: "") }
    var profileName by remember { mutableStateOf(initialProfile?.name ?: "我的开发机") }

    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }

    fun tryConnect(profile: ConnectionProfile) {
        scope.launch {
            isConnecting = true
            errorMessage = null
            val client = MirCodeApiClient(profile)
            client.verifyAuth()
                .onSuccess {
                    prefs.saveProfile(profile)
                    isConnecting = false
                    onConnect(profile)
                }
                .onFailure {
                    isConnecting = false
                    errorMessage = "连接失败: 无法连接至「${profile.name}」(${it.message ?: "网络超时/不可达"})\n请确认电脑端伴侣服务正在运行且端口开放。"
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连接电脑工作区", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showScanner = true }) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "扫码连接",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
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

            // Prominent QR Code Scan Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isConnecting) { showScanner = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "扫描电脑端二维码一键连接",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "扫描终端输出的二维码即可自动配置并登录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Error Message Banner
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Saved devices list
            if (savedProfiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "已保存的设备快捷切换",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedProfiles.forEach { saved ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isConnecting) {
                                    tryConnect(saved)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (saved.connectMode == "relay") Icons.Default.CloudDone else Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = saved.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (saved.connectMode == "relay") "配对码: ${saved.pairCode ?: "未知"}" else "${saved.host}:${saved.port}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = { tryConnect(saved) },
                                    enabled = !isConnecting,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("一键连接", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Tabs for new connection
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("中继服务器 URL") },
                            leadingIcon = { Icon(Icons.Default.CloudQueue, null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val cleanCode = pairCode.replace("-", "").trim()
                                val profile = ConnectionProfile(
                                    name = relayProfileName.ifBlank { "开发机 ($cleanCode)" },
                                    token = relayToken.trim(),
                                    connectMode = "relay",
                                    pairCode = cleanCode,
                                    relayUrl = relayUrl.trim()
                                )
                                tryConnect(profile)
                            },
                            enabled = pairCode.length >= 6 && relayToken.isNotBlank() && !isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("正在预检可用性...")
                            } else {
                                Icon(Icons.Default.Bolt, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("一键免配置异地连接", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // 2. Direct LAN tab
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("设备名称 (自定义)") },
                        leadingIcon = { Icon(Icons.Default.Badge, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("电脑内网 IP 地址") },
                        leadingIcon = { Icon(Icons.Default.Dns, null) },
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
                            tryConnect(profile)
                        },
                        enabled = host.isNotBlank() && directToken.isNotBlank() && !isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("正在连接并验证...")
                        } else {
                            Icon(Icons.Default.Link, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("直连内网工作区", fontWeight = FontWeight.Bold)
                        }
                    }
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
                        text = "1. 在电脑项目目录运行：mircode .\n2. 控制台将自动保留固定的 6 位配对码与二维码\n3. 手机输入配对码与 Token，秒级建立安全加密通道",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onScanned = { profile ->
                showScanner = false
                if (profile.connectMode == "relay") {
                    selectedTab = 0
                    pairCode = profile.pairCode ?: ""
                    relayToken = profile.token
                    relayUrl = profile.relayUrl ?: "https://relay.mircode.dev"
                    relayProfileName = profile.name
                } else {
                    selectedTab = 1
                    host = profile.host
                    port = profile.port.toString()
                    directToken = profile.token
                    profileName = profile.name
                }
                tryConnect(profile)
            }
        )
    }
}
