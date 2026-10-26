package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.EtatVide
import fr.jrrocks.carnetderecette.ui.composants.PlanADeuxExemple
import fr.jrrocks.carnetderecette.ui.composants.RangeeADeux.Passage
import fr.jrrocks.carnetderecette.ui.composants.RangeeADeux.Tache
import fr.jrrocks.carnetderecette.ui.composants.Role.CHAUFFE
import fr.jrrocks.carnetderecette.ui.composants.Role.DECOUPE

/** Recette factice : aucun contenu HelloFresh dans le dépôt (CLAUDE.md, règle 1). */
private val EXEMPLE = listOf(
    Tache(DECOUPE, "Émincer les poireaux", EtatEtape.FAITE),
    Tache(CHAUFFE, "Chauffer l'huile dans la poêle", EtatEtape.FAITE),
    Passage("Les poireaux passent à la chauffe", vers = CHAUFFE, etat = EtatEtape.FAITE),
    Tache(DECOUPE, "Râper le parmesan", EtatEtape.EN_COURS),
    Tache(CHAUFFE, "Faire revenir les poireaux", EtatEtape.EN_COURS),
    Tache(DECOUPE, "Hacher l'ail et l'échalote", EtatEtape.A_FAIRE),
    Tache(CHAUFFE, "Ajouter le riz", EtatEtape.A_FAIRE),
)

private const val LEGENDE = "Exemple\u00A0: un risotto préparé à deux."

private const val RESUME =
    "Découpe : émincer les poireaux, fait ; râper le parmesan, en cours ; hacher l'ail et l'échalote, à faire. " +
        "Chauffe : chauffer l'huile, fait ; faire revenir les poireaux, en cours ; ajouter le riz, à faire. " +
        "Les poireaux émincés sont passés de la découpe à la chauffe."

/** La cuisine à deux (lots 5 et 6). En attendant, un plan d'exemple montre le principe. */
@Composable
fun EcranCuisine(marges: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges)) {
        EtatVide(
            titre = "En cuisine, chacun ses tâches",
            texte = "Chaque cuisinier suit ses tâches à l'écran et à l'oreille. Quand l'un a fini ce dont " +
                "l'autre a besoin, il lui passe la main.",
            mention = "Pas encore dans l'app. D'abord, chaque téléphone suivra ses tâches seul\u00A0; " +
                "la synchro des deux téléphones viendra ensuite.",
        ) {
            PlanADeuxExemple(EXEMPLE, LEGENDE, RESUME)
        }
    }
}
