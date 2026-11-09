package fr.jrrocks.carnetderecette.ui

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.jrrocks.carnetderecette.core.cuisine.LectureSemaine
import fr.jrrocks.carnetderecette.core.cuisine.ModeCuisine
import fr.jrrocks.carnetderecette.core.cuisine.RoleCuisine
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.donnees.DepotLocal
import fr.jrrocks.carnetderecette.donnees.EcritureImpossible
import fr.jrrocks.carnetderecette.donnees.PreferencesCuisine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * La semaine de cuisine, côté téléphone : l'envoi des recettes à Claude (en attendant le dépôt
 * automatique sur le Drive), l'import du samedi et ce que ce téléphone retient (son rôle, à deux
 * ou seul). Dans un ViewModel : une rotation pendant l'import ne le coupe pas.
 */
class CuisineViewModel(application: Application) : AndroidViewModel(application) {

    private val contexte: Application get() = getApplication()
    private val preferences = PreferencesCuisine(application)
    private val depot = DepotLocal.de(application)

    var role by mutableStateOf(preferences.role)
        private set

    var mode by mutableStateOf(preferences.mode)
        private set

    /** Une phrase à annoncer une fois (barre en bas de l'écran), puis [annonceVue]. */
    var annonce by mutableStateOf<String?>(null)
        private set

    /** Le partage à présenter ; l'Activity le lance puis appelle [envoiPresente]. */
    var envoi by mutableStateOf<Intent?>(null)
        private set

    private var occupe = false
    private var chargementMenu: Job? = null

    fun choisirRole(role: RoleCuisine) {
        this.role = role
        preferences.role = role
    }

    fun choisirMode(mode: ModeCuisine) {
        this.mode = mode
        preferences.mode = mode
    }

    /** Le fichier choisi dans le sélecteur. Un refus s'écrit sur la Semaine, sous son étape. */
    fun importer(uri: Uri, semaine: SemaineIso) {
        if (occupe) return
        occupe = true
        viewModelScope.launch {
            try {
                val lecture = depot.importerSemaine(semaine) { contexte.contentResolver.openInputStream(uri) }
                if (lecture is LectureSemaine.Lue) {
                    val n = lecture.fichier.recettes.size
                    annonce = "Semaine ${semaine.numero} importée\u00A0: " + if (n == 1) "1 recette préparée par Claude." else "$n recettes préparées par Claude."
                }
            } finally {
                occupe = false
            }
        }
    }

    /**
     * Prépare le fichier des recettes et le partage d'Android : on l'enregistre sur le Drive,
     * dans « Carnet de recettes », où Claude le lit le vendredi soir. Le téléphone plein s'écrit
     * déjà sur la Semaine.
     */
    fun envoyerAClaude(semaine: SemaineIso) {
        if (occupe) return
        occupe = true
        viewModelScope.launch {
            try {
                val fichier = depot.fichierPourClaude(semaine)
                val uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
                envoi = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, fichier.name)
                    // Le fournisseur de fichiers ne prête le fichier qu'à l'app choisie, en lecture.
                    clipData = ClipData.newRawUri(fichier.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: EcritureImpossible) {
                // Rien à ajouter : la ligne « téléphone plein » de la Semaine le dit déjà.
            } finally {
                occupe = false
            }
        }
    }

    /**
     * Le téléphone de l'autre cuisinier n'a rien scanné : sans le menu, ses recettes importées
     * s'appelleraient « Recette 207 ». Le menu public de la semaine donne leurs titres ; hors
     * ligne, on réessaiera au prochain lancement.
     */
    fun titresSiBesoin(semaine: SemaineIso) {
        val etat = depot.etat(semaine).value
        if (etat.travail == null || etat.menu != null || chargementMenu?.isActive == true) return
        chargementMenu = viewModelScope.launch { depot.chargerMenu(semaine) }
    }

    fun envoiPresente() {
        envoi = null
    }

    /** Aucune app ne sait recevoir le fichier (pas de Drive sur ce téléphone). */
    fun envoiImpossible() {
        envoi = null
        annonce = "Aucune app de ce téléphone ne sait recevoir le fichier\u00A0: installe Google Drive, puis réessaie."
    }

    fun annonceVue() {
        annonce = null
    }
}
