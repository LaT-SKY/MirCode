package com.mircode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mircode.app.data.api.MirCodeWebSocketClient
import com.mircode.app.data.model.ConnectionProfile
import com.mircode.app.ui.navigation.MirCodeNavGraph
import com.mircode.app.ui.theme.MirCodeTheme

class MainActivity : ComponentActivity() {

    private val wsClient = MirCodeWebSocketClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if launched with QR code deep link: mircode://connect?...
        val profileFromIntent = intent?.data?.let { uri ->
            if (uri.scheme == "mircode" && uri.host == "connect") {
                val host = uri.getQueryParameter("host") ?: "127.0.0.1"
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 18980
                val token = uri.getQueryParameter("token") ?: ""
                val name = uri.getQueryParameter("name") ?: "MirCode Companion"
                val code = uri.getQueryParameter("code")
                val relay = uri.getQueryParameter("relay") ?: "https://relay.mircode.dev"
                val tailcat = uri.getQueryParameter("tailcat")

                val mode = if (!code.isNullOrBlank() && code != "连接中...") "relay" else "direct"

                ConnectionProfile(
                    name = name,
                    host = host,
                    port = port,
                    token = token,
                    connectMode = mode,
                    pairCode = code,
                    relayUrl = relay,
                    tailcatAddress = tailcat
                )
            } else null
        }

        setContent {
            MirCodeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MirCodeNavGraph(
                        initialProfile = profileFromIntent,
                        wsClient = wsClient
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsClient.disconnect()
    }
}
