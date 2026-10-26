package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** Une rangée du plan à deux, dans l'ordre du temps. */
sealed interface RangeeADeux {
    /** Une tâche, posée sur la ligne de son rôle. */
    @Immutable
    data class Tache(val role: Role, val texte: String, val etat: EtatEtape) : RangeeADeux

    /** L'un passe la main à l'autre : un tube entre les deux traits. [vers] est le rôle qui reçoit. */
    @Immutable
    data class Passage(val texte: String, val vers: Role, val etat: EtatEtape) : RangeeADeux
}

/*
 * Deux traits parallèles proches du bord, et une seule colonne de texte à droite : chaque
 * libellé garde presque toute la largeur, même à 200 % de taille de police. L'écart entre les
 * deux traits laisse voir le tube d'un passage de main entre leurs marqueurs.
 */
private val AXE_DECOUPE = 28.dp
private val AXE_CHAUFFE = 72.dp
private val TEXTE = 100.dp
private val HAUT = 8.dp

private val Role.axe get() = if (this == Role.DECOUPE) AXE_DECOUPE else AXE_CHAUFFE

/**
 * Le plan à deux, en exemple : la découpe sur le trait rond, la chauffe sur le trait carré,
 * les tâches dans l'ordre du temps et le passage de main dessiné comme un tube. Illustration
 * d'état vide : lue par TalkBack comme une seule phrase ([legende] puis [resume]), jamais
 * comme des tâches réelles.
 */
@Composable
fun PlanADeuxExemple(rangees: List<RangeeADeux>, legende: String, resume: String, modifier: Modifier = Modifier) {
    val typo = MaterialTheme.typography
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$legende $resume" }) {
        Role.entries.forEach { EnTeteRole(it) }
        rangees.forEachIndexed { i, rangee -> RangeePlan(rangee, derniere = i == rangees.lastIndex) }
        Text(
            legende,
            style = typo.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

/** Le départ de la ligne d'un rôle, son nom en toutes lettres. */
@Composable
private fun EnTeteRole(role: Role) {
    val couleurs = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.titleMedium
    val decoupe = Role.DECOUPE.couleur
    val chauffe = Role.CHAUFFE.couleur
    val couleurRole = role.couleur
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind {
                val y = HAUT.toPx() + style.lineHeight.toPx() / 2
                // La ligne de la découpe, ouverte au-dessus, continue derrière l'en-tête de la chauffe.
                if (role == Role.CHAUFFE) troncon(AXE_DECOUPE.toPx(), 0f, size.height, decoupe)
                val x = role.axe.toPx()
                troncon(x, y, size.height, if (role == Role.DECOUPE) decoupe else chauffe)
                marqueur(
                    Offset(x, y), EtatEtape.FAITE, rond = role == Role.DECOUPE, couleurLigne = couleurRole,
                    encre = couleurs.onSurface, fondMarqueur = couleurs.surfaceContainerLowest, fond = couleurs.surface, pointille = couleurs.outline,
                )
            },
    ) {
        Text(role.libelle, style = style, color = couleurs.onSurface, modifier = Modifier.padding(start = TEXTE, end = 16.dp, top = HAUT, bottom = 8.dp))
    }
}

@Composable
private fun RangeePlan(rangee: RangeeADeux, derniere: Boolean) {
    val couleurs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    val decoupe = Role.DECOUPE.couleur
    val chauffe = Role.CHAUFFE.couleur
    val encre = couleurs.onSurface
    val fondMarqueur = couleurs.surfaceContainerLowest
    val fond = couleurs.surface
    val pointille = couleurs.outline
    val (texte, style, couleurTexte) = when (rangee) {
        is RangeeADeux.Tache -> Triple(
            rangee.texte,
            if (rangee.etat == EtatEtape.EN_COURS) typo.titleMedium else typo.bodyLarge,
            if (rangee.etat == EtatEtape.FAITE) couleurs.onSurfaceVariant else couleurs.onSurface,
        )
        is RangeeADeux.Passage -> Triple(rangee.texte, typo.bodyMedium, couleurs.onSurfaceVariant)
    }
    val couleurVers = (rangee as? RangeeADeux.Passage)?.vers?.couleur
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .drawBehind {
                val y = HAUT.toPx() + style.lineHeight.toPx() / 2
                val bas = if (derniere) y else size.height
                troncon(AXE_DECOUPE.toPx(), 0f, bas, decoupe)
                troncon(AXE_CHAUFFE.toPx(), 0f, bas, chauffe)
                when (rangee) {
                    is RangeeADeux.Tache -> marqueur(
                        Offset(rangee.role.axe.toPx(), y), rangee.etat, rond = rangee.role == Role.DECOUPE,
                        couleurLigne = if (rangee.role == Role.DECOUPE) decoupe else chauffe,
                        encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille,
                    )
                    is RangeeADeux.Passage -> {
                        // Le tube : clair tant que la main n'est pas passée, puis de la couleur de qui reçoit.
                        val xA = AXE_DECOUPE.toPx()
                        val xB = AXE_CHAUFFE.toPx()
                        val hauteur = 12.dp.toPx()
                        val coin = CornerRadius(hauteur / 2)
                        val remplissage = if (rangee.etat == EtatEtape.FAITE) couleurVers!! else fondMarqueur
                        drawRoundRect(remplissage, Offset(xA, y - hauteur / 2), Size(xB - xA, hauteur), coin)
                        drawRoundRect(encre, Offset(xA, y - hauteur / 2), Size(xB - xA, hauteur), coin, style = Stroke(3.dp.toPx()))
                        val etatBouts = if (rangee.etat == EtatEtape.FAITE) EtatEtape.FAITE else EtatEtape.A_FAIRE
                        marqueur(Offset(xA, y), etatBouts, rond = true, couleurLigne = decoupe, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille)
                        marqueur(Offset(xB, y), etatBouts, rond = false, couleurLigne = chauffe, encre = encre, fondMarqueur = fondMarqueur, fond = fond, pointille = pointille)
                    }
                }
            },
    ) {
        Text(texte, style = style, color = couleurTexte, modifier = Modifier.padding(start = TEXTE, end = 16.dp, top = HAUT, bottom = 12.dp))
    }
}
