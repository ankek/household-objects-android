package dev.hho.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.hho.android.ui.HhoApp
import dev.hho.android.ui.deeplink.ItemDeepLink
import dev.hho.android.ui.deeplink.parseItemDeepLink
import dev.hho.android.ui.theme.HhoTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingDeepLink by mutableStateOf<ItemDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) captureDeepLink(intent)
        setContent {
            HhoTheme {
                HhoApp(
                    pendingDeepLink = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
    }

    private fun captureDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            parseItemDeepLink(intent.dataString)?.let { pendingDeepLink = it }
        }
    }
}
