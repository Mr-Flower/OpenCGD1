package it.cgd1.sveglia.ui.clock

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.cgd1.sveglia.data.AppLanguage
import it.cgd1.sveglia.data.AppPreferences
import it.cgd1.sveglia.data.ThemeMode
import it.cgd1.sveglia.ui.settings.AppSettingsActions
import it.cgd1.sveglia.ui.settings.AppSettingsScreen
import it.cgd1.sveglia.ui.settings.AppSettingsUiState
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Schermate di controllo per slider e menu a tendina, che senza sveglia non si raggiungono nell'app. */
@RunWith(AndroidJUnit4::class)
class SettingsScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun save(name: String, bitmap: Bitmap) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun deviceSettingsCard() {
        compose.setContent {
            OpenCgd1Theme(themeMode = ThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("root")) {
                    DeviceSettingsCard(
                        settings = previewSettings,
                        currentRingtone = null,
                        onUpdate = {},
                        onVolumeChange = {},
                        onOpenRingtones = {},
                        modifier = Modifier.width(392.dp).padding(16.dp),
                    )
                }
            }
        }
        save("device_settings", compose.onNodeWithTag("root").captureToImage().asAndroidBitmap())
    }

    @Test fun appSettingsWithOpenDropdown() {
        compose.setContent {
            OpenCgd1Theme(themeMode = ThemeMode.DARK) {
                AppSettingsScreen(
                    state = AppSettingsUiState(AppPreferences(themeMode = ThemeMode.DARK), AppLanguage.SYSTEM),
                    actions = object : AppSettingsActions {
                        override fun onThemeModeChange(mode: ThemeMode) = Unit
                        override fun onDynamicColorChange(enabled: Boolean) = Unit
                        override fun onLanguageChange(language: AppLanguage) = Unit
                        override fun onCompleteOnboarding() = Unit
                        override fun onShowOnboarding() = Unit
                    },
                    onBack = {},
                )
            }
        }
        save("app_settings", compose.onRoot().captureToImage().asAndroidBitmap())
        compose.onNodeWithText("Follow device").performClick()
        compose.waitForIdle()
        save("app_settings_language_open", compose.onRoot().captureToImage().asAndroidBitmap())
    }
}
