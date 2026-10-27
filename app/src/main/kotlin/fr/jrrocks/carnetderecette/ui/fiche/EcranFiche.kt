package fr.jrrocks.carnetderecette.ui.fiche

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse

/** Provisoire : remplacé par l'outil du lot 2. */
@Composable
fun EcranFiche(destination: Destination.Fiche, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    EcranPousse("Fiche papier", onRetour) { marges ->
        Text("En construction.", Modifier.padding(marges))
    }
}
