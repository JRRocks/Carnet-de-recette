package fr.jrrocks.carnetderecette.scan

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * Le réticule du scan des étiquettes : une zone centrale fixe de l'aperçu. La même zone sert
 * au dessin ([DessinReticule]) et au tri des éléments lus ([OcrElements]) : ce qui est lu est
 * exactement ce qui est cadré.
 */
object Reticule {
    /** En fractions de l'aperçu affiché : large (l'étiquette est plus large que haute), centré. */
    val ZONE = Zone(gauche = 0.10f, haut = 0.22f, droite = 0.90f, bas = 0.78f)
}

/**
 * Le réticule dessiné : quatre coins blancs de 3 dp, sans masque qui assombrirait la scène.
 * [progression] (0 à 1, l'avancement du consensus) fait grandir les coins jusqu'à former un
 * cadre complet : un retour silencieux, aucun bip avant la validation.
 *
 * Un liseré sombre sous le trait blanc le garde visible sur une étiquette blanche.
 */
@Composable
fun DessinReticule(progression: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val zone = Reticule.ZONE
        val gauche = size.width * zone.gauche
        val haut = size.height * zone.haut
        val droite = size.width * zone.droite
        val bas = size.height * zone.bas
        val largeur = droite - gauche
        val hauteur = bas - haut
        val coin = minOf(largeur, hauteur) * 0.18f
        val avance = progression.coerceIn(0f, 1f)
        val brasHorizontal = coin + (largeur / 2f - coin) * avance
        val brasVertical = coin + (hauteur / 2f - coin) * avance

        fun DrawScope.coins(couleur: Color, epaisseur: Float) {
            fun trait(depart: Offset, arrivee: Offset) =
                drawLine(couleur, depart, arrivee, strokeWidth = epaisseur, cap = StrokeCap.Round)
            trait(Offset(gauche, haut), Offset(gauche + brasHorizontal, haut))
            trait(Offset(gauche, haut), Offset(gauche, haut + brasVertical))
            trait(Offset(droite, haut), Offset(droite - brasHorizontal, haut))
            trait(Offset(droite, haut), Offset(droite, haut + brasVertical))
            trait(Offset(gauche, bas), Offset(gauche + brasHorizontal, bas))
            trait(Offset(gauche, bas), Offset(gauche, bas - brasVertical))
            trait(Offset(droite, bas), Offset(droite - brasHorizontal, bas))
            trait(Offset(droite, bas), Offset(droite, bas - brasVertical))
        }
        coins(Color.Black.copy(alpha = 0.35f), 5.dp.toPx())
        coins(Color.White, 3.dp.toPx())
    }
}
