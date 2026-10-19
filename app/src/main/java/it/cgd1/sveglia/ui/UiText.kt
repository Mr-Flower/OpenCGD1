package it.cgd1.sveglia.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Testo prodotto dal ViewModel senza Context: si risolve nelle risorse solo dalla UI. */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    @Composable
    fun asString(): String = stringResource(id, *args.toTypedArray())

    fun resolve(resources: Resources): String = resources.getString(id, *args.toTypedArray())
}
