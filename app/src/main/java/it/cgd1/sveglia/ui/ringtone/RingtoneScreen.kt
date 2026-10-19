package it.cgd1.sveglia.ui.ringtone

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.data.ringtone.BUILT_IN_RINGTONES
import it.cgd1.sveglia.data.ringtone.CurrentRingtone
import it.cgd1.sveglia.data.ringtone.RingtoneOption
import it.cgd1.sveglia.protocol.Alarm
import it.cgd1.sveglia.protocol.toHex
import it.cgd1.sveglia.ui.components.SectionHeader
import it.cgd1.sveglia.ui.components.contentWidth
import it.cgd1.sveglia.ui.formatTime
import it.cgd1.sveglia.ui.main.CUSTOM_RINGTONE_PREVIEW_ID
import it.cgd1.sveglia.ui.main.CustomRingtoneInfo
import it.cgd1.sveglia.ui.main.MainUiState
import it.cgd1.sveglia.ui.main.OriginalRingtonesState
import it.cgd1.sveglia.ui.main.RingtoneActions
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneScreen(
    state: MainUiState,
    actions: RingtoneActions,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    DisposableEffect(actions) { onDispose { actions.onStopPreview() } }
    LaunchedEffect(actions) { actions.onLoadOriginalRingtones() }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { actions.onCustomRingtonePicked(it.toString()) }
    }
    val uploading = state.ringtoneUploadProgress != null
    val canChange = !state.isBusy && state.settings != null
    val current = state.currentRingtone
    val currentHex = state.settings?.ringtoneSignature?.toHex()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ringtone_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "current") {
                CurrentRingtoneCard(
                    current = current,
                    uploadProgress = state.ringtoneUploadProgress,
                    testAlarm = state.testAlarm,
                    canTest = canChange && !uploading && state.testAlarm == null,
                    onTest = actions::onTestRingtone,
                    modifier = Modifier.contentWidth(),
                )
            }
            item(key = "custom-header") {
                SectionHeader(stringResource(R.string.ringtone_custom_section), Modifier.contentWidth())
            }
            item(key = "custom") {
                CustomRingtoneCard(
                    custom = state.customRingtone,
                    isConverting = state.isConvertingRingtone,
                    isPreviewing = state.previewingRingtoneId == CUSTOM_RINGTONE_PREVIEW_ID,
                    enabled = canChange,
                    onPick = { pickFile.launch(arrayOf("audio/*")) },
                    actions = actions,
                    modifier = Modifier.contentWidth(),
                )
            }
            item(key = "original-header") {
                SectionHeader(stringResource(R.string.ringtone_original_section), Modifier.contentWidth())
            }
            when (val originals = state.originalRingtones) {
                is OriginalRingtonesState.Loaded -> items(originals.ringtones, key = { it.id }) { ringtone ->
                    RingtoneRow(
                        ringtone = ringtone,
                        subtitle = stringResource(R.string.ringtone_original_source),
                        selected = currentHex == ringtone.signatureHex,
                        isPreviewing = state.previewingRingtoneId == ringtone.id,
                        enabled = canChange && !uploading,
                        actions = actions,
                        modifier = Modifier.contentWidth(),
                    )
                }
                OriginalRingtonesState.Failed -> item(key = "original-failed") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.contentWidth()) {
                        Text(
                            stringResource(R.string.ringtone_original_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = actions::onLoadOriginalRingtones) { Text(stringResource(R.string.action_retry)) }
                    }
                }
                else -> item(key = "original-loading") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.contentWidth()) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.ringtone_original_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item(key = "builtin-header") {
                SectionHeader(stringResource(R.string.ringtone_builtin_section), Modifier.contentWidth())
            }
            items(state.builtInRingtones, key = { it.id }) { ringtone ->
                RingtoneRow(
                    ringtone = ringtone,
                    subtitle = stringResource(R.string.ringtone_attribution, ringtone.author, ringtone.license),
                    selected = currentHex == ringtone.signatureHex,
                    isPreviewing = state.previewingRingtoneId == ringtone.id,
                    enabled = canChange && !uploading,
                    actions = actions,
                    modifier = Modifier.contentWidth(),
                )
            }
            item(key = "credits") {
                Text(
                    stringResource(R.string.ringtone_credits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .contentWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}


@Composable
private fun CurrentRingtoneCard(
    current: CurrentRingtone?,
    uploadProgress: Float?,
    testAlarm: Alarm?,
    canTest: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.ringtone_current), style = MaterialTheme.typography.labelLarge)
            Text(current?.label() ?: "—", style = MaterialTheme.typography.headlineSmall)
            if (uploadProgress != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.ringtone_uploading, (uploadProgress * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ringtone_uploading_hint), style = MaterialTheme.typography.bodySmall)
            } else {
                Spacer(Modifier.height(12.dp))
                if (testAlarm != null) {
                    Text(
                        stringResource(R.string.ringtone_test_pending, formatTime(testAlarm.hour, testAlarm.minute)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    FilledTonalButton(onClick = onTest, enabled = canTest) {
                        Icon(painterResource(R.drawable.ic_clock), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ringtone_test))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ringtone_test_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CustomRingtoneCard(
    custom: CustomRingtoneInfo?,
    isConverting: Boolean,
    isPreviewing: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
    actions: RingtoneActions,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            if (custom == null) {
                Text(
                    stringResource(R.string.ringtone_custom_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (isConverting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.ringtone_converting))
                    }
                } else {
                    FilledTonalButton(onClick = onPick, enabled = enabled) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ringtone_pick_file))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(custom.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.ringtone_duration, custom.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PreviewButton(custom.name, isPreviewing, onPreview = actions::onPreviewCustomRingtone, onStop = actions::onStopPreview)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = actions::onDismissCustomRingtone) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = actions::onUploadCustomRingtone, enabled = enabled) {
                        Text(stringResource(R.string.ringtone_upload))
                    }
                }
            }
        }
    }
}

@Composable
private fun RingtoneRow(
    ringtone: RingtoneOption,
    subtitle: String,
    selected: Boolean,
    isPreviewing: Boolean,
    enabled: Boolean,
    actions: RingtoneActions,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier) {
        ListItem(
            leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
            headlineContent = { Text(ringtone.name) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                PreviewButton(
                    name = ringtone.name,
                    isPreviewing = isPreviewing,
                    onPreview = { actions.onPreviewRingtone(ringtone.id) },
                    onStop = actions::onStopPreview,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { actions.onSelectRingtone(ringtone.id) },
            ),
        )
    }
}

@Composable
private fun PreviewButton(name: String, isPreviewing: Boolean, onPreview: () -> Unit, onStop: () -> Unit) {
    if (isPreviewing) {
        IconButton(onClick = onStop) {
            Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.ringtone_stop_preview, name))
        }
    } else {
        IconButton(onClick = onPreview) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.ringtone_preview, name))
        }
    }
}

@Composable
fun CurrentRingtone.label(): String = when (this) {
    is CurrentRingtone.BuiltIn -> ringtone.name
    is CurrentRingtone.Original -> stringResource(R.string.ringtone_original, name)
    CurrentRingtone.Custom -> stringResource(R.string.ringtone_custom)
    is CurrentRingtone.Unknown -> stringResource(R.string.ringtone_unknown)
}

private object PreviewRingtoneActions : RingtoneActions {
    override fun onLoadOriginalRingtones() = Unit
    override fun onTestRingtone() = Unit
    override fun onPreviewRingtone(id: String) = Unit
    override fun onPreviewCustomRingtone() = Unit
    override fun onStopPreview() = Unit
    override fun onSelectRingtone(id: String) = Unit
    override fun onCustomRingtonePicked(uri: String) = Unit
    override fun onUploadCustomRingtone() = Unit
    override fun onDismissCustomRingtone() = Unit
}

@Preview(showBackground = true)
@Composable
private fun RingtoneScreenPreview() {
    OpenCgd1Theme(dynamicColor = false) {
        RingtoneScreen(
            state = MainUiState(
                builtInRingtones = BUILT_IN_RINGTONES,
                previewingRingtoneId = "argon",
                ringtoneUploadProgress = 0.4f,
                customRingtone = CustomRingtoneInfo("La mia sveglia", 12f),
            ),
            actions = PreviewRingtoneActions,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
        )
    }
}
