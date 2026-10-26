package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 */
@Composable
fun Pastille(role: Role, modifier: Modifier = Modifier, initiale: String? = null, taille: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(taille)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, role.forme)
            .border(3.dp, role.couleur, role.forme)
            .semantics { contentDescription = listOfNotNull(role.libelle, initiale).joinToString(" ") },
        contentAlignment = Alignment.Center,
    ) {
        if (initiale != null) {
            Text(
                initiale.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
