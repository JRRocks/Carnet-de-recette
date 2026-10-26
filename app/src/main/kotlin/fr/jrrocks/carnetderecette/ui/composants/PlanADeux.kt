package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Une tâche de l'exemple, d'un côté ou de l'autre. */
@Immutable
data class TacheExemple(val texte: String, val etat: EtatEtape)

/** Une rangée du plan à deux : une tâche de chaque côté, ou un passage de main entre les deux. */
@Immutable
data class RangeeADeux(
    val decoupe: TacheExemple? = null,
    val chauffe: TacheExemple? = null,
    val passage: String? = null,
)

private val AXE_COLONNE = 20.dp
private val TEXTE_COLONNE = 44.dp

/**
 * Le plan à deux, en exemple : deux lignes parallèles, ronde pour la découpe, carrée pour la
 * chauffe, et un passage de main dessiné comme un tube entre elles. Illustration d'état
 * vide : lue par TalkBack comme une seule phrase, jamais comme des tâches réelles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanADeuxExemple(rangees: List<RangeeADeux>, resume: String, modifier: Modifier = Modifier) {
    val couleurs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    val ligneDecoupe = Role.DECOUPE.couleur
    val ligneChauffe = Role.CHAUFFE.couleur
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = resume }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Role.entries.forEach { role ->
                // FlowRow : à grande taille de police, le libellé passe sous la pastille au lieu
                // d'être coupé en plein mot.
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pastille(role, Modifier.align(Alignment.CenterVertically), taille = 40.dp)
                    Text(role.libelle, Modifier.align(Alignment.CenterVertically), style = typo.titleMedium, color = couleurs.onSurface)
                }
            }
        }
        rangees.forEachIndexed { i, rangee ->
            val premiere = i == 0
            val derniere = i == rangees.lastIndex
            val encre = couleurs.onSurface
            val fondMarqueur = couleurs.surfaceContainerLowest
            val fond = couleurs.surface
            val pointille = couleurs.outline
            val demiLigne = typo.titleMedium.lineHeight
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (rangee.passage != null) 64.dp else 56.dp)
                    .drawBehind {
                        val xA = AXE_COLONNE.toPx()
                        val xB = size.width / 2 + AXE_COLONNE.toPx()
                        val y = if (rangee.passage != null) 16.dp.toPx() else 8.dp.toPx() + demiLigne.toPx() / 2
                        listOf(xA to ligneDecoupe, xB to ligneChauffe).forEach { (x, couleur) ->
                            troncon(x, if (premiere) y else 0f, if (derniere) y else size.height, couleur, pointille = false)
                        }
                        if (rangee.passage != null) {
                            // Le tube du passage de main : fond clair, contour d'encre, sous les marqueurs.
                            val hauteur = 12.dp.toPx()
                            val coin = CornerRadius(hauteur / 2)
                            drawRoundRect(fondMarqueur, Offset(xA, y - hauteur / 2), Size(xB - xA, hauteur), coin)
                            drawRoundRect(encre, Offset(xA, y - hauteur / 2), Size(xB - xA, hauteur), coin, style = Stroke(3.dp.toPx()))
                            marqueur(Offset(xA, y), EtatEtape.A_FAIRE, rond = true, couleurLigne = ligneDecoupe, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille)
                            marqueur(Offset(xB, y), EtatEtape.A_FAIRE, rond = false, couleurLigne = ligneChauffe, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille)
                        } else {
                            rangee.decoupe?.let { marqueur(Offset(xA, y), it.etat, rond = true, couleurLigne = ligneDecoupe, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille) }
                            rangee.chauffe?.let { marqueur(Offset(xB, y), it.etat, rond = false, couleurLigne = ligneChauffe, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille) }
                        }
                    },
            ) {
                if (rangee.passage != null) {
                    // Le libellé reste dans la colonne de gauche : le trait de la chauffe ne le traverse pas.
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            rangee.passage,
                            style = typo.bodyMedium,
                            color = couleurs.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = TEXTE_COLONNE, end = 8.dp, top = 30.dp, bottom = 8.dp),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                        listOf(rangee.decoupe, rangee.chauffe).forEach { tache ->
                            Text(
                                tache?.texte.orEmpty(),
                                style = if (tache?.etat == EtatEtape.EN_COURS) typo.titleMedium else typo.bodyMedium,
                                color = if (tache?.etat == EtatEtape.FAITE) couleurs.onSurfaceVariant else couleurs.onSurface,
                                modifier = Modifier.weight(1f).padding(start = TEXTE_COLONNE, end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
