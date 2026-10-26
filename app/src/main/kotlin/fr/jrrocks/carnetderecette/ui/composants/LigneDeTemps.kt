package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Le geste signature de la direction « Correspondance » : le schéma de ligne. Un trait
 * vertical de 6 dp et des marqueurs : plein (fait), cerclé (en cours), creux (à faire), en
 * pointillé (pas encore dans l'app). Devant ce qui n'existe pas encore, le trait garde ses 6 dp
 * et sa couleur, en tirets : un trait mince ou gris ferait du schéma un stepper ordinaire
 * (risque 2 de la direction).
 *
 * Chaque rangée dessine son propre tronçon (`drawBehind`) plutôt qu'un dessin unique derrière
 * la liste : il suit la hauteur réelle du texte, jusqu'à 200 % de taille de police.
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
internal val AXE = 36.dp
internal val DEBUT_TEXTE = 72.dp
private val TRAIT = 6.dp
private val TIRET = 8.dp
private val ESPACE_TIRET = 6.dp
private val ANNEAU = 3.dp
internal val RAYON = 12.dp
private val RAYON_EN_COURS = 16.dp
private val HAUT = 12.dp

/**
 * Une ligne d'étapes. [stylePlaque] : le style de l'étape en cours sur sa plaque ; plus petit
 * dans une illustration, pour qu'elle ne dépasse pas le titre de l'écran. [sous] ajoute un
 * contenu sous le texte d'une étape (motif d'erreur, légende), dans la colonne à 72 dp.
 */
@Composable
fun LigneDeTemps(
    etapes: List<Etape>,
    modifier: Modifier = Modifier,
    stylePlaque: TextStyle = MaterialTheme.typography.headlineMedium,
    sous: @Composable ColumnScope.(Etape) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        etapes.forEachIndexed { i, etape ->
            RangeeEtape(etape, stylePlaque, precedente = etapes.getOrNull(i - 1), suivante = etapes.getOrNull(i + 1)) {
                sous(etape)
            }
        }
    }
}

@Composable
private fun RangeeEtape(
    etape: Etape,
    stylePlaque: TextStyle,
    precedente: Etape?,
    suivante: Etape?,
    contenu: @Composable ColumnScope.() -> Unit,
) {
    val couleurs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    val enCours = etape.etat == EtatEtape.EN_COURS
    // Le marqueur s'aligne sur le milieu de la première ligne du titre, en sp : il suit la
    // taille de police du système.
    val styleTitre: TextStyle = if (enCours) stylePlaque else typo.titleLarge
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
                    troncon(x, 0f, y, trait, aVenir = etape.etat == EtatEtape.PAS_ENCORE)
                }
                if (suivante != null) {
                    troncon(x, y, size.height, trait, aVenir = suivante.etat == EtatEtape.PAS_ENCORE)
                }
                marqueur(Offset(x, y), etape.etat, rond = true, couleurLigne = trait, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille)
            }
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = etape.etat.description
            }
            .padding(start = DEBUT_TEXTE, end = 16.dp, top = HAUT, bottom = 20.dp),
    ) {
        if (enCours) {
            Plaque(etape.titre, style = stylePlaque)
        } else {
            // Le titre et son complément (date, état) partagent la même ligne de base.
            Row {
                Text(etape.titre, style = styleTitre, color = couleurs.onSurface, modifier = Modifier.weight(1f).alignByBaseline())
                etape.fin?.let {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        it,
                        // Chiffres tabulaires : les dates s'alignent d'une étape à l'autre.
                        style = typo.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = couleurs.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
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

/**
 * Un tronçon de trait de 6 dp : plein, ou en tirets devant ce qui n'est pas encore dans l'app.
 * Les tirets sont étirés pour qu'un tronçon compte un nombre entier de motifs : il commence par
 * un tiret et finit par un blanc, et deux rangées voisines se raccordent sans tiret double.
 */
internal fun DrawScope.troncon(x: Float, y0: Float, y1: Float, couleur: Color, aVenir: Boolean = false) {
    if (y1 <= y0) return
    val effet = if (aVenir) {
        val motif = (TIRET + ESPACE_TIRET).toPx()
        val nombre = ((y1 - y0) / motif).roundToInt().coerceAtLeast(1)
        val echelle = (y1 - y0) / (nombre * motif)
        PathEffect.dashPathEffect(floatArrayOf(TIRET.toPx() * echelle, ESPACE_TIRET.toPx() * echelle))
    } else {
        null
    }
    drawLine(couleur, Offset(x, y0), Offset(x, y1), strokeWidth = TRAIT.toPx(), pathEffect = effet)
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

/**
 * La clé de l'anneau en pointillé, au-dessus de la ligne, visible dès l'ouverture : un anneau
 * plus petit que ceux des étapes et une phrase de légende, pour qu'on ne la prenne pas pour une
 * étape. L'état de chaque étape est aussi dit à TalkBack par sa rangée.
 */
@Composable
fun ClePasEncore(modifier: Modifier = Modifier) {
    val couleurs = MaterialTheme.colorScheme
    val pointille = couleurs.outline
    Row(
        modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
            .clearAndSetSemantics { contentDescription = "Anneau en pointillé\u00A0: pas encore dans l'app" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(16.dp))
        Canvas(Modifier.size(16.dp)) {
            drawCircle(
                pointille,
                radius = size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("En pointillé\u00A0: pas encore dans l'app", style = MaterialTheme.typography.bodyMedium, color = couleurs.onSurfaceVariant)
    }
}
