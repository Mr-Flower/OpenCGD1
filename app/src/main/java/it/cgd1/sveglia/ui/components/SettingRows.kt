package it.cgd1.sveglia.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ui.formatTime
import java.time.LocalTime
import kotlin.math.roundToInt

// Le righe stanno dentro una card: il colore lo dà la card.
private val transparentItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** Riga intera toccabile: Switch senza callback proprio, così TalkBack annuncia un solo elemento. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supportingText?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = transparentItemColors,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Int) -> String = { it.toString() },
) {
    // Valore locale durante il trascinamento: si scrive sulla sveglia solo al rilascio.
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = valueLabel(current.roundToInt())
    Column(modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        ClassicSlider(
            value = current,
            onValueChange = { current = it },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            onValueChangeFinished = {
                val v = current.roundToInt()
                if (v != value) onValueChange(v)
            },
            modifier = Modifier.semantics { stateDescription = shown },
        )
    }
}

private val TrackHeight = 6.dp
private val ThumbSize = 20.dp
private val ThumbPressedSize = 26.dp
private val TickRadius = 1.5.dp

/**
 * Lo stile delle barre di volume nelle Impostazioni di Android: binario sottile arrotondato e pomello rotondo,
 * invece della barretta verticale dello slider M3 recente. Colori e comportamento restano quelli di M3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val thumbSize by animateDpAsState(if (pressed || dragged) ThumbPressedSize else ThumbSize, label = "thumb")
    val colors = SliderDefaults.colors(
        activeTickColor = MaterialTheme.colorScheme.onPrimary,
        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = colors,
        modifier = modifier,
        thumb = {
            Box(Modifier.size(ThumbPressedSize), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(thumbSize)
                        .shadow(2.dp, CircleShape)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                drawStopIndicator = null,
                drawTick = { offset, color -> drawCircle(color, radius = TickRadius.toPx(), center = offset) },
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = TrackHeight / 2,
                modifier = Modifier.height(TrackHeight),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRow(label: String, value: LocalTime, onValueChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                formatTime(value.hour, value.minute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        colors = transparentItemColors,
        modifier = modifier.clickable(role = Role.Button) { open = true },
    )
    if (open) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onValueChange(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
