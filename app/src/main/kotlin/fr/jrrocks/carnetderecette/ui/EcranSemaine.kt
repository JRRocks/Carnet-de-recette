package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.ui.theme.chiffres
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
 * pas faite : tout reste sur le téléphone) ; le vendredi, on scanne les box ; les temps
 * suivants sont annoncés, pas encore dans l'app.
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
        ClePasEncore(Modifier.padding(top = 8.dp, bottom = 8.dp))
        LigneDeTemps(etapesDeLaSemaine(etat)) { etape ->
            when (etape.titre) {
                DRIVE -> ActionDuDrive(drive, actions)
                // Les box déjà scannées, chacune avec sa recette (ou l'état de la lecture de sa page).
                VENDREDI -> etat.semaine?.let { BoxesDeLaSemaine(it, actions) }
                // Qui fait quoi : le rond et le carré, là où l'on cuisinera.
                EN_CUISINE -> LegendeRoles(Modifier.padding(top = 12.dp))
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

/** Les box scannées cette semaine : numéro, titre, nombre de personnes ; un toucher ouvre la recette. */
@Composable
private fun BoxesDeLaSemaine(semaine: EtatSemaine, actions: ActionsCarnet) {
    if (semaine.boxes.isEmpty()) return
    Column(Modifier.padding(top = 8.dp)) {
        semaine.boxes.sortedBy { it.numero }.forEach { box ->
            val recette = semaine.recette(box.numero)
            val titre = recette?.titre ?: semaine.menu?.entree(box.numero)?.titre ?: "Recette ${box.numero}"
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "Ouvrir la recette") { actions.ouvrir(Destination.Recette(semaine.semaine, box.numero)) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${box.numero}",
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
                Text(
                    "${box.personnes}\u00A0pers.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
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
 * L'étape du Drive, puis les quatre temps de la semaine. Le vendredi (scan des box) est dans
 * l'app depuis le lot 2 : c'est l'étape en cours tant qu'aucune box n'est scannée.
 */
internal fun etapesDeLaSemaine(etat: EtatCarnet): List<Etape> {
    val vendredi = vendrediDeReference(etat.aujourdHui)
    val samedi = vendredi.plusDays(1)
    val boxes = etat.semaine?.boxes.orEmpty()
    return listOf(
        etapeDrive(etat.drive),
        if (boxes.isEmpty()) {
            Etape(VENDREDI, EtatEtape.EN_COURS, "Scanne chaque box à son arrivée\u00A0: l'app lit son numéro et va chercher la recette.", fin = dateCourte(vendredi))
        } else {
            Etape(VENDREDI, EtatEtape.FAITE, if (boxes.size == 1) "1 box scannée." else "${boxes.size} box scannées.", fin = dateCourte(vendredi))
        },
        Etape("Vendredi soir", EtatEtape.PAS_ENCORE, "Claude découpe chaque recette en tâches pour vous deux et prépare l'audio."),
        Etape("Samedi", EtatEtape.PAS_ENCORE, "Importer la semaine depuis le Drive, d'un geste.", fin = dateCourte(samedi)),
        Etape(EN_CUISINE, EtatEtape.PAS_ENCORE, "Chacun suit ses tâches, à l'écran et à l'oreille."),
    )
}

private const val DRIVE = "Drive du foyer"
private const val VENDREDI = "Vendredi"
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
