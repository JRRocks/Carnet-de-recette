package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.layout.Column
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
 * L'accueil : où en est la semaine et ce qui vient, sous la forme du schéma de ligne. Au
 * lot 1, une étape « Drive du foyer » ouvre la ligne ; les quatre temps de la semaine sont
 * annoncés, pas encore dans l'app.
 *
 * En tête, au plus deux lignes d'état : la mise à jour, puis « hors ligne ». L'erreur du
 * Drive s'écrit sous son étape, près du bouton « Réessayer ».
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
        if (etat.maj.surLaSemaine) {
            LigneMiseAJour(etat.maj, actions, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
        if (drive is EtatDrive.Pret && drive.horsLigne) {
            LigneEtat(R.drawable.ic_cloud_off, DriveCarnet.MOTIF_HORS_LIGNE, TonEtat.NEUTRE, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
        }
        ClePasEncore(Modifier.padding(top = 8.dp, bottom = 8.dp))
        LigneDeTemps(etapesDeLaSemaine(etat)) { etape ->
            when {
                // Le motif sous l'étape, près du bouton « Réessayer » : la cause et le remède au même endroit.
                etape.titre == DRIVE && drive is EtatDrive.Erreur ->
                    LigneEtat(R.drawable.ic_error, drive.motif, TonEtat.ERREUR, Modifier.padding(top = 12.dp))
                // Qui fait quoi : le rond et le carré, là où l'on cuisinera.
                etape.titre == EN_CUISINE -> LegendeRoles(Modifier.padding(top = 12.dp))
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

/** L'étape du Drive, puis les quatre temps de la semaine, pas encore dans l'app. */
internal fun etapesDeLaSemaine(etat: EtatCarnet): List<Etape> {
    val vendredi = vendrediDeReference(etat.aujourdHui)
    val samedi = vendredi.plusDays(1)
    return listOf(
        etapeDrive(etat.drive),
        Etape("Vendredi", EtatEtape.PAS_ENCORE, "Scanner les box à leur arrivée.", fin = dateCourte(vendredi)),
        Etape("Vendredi soir", EtatEtape.PAS_ENCORE, "Claude découpe chaque recette en tâches pour vous deux et prépare l'audio."),
        Etape("Samedi", EtatEtape.PAS_ENCORE, "Importer la semaine depuis le Drive, d'un geste.", fin = dateCourte(samedi)),
        Etape(EN_CUISINE, EtatEtape.PAS_ENCORE, "Chacun suit ses tâches, à l'écran et à l'oreille."),
    )
}

private const val DRIVE = "Drive du foyer"
private const val EN_CUISINE = "En cuisine"

private fun etapeDrive(drive: EtatDrive): Etape = when (drive) {
    EtatDrive.NonConnecte -> Etape(
        DRIVE,
        EtatEtape.EN_COURS,
        "Branche le compte Google du foyer. L'app y crée le dossier «\u00A0${DriveCarnet.NOM_DOSSIER}\u00A0», " +
            "à ranger ensuite dans «\u00A0Maison\u00A0».",
    )
    EtatDrive.EnCours -> Etape(DRIVE, EtatEtape.EN_COURS, "Google te demande l'accord, puis l'app prépare le dossier…")
    is EtatDrive.Erreur -> Etape(DRIVE, EtatEtape.EN_COURS, "Le branchement n'a pas abouti.")
    // Revérification silencieuse au lancement : l'étape reste faite, rien ne bouge.
    is EtatDrive.Verification -> Etape(DRIVE, EtatEtape.FAITE, drive.compte, fin = "branché")
    is EtatDrive.Pret -> Etape(DRIVE, EtatEtape.FAITE, drive.compte, fin = if (drive.horsLigne) "hors ligne" else "branché")
}
