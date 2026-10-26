package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * L'écran d'une fonction pas encore livrée : il **apprend** ce qui arrivera (titre, phrase,
 * illustration), plutôt que de dire « rien ici ». La mention finale dit honnêtement l'état.
 */
@Composable
fun EtatVide(
    titre: String,
    texte: String,
    mention: String,
    modifier: Modifier = Modifier,
    illustration: (@Composable () -> Unit)? = null,
) {
    Column(modifier.padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(titre, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(texte, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (illustration != null) {
            Spacer(Modifier.height(4.dp))
            illustration()
        }
        Spacer(Modifier.height(4.dp))
        Text(mention, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
