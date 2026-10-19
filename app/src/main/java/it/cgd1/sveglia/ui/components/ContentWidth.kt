package it.cgd1.sveglia.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Su tablet e telefoni orizzontali il contenuto non si allarga a tutto schermo (M3: larghezza leggibile).
private val MaxContentWidth = 640.dp

fun Modifier.contentWidth(): Modifier = widthIn(max = MaxContentWidth).fillMaxWidth()
