package fr.jrrocks.carnetderecette.ui.composants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.ui.theme.Carnet

/*
 * Les boutons du Carnet : ceux de Material 3, aux coins de plaque (8 dp) plutôt qu'en
 * pilule. `ButtonDefaults.shape` ne lit pas le thème : d'où ces enveloppes, qui posent
 * `shapes.medium` et une hauteur minimale de 48 dp.
 */

enum class SorteBouton { PLEIN, TONAL, CONTOUR, TEXTE }

@Composable
fun BoutonCarnet(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sorte: SorteBouton = SorteBouton.PLEIN,
    @DrawableRes icone: Int? = null,
    hauteur: Dp = 48.dp,
) {
    val forme = MaterialTheme.shapes.medium
    val m = modifier.heightIn(min = hauteur)
    val contenu: @Composable () -> Unit = {
        if (icone != null) {
            Icon(painterResource(icone), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texte, style = MaterialTheme.typography.labelLarge)
    }
    when (sorte) {
        SorteBouton.PLEIN -> Button(
            onClick = onClick,
            modifier = m,
            shape = forme,
            colors = ButtonDefaults.buttonColors(containerColor = Carnet.couleurs.barre, contentColor = Carnet.couleurs.surBarre),
        ) { contenu() }
        SorteBouton.TONAL -> FilledTonalButton(onClick = onClick, modifier = m, shape = forme) { contenu() }
        SorteBouton.CONTOUR -> OutlinedButton(onClick = onClick, modifier = m, shape = forme) { contenu() }
        SorteBouton.TEXTE -> TextButton(onClick = onClick, modifier = m, shape = forme) { contenu() }
    }
}
