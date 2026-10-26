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
import androidx.compose.foundation.layout.widthIn
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
import fr.jrrocks.carnetderecette.ui.theme.chiffres
import kotlin.math.roundToInt

/** Le ton d'une ligne d'état : il se lit aussi par l'icône et la phrase, jamais par la couleur seule. */
enum class TonEtat { ERREUR, INFO, NEUTRE }

/**
 * Une ligne d'état pleine largeur (erreur, mise à jour) : icône, phrase qui dit la cause et
 * quoi faire, et ses actions. Pas une boîte de dialogue : elle n'interrompt personne en pleine
 * cuisine. Chaque écran en montre au plus deux.
 *
 * La phrase est une zone annoncée par TalkBack (`liveRegion`) quand [annoncer] est vrai : on
 * annonce un changement d'état, jamais chaque pas d'une progression. [progression] affiche une
 * barre et son pourcentage à part, hors de la zone annoncée.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LigneEtat(
    @DrawableRes icone: Int,
    texte: String,
    ton: TonEtat,
    modifier: Modifier = Modifier,
    progression: Float? = null,
    annoncer: Boolean = progression == null,
    actions: (@Composable () -> Unit)? = null,
) {
    val couleurs = MaterialTheme.colorScheme
    val (fond, encre) = when (ton) {
        TonEtat.ERREUR -> couleurs.errorContainer to couleurs.onErrorContainer
        TonEtat.INFO -> couleurs.secondaryContainer to couleurs.onSecondaryContainer
        TonEtat.NEUTRE -> couleurs.surfaceContainerHigh to couleurs.onSurface
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = fond,
        contentColor = encre,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (actions != null) 4.dp else 12.dp)) {
            // La Surface de Material ne fusionne pas sa sémantique : l'annonce se pose sur le
            // nœud qui porte le texte, sinon TalkBack la perd.
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    if (annoncer) liveRegion = LiveRegionMode.Polite
                },
            ) {
                Icon(painterResource(icone), contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(texte, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            if (progression != null) {
                Row(Modifier.fillMaxWidth().padding(start = 36.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { progression },
                        modifier = Modifier.weight(1f),
                        color = couleurs.primary,
                        // La piste par défaut est secondaryContainer, le fond même de la ligne.
                        trackColor = encre.copy(alpha = 0.22f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${(progression * 100).roundToInt()}\u00A0%",
                        style = MaterialTheme.typography.bodyMedium.chiffres(),
                        modifier = Modifier.widthIn(min = 48.dp),
                    )
                }
            }
            if (actions != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    // À grande police, les boutons passent l'un sous l'autre : 8 dp entre eux.
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { actions() }
            }
        }
    }
}
