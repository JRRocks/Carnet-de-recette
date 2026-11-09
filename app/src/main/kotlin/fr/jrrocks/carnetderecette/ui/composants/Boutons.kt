package fr.jrrocks.carnetderecette.ui.composants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.jrrocks.carnetderecette.ui.theme.Carnet

/*
 * Les boutons du Carnet : ceux de Material 3, aux coins de plaque (8 dp) plutôt qu'en
 * pilule. `ButtonDefaults.shape` ne lit pas le thème : d'où ces enveloppes, qui posent
 * `shapes.medium` et une hauteur minimale de 48 dp.
 */

enum class SorteBouton { PLEIN, CONTOUR, TEXTE }

/**
 * Un choix parmi quelques-uns (« À deux | Seul », « Découpe | Chauffe ») : plein quand il est
 * pris, au contour sinon. Un seul bouton qui change de couleurs, jamais deux boutons qu'on
 * échange : TalkBack garde le focus dessus et annonce « sélectionné ».
 */
@Composable
fun BoutonChoix(
    texte: String,
    pris: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
) {
    val tailleIcone = with(LocalDensity.current) { 20.sp.toDp() }.coerceAtMost(36.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).semantics { selected = pris },
        shape = MaterialTheme.shapes.medium,
        colors = if (pris) {
            ButtonDefaults.outlinedButtonColors(containerColor = Carnet.couleurs.action, contentColor = Carnet.couleurs.surAction)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        border = BorderStroke(1.dp, if (pris) Carnet.couleurs.action else MaterialTheme.colorScheme.outline),
    ) {
        if (icone != null) {
            Icon(painterResource(icone), contentDescription = null, modifier = Modifier.size(tailleIcone))
            Spacer(Modifier.width(8.dp))
        }
        Text(texte, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BoutonCarnet(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sorte: SorteBouton = SorteBouton.PLEIN,
    @DrawableRes icone: Int? = null,
) {
    val forme = MaterialTheme.shapes.medium
    val m = modifier.heightIn(min = 48.dp)
    // L'icône suit la taille de police, comme le libellé : à 200 %, 20 dp paraîtraient minuscules.
    val tailleIcone = with(LocalDensity.current) { 20.sp.toDp() }.coerceAtMost(36.dp)
    val contenu: @Composable () -> Unit = {
        if (icone != null) {
            Icon(painterResource(icone), contentDescription = null, modifier = Modifier.size(tailleIcone))
            Spacer(Modifier.width(8.dp))
        }
        Text(texte, style = MaterialTheme.typography.labelLarge)
    }
    when (sorte) {
        SorteBouton.PLEIN -> Button(
            onClick = onClick,
            modifier = m,
            shape = forme,
            colors = ButtonDefaults.buttonColors(containerColor = Carnet.couleurs.action, contentColor = Carnet.couleurs.surAction),
        ) { contenu() }
        SorteBouton.CONTOUR -> OutlinedButton(onClick = onClick, modifier = m, shape = forme) { contenu() }
        SorteBouton.TEXTE -> TextButton(onClick = onClick, modifier = m, shape = forme) { contenu() }
    }
}
