package it.cgd1.sveglia.data

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val onboardingCompleted: Boolean = false,
)
