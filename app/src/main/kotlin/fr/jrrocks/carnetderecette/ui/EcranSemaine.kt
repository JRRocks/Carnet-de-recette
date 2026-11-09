package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import fr.jrrocks.carnetderecette.core.cuisine.ModeCuisine
import fr.jrrocks.carnetderecette.core.cuisine.Planificateur
import fr.jrrocks.carnetderecette.core.cuisine.RecettePreparee
import fr.jrrocks.carnetderecette.core.cuisine.RoleCuisine
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.ui.composants.TeteDeLigne
import fr.jrrocks.carnetderecette.ui.composants.enUi
import fr.jrrocks.carnetderecette.ui.theme.chiffres
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.drive.DriveCarnet
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.ClePasEncore
import fr.jrrocks.carnetderecette.ui.composants.Etape
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.LegendeRoles
import fr.jrrocks.carnetderecette.ui.composants.LigneDeTemps
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat

/** Marge sous le contenu quand le bouton d'action flotte au-dessus : 64 dp de bouton, 16 de marge, 16 d'air. */
private val SOUS_LE_BOUTON = 96.dp

/**
 * L'accueil : où en est la semaine et ce qui vient, sous la forme du schéma de ligne. Une
 * étape « Drive du foyer » ouvre la ligne (facultative tant que la configuration OAuth n'est
 * pas faite : tout reste sur le téléphone) ; le vendredi, on scanne les box et l'on envoie
 * leurs recettes à Claude ; le samedi, on importe la semaine qu'il a préparée ; en cuisine,
 * la recette de ce soir.
 *
 * En tête, les lignes d'état : l'accès HelloFresh rompu et le téléphone plein quand ils
 * arrivent, puis la mise à jour et « hors ligne ». L'erreur du Drive s'écrit sous son étape,
 * près de son bouton « Réessayer ».
 */
@Composable
fun EcranSemaine(etat: EtatCarnet, actions: ActionsCarnet, marges: PaddingValues, avecBouton: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(marges)
            .padding(top = 16.dp, bottom = if (avecBouton) SOUS_LE_BOUTON else 24.dp),
    ) {
        val drive = etat.drive
        // Le site a changé : la sonde le dit, et le mode dégradé est à un geste.
        etat.semaine?.accesRompu?.let { motif ->
            LigneEtat(
                R.drawable.ic_error,
                "Accès HelloFresh rompu\u00A0: $motif Passe en mode dégradé\u00A0: photographie la fiche papier de chaque box.",
                TonEtat.ERREUR,
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                BoutonCarnet(
                    "Photographier la fiche",
                    { actions.ouvrir(Destination.Fiche(etat.semaineIso, numero = null)) },
                    sorte = SorteBouton.CONTOUR,
                    icone = R.drawable.ic_photo_camera,
                )
            }
        }
        // Le téléphone est plein : ce qu'on scanne ne se garde plus tant qu'on n'a pas fait de place.
        etat.semaine?.ecritureImpossible?.let { motif ->
            LigneEtat(R.drawable.ic_error, motif, TonEtat.ERREUR, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
        if (etat.maj.surLaSemaine) {
            LigneMiseAJour(etat.maj, actions, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
        if (drive is EtatDrive.Pret && drive.horsLigne) {
            LigneEtat(R.drawable.ic_cloud_off, DriveCarnet.MOTIF_HORS_LIGNE, TonEtat.NEUTRE, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
        val etapes = remember(etat) { etapesDeLaSemaine(etat) }
        // La clé du pointillé, seulement s'il y en a un sur la ligne.
        if (etapes.any { it.etat == EtatEtape.PAS_ENCORE }) ClePasEncore(Modifier.padding(top = 8.dp, bottom = 8.dp))
        LigneDeTemps(etapes, Modifier.padding(top = 8.dp)) { etape ->
            val semaine = etat.semaine
            when (etape.titre) {
                DRIVE -> ActionDuDrive(drive, actions)
                // Les box déjà scannées, chacune avec sa recette (ou l'état de la lecture de sa page).
                VENDREDI -> semaine?.let { BoxesDeLaSemaine(it, actions) }
                VENDREDI_SOIR -> semaine?.let { EnvoiAClaude(it, actions) }
                SAMEDI -> semaine?.let { ImportDuSamedi(it, actions) }
                EN_CUISINE -> {
                    val ceSoir = semaine?.ceSoir
                    if (semaine != null && ceSoir != null) {
                        CeSoir(semaine, ceSoir, etat.mode)
                    } else if (semaine?.travail == null) {
                        // Qui fait quoi : le rond et le carré, là où l'on cuisinera.
                        LegendeRoles(Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Brancher le Drive, dans son étape. Le motif d'une erreur s'écrit au même endroit que son
 * remède, « Réessayer ».
 */
@Composable
private fun ActionDuDrive(drive: EtatDrive, actions: ActionsCarnet) {
    when (drive) {
        EtatDrive.NonConnecte -> BoutonCarnet(
            "Brancher le Drive",
            actions.brancher,
            modifier = Modifier.padding(top = 12.dp),
            sorte = SorteBouton.CONTOUR,
            icone = R.drawable.ic_add_to_drive,
        )
        is EtatDrive.Erreur -> {
            LigneEtat(R.drawable.ic_error, drive.motif, TonEtat.ERREUR, Modifier.padding(top = 12.dp))
            BoutonCarnet(
                "Réessayer",
                actions.brancher,
                modifier = Modifier.padding(top = 8.dp),
                sorte = SorteBouton.CONTOUR,
                icone = R.drawable.ic_refresh,
            )
        }
        EtatDrive.EnCours, is EtatDrive.Verification, is EtatDrive.Pret -> Unit
    }
}

/**
 * Les box scannées cette semaine : numéro, titre, nombre de personnes ; un toucher ouvre la
 * recette. Le bouton flottant passe ensuite à l'import : le scan reste ouvert d'ici, pour une
 * box arrivée plus tard.
 */
@Composable
private fun BoxesDeLaSemaine(semaine: EtatSemaine, actions: ActionsCarnet) {
    if (semaine.boxes.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        semaine.boxes.sortedBy { it.numero }.forEach { box ->
            LigneDeBox(
                box.numero,
                semaine.titre(box.numero),
                "${box.personnes}\u00A0pers.",
                Modifier.clickable(onClickLabel = "Ouvrir la recette") { actions.ouvrir(Destination.Recette(semaine.semaine, box.numero)) },
            )
        }
        BoutonCarnet(
            "Scanner d'autres box",
            { actions.ouvrir(Destination.Scanner(semaine.semaine)) },
            modifier = Modifier.padding(top = 4.dp),
            sorte = SorteBouton.TEXTE,
            icone = R.drawable.ic_document_scanner,
        )
    }
}

/** Une box : son numéro, son titre, et à droite son nombre de personnes ou son état ; [modifier] la rend touchable. */
@Composable
internal fun LigneDeBox(numero: Int, titre: String, droite: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$numero",
            style = MaterialTheme.typography.titleMedium.chiffres(),
            modifier = Modifier.widthIn(min = 44.dp),
        )
        Text(
            titre,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (droite != null) {
            Text(
                droite,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Le vendredi soir : tant que l'app ne dépose pas elle-même les recettes sur le Drive, on les y
 * envoie d'un geste (partage d'Android) ; Claude les y lit. Une fois la semaine importée, ce
 * que Claude a signalé (une box qu'il n'a pas pu préparer) s'écrit ici.
 */
@Composable
private fun EnvoiAClaude(semaine: EtatSemaine, actions: ActionsCarnet) {
    semaine.travail?.message?.let { message ->
        LigneEtat(R.drawable.ic_info, "Claude\u00A0: $message", TonEtat.INFO, Modifier.padding(top = 12.dp))
    }
    // Renvoyer reste possible : une box scannée après l'import, des fiches photographiées en secours.
    if (semaine.aEnvoyer) {
        BoutonCarnet(
            if (semaine.travail == null) "Envoyer les recettes" else "Renvoyer les recettes",
            actions.envoyerAClaude,
            modifier = Modifier.padding(top = 12.dp),
            sorte = if (semaine.preparees.isEmpty()) SorteBouton.CONTOUR else SorteBouton.TEXTE,
            icone = R.drawable.ic_add_to_drive,
        )
    }
}

/**
 * Le samedi : ce que l'import a refusé, avec de quoi choisir un autre fichier, ou ce qu'il a
 * laissé de côté. Sur le téléphone qui n'a rien scanné (celui de l'autre cuisinier), le bouton
 * flottant reste au scan : l'import se fait d'ici.
 */
@Composable
private fun ImportDuSamedi(semaine: EtatSemaine, actions: ActionsCarnet) {
    val refus = semaine.importRefuse
    when {
        refus != null -> LigneEtat(R.drawable.ic_error, refus, TonEtat.ERREUR, Modifier.padding(top = 12.dp)) {
            BoutonCarnet("Choisir un autre fichier", actions.importer, sorte = SorteBouton.CONTOUR, icone = R.drawable.ic_folder)
        }
        // Une semaine à cuisiner : Claude a pu en écrire une autre version (« -v2 »).
        semaine.preparees.isNotEmpty() -> {
            if (semaine.remarquesImport.isNotEmpty()) {
                LigneEtat(R.drawable.ic_info, semaine.remarquesImport.joinToString(" "), TonEtat.INFO, Modifier.padding(top = 12.dp))
            }
            BoutonCarnet(
                "Importer une autre version",
                actions.importer,
                modifier = Modifier.padding(top = 4.dp),
                sorte = SorteBouton.TEXTE,
                icone = R.drawable.ic_folder,
            )
        }
        // Sans rien à envoyer (le téléphone de l'autre cuisinier), le bouton flottant reste au scan.
        !semaine.aEnvoyer -> BoutonCarnet(
            "Importer la semaine",
            actions.importer,
            modifier = Modifier.padding(top = 12.dp),
            sorte = SorteBouton.CONTOUR,
            icone = R.drawable.ic_folder,
        )
    }
}

/** La recette de ce soir et, à deux, ce que chacun aura à faire. */
@Composable
private fun CeSoir(semaine: EtatSemaine, recette: RecettePreparee, mode: ModeCuisine) {
    Column(Modifier.padding(top = 8.dp)) {
        LigneDeBox(recette.numero, semaine.titre(recette.numero), recette.personnes?.let { "$it\u00A0pers." })
        if (mode == ModeCuisine.A_DEUX) {
            val plan = remember(recette) { Planificateur.planifier(recette, ModeCuisine.A_DEUX) }
            RoleCuisine.entries.forEach { role ->
                val taches = plan.duRole(role).count { !it.tache.passive }
                ResumeRole(role, "${role.enUi().libelle}\u00A0: ${pluriel(taches, "tâche", "tâches")} · ${plan.occupation(role)}\u00A0min")
            }
        }
    }
}

/** La tête de ligne d'un rôle et ce qu'il aura à faire. */
@Composable
private fun ResumeRole(role: RoleCuisine, texte: String) {
    Row(
        Modifier.padding(top = 8.dp).clearAndSetSemantics { contentDescription = texte },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeteDeLigne(role.enUi())
        Spacer(Modifier.width(12.dp))
        Text(texte, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** La Semaine ne montre la mise à jour que si elle attend un geste ou se télécharge ; pas après « Plus tard ». */
private val EtatMiseAJour.surLaSemaine: Boolean
    get() = attendUnGeste || this is EtatMiseAJour.Telechargement

/**
 * La proposition de mise à jour, en ligne d'état plutôt qu'en boîte de dialogue : elle
 * n'interrompt personne en pleine cuisine. Reprise dans Réglages, sans « Plus tard » une fois
 * la proposition écartée.
 */
@Composable
fun LigneMiseAJour(maj: EtatMiseAJour, actions: ActionsCarnet, modifier: Modifier = Modifier) {
    val plusTard = @Composable { BoutonCarnet("Plus tard", actions.majPlusTard, sorte = SorteBouton.TEXTE) }
    when (maj) {
        EtatMiseAJour.Aucune -> Unit
        is EtatMiseAJour.Disponible -> LigneEtat(
            R.drawable.ic_system_update,
            "La version ${maj.versionNom} est prête.",
            TonEtat.INFO,
            modifier,
        ) {
            plusTard()
            BoutonCarnet("Mettre à jour", actions.mettreAJour)
        }
        is EtatMiseAJour.Ecartee -> LigneEtat(
            R.drawable.ic_system_update,
            "La version ${maj.versionNom} est prête.",
            TonEtat.INFO,
            modifier,
        ) {
            BoutonCarnet("Mettre à jour", actions.mettreAJour)
        }
        is EtatMiseAJour.Telechargement -> LigneEtat(
            R.drawable.ic_system_update,
            "Téléchargement de la version ${maj.versionNom}…",
            TonEtat.INFO,
            modifier,
            progression = maj.fraction,
        )
        is EtatMiseAJour.AAutoriser -> LigneEtat(
            R.drawable.ic_system_update,
            "Autorise l'installation pour Carnet de recette dans les réglages Android, puis appuie sur «\u00A0Mettre à jour\u00A0».",
            TonEtat.INFO,
            modifier,
        ) {
            plusTard()
            BoutonCarnet("Mettre à jour", actions.mettreAJour)
        }
        is EtatMiseAJour.Echec -> LigneEtat(
            R.drawable.ic_error,
            "Téléchargement de la version ${maj.versionNom} interrompu\u00A0: ${maj.motif}.",
            TonEtat.ERREUR,
            modifier,
        ) {
            plusTard()
            BoutonCarnet("Réessayer", actions.mettreAJour, sorte = SorteBouton.CONTOUR)
        }
    }
}

/**
 * L'étape du Drive, puis les quatre temps de la semaine, tous dans l'app depuis le lot 5 : on
 * scanne, on envoie les recettes à Claude, on importe ce qu'il a préparé, on cuisine. L'étape
 * en cours est la première qui attend un geste.
 */
internal fun etapesDeLaSemaine(etat: EtatCarnet): List<Etape> {
    val vendredi = vendrediDeReference(etat.aujourdHui)
    val samedi = vendredi.plusDays(1)
    val semaine = etat.semaine
    val travail = semaine?.travail
    // Importée mais sans recette : Claude n'a rien pu préparer (accès au site rompu, sans fiche).
    val rienAPreparer = travail != null && travail.recettes.isEmpty()
    val ceSoir = semaine?.ceSoir
    return listOf(
        etapeDrive(etat.drive),
        when {
            semaine != null && semaine.aEnvoyer -> Etape(VENDREDI, EtatEtape.FAITE, ceQuiEstScanne(semaine), fin = dateCourte(vendredi))
            // La semaine est importée sur le téléphone qui n'a rien scanné : c'est fait, ailleurs.
            travail != null -> Etape(VENDREDI, EtatEtape.FAITE, "Box scannées sur l'autre téléphone.", fin = dateCourte(vendredi))
            else -> Etape(VENDREDI, EtatEtape.EN_COURS, "Scanne chaque box à son arrivée\u00A0: l'app lit son numéro et va chercher la recette.", fin = dateCourte(vendredi))
        },
        when {
            rienAPreparer -> Etape(VENDREDI_SOIR, EtatEtape.EN_COURS, "Claude n'a rien pu préparer\u00A0: suis son message, puis renvoie les recettes.")
            semaine != null && travail != null -> Etape(VENDREDI_SOIR, EtatEtape.FAITE, resumeDuTravail(semaine, etat.mode))
            semaine != null && semaine.aEnvoyer -> Etape(
                VENDREDI_SOIR,
                EtatEtape.EN_COURS,
                "Envoie les recettes sur le Drive, dans «\u00A0Carnet de recettes\u00A0», puis lance Claude\u00A0: " +
                    "il découpe chacune en tâches pour vous deux et prépare l'audio.",
            )
            else -> Etape(VENDREDI_SOIR, EtatEtape.A_FAIRE, "Claude découpe chaque recette en tâches pour vous deux et prépare l'audio.")
        },
        when {
            rienAPreparer -> Etape(SAMEDI, EtatEtape.A_FAIRE, "Importe la nouvelle version de Claude.", fin = dateCourte(samedi))
            travail != null -> Etape(SAMEDI, EtatEtape.FAITE, "Semaine importée.", fin = dateCourte(samedi))
            else -> Etape(SAMEDI, EtatEtape.A_FAIRE, "Importe la semaine préparée par Claude, d'un geste.", fin = dateCourte(samedi))
        },
        when {
            ceSoir != null -> {
                val plan = Planificateur.planifier(ceSoir, etat.mode)
                val qui = if (etat.mode == ModeCuisine.A_DEUX) "à deux" else "seul"
                Etape(EN_CUISINE, EtatEtape.EN_COURS, "Ce soir\u00A0: la ${ceSoir.numero}, environ ${dureeEnClair(plan.duree)} $qui.")
            }
            travail != null && !rienAPreparer -> Etape(EN_CUISINE, EtatEtape.FAITE, "Toutes les recettes de la semaine sont cuisinées.")
            else -> Etape(EN_CUISINE, EtatEtape.A_FAIRE, "Chacun suit ses tâches, à l'écran et à l'oreille.")
        },
    )
}

/** « 2 box scannées. », « 1 fiche photographiée. », « 2 box scannées, 1 fiche photographiée. » */
private fun ceQuiEstScanne(semaine: EtatSemaine): String {
    val boxes = semaine.boxes.size
    val fiches = semaine.fichier.fiches.size
    val parties = listOfNotNull(
        boxes.takeIf { it > 0 }?.let { if (it == 1) "1\u00A0box scannée" else "$it\u00A0box scannées" },
        fiches.takeIf { it > 0 }?.let { if (it == 1) "1\u00A0fiche photographiée" else "$it\u00A0fiches photographiées" },
    )
    return parties.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
}

/** « Claude a préparé les 6 recettes : 112 tâches pour vous deux, 105 segments à écouter. » */
private fun resumeDuTravail(semaine: EtatSemaine, mode: ModeCuisine): String {
    val recettes = semaine.preparees
    val taches = recettes.sumOf { it.taches.size }
    val segments = recettes.sumOf { it.segments.size }
    val quoi = if (recettes.size == 1) "la recette" else "les ${recettes.size} recettes"
    val pourQui = if (mode == ModeCuisine.A_DEUX) " pour vous deux" else ""
    return "Claude a préparé $quoi\u00A0: ${pluriel(taches, "tâche", "tâches")}$pourQui, ${pluriel(segments, "segment", "segments")} à écouter."
}

/** « 1 tâche », « 4 tâches », sans que le nombre reste seul en fin de ligne. */
internal fun pluriel(n: Int, un: String, plusieurs: String): String = if (n == 1) "1\u00A0$un" else "$n\u00A0$plusieurs"

private const val DRIVE = "Drive du foyer"
private const val VENDREDI = "Vendredi"
private const val VENDREDI_SOIR = "Vendredi soir"
private const val SAMEDI = "Samedi"
private const val EN_CUISINE = "En cuisine"

private fun etapeDrive(drive: EtatDrive): Etape = when (drive) {
    // Facultatif pour l'instant : les outils gardent tout sur le téléphone (décision du 2026-10-27).
    EtatDrive.NonConnecte -> Etape(
        DRIVE,
        EtatEtape.A_FAIRE,
        "Branche le compte Google du foyer pour partager la semaine avec Claude. D'ici là, tout reste sur le téléphone.",
    )
    EtatDrive.EnCours -> Etape(DRIVE, EtatEtape.A_FAIRE, "Google te demande l'accord, puis l'app prépare le dossier…")
    is EtatDrive.Erreur -> Etape(DRIVE, EtatEtape.A_FAIRE, "Le branchement n'a pas abouti.")
    // Revérification silencieuse au lancement : l'étape reste faite, rien ne bouge.
    is EtatDrive.Verification -> Etape(DRIVE, EtatEtape.FAITE, drive.compte, fin = "branché")
    is EtatDrive.Pret -> Etape(DRIVE, EtatEtape.FAITE, drive.compte, fin = if (drive.horsLigne) "hors ligne" else "branché")
}
