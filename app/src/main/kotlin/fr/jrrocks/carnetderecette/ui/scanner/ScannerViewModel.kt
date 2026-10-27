package fr.jrrocks.carnetderecette.ui.scanner

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.jrrocks.carnetderecette.core.scan.BoxValidee
import fr.jrrocks.carnetderecette.core.scan.ElementLu
import fr.jrrocks.carnetderecette.core.scan.ReconnaissanceEtiquette
import fr.jrrocks.carnetderecette.core.scan.SuiviEtiquettes
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.core.semaine.OrigineBox
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.donnees.DepotLocal
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.donnees.ResultatMenu
import fr.jrrocks.carnetderecette.donnees.ResultatRecette
import fr.jrrocks.carnetderecette.scan.Cliche
import fr.jrrocks.carnetderecette.scan.ScanFeedback
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Le scan des box du vendredi (cadrage § 5) : la chaîne du moteur `scan-multimode` adaptée à
 * l'étiquette, puis le dépôt de la semaine.
 *
 * Image après image, sur le fil d'analyse de CameraX ([surImage]) : les éléments lus dans le
 * réticule → [ReconnaissanceEtiquette] (comparaison aux numéros du menu, sans regex) →
 * [SuiviEtiquettes] (consensus, vote des personnes, anti-rebond). Une box validée sonne
 * ([ScanFeedback]), garde une petite photo de son étiquette et part dans le dépôt, qui lit la
 * page de sa recette.
 *
 * Les box scannées vivent dans le dépôt ([DepotLocal]) : l'écran les relit de son état.
 * Ce ViewModel ne garde que ce qui est propre à l'écran (le dernier résultat, la lecture du
 * menu, la liste ouverte).
 */
class ScannerViewModel(application: Application, private val semaine: SemaineIso) : AndroidViewModel(application) {

    private val depot = DepotLocal.de(application)
    private val retourSonore = ScanFeedback(application)

    /** Seulement sur le fil d'analyse : le consensus et l'anti-rebond ne sont pas thread-safe. */
    private val suivi = SuiviEtiquettes()

    /** Demandée depuis le fil principal, faite sur le fil d'analyse à l'image suivante. */
    @Volatile private var aReinitialiser = false

    /** Les numéros du menu, lus par le fil d'analyse. */
    @Volatile private var numerosDuMenu: Set<Int> = depot.etat(semaine).value.menu?.numeros.orEmpty()

    private data class Local(
        val menu: EtatMenu = EtatMenu.Lecture,
        val courante: Int? = null,
        val doublon: Boolean = false,
        /** Box dont la page n'a pas pu être lue faute de réseau. */
        val horsLigne: Set<Int> = emptySet(),
        val menuOuvert: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private val progression = MutableStateFlow(0f)

    val etat: StateFlow<EtatScanner> = combine(depot.etat(semaine), local, progression, ::vue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vue(depot.etat(semaine).value, local.value, 0f))

    /** À chaque ouverture de l'écran : le consensus repart de zéro, le menu est lu s'il ne l'est pas. */
    fun demarrer() {
        aReinitialiser = true
        local.update { it.copy(doublon = false, menuOuvert = false) }
        if (local.value.menu != EtatMenu.Lu) relireMenu()
    }

    fun relireMenu() {
        local.update { it.copy(menu = EtatMenu.Lecture) }
        viewModelScope.launch {
            val menu = when (val resultat = depot.chargerMenu(semaine)) {
                is ResultatMenu.Lu -> {
                    numerosDuMenu = resultat.menu.numeros
                    EtatMenu.Lu
                }
                is ResultatMenu.HorsLigne -> EtatMenu.HorsLigne
                is ResultatMenu.AccesRompu -> EtatMenu.AccesRompu
            }
            local.update { it.copy(menu = menu) }
        }
    }

    /** Une image analysée, **sur le fil d'analyse** de CameraX ([fr.jrrocks.carnetderecette.scan.OcrElements]). */
    fun surImage(elements: List<ElementLu>, cliche: Cliche) {
        if (aReinitialiser) {
            suivi.reinitialiser()
            aReinitialiser = false
        }
        val validee = suivi.soumettre(ReconnaissanceEtiquette.reconnaitre(elements, numerosDuMenu))
        progression.value = suivi.progression
        if (validee != null) {
            val deja = depot.etat(semaine).value.fichier.box(validee.numero) != null
            // L'image qui a validé la box est encore ouverte : sa photo, tant qu'on la tient.
            val photo = if (deja) null else cliche.etiquette()
            viewModelScope.launch { enregistrer(validee, deja, photo) }
        }
    }

    private suspend fun enregistrer(validee: BoxValidee, deja: Boolean, photo: Bitmap?) {
        local.update { it.copy(courante = validee.numero, doublon = deja, menuOuvert = false) }
        if (deja) {
            // Déjà scannée : on la montre, rien n'est réécrit (une correction à la main resterait).
            retourSonore.duplicate()
            return
        }
        retourSonore.success()
        val box = BoxScannee(
            numero = validee.numero,
            personnes = validee.personnesProposees ?: personnesParDefaut(),
            aConfirmer = validee.aConfirmer,
            elements = validee.elements,
            origine = OrigineBox.SCAN,
            photo = photo?.let { ecrirePhoto(it, "etiquette-${validee.numero}.jpg") },
            scanneLe = Instant.now().toString(),
        )
        ajouter(box)
    }

    /** Une recette touchée dans le menu : l'étiquette était illisible, le nombre de personnes reste à confirmer. */
    fun choisir(numero: Int) {
        val existante = depot.etat(semaine).value.fichier.box(numero)
        local.update { it.copy(courante = numero, doublon = existante != null, menuOuvert = false) }
        if (existante != null) return
        viewModelScope.launch {
            ajouter(
                BoxScannee(
                    numero = numero,
                    personnes = personnesParDefaut(),
                    aConfirmer = true,
                    origine = OrigineBox.MENU,
                    scanneLe = Instant.now().toString(),
                ),
            )
        }
    }

    fun selectionner(numero: Int) = local.update { it.copy(courante = numero, doublon = false) }

    fun ouvrirMenu(ouvert: Boolean) = local.update { it.copy(menuOuvert = ouvert) }

    fun corrigerPersonnes(numero: Int, personnes: Int) {
        viewModelScope.launch { depot.corrigerPersonnes(semaine, numero, personnes.coerceIn(PERSONNES_MIN, PERSONNES_MAX)) }
    }

    fun retirer(numero: Int) {
        val photo = depot.etat(semaine).value.fichier.box(numero)?.photo
        local.update { it.copy(courante = it.courante.takeUnless { c -> c == numero }, horsLigne = it.horsLigne - numero) }
        viewModelScope.launch {
            depot.retirerBox(semaine, numero)
            if (photo != null) withContext(Dispatchers.IO) { File(depot.dossierPhotos(semaine), photo).delete() }
        }
    }

    fun relirePage(numero: Int) {
        local.update { it.copy(horsLigne = it.horsLigne - numero) }
        viewModelScope.launch { noterResultat(numero, depot.relireRecette(semaine, numero)) }
    }

    private suspend fun ajouter(box: BoxScannee) {
        local.update { it.copy(horsLigne = it.horsLigne - box.numero) }
        noterResultat(box.numero, depot.ajouterBox(semaine, box))
    }

    private fun noterResultat(numero: Int, resultat: ResultatRecette) {
        if (resultat is ResultatRecette.HorsLigne) local.update { it.copy(horsLigne = it.horsLigne + numero) }
    }

    /** Sans nombre lu, celui des box déjà scannées (le foyer commande souvent pareil), sinon 2 ; à confirmer. */
    private fun personnesParDefaut(): Int =
        depot.etat(semaine).value.boxes.groupingBy { it.personnes }.eachCount().maxByOrNull { it.value }?.key ?: 2

    /** La petite photo de l'étiquette, en JPEG qualité 70 ; `null` si l'écriture échoue (le scan reste). */
    private suspend fun ecrirePhoto(photo: Bitmap, nom: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            File(depot.dossierPhotos(semaine), nom).outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 70, it) }
            nom
        }.getOrNull()
    }

    private fun vue(semaineEtat: EtatSemaine, l: Local, avancement: Float): EtatScanner {
        val menu = semaineEtat.menu
        val boxes = semaineEtat.boxes.map { box ->
            BoxVue(
                numero = box.numero,
                titre = menu?.entree(box.numero)?.titre ?: semaineEtat.recette(box.numero)?.titre,
                personnes = box.personnes,
                aConfirmer = box.aConfirmer,
                page = when {
                    box.numero in semaineEtat.enLecture -> EtatPage.EN_LECTURE
                    box.recetteId != null -> EtatPage.LUE
                    box.numero in l.horsLigne -> EtatPage.HORS_LIGNE
                    else -> EtatPage.A_LIRE
                },
            )
        }
        return EtatScanner(
            progression = avancement,
            // Une page de recette illisible rompt l'accès autant que le menu : même ligne d'erreur.
            menu = if (semaineEtat.accesRompu != null) EtatMenu.AccesRompu else l.menu,
            courante = boxes.firstOrNull { it.numero == l.courante },
            doublon = l.doublon,
            boxes = boxes,
            entreesMenu = menu?.entrees.orEmpty(),
            menuOuvert = l.menuOuvert,
        )
    }

    override fun onCleared() {
        retourSonore.release()
    }

    companion object {
        fun fabrique(semaine: SemaineIso): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScannerViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, semaine) }
        }
    }
}
