package it.cgd1.sveglia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import it.cgd1.sveglia.R
import it.cgd1.sveglia.protocol.SensorData
import it.cgd1.sveglia.protocol.Weekday
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

fun formatOffset(minutes: Int): String {
    val sign = if (minutes >= 0) "+" else "−"
    val hours = abs(minutes) / 60
    val rest = abs(minutes) % 60
    return if (rest == 0) "UTC$sign$hours" else "UTC$sign$hours:%02d".format(rest)
}

fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun Weekday.shortName(locale: Locale): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.titlecase(locale) }

private val WORKDAYS = setOf(Weekday.MONDAY, Weekday.TUESDAY, Weekday.WEDNESDAY, Weekday.THURSDAY, Weekday.FRIDAY)
private val WEEKEND = setOf(Weekday.SATURDAY, Weekday.SUNDAY)

@Composable
@ReadOnlyComposable
fun daysLabel(days: Set<Weekday>): String = when {
    days.isEmpty() -> stringResource(R.string.days_once)
    days.size == Weekday.entries.size -> stringResource(R.string.days_every_day)
    days == WORKDAYS -> stringResource(R.string.days_workdays)
    days == WEEKEND -> stringResource(R.string.days_weekend)
    else -> {
        val locale = LocalConfiguration.current.locales[0]
        Weekday.entries.filter { it in days }.joinToString(" ") { it.shortName(locale) }
    }
}

@Composable
@ReadOnlyComposable
fun sensorLabel(sensor: SensorData): String = listOfNotNull(
    sensor.temperature?.let { stringResource(R.string.sensor_temperature, it) },
    sensor.humidity?.let { stringResource(R.string.sensor_humidity, it) },
    sensor.battery?.let { stringResource(R.string.sensor_battery, it) },
).joinToString(" · ")
