package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Vert « herbes fraîches », clair ou sombre selon le téléphone. */
private val Clair = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B3D0E),
    secondary = Color(0xFF5D6B58),
    background = Color(0xFFF8FAF5),
    surface = Color(0xFFF8FAF5),
    surfaceVariant = Color(0xFFE1E8DC),
    // Les cartes prennent ces teintes : sans elles, Material garde son lilas par défaut.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5EE),
    surfaceContainer = Color(0xFFECF0E8),
    surfaceContainerHigh = Color(0xFFE6EBE2),
    surfaceContainerHighest = Color(0xFFE1E6DC),
    error = Color(0xFFB3261E),
)

private val Sombre = darkColorScheme(
    primary = Color(0xFF8BD18E),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFBCCBB5),
    background = Color(0xFF111410),
    surface = Color(0xFF111410),
    surfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFF0C0F0B),
    surfaceContainerLow = Color(0xFF191D18),
    surfaceContainer = Color(0xFF1D211C),
    surfaceContainerHigh = Color(0xFF282B26),
    surfaceContainerHighest = Color(0xFF323631),
    error = Color(0xFFF2B8B5),
)

@Composable
fun ThemeCarnet(sombre: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (sombre) Sombre else Clair, content = contenu)
}
