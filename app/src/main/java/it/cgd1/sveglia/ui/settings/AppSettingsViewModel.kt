package it.cgd1.sveglia.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.cgd1.sveglia.data.AppLanguage
import it.cgd1.sveglia.data.AppPreferences
import it.cgd1.sveglia.data.AppPreferencesRepository
import it.cgd1.sveglia.data.LanguageController
import it.cgd1.sveglia.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettingsUiState(
    val preferences: AppPreferences,
    val language: AppLanguage,
)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val repository: AppPreferencesRepository,
    private val languageController: LanguageController,
) : ViewModel(), AppSettingsActions {

    // Il ViewModel sopravvive alla ricreazione dell'Activity dovuta al cambio lingua: la lingua va tenuta qui.
    private val language = MutableStateFlow(languageController.current())

    /** null finché DataStore non ha risposto: l'Activity tiene lo splash per non mostrare il tema sbagliato. */
    val uiState: StateFlow<AppSettingsUiState?> = combine(repository.preferences, language, ::AppSettingsUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    override fun onDynamicColorChange(enabled: Boolean) {
        viewModelScope.launch { repository.setUseDynamicColor(enabled) }
    }

    override fun onCompleteOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(true) }
    }

    override fun onShowOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(false) }
    }

    override fun onLanguageChange(language: AppLanguage) {
        this.language.value = language
        languageController.set(language)
    }
}
