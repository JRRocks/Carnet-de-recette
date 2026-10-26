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
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.ui.composants.Etape
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.EtatVide
import fr.jrrocks.carnetderecette.ui.composants.LigneDeTemps

private const val LEGENDE = "Exemple\u00A0: la semaine en quatre temps."
private const val RESUME = "Vendredi et vendredi soir sont faits ; le samedi, on importe la semaine dans le carnet ; puis on cuisine."

/** Le carnet des recettes importées (lot 4). En attendant, il explique d'où elles viendront. */
@Composable
fun EcranCarnet(marges: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges)) {
        EtatVide(
            titre = "Le carnet se remplit le samedi",
            texte = "Le vendredi soir, Claude découpe les recettes en tâches pour vous deux. Le samedi, " +
                "un geste les ramène ici depuis le Drive\u00A0: «\u00A0Importer la semaine\u00A0».",
            mention = "Pas encore dans l'app.",
        ) {
            Column(Modifier.clearAndSetSemantics { contentDescription = "$LEGENDE $RESUME" }) {
                LigneDeTemps(
                    listOf(
                        Etape("Vendredi", EtatEtape.FAITE),
                        Etape("Vendredi soir", EtatEtape.FAITE),
                        Etape("Samedi", EtatEtape.EN_COURS, "Importer la semaine"),
                        Etape("En cuisine", EtatEtape.A_FAIRE),
                    ),
                    // Dans un exemple, la plaque ne dépasse pas le titre de l'écran.
                    stylePlaque = MaterialTheme.typography.titleLarge,
                )
                Text(
                    LEGENDE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}
