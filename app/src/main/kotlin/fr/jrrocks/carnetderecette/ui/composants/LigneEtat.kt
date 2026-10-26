package fr.jrrocks.carnetderecette.ui.composants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Le ton d'une ligne d'état : il se lit aussi par l'icône et la phrase, jamais par la couleur seule. */
enum class TonEtat { ERREUR, INFO, NEUTRE }

/**
 * Une ligne d'état pleine largeur (erreur, mise à jour, hors ligne) : icône, phrase qui dit la
 * cause et quoi faire, et ses actions. Pas une boîte de dialogue : elle n'interrompt personne
 * en pleine cuisine. Au plus deux à la fois, dans l'ordre erreur, mise à jour, hors ligne.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LigneEtat(
    @DrawableRes icone: Int,
    texte: String,
    ton: TonEtat,
    modifier: Modifier = Modifier,
    progression: Float? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val couleurs = MaterialTheme.colorScheme
    val (fond, encre) = when (ton) {
        TonEtat.ERREUR -> couleurs.errorContainer to couleurs.onErrorContainer
        TonEtat.INFO -> couleurs.secondaryContainer to couleurs.onSecondaryContainer
        TonEtat.NEUTRE -> couleurs.surfaceContainerHigh to couleurs.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = fond,
        contentColor = encre,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (actions != null) 4.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(icone), contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(texte, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            if (progression != null) {
                LinearProgressIndicator(
                    progress = { progression },
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 12.dp),
                )
            }
            if (actions != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) { actions() }
            }
        }
    }
}
