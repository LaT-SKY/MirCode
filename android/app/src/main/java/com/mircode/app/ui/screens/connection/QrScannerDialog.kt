package com.mircode.app.ui.screens.connection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.mircode.app.data.model.ConnectionProfile
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onScanned: (ConnectionProfile) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            if (hasCameraPermission) {
                CameraScannerView(
                    onDismiss = onDismiss,
                    onScanned = onScanned
                )
            } else {
                PermissionDeniedView(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun CameraScannerView(
    onDismiss: () -> Unit,
    onScanned: (ConnectionProfile) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }

    val scanLineAnim = rememberInfiniteTransition(label = "scanLine")
    val scanLineProgress by scanLineAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // CameraX Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val executor = Executors.newSingleThreadExecutor()

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val reader = MultiFormatReader().apply {
                        val hints = mapOf(
                            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                            DecodeHintType.CHARACTER_SET to "UTF-8"
                        )
                        setHints(hints)
                    }

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (hasScanned) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        try {
                            val bitmap = imageProxy.toBitmap()
                            val width = bitmap.width
                            val height = bitmap.height
                            val pixels = IntArray(width * height)
                            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                            val source = RGBLuminanceSource(width, height, pixels)
                            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                            val result = reader.decodeWithState(binaryBitmap)
                            val text = result.text
                            if (!text.isNullOrBlank()) {
                                val profile = parseConnectionUri(text)
                                if (profile != null) {
                                    hasScanned = true
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        onScanned(profile)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            // No QR found in this frame
                        } finally {
                            reader.reset()
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Overlay with cutout and animated scanline
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = size.width * 0.72f
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2.3f

            val cutoutPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(left, top), Size(boxSize, boxSize)),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                )
            }

            clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                drawRect(color = Color.Black.copy(alpha = 0.65f))
            }

            // Draw border
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )

            // Draw animated laser scan line
            val scanY = top + (boxSize * scanLineProgress)
            drawLine(
                color = Color(0xFF388AF6),
                start = Offset(left + 16.dp.toPx(), scanY),
                end = Offset(left + boxSize - 16.dp.toPx(), scanY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Top bar controls (Close & Flashlight)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }

            Text(
                text = "扫描电脑端二维码",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    val target = !isTorchOn
                    camera?.cameraControl?.enableTorch(target)
                    isTorchOn = target
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "手电筒",
                    tint = if (isTorchOn) Color(0xFFFFD54F) else Color.White
                )
            }
        }

        // Bottom instruction prompt
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "将电脑终端输出的二维码放入框内即可自动识别",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "支持 6 位专属配对码 / 局域网直连自动配置",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionDeniedView(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "扫码功能需要使用您的手机相机来扫描电脑端展示的配对二维码。请授予相机访问权限。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("授予相机权限")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDismiss) {
            Text("取消", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

fun parseConnectionUri(rawText: String): ConnectionProfile? {
    val trimmed = rawText.trim()

    // 1. Standard mircode://connect?... URI
    if (trimmed.startsWith("mircode://", ignoreCase = true)) {
        try {
            val uri = Uri.parse(trimmed)
            val host = uri.getQueryParameter("host") ?: "127.0.0.1"
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 18980
            val token = uri.getQueryParameter("token") ?: ""
            val name = uri.getQueryParameter("name") ?: "MirCode Companion"
            val code = uri.getQueryParameter("code")
            val relay = uri.getQueryParameter("relay") ?: "https://relay.mircode.dev"
            val tailcat = uri.getQueryParameter("tailcat")
            val mode = if (!code.isNullOrBlank() && code != "连接中...") "relay" else "direct"

            return ConnectionProfile(
                name = name,
                host = host,
                port = port,
                token = token,
                connectMode = mode,
                pairCode = code,
                relayUrl = relay,
                tailcatAddress = tailcat
            )
        } catch (_: Exception) {}
    }

    // 2. HTTP URL fallback (e.g., http://192.168.1.100:18980)
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        try {
            val uri = Uri.parse(trimmed)
            val host = uri.host ?: "127.0.0.1"
            val port = if (uri.port != -1) uri.port else (if (trimmed.startsWith("https")) 443 else 80)
            val token = uri.getQueryParameter("token") ?: ""
            return ConnectionProfile(
                name = "局域网电脑",
                host = host,
                port = port,
                token = token,
                connectMode = "direct"
            )
        } catch (_: Exception) {}
    }

    // 3. Raw 6-digit pair code (e.g. 123-456 or 123456)
    val digitOnly = trimmed.filter { it.isDigit() }
    if (digitOnly.length == 6) {
        val formatted = "${digitOnly.substring(0, 3)}-${digitOnly.substring(3, 6)}"
        return ConnectionProfile(
            name = "远程配对电脑",
            host = "127.0.0.1",
            port = 18980,
            token = "",
            connectMode = "relay",
            pairCode = formatted
        )
    }

    return null
}
