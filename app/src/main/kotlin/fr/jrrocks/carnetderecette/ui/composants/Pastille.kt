package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.jrrocks.carnetderecette.ui.theme.Carnet

/** Les deux rôles en cuisine. Chacun a sa forme pour toujours : rond pour l'un, carré pour l'autre. */
enum class Role(val libelle: String) {
    DECOUPE("Découpe"),
    CHAUFFE("Chauffe"),
}

val Role.forme: Shape
    get() = when (this) {
        Role.DECOUPE -> CircleShape
        Role.CHAUFFE -> RoundedCornerShape(6.dp)
    }

val Role.couleur: Color
    @Composable get() = when (this) {
        Role.DECOUPE -> Carnet.couleurs.ligneDecoupe
        Role.CHAUFFE -> Carnet.couleurs.ligneChauffe
    }

/**
 * La pastille d'un cuisinier : son initiale **à l'encre** sur fond clair, dans un contour de
 * 3 dp à sa couleur, rond ou carré. L'identité tient donc par la forme, la lettre et la
 * couleur, jamais par la teinte seule (relecture des juges : pas de lettre blanche dans un
 * rond plein, qui imiterait un pictogramme de transport).
 *
 * Toujours avec une initiale : vide, elle ressemblerait à un bouton radio ou à une case à
 * cocher. Sans profil, on montre [TeteDeLigne]. La taille suit la taille de police, pour que
 * la lettre tienne à 200 %.
 */
@Composable
fun Pastille(role: Role, initiale: String, modifier: Modifier = Modifier, taille: Dp = 40.dp) {
    val cote = with(LocalDensity.current) { taille.value.sp.toDp() }.coerceAtLeast(taille)
    Box(
        modifier = modifier
            .size(cote)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, role.forme)
            .border(3.dp, role.couleur, role.forme)
            .clearAndSetSemantics { contentDescription = "${role.libelle} ${initiale.take(1).uppercase()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initiale.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * La tête de la ligne d'un rôle, quand aucun cuisinier n'est encore nommé : un marqueur plein
 * de sa couleur et de sa forme, posé sur le départ de son trait. Elle se lit comme un morceau
 * du plan, pas comme un contrôle. Décorative : le rôle est écrit à côté.
 */
@Composable
fun TeteDeLigne(role: Role, modifier: Modifier = Modifier) {
    val couleur = role.couleur
    val encre = MaterialTheme.colorScheme.onSurface
    val fond = MaterialTheme.colorScheme.surface
    Canvas(modifier.size(RAYON * 2, RAYON * 2 + 12.dp)) {
        val centre = Offset(size.width / 2, RAYON.toPx())
        troncon(centre.x, centre.y, size.height, couleur)
        marqueur(centre, EtatEtape.FAITE, rond = role == Role.DECOUPE, couleurLigne = couleur, encre = encre, fondMarqueur = fond, fond = fond, pointille = encre)
    }
}

/**
 * Qui fait quoi, faute de profil : la tête de ligne de chaque rôle et son nom en toutes
 * lettres, sans prénom inventé. TalkBack lit la forme et le rôle (« Rond : découpe »).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegendeRoles(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Role.entries.forEach { role ->
            val forme = if (role == Role.DECOUPE) "Rond" else "Carré"
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "$forme\u00A0: ${role.libelle.lowercase()}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeteDeLigne(role)
                Spacer(Modifier.width(8.dp))
                Text(role.libelle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
