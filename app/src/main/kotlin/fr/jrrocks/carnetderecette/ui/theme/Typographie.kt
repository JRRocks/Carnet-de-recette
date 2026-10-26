package fr.jrrocks.carnetderecette.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.jrrocks.carnetderecette.R

/*
 * Une seule famille, embarquée : Atkinson Hyperlegible Next (Braille Institute, SIL OFL 1.1,
 * texte de la licence dans assets/licences). Dessinée pour la basse vision : I, l et 1
 * distincts, 0 et O distincts, ouvertures larges. C'est la scène de la cuisine : coup d'œil
 * d'une seconde à 50-80 cm, lumière chaude, buée. Police variable (wght 200-800), lue dès
 * l'API 26. Jamais en dessous de 500 : les traits fins se délavent sous la lumière chaude.
 */
@OptIn(ExperimentalTextApi::class)
private fun atkinson(graisse: Int) = Font(
    resId = R.font.atkinson_hyperlegible_next,
    weight = FontWeight(graisse),
    variationSettings = FontVariation.Settings(FontVariation.weight(graisse)),
)

val Atkinson = FontFamily(atkinson(500), atkinson(600), atkinson(700), atkinson(800))

private fun style(taille: Int, interligne: Int, graisse: FontWeight, interlettrage: Double = 0.0) = TextStyle(
    fontFamily = Atkinson,
    fontWeight = graisse,
    fontSize = taille.sp,
    lineHeight = interligne.sp,
    letterSpacing = interlettrage.em,
)

/** L'échelle Material, relevée pour une lecture à distance de bras (corps courant à 18 sp). */
private fun typographie(corps: FontWeight, libelle: FontWeight) = Typography(
    displayLarge = style(52, 60, FontWeight.Bold),
    displayMedium = style(44, 52, FontWeight.Bold),
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(18, 24, FontWeight.SemiBold, 0.006),
    titleSmall = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(18, 26, corps),
    bodyMedium = style(16, 24, corps),
    bodySmall = style(14, 20, corps),
    labelLarge = style(16, 20, libelle, 0.006),
    labelMedium = style(14, 18, libelle, 0.014),
    labelSmall = style(12, 16, libelle, 0.025),
)

internal val TypographieClaire = typographie(corps = FontWeight.Medium, libelle = FontWeight.SemiBold)

/** En sombre, le texte clair sur fond foncé paraît plus maigre : corps et libellés montent d'un cran. */
internal val TypographieSombre = typographie(corps = FontWeight.SemiBold, libelle = FontWeight.Bold)

/** Chiffres tabulaires et gras, pour les durées, quantités, dates et numéros de box. */
fun TextStyle.chiffres(): TextStyle = copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)
