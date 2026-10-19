package it.cgd1.sveglia.ui.settings

import androidx.compose.runtime.Stable
import it.cgd1.sveglia.data.AppLanguage
import it.cgd1.sveglia.data.ThemeMode

@Stable
interface AppSettingsActions {
    fun onThemeModeChange(mode: ThemeMode)
    fun onDynamicColorChange(enabled: Boolean)
    fun onLanguageChange(language: AppLanguage)
    fun onCompleteOnboarding()
    fun onShowOnboarding()
}
