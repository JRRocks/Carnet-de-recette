package fr.jrrocks.carnetderecette.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Coins faiblement arrondis, ceux d'une plaque émaillée : pas de pilule pour les actions.
 * extraSmall : plaque et snackbars · small : pastille carrée, champs · medium : boutons et
 * lignes d'état · large : bouton d'action flottant · extraLarge : dialogues (rares).
 */
internal val FormesCarnet = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
