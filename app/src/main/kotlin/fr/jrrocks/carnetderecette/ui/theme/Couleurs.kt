package fr.jrrocks.carnetderecette.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Direction « Correspondance » (docs/design/direction.md) : fond blanc faïence, coque bleu
 * nuit émaillé, deux couleurs de cuisinier sur l'axe bleu-jaune (sûr pour les daltoniens
 * rouge-vert). Contrastes calculés en WCAG : texte ≥ 7:1 dans les deux thèmes.
 * Pas de couleur dynamique (Material You) : la couleur d'un cuisinier ne doit pas changer
 * avec le fond d'écran.
 */

internal val SchemaClair: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E3A70),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF14284B),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF44526A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE3EC),
    onSecondaryContainer = Color(0xFF141D2C),
    tertiary = Color(0xFF474D57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E5EA),
    onTertiaryContainer = Color(0xFF191D24),
    background = Color(0xFFF4F6F7),
    onBackground = Color(0xFF0F1620),
    surface = Color(0xFFF4F6F7),
    onSurface = Color(0xFF0F1620),
    surfaceVariant = Color(0xFFDCE1E7),
    onSurfaceVariant = Color(0xFF323B48),
    outline = Color(0xFF687282),
    outlineVariant = Color(0xFFC2C9D2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF1F3),
    surfaceContainer = Color(0xFFE8ECEF),
    surfaceContainerHigh = Color(0xFFE2E6EA),
    surfaceContainerHighest = Color(0xFFDBE0E5),
    error = Color(0xFFA8123E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBDDE2),
    onErrorContainer = Color(0xFF3E0013),
)

internal val SchemaSombre: ColorScheme = darkColorScheme(
    primary = Color(0xFFB2C6F2),
    onPrimary = Color(0xFF0C2350),
    primaryContainer = Color(0xFF1C3462),
    onPrimaryContainer = Color(0xFFE1E8FA),
    secondary = Color(0xFFBCC7D9),
    onSecondary = Color(0xFF212C3E),
    secondaryContainer = Color(0xFF36435A),
    onSecondaryContainer = Color(0xFFDCE5F4),
    tertiary = Color(0xFFC5C9D1),
    onTertiary = Color(0xFF2A2F37),
    tertiaryContainer = Color(0xFF3B414A),
    onTertiaryContainer = Color(0xFFE2E5EA),
    background = Color(0xFF0E131A),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF0E131A),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF2E3642),
    onSurfaceVariant = Color(0xFFC6CEDA),
    outline = Color(0xFF8D97A5),
    outlineVariant = Color(0xFF3D4653),
    surfaceContainerLowest = Color(0xFF090D12),
    surfaceContainerLow = Color(0xFF151B23),
    surfaceContainer = Color(0xFF19202A),
    surfaceContainerHigh = Color(0xFF232B36),
    surfaceContainerHighest = Color(0xFF2D3541),
    error = Color(0xFFFFB3C1),
    onError = Color(0xFF5C0021),
    errorContainer = Color(0xFF7E1230),
    onErrorContainer = Color(0xFFFFD9E0),
)

/**
 * Les couleurs propres au Carnet, hors rôles Material.
 *
 * [ligneDecoupe] et [ligneChauffe] ne servent **jamais au texte** : seulement aux traits,
 * marqueurs et contours de pastille, toujours doublés d'une forme (rond, carré) et d'un mot.
 * [barre] porte la coque (barre d'application, bouton d'action), [plaque] l'étiquette de
 * l'étape en cours ; [surBarre] et [surPlaque], le texte qui s'y pose. En clair, les deux
 * sont le même bleu nuit ; en sombre, la plaque s'éclaircit pour rester un repère.
 */
@Immutable
data class CouleursCarnet(
    val ligneDecoupe: Color,
    val ligneChauffe: Color,
    val barre: Color,
    val surBarre: Color,
    val plaque: Color,
    val surPlaque: Color,
)

internal val CouleursClaires = CouleursCarnet(
    ligneDecoupe = Color(0xFF2A52A8),
    ligneChauffe = Color(0xFF9A6A00),
    barre = Color(0xFF14284B),
    surBarre = Color(0xFFFFFFFF),
    plaque = Color(0xFF14284B),
    surPlaque = Color(0xFFFFFFFF),
)

internal val CouleursSombres = CouleursCarnet(
    ligneDecoupe = Color(0xFF7A9DE8),
    ligneChauffe = Color(0xFFE8BE5C),
    barre = Color(0xFF1C3462),
    surBarre = Color(0xFFE1E8FA),
    plaque = Color(0xFFB2C6F2),
    surPlaque = Color(0xFF0C2350),
)

val LocalCouleursCarnet = staticCompositionLocalOf { CouleursClaires }
