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
import fr.jrrocks.carnetderecette.donnees.EcritureImpossible
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.donnees.ResultatMenu
import fr.jrrocks.carnetderecette.donnees.ResultatRecette
import fr.jrrocks.carnetderecette.scan.Cliche
import fr.jrrocks.carnetderecette.scan.ScanFeedback
import java.io.File
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
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
 * [SuiviEtiquettes] (consensus, vote des personnes, anti-rebond glissant). Une box validée
 * sonne ([ScanFeedback]), garde une petite photo de son étiquette et part dans le dépôt, qui
 * lit la page de sa recette.
 *
 * Les box scannées vivent dans le dépôt ([DepotLocal]) : l'écran les relit de son état.
 * Ce ViewModel ne garde que ce qui est propre à l'écran (le dernier résultat, la lecture du
 * menu, la liste ouverte). Une écriture du dépôt qui échoue (téléphone plein) s'affiche en
 * ligne d'erreur au lieu de faire planter l'app.
 */
class ScannerViewModel(application: Application, private val semaine: SemaineIso) : AndroidViewModel(application) {

    private val depot = DepotLocal.de(application)
    private val retourSonore = ScanFeedback(application)

    /** [SuiviEtiquettes.soumettre] sur le fil d'analyse ; [SuiviEtiquettes.ignorer] depuis le fil principal. */
    private val suivi = SuiviEtiquettes()

    /** Demandée depuis le fil principal, faite sur le fil d'analyse à l'image suivante. */
    @Volatile private var aReinitialiser = false

    /** Les numéros du menu, lus par le fil d'analyse. */
    @Volatile private var numerosDuMenu: Set<Int> = depot.etat(semaine).value.menu?.numeros.orEmpty()

    private data class Local(
        val menu: EtatMenu = EtatMenu.Lecture,
        val courante: Int? = null,
        /** [courante] a été touchée dans la liste ou choisie dans le menu, pas lue sur une étiquette. */
        val couranteChoisie: Boolean = false,
        val doublon: Boolean = false,
        /**
         * La box qui vient d'être validée ou choisie, tant que le dépôt ne l'a pas encore
         * écrite : le dernier résultat la montre sans disparaître un instant.
         */
        val provisoire: BoxScannee? = null,
        /** Box dont la page n'a pas pu être lue faute de réseau. */
        val horsLigne: Set<Int> = emptySet(),
        val menuOuvert: Boolean = false,
        /** Une écriture du dépôt a échoué : la phrase à montrer. */
        val erreur: String? = null,
    )

    private val local = MutableStateFlow(Local())
    private val progression = MutableStateFlow(0f)

    val etat: StateFlow<EtatScanner> = combine(depot.etat(semaine), local, progression, ::vue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vue(depot.etat(semaine).value, local.value, 0f))

    /** À chaque ouverture de l'écran : le consensus repart de zéro, le menu est lu s'il ne l'est pas. */
    fun demarrer() {
        aReinitialiser = true
        local.update { it.copy(doublon = false, menuOuvert = false, erreur = null, couranteChoisie = false) }
        if (local.value.menu != EtatMenu.Lu) lireMenu(forcer = false)
    }

    /** « Réessayer » : le menu est relu sur le site, même s'il est déjà sur le téléphone. */
    fun relireMenu() = lireMenu(forcer = true)

    private fun lireMenu(forcer: Boolean) {
        local.update { it.copy(menu = EtatMenu.Lecture) }
        viewModelScope.launch {
            val menu = try {
                when (val resultat = depot.chargerMenu(semaine, forcer)) {
                    is ResultatMenu.Lu -> {
                        numerosDuMenu = resultat.menu.numeros
                        EtatMenu.Lu
                    }
                    // Relu pour rien, faute de réseau : le menu déjà sur le téléphone sert encore.
                    is ResultatMenu.HorsLigne -> if (depot.etat(semaine).value.menu != null) EtatMenu.Lu else EtatMenu.HorsLigne
                    is ResultatMenu.AccesRompu -> EtatMenu.AccesRompu
                }
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (e: Exception) {
                // Le réseau est traité par le dépôt : ce qui remonte ici est l'écriture du menu.
                if (depot.etat(semaine).value.menu != null) EtatMenu.Lu else EtatMenu.NonEnregistre
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
        if (deja) {
            // Déjà scannée (sortie du champ puis rescannée) : on la montre, rien n'est réécrit
            // (une correction à la main resterait). La liste ouverte reste ouverte, et la box
            // touchée exprès dans la liste reste en grand.
            retourSonore.duplicate()
            local.update { l ->
                if (l.couranteChoisie && l.courante != null && l.courante != validee.numero) l
                else l.copy(courante = validee.numero, couranteChoisie = false, doublon = true)
            }
            return
        }
        retourSonore.success()
        val provisoire = BoxScannee(
            numero = validee.numero,
            personnes = validee.personnesProposees ?: personnesParDefaut(),
            aConfirmer = validee.aConfirmer,
            elements = validee.elements,
            origine = OrigineBox.SCAN,
            scanneLe = Instant.now().toString(),
        )
        local.update {
            it.copy(courante = validee.numero, couranteChoisie = false, doublon = false, menuOuvert = false, provisoire = provisoire)
        }
        ajouter(provisoire.copy(photo = photo?.let { ecrirePhoto(it, "etiquette-${validee.numero}.jpg") }))
    }

    /** Une recette touchée dans le menu : l'étiquette était illisible, le nombre de personnes reste à confirmer. */
    fun choisir(numero: Int) {
        val existante = depot.etat(semaine).value.fichier.box(numero)
        if (existante != null) {
            local.update { it.copy(courante = numero, couranteChoisie = true, doublon = true, menuOuvert = false) }
            return
        }
        val box = BoxScannee(
            numero = numero,
            personnes = personnesParDefaut(),
            aConfirmer = true,
            origine = OrigineBox.MENU,
            scanneLe = Instant.now().toString(),
        )
        local.update { it.copy(courante = numero, couranteChoisie = true, doublon = false, menuOuvert = false, provisoire = box) }
        viewModelScope.launch { ajouter(box) }
    }

    fun selectionner(numero: Int) = local.update { it.copy(courante = numero, couranteChoisie = true, doublon = false) }

    fun ouvrirMenu(ouvert: Boolean) = local.update { it.copy(menuOuvert = ouvert) }

    fun corrigerPersonnes(numero: Int, personnes: Int) {
        viewModelScope.launch {
            ecrire { depot.corrigerPersonnes(semaine, numero, personnes.coerceIn(PERSONNES_MIN, PERSONNES_MAX)) }
        }
    }

    fun retirer(numero: Int) {
        // Retirée exprès : son étiquette, encore dans le réticule, ne la fait pas revenir.
        suivi.ignorer(numero)
        val photo = depot.etat(semaine).value.fichier.box(numero)?.photo
        local.update {
            val effacee = it.courante == numero
            it.copy(
                courante = it.courante.takeUnless { effacee },
                couranteChoisie = it.couranteChoisie && !effacee,
                provisoire = it.provisoire.takeUnless { p -> p?.numero == numero },
                horsLigne = it.horsLigne - numero,
            )
        }
        viewModelScope.launch {
            // La photo n'est effacée que si le retrait est écrit : sinon la box la garde.
            val retiree = ecrire { depot.retirerBox(semaine, numero) } && depot.etat(semaine).value.fichier.box(numero) == null
            if (retiree && photo != null) withContext(Dispatchers.IO) { File(depot.dossierPhotos(semaine), photo).delete() }
        }
    }

    fun relirePage(numero: Int) {
        local.update { it.copy(horsLigne = it.horsLigne - numero) }
        viewModelScope.launch { ecrire { noterResultat(numero, depot.relireRecette(semaine, numero)) } }
    }

    private suspend fun ajouter(box: BoxScannee) {
        local.update { it.copy(horsLigne = it.horsLigne - box.numero) }
        try {
            ecrire { noterResultat(box.numero, depot.ajouterBox(semaine, box)) }
        } finally {
            // Écrite (ou en échec) : le dépôt dit désormais ce qu'il en est.
            local.update { it.copy(provisoire = it.provisoire.takeUnless { p -> p?.numero == box.numero }) }
        }
    }

    /**
     * Une écriture du dépôt. Un échec (téléphone plein, renommage refusé) s'affiche au lieu de
     * faire planter l'app ; la suivante qui réussit efface la phrase. Rend vrai si elle a réussi.
     */
    private suspend fun ecrire(ecriture: suspend () -> Unit): Boolean =
        try {
            ecriture()
            local.update { if (it.erreur != null) it.copy(erreur = null) else it }
            true
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (e: Exception) {
            local.update { it.copy(erreur = ERREUR_ECRITURE) }
            false
        }

    private fun noterResultat(numero: Int, resultat: ResultatRecette) {
        // Téléphone plein : ce n'est pas le réseau, la ligne d'erreur le dit déjà.
        if (resultat is ResultatRecette.HorsLigne && resultat.motif != EcritureImpossible.MOTIF_ECRITURE) {
            local.update { it.copy(horsLigne = it.horsLigne + numero) }
        }
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
        fun vueDe(box: BoxScannee, page: EtatPage) = BoxVue(
            numero = box.numero,
            titre = menu?.entree(box.numero)?.titre ?: semaineEtat.recette(box.numero)?.titre,
            personnes = box.personnes,
            aConfirmer = box.aConfirmer,
            page = page,
        )
        val boxes = semaineEtat.boxes.map { box ->
            vueDe(
                box,
                when {
                    box.numero in semaineEtat.enLecture -> EtatPage.EN_LECTURE
                    box.recetteId != null -> EtatPage.LUE
                    box.numero in l.horsLigne -> EtatPage.HORS_LIGNE
                    else -> EtatPage.A_LIRE
                },
            )
        }
        // Pas encore écrite : montrée telle que validée, sa page sur le point d'être lue.
        val courante = boxes.firstOrNull { it.numero == l.courante }
            ?: l.provisoire?.takeIf { it.numero == l.courante }?.let { vueDe(it, EtatPage.EN_LECTURE) }
        return EtatScanner(
            progression = avancement,
            // Une page de recette illisible rompt l'accès autant que le menu : même ligne d'erreur.
            menu = if (semaineEtat.accesRompu != null) EtatMenu.AccesRompu else l.menu,
            courante = courante,
            doublon = l.doublon,
            boxes = boxes,
            entreesMenu = menu?.entrees.orEmpty(),
            menuOuvert = l.menuOuvert,
            // Le dépôt dit aussi quand le téléphone est plein (il ne lève plus d'erreur).
            erreur = l.erreur ?: semaineEtat.ecritureImpossible,
        )
    }

    override fun onCleared() {
        retourSonore.release()
    }

    companion object {
        /** Ce que dit l'écran quand le dépôt n'a pas pu écrire : la cause la plus probable et quoi faire. */
        const val ERREUR_ECRITURE =
            "Le téléphone n'a pas pu enregistrer\u00A0: il manque peut-être de place. Libère-en, puis réessaie."

        fun fabrique(semaine: SemaineIso): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScannerViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, semaine) }
        }
    }
}
