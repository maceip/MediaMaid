package ai.musicconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ai.musicconverter.ui.MusicConverterScreen
import ai.musicconverter.ui.components.CrtCloseWrapper
import ai.musicconverter.ui.theme.MusicConverterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep splash visible briefly while app initializes
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        setContent {
            // Dismiss splash once Compose is ready
            keepSplash = false

            MusicConverterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrtCloseWrapper {
                        MusicConverterScreen()
                    }
                }
            }
        }
    }
}
