package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.layout.Arrangement
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
import fr.jrrocks.carnetderecette.ui.composants.Etape
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.LegendeLigne
import fr.jrrocks.carnetderecette.ui.composants.LigneDeTemps
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat
import kotlin.math.roundToInt

/** Marge sous le contenu quand le bouton d'action flotte au-dessus : 64 dp de bouton, 16 de marge, 16 d'air. */
private val SOUS_LE_BOUTON = 96.dp

/**
 * L'accueil : où en est la semaine et ce qui vient, sous la forme du schéma de ligne. Au
 * lot 1, une étape « Drive du foyer » ouvre la ligne ; les quatre temps de la semaine sont
 * en pointillé tant qu'ils ne sont pas dans l'app.
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
        LignesEtatSemaine(etat, actions)
        LigneDeTemps(etapesDeLaSemaine(etat), modifier = Modifier.padding(top = 8.dp)) {
            val drive = etat.drive
            if (drive is EtatDrive.Erreur) {
                // Le motif sous l'étape, près du bouton « Réessayer » : la cause et le remède au même endroit.
                LigneEtat(R.drawable.ic_error, drive.motif, TonEtat.ERREUR, Modifier.padding(top = 12.dp))
            }
        }
        LegendeLigne(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
    }
}

/** Les lignes d'état en tête : mise à jour, puis hors ligne. L'erreur du Drive vit sous son étape. */
@Composable
private fun LignesEtatSemaine(etat: EtatCarnet, actions: ActionsCarnet) {
    val drive = etat.drive
    val horsLigne = drive is EtatDrive.Pret && drive.horsLigne
    if (etat.maj is EtatMiseAJour.Aucune && !horsLigne) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LigneMiseAJour(etat.maj, actions)
        if (horsLigne) LigneEtat(R.drawable.ic_cloud_off, DriveCarnet.MOTIF_HORS_LIGNE, TonEtat.NEUTRE)
    }
}

/**
 * La proposition de mise à jour, en ligne d'état plutôt qu'en boîte de dialogue : elle
 * n'interrompt personne en pleine cuisine. Reprise telle quelle dans Réglages.
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
        is EtatMiseAJour.Telechargement -> LigneEtat(
            R.drawable.ic_system_update,
            "Téléchargement de la version ${maj.versionNom}… ${(maj.fraction * 100).roundToInt()} %",
            TonEtat.INFO,
            modifier,
            progression = maj.fraction,
        )
        is EtatMiseAJour.AAutoriser -> LigneEtat(
            R.drawable.ic_system_update,
            "Autorise l'installation pour Carnet de recette dans les réglages Android, puis appuie sur « Mettre à jour ».",
            TonEtat.INFO,
            modifier,
        ) {
            plusTard()
            BoutonCarnet("Mettre à jour", actions.mettreAJour)
        }
        is EtatMiseAJour.Echec -> LigneEtat(
            R.drawable.ic_error,
            "Téléchargement de la version ${maj.versionNom} interrompu : ${maj.motif}.",
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
    val vendredi = prochainVendredi(etat.aujourdHui)
    val samedi = vendredi.plusDays(1)
    return listOf(
        etapeDrive(etat.drive),
        Etape("Vendredi", EtatEtape.PAS_ENCORE, "Scanner les box à leur arrivée.", fin = dateCourte(vendredi)),
        Etape("Vendredi soir", EtatEtape.PAS_ENCORE, "Claude découpe chaque recette en tâches pour vous deux et prépare l'audio."),
        Etape("Samedi", EtatEtape.PAS_ENCORE, "Importer la semaine depuis le Drive, d'un geste.", fin = dateCourte(samedi)),
        Etape("En cuisine", EtatEtape.PAS_ENCORE, "Chacun suit ses tâches, à l'écran et à l'oreille."),
    )
}

private const val DRIVE = "Drive du foyer"

private fun etapeDrive(drive: EtatDrive): Etape = when (drive) {
    EtatDrive.NonConnecte -> Etape(
        DRIVE,
        EtatEtape.EN_COURS,
        "Branche le compte Google du foyer. L'app y crée le dossier « ${DriveCarnet.NOM_DOSSIER} », " +
            "à ranger ensuite dans « Maison ».",
    )
    EtatDrive.EnCours -> Etape(DRIVE, EtatEtape.EN_COURS, "Connexion au compte Google…")
    is EtatDrive.Erreur -> Etape(DRIVE, EtatEtape.EN_COURS, "Le branchement n'a pas abouti.")
    is EtatDrive.Pret -> Etape(DRIVE, EtatEtape.FAITE, drive.compte, fin = if (drive.horsLigne) "hors ligne" else "branché")
}
