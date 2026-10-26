package fr.jrrocks.carnetderecette.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.jrrocks.carnetderecette.drive.DriveCarnet
import fr.jrrocks.carnetderecette.drive.DriveCarnet.Autorisation
import fr.jrrocks.carnetderecette.drive.PreferencesDrive
import fr.jrrocks.carnetderecette.maj.MiseAJour
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * L'état de l'accueil : le branchement au Drive et la mise à jour en ligne.
 *
 * Dans un ViewModel plutôt que dans des `remember` de l'écran (relecture du lot 1) : une
 * rotation, un passage en thème sombre ou un changement de taille de police recréent
 * l'Activity. Avec des `remember`, « Plus tard » ne tenait pas, un téléchargement en cours se
 * perdait, et un second appui pendant le branchement pouvait créer deux dossiers sur le Drive.
 * Ici, l'état et les coroutines (`viewModelScope`) survivent à la recréation, et un seul
 * branchement ou téléchargement tourne à la fois.
 */
class AccueilViewModel(application: Application) : AndroidViewModel(application) {

    private val contexte: Application get() = getApplication()
    private val preferences = PreferencesDrive(application)

    var etat by mutableStateOf<EtatDrive>(EtatDrive.NonConnecte)
        private set

    /** L'écran de consentement Google à présenter ; l'Activity le lance puis appelle [consentementPresente]. */
    var consentement by mutableStateOf<PendingIntent?>(null)
        private set

    /** La mise à jour en ligne, affichée en ligne d'état sur la Semaine et dans Réglages. */
    var maj by mutableStateOf<EtatMiseAJour>(EtatMiseAJour.Aucune)
        private set

    /**
     * Vrai une seule fois, au premier branchement réussi : l'écran annonce le dossier créé et
     * rappelle de le ranger dans « Maison » ([annonceVue] le marque comme lu).
     */
    var annonceDossier by mutableStateOf(false)
        private set

    private var publication: MiseAJour.Publication? = null
    private var branchement: Job? = null
    private var telechargement: Job? = null

    init {
        viewModelScope.launch {
            publication = MiseAJour.publicationDuLancement(contexte)
            publication?.let { maj = EtatMiseAJour.Disponible(it.versionNom) }
        }
        preferences.compte?.let { verifierAuLancement(it) }
    }

    fun annonceVue() {
        annonceDossier = false
        preferences.dossierAnnonce = true
    }

    /** Bouton « Connecter Google Drive » (ou « Réessayer »). */
    fun connecter() {
        if (branchement?.isActive == true) return
        etat = EtatDrive.EnCours
        branchement = viewModelScope.launch {
            when (val autorisation = DriveCarnet.demanderAutorisation(contexte)) {
                is Autorisation.Accordee -> preparerDossier(autorisation.compte)
                is Autorisation.AEcran -> consentement = autorisation.intention
                is Autorisation.Echec -> etat = EtatDrive.Erreur(autorisation.motif)
            }
        }
    }

    fun consentementPresente() {
        consentement = null
    }

    /** Ce que l'écran de consentement a rendu. */
    fun retourConsentement(donnees: Intent?) {
        if (branchement?.isActive == true) return
        etat = EtatDrive.EnCours
        branchement = viewModelScope.launch {
            when (val autorisation = DriveCarnet.autorisationDepuisIntention(contexte, donnees)) {
                is Autorisation.Accordee -> preparerDossier(autorisation.compte)
                is Autorisation.Echec -> etat = EtatDrive.Erreur(autorisation.motif)
                // Ne se produit pas au retour d'un consentement : on repart de zéro.
                is Autorisation.AEcran -> etat = EtatDrive.NonConnecte
            }
        }
    }

    /**
     * Déjà branché sur ce téléphone : on revérifie en silence. Si Google réclame à nouveau son
     * écran, l'accueil repropose le bouton ; sans réseau, le compte reste affiché « hors ligne ».
     */
    private fun verifierAuLancement(compte: String) {
        etat = EtatDrive.EnCours
        branchement = viewModelScope.launch {
            when (val autorisation = DriveCarnet.demanderAutorisation(contexte)) {
                is Autorisation.Accordee -> preparerDossier(autorisation.compte)
                is Autorisation.AEcran -> etat = EtatDrive.NonConnecte
                is Autorisation.Echec -> etat =
                    if (autorisation.reseau && brancheMemorise(compte)) {
                        EtatDrive.Pret(compte, horsLigne = true)
                    } else {
                        EtatDrive.Erreur(autorisation.motif)
                    }
            }
        }
    }

    /** Le compte est autorisé ; reste à trouver ou créer le dossier de l'app. */
    private suspend fun preparerDossier(compte: String) {
        etat = try {
            val dossier = DriveCarnet.dossier(contexte)
            preferences.memoriser(compte, dossier.id)
            if (!preferences.dossierAnnonce) annonceDossier = true
            EtatDrive.Pret(compte)
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (autorisation: DriveCarnet.AutorisationRevoquee) {
            preferences.oublier()
            EtatDrive.Erreur(DriveCarnet.MOTIF_ACCES_RETIRE)
        } catch (refus: DriveCarnet.RefusDrive) {
            EtatDrive.Erreur(refus.message ?: "Le Drive a refusé la demande.")
        } catch (panne: IOException) {
            when {
                !DriveCarnet.estPanneReseau(panne) -> EtatDrive.Erreur("Le Drive ne répond pas correctement : ${panne.message}")
                // « Hors ligne » seulement si ce branchement-là est déjà mémorisé : au premier
                // branchement, rien ne serait vérifié au lancement suivant.
                brancheMemorise(compte) -> EtatDrive.Pret(compte, horsLigne = true)
                else -> EtatDrive.Erreur(DriveCarnet.MOTIF_RESEAU)
            }
        } catch (jamaisAutorise: IllegalStateException) {
            EtatDrive.NonConnecte
        }
    }

    private fun brancheMemorise(compte: String): Boolean =
        preferences.compte == compte && preferences.dossierId != null

    // ── Mise à jour en ligne ────────────────────────────────────────────────────

    /** « Plus tard » : tient jusqu'au prochain lancement de l'app, rotation comprise. */
    fun ecarterMiseAJour() {
        if (maj !is EtatMiseAJour.Telechargement) maj = EtatMiseAJour.Aucune
    }

    /** « Mettre à jour ». [ouvrirReglages] présente l'écran « sources inconnues » s'il le faut. */
    fun mettreAJour(ouvrirReglages: (Intent) -> Unit) {
        val aInstaller = publication ?: return
        if (telechargement?.isActive == true) return
        if (!MiseAJour.peutInstaller(contexte)) {
            maj = EtatMiseAJour.AAutoriser(aInstaller.versionNom)
            ouvrirReglages(MiseAJour.ecranSourcesInconnues(contexte))
            return
        }
        maj = EtatMiseAJour.Telechargement(aInstaller.versionNom, 0f)
        telechargement = viewModelScope.launch {
            maj = try {
                val apk = MiseAJour.telecharger(contexte, aInstaller) { fraction ->
                    maj = EtatMiseAJour.Telechargement(aInstaller.versionNom, fraction)
                }
                MiseAJour.installer(contexte, apk)
                // L'installateur du système prend la main ; si on l'annule, l'offre reste là.
                EtatMiseAJour.Disponible(aInstaller.versionNom)
            } catch (echec: IOException) {
                EtatMiseAJour.Echec(aInstaller.versionNom, echec.message ?: "réseau indisponible")
            }
        }
    }
}
