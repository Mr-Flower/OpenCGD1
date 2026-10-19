package it.cgd1.sveglia.ui.clock

import android.graphics.Bitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.cgd1.sveglia.data.ThemeMode
import it.cgd1.sveglia.ui.main.ClockReadings
import it.cgd1.sveglia.ui.main.ConnectionState
import it.cgd1.sveglia.ui.main.MainUiState
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Le regressioni segnalate: "°C" che andava a capo e "Batteria" scritto in verticale.
 * Si verifica su un telefono stretto con i caratteri ingranditi; le immagini restano sul dispositivo per controllo.
 */
@RunWith(AndroidJUnit4::class)
class ClockDashboardTest {

    @get:Rule val compose = createComposeRule()

    private val state = MainUiState(
        connection = ConnectionState.Connecting,
        readings = ClockReadings(23.4, 48.0, 87, updatedAtMillis = 0, batteryUpdatedAtMillis = 0),
        firmware = "1.0.1_0130",
    )

    private fun render(widthDp: Int, fontScale: Float, theme: ThemeMode = ThemeMode.LIGHT) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                OpenCgd1Theme(themeMode = theme, dynamicColor = false) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.testTag("root")) {
                        ClockDashboard(state, onRetry = {}, modifier = Modifier.width(widthDp.dp).padding(16.dp))
                    }
                }
            }
        }
    }

    private fun assertSingleLine(text: String) {
        val bounds = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        val width = bounds.right - bounds.left
        assertTrue("$text: ${width} x ${height}", width > height)
    }

    /** Il testo è disegnato per intero: la larghezza misurata non supera quella che il layout gli concede. */
    private fun assertFits(text: String) {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertTrue("$text clipped", !layout.single().hasVisualOverflow)
    }

    private fun save(name: String) {
        val bitmap = compose.onNodeWithTag("root").captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun smallPhoneLargeFont() {
        render(widthDp = 320, fontScale = 1.3f)
        assertSingleLine("Battery 87%")
        assertSingleLine("Connecting…")
        assertFits("Temperature")
        save("dashboard_320_1.3")
    }

    @Test fun regularPhone() {
        render(widthDp = 392, fontScale = 1f)
        assertSingleLine("Battery 87%")
        save("dashboard_392_1.0")
    }

    @Test fun amoled() {
        render(widthDp = 392, fontScale = 1f, theme = ThemeMode.AMOLED)
        save("dashboard_392_amoled")
    }
}
