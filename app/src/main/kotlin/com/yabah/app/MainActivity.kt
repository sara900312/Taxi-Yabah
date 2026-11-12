package com.yabah.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yabah.app.ui.LocalizedApp
import com.yabah.app.ui.nav.YabahNavHost
import com.yabah.app.ui.theme.YabahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as YabahApplication).container
        setContent { YabahRoot(container) }
    }
}

@Composable
fun YabahRoot(container: AppContainer, start: String? = null) {
    val language by container.prefs.language.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAppContainer provides container) {
        LocalizedApp(language) {
            YabahTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (start != null) YabahNavHost(container, start = start) else YabahNavHost(container)
                }
            }
        }
    }
}
