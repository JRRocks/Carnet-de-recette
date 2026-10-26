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
     * Vrai une seule fois, au premier branchement réussi sur ce téléphone : l'écran annonce le
     * dossier et rappelle de le ranger dans « Maison » ([annonceVue] le marque comme lu).
     * Pas au simple retour d'un téléphone déjà branché, ni après une mise à jour de l'app.
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

    /** Bouton « Brancher le Drive » (ou « Réessayer »). */
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
     * Déjà branché sur ce téléphone : on revérifie en silence, le compte reste affiché. Si
     * Google réclame à nouveau son écran, l'accueil repropose le bouton ; sans réseau, le
     * compte reste affiché « hors ligne ».
     */
    private fun verifierAuLancement(compte: String) {
        etat = EtatDrive.Verification(compte)
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
            val premierBranchement = preferences.dossierId == null
            val dossier = DriveCarnet.dossier(contexte)
            preferences.memoriser(compte, dossier.id)
            if (premierBranchement && !preferences.dossierAnnonce) annonceDossier = true
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
                // Pas le message brut de l'exception : souvent de l'anglais technique.
                !DriveCarnet.estPanneReseau(panne) -> EtatDrive.Erreur("Le Drive ne répond pas correctement. Réessaie dans un moment.")
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

    /**
     * « Plus tard » : la Semaine n'en parle plus jusqu'au prochain lancement (rotation
     * comprise), mais Réglages la propose toujours.
     */
    fun ecarterMiseAJour() {
        maj = when (val courante = maj) {
            is EtatMiseAJour.Disponible -> EtatMiseAJour.Ecartee(courante.versionNom)
            is EtatMiseAJour.AAutoriser -> EtatMiseAJour.Ecartee(courante.versionNom)
            is EtatMiseAJour.Echec -> EtatMiseAJour.Ecartee(courante.versionNom)
            else -> courante
        }
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
                EtatMiseAJour.Echec(aInstaller.versionNom, causeDuTelechargement(echec))
            }
        }
    }
}

/** Les refus que `MiseAJour` écrit lui-même, en français. */
private val MESSAGES_MAJ = listOf("Téléchargement ", "Fichier téléchargé ")

/**
 * Ce que l'utilisateur lit après un téléchargement raté : jamais le message brut d'une
 * exception (souvent de l'anglais technique, « Unable to resolve host… »).
 */
internal fun causeDuTelechargement(echec: IOException): String = when {
    DriveCarnet.estPanneReseau(echec) -> "pas de réseau, réessaie une fois connecté"
    // « Téléchargement refusé (code 404). » devient « téléchargement refusé (code 404) ».
    echec.message?.let { m -> MESSAGES_MAJ.any(m::startsWith) } == true ->
        echec.message!!.trimEnd('.').replaceFirstChar { it.lowercase() }
    else -> "le téléchargement s'est coupé"
}
