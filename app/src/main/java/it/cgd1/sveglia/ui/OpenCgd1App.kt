package it.cgd1.sveglia.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import it.cgd1.sveglia.R
import it.cgd1.sveglia.ui.clock.ClockScreen
import it.cgd1.sveglia.ui.components.CenteredMessage
import it.cgd1.sveglia.ui.main.MainEffect
import it.cgd1.sveglia.ui.main.MainViewModel
import it.cgd1.sveglia.ui.ringtone.RingtoneScreen
import it.cgd1.sveglia.ui.scan.ScanScreen
import it.cgd1.sveglia.ui.settings.AppSettingsActions
import it.cgd1.sveglia.ui.settings.AppSettingsScreen
import it.cgd1.sveglia.ui.settings.AppSettingsUiState
import it.cgd1.sveglia.ui.welcome.WelcomeScreen

private val BLUETOOTH_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun Context.hasBluetoothPermissions(): Boolean = BLUETOOTH_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun OpenCgd1App(
    settingsState: AppSettingsUiState,
    settingsActions: AppSettingsActions,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Lo stato dei permessi è della UI: può cambiare dalle impostazioni di sistema mentre l'app è in pausa.
    var permissionsGranted by remember { mutableStateOf(context.hasBluetoothPermissions()) }
    LifecycleResumeEffect(Unit) {
        permissionsGranted = context.hasBluetoothPermissions()
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsGranted = context.hasBluetoothPermissions()
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is MainEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.text.resolve(resources))
                }
            }
        }
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showRingtones by rememberSaveable { mutableStateOf(false) }
    val openSettings = { showSettings = true }

    val modifier = Modifier.fillMaxSize()
    when {
        !settingsState.preferences.onboardingCompleted -> WelcomeScreen(
            permissionsGranted = permissionsGranted,
            onRequestPermissions = { permissionLauncher.launch(BLUETOOTH_PERMISSIONS) },
            onFinish = settingsActions::onCompleteOnboarding,
            modifier = modifier,
        )
        showSettings -> AppSettingsScreen(
            state = settingsState,
            actions = settingsActions,
            onBack = { showSettings = false },
            modifier = modifier,
        )
        !state.isBluetoothAvailable -> CenteredMessage(
            icon = Icons.Filled.Warning,
            text = stringResource(R.string.gate_no_bluetooth),
            modifier = modifier,
        )
        !permissionsGranted -> CenteredMessage(
            icon = Icons.Filled.Lock,
            text = stringResource(R.string.gate_permission),
            modifier = modifier,
            primaryAction = stringResource(R.string.action_grant_permission) to { permissionLauncher.launch(BLUETOOTH_PERMISSIONS) },
            secondaryAction = stringResource(R.string.action_open_app_settings) to {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
        state.isBluetoothEnabled == null -> Box(modifier)
        state.isBluetoothEnabled == false -> CenteredMessage(
            icon = Icons.Filled.Warning,
            text = stringResource(R.string.gate_bluetooth_off),
            modifier = modifier,
            primaryAction = stringResource(R.string.action_enable_bluetooth) to {
                try {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } catch (_: SecurityException) {
                    permissionLauncher.launch(BLUETOOTH_PERMISSIONS)
                }
            },
        )
        state.openClock == null -> ScanScreen(
            state = state,
            actions = viewModel,
            snackbarHostState = snackbarHostState,
            onOpenSettings = openSettings,
            modifier = modifier,
        )
        showRingtones -> RingtoneScreen(
            state = state,
            actions = viewModel,
            snackbarHostState = snackbarHostState,
            onBack = { showRingtones = false },
            modifier = modifier,
        )
        else -> ClockScreen(
            state = state,
            actions = viewModel,
            snackbarHostState = snackbarHostState,
            onOpenSettings = openSettings,
            onOpenRingtones = { showRingtones = true },
            modifier = modifier,
        )
    }
}
