package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/**
 * L'écran d'une fonction pas encore livrée : il **apprend** ce qui arrivera (titre, phrase,
 * illustration), plutôt que de dire « rien ici ». La mention dit l'état honnêtement, avant
 * l'illustration, pour qu'on ne prenne pas l'exemple pour la réalité.
 *
 * L'illustration prend toute la largeur : un schéma de ligne y garde son axe à 36 dp du bord,
 * comme sur la Semaine. Les textes ont la gouttière de 16 dp.
 */
@Composable
fun EtatVide(
    titre: String,
    texte: String,
    mention: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
) {
    val gouttiere = Modifier.padding(horizontal = 16.dp)
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Text(
            titre,
            style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = gouttiere.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(texte, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = gouttiere)
        Spacer(Modifier.height(8.dp))
        Text(mention, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = gouttiere)
        if (illustration != null) {
            Spacer(Modifier.height(24.dp))
            illustration()
        }
    }
}
