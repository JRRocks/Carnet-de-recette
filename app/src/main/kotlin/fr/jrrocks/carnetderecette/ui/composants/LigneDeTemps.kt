package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/*
 * Le geste signature de la direction « Correspondance » : le schéma de ligne. Un trait
 * vertical de 6 dp, des marqueurs ronds (fait, en cours, à faire), et un trait en pointillé
 * pour ce qui n'existe pas encore dans l'app. Chaque rangée dessine son propre tronçon
 * (`drawBehind`) plutôt qu'un dessin unique derrière la liste : il suit la hauteur réelle du
 * texte, jusqu'à 200 % de taille de police (relecture des juges).
 *
 * Le monde du plan de ligne ne prête que la forme : les textes de l'app ne parlent jamais de
 * station, de terminus ou de correspondance.
 */

enum class EtatEtape(val description: String) {
    FAITE("Fait"),
    EN_COURS("En cours"),
    A_FAIRE("À faire"),
    PAS_ENCORE("Pas encore dans l'app"),
}

@Immutable
data class Etape(
    val titre: String,
    val etat: EtatEtape,
    val detail: String? = null,
    val fin: String? = null,
)

/** Abscisse du trait depuis le bord gauche, et début du texte. */
private val AXE = 36.dp
private val DEBUT_TEXTE = 72.dp
private val TRAIT = 6.dp
private val POINTILLE = 3.dp
private val ANNEAU = 3.dp
private val RAYON = 12.dp
private val RAYON_EN_COURS = 16.dp
private val HAUT = 12.dp

@Composable
fun LigneDeTemps(
    etapes: List<Etape>,
    modifier: Modifier = Modifier,
    enCours: @Composable ColumnScope.(Etape) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        etapes.forEachIndexed { i, etape ->
            RangeeEtape(etape, precedente = etapes.getOrNull(i - 1), suivante = etapes.getOrNull(i + 1)) {
                if (etape.etat == EtatEtape.EN_COURS) enCours(etape)
            }
        }
    }
}

@Composable
private fun RangeeEtape(
    etape: Etape,
    precedente: Etape?,
    suivante: Etape?,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val couleurs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    val enCours = etape.etat == EtatEtape.EN_COURS
    // Le marqueur s'aligne sur le milieu de la première ligne du titre, en sp : il suit la
    // taille de police du système.
    val styleTitre: TextStyle = if (enCours) typo.headlineMedium else typo.titleLarge
    val decalagePlaque = if (enCours) 4.dp else 0.dp
    val trait = couleurs.primary
    val pointille = couleurs.outline
    val encre = couleurs.onSurface
    val fondMarqueur = couleurs.surfaceContainerLowest
    val fond = couleurs.surface

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .drawBehind {
                val x = AXE.toPx()
                val y = HAUT.toPx() + decalagePlaque.toPx() + styleTitre.lineHeight.toPx() / 2
                if (precedente != null) {
                    troncon(x, 0f, y, if (etape.etat == EtatEtape.PAS_ENCORE) pointille else trait, etape.etat == EtatEtape.PAS_ENCORE)
                }
                if (suivante != null) {
                    val pasEncore = suivante.etat == EtatEtape.PAS_ENCORE
                    troncon(x, y, size.height, if (pasEncore) pointille else trait, pasEncore)
                }
                marqueur(Offset(x, y), etape.etat, rond = true, couleurLigne = trait, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille)
            }
            .semantics(mergeDescendants = true) { stateDescription = etape.etat.description }
            .padding(start = DEBUT_TEXTE, end = 16.dp, top = HAUT, bottom = 20.dp),
    ) {
        if (enCours) {
            Plaque(etape.titre)
        } else {
            // Le titre et son complément (date, état) partagent la même ligne de base.
            Row {
                Text(etape.titre, style = styleTitre, color = couleurs.onSurface, modifier = Modifier.weight(1f).alignByBaseline())
                etape.fin?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(it, style = typo.bodyMedium, color = couleurs.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                }
            }
        }
        etape.detail?.let {
            Text(
                it,
                style = if (enCours) typo.bodyLarge else typo.bodyMedium,
                color = if (enCours) couleurs.onSurface else couleurs.onSurfaceVariant,
                modifier = Modifier.padding(top = if (enCours) 8.dp else 2.dp),
            )
        }
        contenu()
    }
}

/** Un tronçon de trait : plein (6 dp, couleur de la ligne) ou en pointillé (pas encore dans l'app). */
internal fun DrawScope.troncon(x: Float, y0: Float, y1: Float, couleur: Color, pointille: Boolean) {
    if (y1 <= y0) return
    if (pointille) {
        drawLine(
            couleur,
            Offset(x, y0),
            Offset(x, y1),
            strokeWidth = POINTILLE.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
        )
    } else {
        drawLine(couleur, Offset(x, y0), Offset(x, y1), strokeWidth = TRAIT.toPx())
    }
}

/**
 * Un marqueur d'étape : plein (fait), cerclé (en cours), creux (à faire), en pointillé (pas
 * encore dans l'app). [rond] : rond pour la découpe et la semaine, carré pour la chauffe.
 */
internal fun DrawScope.marqueur(
    centre: Offset,
    etat: EtatEtape,
    rond: Boolean,
    couleurLigne: Color,
    encre: Color,
    fondMarqueur: Color,
    fond: Color,
    pointille: Color,
) {
    val anneau = ANNEAU.toPx()
    fun forme(rayon: Float, couleur: Color, style: Stroke? = null) {
        if (rond) {
            if (style == null) drawCircle(couleur, rayon, centre) else drawCircle(couleur, rayon - style.width / 2, centre, style = style)
        } else {
            val demi = if (style == null) rayon else rayon - style.width / 2
            val coin = CornerRadius(6.dp.toPx() * demi / RAYON.toPx())
            val tl = Offset(centre.x - demi, centre.y - demi)
            val taille = Size(demi * 2, demi * 2)
            if (style == null) drawRoundRect(couleur, tl, taille, coin) else drawRoundRect(couleur, tl, taille, coin, style = style)
        }
    }
    when (etat) {
        EtatEtape.FAITE -> {
            forme(RAYON.toPx(), couleurLigne)
            forme(RAYON.toPx(), encre, Stroke(anneau))
        }
        EtatEtape.EN_COURS -> {
            forme(RAYON_EN_COURS.toPx(), fondMarqueur)
            forme(RAYON_EN_COURS.toPx(), encre, Stroke(anneau))
            forme(9.dp.toPx(), couleurLigne)
        }
        EtatEtape.A_FAIRE -> {
            forme(RAYON.toPx(), fondMarqueur)
            forme(RAYON.toPx(), encre, Stroke(anneau))
        }
        EtatEtape.PAS_ENCORE -> {
            forme(RAYON.toPx(), fond)
            forme(
                RAYON.toPx(),
                pointille,
                Stroke(anneau, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
    }
}

/** La légende de la ligne : les deux rôles et leur forme, et le pointillé de ce qui viendra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegendeLigne(modifier: Modifier = Modifier) {
    val couleurs = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Role.entries.forEach { role ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pastille(role, taille = 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(role.libelle, style = MaterialTheme.typography.bodyLarge, color = couleurs.onSurface)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fond = couleurs.surface
            val pointille = couleurs.outline
            Canvas(Modifier.size(32.dp)) {
                marqueur(center, EtatEtape.PAS_ENCORE, rond = true, couleurLigne = pointille, encre = pointille, fondMarqueur = fond, fond = fond, pointille = pointille)
            }
            Spacer(Modifier.width(8.dp))
            Text("Pas encore dans l'app", style = MaterialTheme.typography.bodyLarge, color = couleurs.onSurface)
        }
    }
}
