package fr.jrrocks.carnetderecette.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.cuisine.LignePlan
import fr.jrrocks.carnetderecette.core.cuisine.ModeCuisine
import fr.jrrocks.carnetderecette.core.cuisine.Plan
import fr.jrrocks.carnetderecette.core.cuisine.Planificateur
import fr.jrrocks.carnetderecette.core.cuisine.RecettePreparee
import fr.jrrocks.carnetderecette.core.cuisine.RoleCuisine
import fr.jrrocks.carnetderecette.core.cuisine.SegmentsDuCuisinier
import fr.jrrocks.carnetderecette.core.cuisine.Tache
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.EtatVide
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.PlanADeux
import fr.jrrocks.carnetderecette.ui.composants.PlanADeuxExemple
import fr.jrrocks.carnetderecette.ui.composants.RangeeADeux
import fr.jrrocks.carnetderecette.ui.composants.RangeeADeux.Passage
import fr.jrrocks.carnetderecette.ui.composants.RangeeADeux.Tache as TacheDuPlan
import fr.jrrocks.carnetderecette.ui.composants.Role.CHAUFFE
import fr.jrrocks.carnetderecette.ui.composants.Role.DECOUPE
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat
import fr.jrrocks.carnetderecette.ui.composants.enUi
import fr.jrrocks.carnetderecette.ui.theme.chiffres

/**
 * La cuisine (lot 5) : la recette de ce soir, son plan à deux (ou seul), puis « Je suis… » et
 * « Commencer mes tâches » : chaque téléphone lit les segments de son cuisinier. Plus bas, les
 * autres recettes de la semaine, à choisir pour ce soir.
 *
 * Avant l'import du samedi, un plan d'exemple montre le principe et la lecture à voix haute
 * s'essaie sur une recette d'exemple.
 */
@Composable
fun EcranCuisine(etat: EtatCarnet, actions: ActionsCarnet, marges: PaddingValues) {
    val semaine = etat.semaine
    val preparees = semaine?.preparees.orEmpty()
    if (semaine == null || preparees.isEmpty()) {
        SansSemaine(etat, actions, marges)
        return
    }
    // La recette touchée dans la liste ; à défaut, celle que Claude conseille pour ce soir.
    var choisie by rememberSaveable(semaine.semaine.toString()) { mutableStateOf<Int?>(null) }
    val recette = preparees.firstOrNull { it.numero == choisie } ?: semaine.ceSoir ?: preparees.first()
    val plan = remember(recette, etat.mode) { Planificateur.planifier(recette, etat.mode) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges).padding(vertical = 16.dp)) {
        EnTete(semaine, recette, plan)
        ChoixDuMode(etat.mode, actions.choisirMode)
        if (etat.mode == ModeCuisine.A_DEUX) {
            PlanADeux(rangeesDuPlan(plan), legende = "Le même plan sur vos deux téléphones.", modifier = Modifier.padding(top = 4.dp))
        } else {
            Text(
                "${pluriel(plan.places.count { !it.tache.passive }, "tâche", "tâches")} à la suite\u00A0: pendant les cuissons, la voix te fait avancer le reste.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Commencer(semaine, recette, etat, actions)
        AutresRecettes(semaine, recette) { choisie = it }
    }
}

/** « Ce soir », le plat, et combien de temps il prendra ; le doute de Claude s'il en a un. */
@Composable
private fun EnTete(semaine: EtatSemaine, recette: RecettePreparee, plan: Plan) {
    val couleurs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            if (recette.numero in semaine.cuisinees) "Déjà cuisinée" else "Ce soir",
            style = MaterialTheme.typography.titleMedium,
            color = couleurs.onSurfaceVariant,
        )
        Text(
            semaine.numeroEtTitre(recette.numero),
            style = MaterialTheme.typography.headlineSmall,
            color = couleurs.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val personnes = recette.personnes?.let { if (it == 1) "1\u00A0personne · " else "$it\u00A0personnes · " }.orEmpty()
        val qui = if (plan.mode == ModeCuisine.A_DEUX) "à deux" else "seul"
        Text(
            "${personnes}environ ${dureeEnClair(plan.duree)} $qui",
            style = MaterialTheme.typography.bodyLarge.chiffres(),
            color = couleurs.onSurfaceVariant,
        )
        if (recette.aConfirmer) {
            LigneEtat(
                R.drawable.ic_help,
                "Claude a un doute\u00A0: ${recette.confirmation?.let(::enMinuscule) ?: "vérifie le nombre de personnes sur l'étiquette."}",
                TonEtat.INFO,
                Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** À deux ou seul : retenu par ce téléphone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoixDuMode(mode: ModeCuisine, choisir: (ModeCuisine) -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Choix("À deux", mode == ModeCuisine.A_DEUX, R.drawable.ic_group) { choisir(ModeCuisine.A_DEUX) }
        Choix("Seul", mode == ModeCuisine.SEUL, null) { choisir(ModeCuisine.SEUL) }
    }
}

/** Un choix parmi deux : plein quand il est pris, au contour sinon ; TalkBack dit lequel est pris. */
@Composable
private fun Choix(texte: String, pris: Boolean, @DrawableRes icone: Int?, choisir: () -> Unit) {
    BoutonCarnet(
        texte,
        choisir,
        Modifier.semantics { selected = pris },
        sorte = if (pris) SorteBouton.PLEIN else SorteBouton.CONTOUR,
        icone = icone,
    )
}

/**
 * Qui cuisine sur ce téléphone, puis le départ de la séance. À deux, chacun choisit son rôle
 * une fois : le téléphone le retient. Un rôle sans segment pour cette recette le dit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Commencer(semaine: EtatSemaine, recette: RecettePreparee, etat: EtatCarnet, actions: ActionsCarnet) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        val qui: Qui? = if (etat.mode == ModeCuisine.SEUL) {
            Qui.SEUL
        } else {
            Text("Je suis…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Choix("Découpe", etat.role == RoleCuisine.DECOUPE, R.drawable.ic_blender) { actions.choisirRole(RoleCuisine.DECOUPE) }
                Choix("Chauffe", etat.role == RoleCuisine.CHAUFFE, R.drawable.ic_skillet) { actions.choisirRole(RoleCuisine.CHAUFFE) }
            }
            when (etat.role) {
                RoleCuisine.DECOUPE -> Qui.DECOUPE
                RoleCuisine.CHAUFFE -> Qui.CHAUFFE
                null -> null
            }
        }
        val segments = remember(recette, qui) {
            when (qui) {
                Qui.SEUL -> SegmentsDuCuisinier.seul(recette).segments.size
                Qui.DECOUPE -> SegmentsDuCuisinier.aDeux(recette, RoleCuisine.DECOUPE).segments.size
                Qui.CHAUFFE -> SegmentsDuCuisinier.aDeux(recette, RoleCuisine.CHAUFFE).segments.size
                Qui.RECETTE, null -> 0
            }
        }
        when {
            qui == null -> Mention("Choisis ton rôle\u00A0: ce téléphone le retient.")
            segments == 0 -> Mention(
                if (qui == Qui.SEUL) "Claude n'a rien préparé à lire pour cette recette." else "Claude n'a rien prévu pour la ${etat.role?.enUi()?.libelle?.lowercase()} dans cette recette.",
            )
            else -> BoutonCarnet(
                if (qui == Qui.SEUL) "Commencer" else "Commencer mes tâches",
                { actions.ouvrir(Destination.Seance(semaine.semaine, recette.numero, qui)) },
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                icone = R.drawable.ic_play_arrow,
            )
        }
    }
}

@Composable
private fun Mention(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
}

/** Les autres recettes de la semaine ; un toucher en fait celle de ce soir. */
@Composable
private fun AutresRecettes(semaine: EtatSemaine, recette: RecettePreparee, choisir: (Int) -> Unit) {
    val autres = semaine.preparees.filter { it.numero != recette.numero }
    if (autres.isEmpty()) return
    HorizontalDivider(Modifier.padding(top = 24.dp, bottom = 8.dp))
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Les autres recettes de la semaine",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
        )
        autres.forEach { autre ->
            val droite = if (autre.numero in semaine.cuisinees) "cuisinée" else autre.personnes?.let { "$it\u00A0pers." }
            LigneDeBox(
                autre.numero,
                semaine.titre(autre.numero),
                droite,
                Modifier.clickable(onClickLabel = "Cuisiner celle-ci ce soir") { choisir(autre.numero) },
            )
        }
    }
}

/** Avant l'import : le principe, sur un plan d'exemple, et la voix à essayer. */
@Composable
private fun SansSemaine(etat: EtatCarnet, actions: ActionsCarnet, marges: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges)) {
        EtatVide(
            titre = "En cuisine, chacun ses tâches",
            texte = "Chaque cuisinier suit ses tâches à l'écran et à l'oreille. Quand l'un a fini ce dont " +
                "l'autre a besoin, il lui passe la main.",
            mention = "Importe la semaine préparée par Claude\u00A0: le plan de chaque recette apparaît ici. " +
                "La synchro des deux téléphones viendra ensuite.",
        ) {
            PlanADeuxExemple(EXEMPLE, LEGENDE, RESUME)
        }
        // Sur chaque téléphone, même celui qui n'a rien scanné : chacun importe la même semaine.
        BoutonCarnet(
            "Importer la semaine",
            actions.importer,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            icone = R.drawable.ic_folder,
        )
        // Déjà là : la voix et les commandes vocales, sur la recette d'exemple.
        BoutonCarnet(
            "Essayer la lecture à voix haute",
            { actions.ouvrir(Destination.Seance(etat.semaineIso, numero = null)) },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            sorte = SorteBouton.CONTOUR,
            icone = R.drawable.ic_volume_up,
        )
    }
}

/**
 * Le plan à deux, rangée par rangée : chaque tâche sur la ligne de son rôle, et chaque passage
 * de main juste avant la tâche qui en a besoin. Une attente dit sa durée : c'est elle qu'on
 * guette. Rien n'est encore fait : la synchro des téléphones (lot 6) fera avancer le plan.
 */
internal fun rangeesDuPlan(plan: Plan): List<RangeeADeux> = plan.lignes().map { ligne ->
    when (ligne) {
        is LignePlan.Etape -> TacheDuPlan(ligne.place.role.enUi(), libelle(ligne.place.tache), EtatEtape.A_FAIRE)
        is LignePlan.Passage -> Passage(
            "Passe à la ${ligne.vers.enUi().libelle.lowercase()}\u00A0: ${ligne.source.tache.texte.replaceFirstChar { it.lowercase() }}",
            vers = ligne.vers.enUi(),
            etat = EtatEtape.A_FAIRE,
        )
    }
}

private fun libelle(tache: Tache): String =
    if (tache.passive && "min" !in tache.texte) "${tache.texte} (${tache.minutes}\u00A0min)" else tache.texte

/** « Le 4 est effacé » → « le 4 est effacé », pour suivre « Claude a un doute : » ; un sigle reste tel quel. */
private fun enMinuscule(phrase: String): String =
    if (phrase.length > 1 && phrase[0].isUpperCase() && phrase[1].isLowerCase()) phrase.replaceFirstChar { it.lowercase() } else phrase

/** « 29 min », « 1 h », « 1 h 05 ». */
internal fun dureeEnClair(minutes: Int): String {
    if (minutes < 60) return "$minutes\u00A0min"
    val reste = minutes % 60
    return if (reste == 0) "${minutes / 60}\u00A0h" else "${minutes / 60}\u00A0h\u00A0${reste.toString().padStart(2, '0')}"
}

/** Recette factice : aucun contenu HelloFresh dans le dépôt (CLAUDE.md, règle 1). */
private val EXEMPLE = listOf(
    TacheDuPlan(DECOUPE, "Émincer les poireaux", EtatEtape.FAITE),
    TacheDuPlan(CHAUFFE, "Chauffer l'huile dans la poêle", EtatEtape.FAITE),
    Passage("Les poireaux passent à la chauffe", vers = CHAUFFE, etat = EtatEtape.FAITE),
    TacheDuPlan(DECOUPE, "Râper le parmesan", EtatEtape.EN_COURS),
    TacheDuPlan(CHAUFFE, "Faire revenir les poireaux", EtatEtape.EN_COURS),
    TacheDuPlan(DECOUPE, "Hacher l'ail et l'échalote", EtatEtape.A_FAIRE),
    TacheDuPlan(CHAUFFE, "Ajouter le riz", EtatEtape.A_FAIRE),
)

private const val LEGENDE = "Exemple\u00A0: un risotto préparé à deux."

private const val RESUME =
    "Découpe : émincer les poireaux, fait ; râper le parmesan, en cours ; hacher l'ail et l'échalote, à faire. " +
        "Chauffe : chauffer l'huile, fait ; faire revenir les poireaux, en cours ; ajouter le riz, à faire. " +
        "Les poireaux émincés sont passés de la découpe à la chauffe."
