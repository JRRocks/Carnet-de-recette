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
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.EtatVide
import fr.jrrocks.carnetderecette.ui.composants.PlanADeuxExemple
import fr.jrrocks.carnetderecette.ui.composants.RangeeADeux
import fr.jrrocks.carnetderecette.ui.composants.TacheExemple

/** Recette factice : aucun contenu HelloFresh dans le dépôt (CLAUDE.md, règle 1). */
private val EXEMPLE = listOf(
    RangeeADeux(
        decoupe = TacheExemple("Émincer les poireaux", EtatEtape.FAITE),
        chauffe = TacheExemple("Chauffer l'huile dans la poêle", EtatEtape.FAITE),
    ),
    RangeeADeux(passage = "Les poireaux passent à la chauffe"),
    RangeeADeux(
        decoupe = TacheExemple("Râper le parmesan", EtatEtape.EN_COURS),
        chauffe = TacheExemple("Faire revenir les poireaux", EtatEtape.EN_COURS),
    ),
    RangeeADeux(
        decoupe = TacheExemple("Hacher l'ail et l'échalote", EtatEtape.A_FAIRE),
        chauffe = TacheExemple("Ajouter le riz", EtatEtape.A_FAIRE),
    ),
)

private const val RESUME_EXEMPLE =
    "Exemple d'un risotto préparé à deux. Découpe : émincer les poireaux, fait ; râper le parmesan, en cours ; " +
        "hacher l'ail et l'échalote, à faire. Chauffe : chauffer l'huile, fait ; faire revenir les poireaux, en cours ; " +
        "ajouter le riz, à faire. Les poireaux émincés passent de la découpe à la chauffe."

/** La cuisine à deux (lots 5 et 6). En attendant, un plan d'exemple montre le principe. */
@Composable
fun EcranCuisine(marges: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges)) {
        EtatVide(
            titre = "Ce soir, chacun ses tâches",
            texte = "Chaque cuisinier suit ses tâches à l'écran et à l'oreille. Quand l'un a fini ce dont " +
                "l'autre a besoin, il lui passe la main.",
            mention = "Arrive après l'import du samedi. D'abord, chaque téléphone suivra ses tâches seul ; " +
                "la synchro des deux téléphones viendra ensuite.",
        ) {
            PlanADeuxExemple(EXEMPLE, RESUME_EXEMPLE, Modifier.padding(top = 8.dp))
            Text(
                "Exemple : un risotto préparé à deux.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
