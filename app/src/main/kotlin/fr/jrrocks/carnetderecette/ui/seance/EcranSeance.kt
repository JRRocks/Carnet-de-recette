package fr.jrrocks.carnetderecette.ui.seance

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse

/** Provisoire : remplacé par l'outil du lot 2. */
@Composable
fun EcranSeance(destination: Destination.Seance, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    EcranPousse("Séance", onRetour) { marges ->
        Text("En construction.", Modifier.padding(marges))
    }
}
