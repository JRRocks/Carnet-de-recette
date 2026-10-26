package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.ui.theme.Carnet

/** L'étiquette de l'étape en cours : le texte posé sur la plaque bleu nuit, coins de 4 dp. */
@Composable
fun Plaque(texte: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineMedium) {
    Surface(modifier = modifier, color = Carnet.couleurs.plaque, contentColor = Carnet.couleurs.surPlaque, shape = MaterialTheme.shapes.extraSmall) {
        Text(texte, style = style, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
