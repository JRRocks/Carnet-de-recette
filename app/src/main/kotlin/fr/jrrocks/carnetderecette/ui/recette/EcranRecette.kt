package fr.jrrocks.carnetderecette.ui.recette

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse

/** Provisoire : remplacé par l'outil du lot 2. */
@Composable
fun EcranRecette(destination: Destination.Recette, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    EcranPousse("Recette", onRetour) { marges ->
        Text("En construction.", Modifier.padding(marges))
    }
}
