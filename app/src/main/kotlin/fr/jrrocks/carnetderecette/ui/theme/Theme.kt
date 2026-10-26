package fr.jrrocks.carnetderecette.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Le thème du Carnet : clair par défaut (la cuisine est éclairée), sombre composé à part entière. */
@Composable
fun ThemeCarnet(sombre: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCouleursCarnet provides if (sombre) CouleursSombres else CouleursClaires) {
        MaterialTheme(
            colorScheme = if (sombre) SchemaSombre else SchemaClair,
            typography = if (sombre) TypographieSombre else TypographieClaire,
            shapes = FormesCarnet,
            content = contenu,
        )
    }
}

/** Accès court aux couleurs propres au Carnet. */
object Carnet {
    val couleurs: CouleursCarnet
        @Composable @ReadOnlyComposable get() = LocalCouleursCarnet.current
}
