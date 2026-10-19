package it.cgd1.sveglia.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject

/**
 * Lingua per-app: su Android 13+ la gestisce il sistema (è visibile anche nelle Impostazioni),
 * prima AppCompat la salva da sé grazie a `autoStoreLocales` nel manifest.
 */
interface LanguageController {
    fun current(): AppLanguage
    fun set(language: AppLanguage)
}

class AppCompatLanguageController @Inject constructor() : LanguageController {

    override fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales.toLanguageTags())
    }

    override fun set(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language.tag),
        )
    }
}
