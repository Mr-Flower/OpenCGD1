package it.cgd1.sveglia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import it.cgd1.sveglia.R

// Inter al posto di MiSans (il font delle app Xiaomi, con licenza proprietaria): forme simili, licenza OFL.
// Un solo file variabile copre tutti i pesi; ogni Font ne fissa uno tramite l'asse wght.
private fun interFont(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Inter = FontFamily(
    interFont(FontWeight.Light),
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
    interFont(FontWeight.Bold),
)

private val Default = Typography()

private fun TextStyle.inter() = copy(fontFamily = Inter)

val AppTypography = Typography(
    displayLarge = Default.displayLarge.inter(),
    displayMedium = Default.displayMedium.inter(),
    displaySmall = Default.displaySmall.inter(),
    headlineLarge = Default.headlineLarge.inter(),
    headlineMedium = Default.headlineMedium.inter(),
    headlineSmall = Default.headlineSmall.inter(),
    titleLarge = Default.titleLarge.inter(),
    titleMedium = Default.titleMedium.inter(),
    titleSmall = Default.titleSmall.inter(),
    bodyLarge = Default.bodyLarge.inter(),
    bodyMedium = Default.bodyMedium.inter(),
    bodySmall = Default.bodySmall.inter(),
    labelLarge = Default.labelLarge.inter(),
    labelMedium = Default.labelMedium.inter(),
    labelSmall = Default.labelSmall.inter(),
)
