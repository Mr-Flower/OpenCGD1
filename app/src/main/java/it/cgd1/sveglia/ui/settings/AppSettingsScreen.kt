package it.cgd1.sveglia.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.cgd1.sveglia.R
import it.cgd1.sveglia.data.AppLanguage
import it.cgd1.sveglia.data.AppPreferences
import it.cgd1.sveglia.data.ThemeMode
import it.cgd1.sveglia.ui.components.SectionHeader
import it.cgd1.sveglia.ui.components.SwitchRow
import it.cgd1.sveglia.ui.theme.OpenCgd1Theme
import it.cgd1.sveglia.ui.theme.isDynamicColorSupported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    state: AppSettingsUiState,
    actions: AppSettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cardModifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()

            SectionHeader(stringResource(R.string.app_settings_appearance), cardModifier)
            ElevatedCard(cardModifier) {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        RadioRow(
                            label = stringResource(mode.labelRes),
                            selected = state.preferences.themeMode == mode,
                            onClick = { actions.onThemeModeChange(mode) },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val dynamicSupported = isDynamicColorSupported()
                SwitchRow(
                    label = stringResource(R.string.app_settings_dynamic_color),
                    supportingText = stringResource(
                        if (dynamicSupported) R.string.app_settings_dynamic_color_hint
                        else R.string.app_settings_dynamic_color_unsupported,
                    ),
                    checked = dynamicSupported && state.preferences.useDynamicColor,
                    enabled = dynamicSupported,
                    onCheckedChange = actions::onDynamicColorChange,
                )
            }

            SectionHeader(stringResource(R.string.app_settings_help), cardModifier)
            ElevatedCard(cardModifier) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_settings_show_welcome)) },
                    supportingContent = { Text(stringResource(R.string.app_settings_show_welcome_hint)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(role = Role.Button) {
                        onBack()
                        actions.onShowOnboarding()
                    },
                )
            }

            SectionHeader(stringResource(R.string.app_settings_language), cardModifier)
            ElevatedCard(cardModifier) {
                Column(Modifier.selectableGroup()) {
                    AppLanguage.entries.forEach { language ->
                        RadioRow(
                            label = stringResource(language.labelRes),
                            selected = state.language == language,
                            onClick = { actions.onLanguageChange(language) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.AMOLED -> R.string.theme_amoled
    }

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ITALIAN -> R.string.language_italian
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.FRENCH -> R.string.language_french
        AppLanguage.SPANISH -> R.string.language_spanish
    }

private object PreviewAppSettingsActions : AppSettingsActions {
    override fun onThemeModeChange(mode: ThemeMode) = Unit
    override fun onDynamicColorChange(enabled: Boolean) = Unit
    override fun onLanguageChange(language: AppLanguage) = Unit
    override fun onCompleteOnboarding() = Unit
    override fun onShowOnboarding() = Unit
}

@Preview(showBackground = true)
@Composable
private fun AppSettingsScreenPreview() {
    OpenCgd1Theme(themeMode = ThemeMode.AMOLED, dynamicColor = false) {
        AppSettingsScreen(
            state = AppSettingsUiState(AppPreferences(themeMode = ThemeMode.AMOLED), AppLanguage.SYSTEM),
            actions = PreviewAppSettingsActions,
            onBack = {},
        )
    }
}
