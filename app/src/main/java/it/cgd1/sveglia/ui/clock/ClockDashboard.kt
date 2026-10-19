package it.cgd1.sveglia.ui.clock

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ui.main.ClockReadings
import it.cgd1.sveglia.ui.main.ConnectionState
import it.cgd1.sveglia.ui.main.MainUiState
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val BATTERY_LOW = 20
private const val BATTERY_MEDIUM = 60

/** Temperatura e umidità in primo piano, come nell'app Qingping; sotto, collegamento e batteria. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClockDashboard(state: MainUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val readings = state.readings
    val celsius = state.settings?.unitCelsius ?: true

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (state.connection as? ConnectionState.Failed)?.let { FailureCard(it, onRetry) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            ReadingTile(
                icon = R.drawable.ic_thermometer,
                label = stringResource(R.string.dashboard_temperature),
                value = readings.temperature?.let { "%.1f".format(locale, if (celsius) it else it * 9 / 5 + 32) },
                unit = if (celsius) "°C" else "°F",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            ReadingTile(
                icon = R.drawable.ic_droplet,
                label = stringResource(R.string.dashboard_humidity),
                value = readings.humidity?.let { "%.0f".format(locale, it) },
                unit = "%",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        // FlowRow: con testi lunghi (o caratteri grandi) le pillole vanno a capo invece di schiacciarsi.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            ConnectionPill(state.connection)
            readings.battery?.let { BatteryPill(it) }
        }
        Text(
            listOfNotNull(updatedText(readings, locale), state.firmware?.let { stringResource(R.string.status_firmware, it) })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadingTile(
    @DrawableRes icon: Int,
    label: String,
    value: String?,
    unit: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val spoken = if (value != null) "$label $value $unit" else "$label —"
    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            // Icona sopra l'etichetta: accanto, su un telefono stretto con caratteri grandi, "Temperatura" non ci sta.
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Top) {
                val numberStyle = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Light)
                // Il numero si rimpicciolisce per stare in una riga; l'unità resta sempre accanto, mai a capo.
                Text(
                    value ?: "—",
                    style = numberStyle,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = numberStyle.fontSize),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (value != null) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionPill(connection: ConnectionState) {
    val label = stringResource(
        when (connection) {
            ConnectionState.Connecting -> R.string.status_connecting_short
            ConnectionState.Connected -> R.string.status_connected
            is ConnectionState.Failed -> R.string.status_failed
            ConnectionState.Idle -> R.string.status_idle_short
        },
    )
    Pill(label) {
        when (connection) {
            ConnectionState.Connecting -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            else -> Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (connection) {
                            ConnectionState.Connected -> MaterialTheme.colorScheme.primary
                            is ConnectionState.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
    }
}

@Composable
private fun BatteryPill(level: Int) {
    val icon = when {
        level <= BATTERY_LOW -> R.drawable.ic_battery_low
        level <= BATTERY_MEDIUM -> R.drawable.ic_battery_medium
        else -> R.drawable.ic_battery_full
    }
    val tint = if (level <= BATTERY_LOW) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Pill(stringResource(R.string.dashboard_battery, level)) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Pill(label: String, leading: @Composable () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            leading()
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun updatedText(readings: ClockReadings, locale: Locale): String {
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
    val updated = readings.updatedAtMillis
    val battery = readings.batteryUpdatedAtMillis
    return when {
        updated == null -> stringResource(R.string.dashboard_no_readings)
        battery != null && battery != updated -> stringResource(R.string.dashboard_updated_battery, time(updated), time(battery))
        else -> stringResource(R.string.dashboard_updated, time(updated))
    }
}

@Composable
private fun FailureCard(failure: ConnectionState.Failed, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (failure.isAuthRejected) R.string.status_rejected else R.string.status_failed),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(failure.reason.asString(), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ClockDashboardPreview() {
    OpenCgd1Theme(dynamicColor = false) {
        ClockDashboard(
            state = MainUiState(
                connection = ConnectionState.Connecting,
                readings = ClockReadings(23.4, 48.0, 87, updatedAtMillis = 0, batteryUpdatedAtMillis = 0),
            ),
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
