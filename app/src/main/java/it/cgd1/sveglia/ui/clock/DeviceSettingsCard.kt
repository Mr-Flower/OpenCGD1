package it.cgd1.sveglia.ui.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.protocol.DeviceSettings
import it.cgd1.sveglia.protocol.Language
import it.cgd1.sveglia.ui.components.SliderRow
import it.cgd1.sveglia.ui.components.SwitchRow
import it.cgd1.sveglia.ui.components.TimeRow

private const val VOLUME_MIN = 1
private const val VOLUME_MAX = 5
private const val BRIGHTNESS_STEP = 10

@Composable
fun DeviceSettingsCard(
    settings: DeviceSettings,
    onUpdate: ((DeviceSettings) -> DeviceSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val percent: (Int) -> String = { resources.getString(R.string.value_percent, it) }
    ElevatedCard(modifier) {
        Column(Modifier.padding(vertical = 8.dp)) {
            // Il firmware può riportare valori fuori dal range noto: l'intervallo si allarga per non perderli.
            SliderRow(
                label = stringResource(R.string.settings_volume),
                value = settings.volume,
                range = minOf(VOLUME_MIN, settings.volume)..maxOf(VOLUME_MAX, settings.volume),
                step = 1,
                onValueChange = { v -> onUpdate { it.copy(volume = v) } },
            )
            SwitchRow(
                stringResource(R.string.settings_24h),
                settings.timeFormat24h,
                onCheckedChange = { on -> onUpdate { it.copy(timeFormat24h = on) } },
            )
            SwitchRow(
                stringResource(R.string.settings_celsius),
                settings.unitCelsius,
                onCheckedChange = { on -> onUpdate { it.copy(unitCelsius = on) } },
            )
            SwitchRow(
                stringResource(R.string.settings_english),
                settings.language == Language.ENGLISH,
                supportingText = stringResource(R.string.settings_english_hint),
                onCheckedChange = { on -> onUpdate { it.copy(language = if (on) Language.ENGLISH else Language.CHINESE) } },
            )
            SliderRow(
                label = stringResource(R.string.settings_day_brightness),
                value = settings.dayBrightness,
                range = 0..100,
                step = BRIGHTNESS_STEP,
                valueLabel = percent,
                onValueChange = { v -> onUpdate { it.copy(dayBrightness = v) } },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                stringResource(R.string.settings_night_mode),
                settings.nightMode,
                onCheckedChange = { on -> onUpdate { it.copy(nightMode = on) } },
            )
            if (settings.nightMode) {
                SliderRow(
                    label = stringResource(R.string.settings_night_brightness),
                    value = settings.nightBrightness,
                    range = 0..100,
                    step = BRIGHTNESS_STEP,
                    valueLabel = percent,
                    onValueChange = { v -> onUpdate { it.copy(nightBrightness = v) } },
                )
                TimeRow(stringResource(R.string.settings_night_start), settings.nightStart, { t -> onUpdate { it.copy(nightStart = t) } })
                TimeRow(stringResource(R.string.settings_night_end), settings.nightEnd, { t -> onUpdate { it.copy(nightEnd = t) } })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.settings_screen_light, settings.screenLightSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
