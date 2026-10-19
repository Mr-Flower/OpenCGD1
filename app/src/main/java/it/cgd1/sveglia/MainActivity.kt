package it.cgd1.sveglia

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import it.cgd1.sveglia.ui.OpenCgd1App
import it.cgd1.sveglia.ui.settings.AppSettingsViewModel
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import it.cgd1.sveglia.ui.theme.isDark

// AppCompatActivity, non ComponentActivity: serve ad AppCompatDelegate per applicare la lingua per-app sotto Android 13.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val settingsViewModel: AppSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { settingsViewModel.uiState.value == null }
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val dark = current.preferences.themeMode.isDark()

            // Il tema dell'app può differire da quello di sistema: le icone delle barre vanno allineate a mano.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }

            OpenCgd1Theme(
                themeMode = current.preferences.themeMode,
                dynamicColor = current.preferences.useDynamicColor,
            ) {
                OpenCgd1App(settingsState = current, settingsActions = settingsViewModel)
            }
        }
    }

    private companion object {
        // Gli stessi scrim che enableEdgeToEdge usa di default per la navigazione a tre pulsanti.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
