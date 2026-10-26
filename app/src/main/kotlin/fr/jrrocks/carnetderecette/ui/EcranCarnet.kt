package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import fr.jrrocks.carnetderecette.ui.composants.Etape
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.EtatVide
import fr.jrrocks.carnetderecette.ui.composants.LigneDeTemps

/** Le carnet des recettes importées (lot 4). En attendant, il explique d'où elles viendront. */
@Composable
fun EcranCarnet(marges: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges)) {
        EtatVide(
            titre = "Le carnet se remplit le samedi",
            texte = "Le vendredi soir, Claude découpe les recettes en tâches pour vous deux. Le samedi, " +
                "un geste les ramène ici depuis le Drive : « Importer la semaine ».",
            mention = "Pas encore dans cette version.",
        ) {
            val resume = "Exemple : la semaine en quatre temps. Vendredi et vendredi soir sont faits, " +
                "samedi on importe la semaine, puis on cuisine."
            Column(Modifier.clearAndSetSemantics { contentDescription = resume }) {
                LigneDeTemps(
                    listOf(
                        Etape("Vendredi", EtatEtape.FAITE),
                        Etape("Vendredi soir", EtatEtape.FAITE),
                        Etape("Samedi", EtatEtape.EN_COURS, "Importer la semaine"),
                        Etape("En cuisine", EtatEtape.A_FAIRE),
                    ),
                )
                Text(
                    "Exemple",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
