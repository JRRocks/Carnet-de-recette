package fr.jrrocks.carnetderecette.ui.fiche

import android.app.Application
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.jrrocks.carnetderecette.core.fiche.FicheScannee
import fr.jrrocks.carnetderecette.core.fiche.PageFiche
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.donnees.DepotLocal
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.scan.OcrPage
import fr.jrrocks.carnetderecette.scan.PhotoIntrouvable
import fr.jrrocks.carnetderecette.scan.RangementFiche
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
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
 * La fiche papier photographiée en secours (cadrage § 9, décision du 2026-10-27).
 *
 * Chaque photo est prise en pleine résolution ([ImageCapture]), puis lue par l'OCR
 * ([OcrPage]) : le texte brut et sa place, **sans regex ni interprétation**. « Enregistrer »
 * range les photos à côté du fichier de la semaine et y ajoute la fiche ; Claude la structure
 * le vendredi soir.
 *
 * Les photos attendent dans le cache tant que la fiche n'est pas enregistrée : un retour par
 * erreur ne les perd pas tant que l'app reste ouverte, l'écran les retrouve. Celles qu'un
 * processus tué a laissées sont effacées à la première fiche ouverte ensuite.
 */
class FicheViewModel(
    application: Application,
    private val semaine: SemaineIso,
    private val numeroDonne: Int?,
) : AndroidViewModel(application) {

    private val depot = DepotLocal.de(application)
    private val brouillons = File(application.cacheDir, "fiche").apply { mkdirs() }

    init {
        // Une fois par processus, avant toute photo : aucun autre écran de fiche n'a encore de
        // brouillon, ce qui reste vient d'un processus tué (app balayée, tuée en arrière-plan).
        if (NETTOYE.compareAndSet(false, true)) {
            brouillons.listFiles()?.forEach { it.delete() }
            RangementFiche.oubliees(depot.dossierPhotos(semaine)).forEach { it.delete() }
        }
    }

    /** Une face prise : la photo en attente, ce que l'OCR y a lu, sa vignette. */
    private class Prise(val fichier: File, val page: PageFiche, val vue: PageVue)

    private data class Local(
        val saisie: String = "",
        val personnes: Int? = null,
        /** Réglé à la main : choisir une autre recette ne le remplace plus. */
        val personnesManuelles: Boolean = false,
        val recto: Prise? = null,
        val verso: Prise? = null,
        val enCours: Face? = null,
        val erreur: String? = null,
        val menuOuvert: Boolean = false,
        val enregistrement: Boolean = false,
        /** Posé au premier essai d'enregistrement : les suivants rangent sous les mêmes noms. */
        val horodatage: String? = null,
    ) {
        fun prise(face: Face): Prise? = if (face == Face.RECTO) recto else verso
        fun avec(face: Face, prise: Prise?): Local = if (face == Face.RECTO) copy(recto = prise) else copy(verso = prise)
    }

    private val local = MutableStateFlow(
        // Venue du scanner, la box est connue : son nombre de personnes aussi.
        Local(personnes = numeroDonne?.let { depot.etat(semaine).value.fichier.box(it)?.personnes }),
    )

    /** Vrai une fois la fiche enregistrée : l'écran se referme ([retourFait] l'acquitte). */
    private val enregistree = MutableStateFlow(false)
    val fini: StateFlow<Boolean> = enregistree

    /**
     * Vrai tant que l'écran est ouvert ([demarrer] → [quitte]) ; une rotation ne le quitte pas.
     * Un enregistrement qui finit après un Retour ne refermera donc pas la visite suivante.
     */
    private var present = false

    val etat: StateFlow<EtatFiche> = combine(depot.etat(semaine), local, ::vue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vue(depot.etat(semaine).value, local.value))

    /** À l'ouverture de l'écran : le menu est lu s'il ne l'est pas, pour proposer les recettes. */
    fun demarrer() {
        present = true
        local.update { it.copy(erreur = null, menuOuvert = false) }
        viewModelScope.launch {
            try {
                depot.chargerMenu(semaine)
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (e: Exception) {
                // Le réseau est traité par le dépôt : ce qui remonte ici est l'écriture du menu.
                local.update { it.copy(erreur = ERREUR_MENU) }
            }
        }
    }

    /** Photographie la face suivante avec [capture], liée à l'aperçu de l'écran. */
    fun photographier(capture: ImageCapture) {
        val l = local.value
        val face = when {
            l.enCours != null || l.enregistrement -> return
            l.recto == null -> Face.RECTO
            l.verso == null -> Face.VERSO
            else -> return
        }
        local.update { it.copy(enCours = face, erreur = null) }
        val fichier = File(brouillons, "${face.name.lowercase()}-${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(fichier).build(),
            ContextCompat.getMainExecutor(getApplication()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(resultat: ImageCapture.OutputFileResults) {
                    val lecture = viewModelScope.launch { lire(face, fichier) }
                    // Écran déjà détruit : la lecture ne démarrera pas, la photo ne reste pas dans le cache.
                    if (lecture.isCancelled) fichier.delete()
                }

                override fun onError(erreur: ImageCaptureException) {
                    fichier.delete()
                    local.update { it.copy(enCours = null, erreur = "La photo n'a pas été prise\u00A0: réessaie.") }
                }
            },
        )
    }

    private suspend fun lire(face: Face, fichier: File) {
        val prise = try {
            val page = OcrPage.lire(fichier, fichier.name)
            val vignette = OcrPage.vignette(fichier, COTE_VIGNETTE)
            Prise(fichier, page, PageVue(page.blocs.size, vignette?.asImageBitmap()))
        } catch (annulation: CancellationException) {
            // Pas encore dans l'état : personne d'autre ne l'effacerait.
            fichier.delete()
            throw annulation
        } catch (e: Exception) {
            fichier.delete()
            local.update { it.copy(enCours = null, erreur = "La photo est illisible\u00A0: reprends-la.") }
            return
        }
        local.update { it.avec(face, prise).copy(enCours = null) }
    }

    fun reprendre(face: Face) {
        local.value.prise(face)?.fichier?.delete()
        local.update { it.avec(face, null) }
    }

    /** Le numéro tapé : des chiffres seulement, quatre au plus. */
    fun saisir(texte: String) = local.update { it.copy(saisie = texte.filter(Char::isDigit).take(4)) }

    fun ouvrirMenu(ouvert: Boolean) = local.update { it.copy(menuOuvert = ouvert) }

    /** Une recette du menu : son numéro, et le nombre de personnes de sa box s'il n'a pas été réglé à la main. */
    fun choisir(numero: Int) {
        val box = depot.etat(semaine).value.fichier.box(numero)
        local.update {
            it.copy(
                saisie = numero.toString(),
                menuOuvert = false,
                personnes = if (it.personnesManuelles) it.personnes else box?.personnes,
            )
        }
    }

    fun corrigerPersonnes(personnes: Int?) = local.update { it.copy(personnes = personnes, personnesManuelles = true) }

    /**
     * Range les photos à côté du fichier de la semaine, puis la fiche dans le dépôt
     * ([RangementFiche] : noms en attente, puis définitifs une fois la fiche inscrite). Un
     * échec se reprend avec « Enregistrer » : rien n'est perdu ni rangé deux fois.
     */
    fun enregistrer() {
        val l = local.value
        val recto = l.recto ?: return
        if (l.enregistrement || l.enCours != null) return
        val numero = numero(l)
        val horodatage = l.horodatage ?: HORODATAGE.format(Instant.now())
        val base = "fiche-" + (numero?.toString() ?: horodatage)
        local.update { it.copy(enregistrement = true, erreur = null, horodatage = horodatage) }
        viewModelScope.launch {
            try {
                val faces = listOfNotNull(Face.RECTO to recto, l.verso?.let { Face.VERSO to it })
                val rangees = withContext(Dispatchers.IO) {
                    val dossier = depot.dossierPhotos(semaine)
                    faces.map { (face, prise) ->
                        val nom = "$base-${face.name.lowercase()}.jpg"
                        val attente = File(dossier, RangementFiche.enAttente(nom))
                        RangementFiche.deplacer(prise.fichier, attente)
                        // Rangée : la prise pointe désormais sur elle, un nouvel essai ne la redéplace pas.
                        local.update { if (it.prise(face) === prise) it.avec(face, Prise(attente, prise.page, prise.vue)) else it }
                        Rangee(attente, File(dossier, nom), prise.page.copy(fichier = nom))
                    }
                }
                depot.ajouterFiche(semaine, FicheScannee(numero, l.personnes, rangees.map { it.page }, prisLe = Instant.now().toString()))
                withContext(Dispatchers.IO) {
                    // Inscrite : les photos prennent leur nom et remplacent celles d'une fiche précédente.
                    rangees.forEach { RangementFiche.deplacer(it.attente, it.definitive) }
                    // Une fiche refaite pour la même box, sans verso : celui de la précédente ne reste pas.
                    if (l.verso == null) File(depot.dossierPhotos(semaine), "$base-verso.jpg").delete()
                }
                local.value = Local()
                // Écran quitté pendant l'enregistrement : la fiche est enregistrée, rien à refermer.
                enregistree.value = present
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (e: Exception) {
                val erreur = when (e) {
                    is PhotoIntrouvable -> "Une photo n'est plus sur le téléphone\u00A0: reprends-la, puis enregistre."
                    is IOException -> "La fiche n'a pas été enregistrée\u00A0: le téléphone manque peut-être de place. Libère-en, puis réessaie."
                    else -> "La fiche n'a pas été enregistrée\u00A0: réessaie."
                }
                local.update { it.copy(enregistrement = false, erreur = erreur) }
            }
        }
    }

    /** Une photo rangée : son nom en attente, son nom définitif, sa page telle qu'inscrite. */
    private class Rangee(val attente: File, val definitive: File, val page: PageFiche)

    fun retourFait() {
        enregistree.value = false
    }

    /** L'écran est vraiment quitté (pas une rotation). */
    fun quitte() {
        present = false
        enregistree.value = false
    }

    private fun numero(l: Local): Int? = numeroDonne ?: l.saisie.toIntOrNull()

    private fun vue(semaineEtat: EtatSemaine, l: Local): EtatFiche {
        val numero = numero(l)
        return EtatFiche(
            numeroDonne = numeroDonne != null,
            numero = numero,
            titre = numero?.let { semaineEtat.menu?.entree(it)?.titre ?: semaineEtat.recette(it)?.titre },
            dejaPrise = numero != null && semaineEtat.fichier.fiches.any { it.numero == numero },
            saisie = l.saisie,
            entreesMenu = semaineEtat.menu?.entrees.orEmpty(),
            scannees = semaineEtat.boxes.map { it.numero }.toSet(),
            menuOuvert = l.menuOuvert,
            recto = l.recto?.vue,
            verso = l.verso?.vue,
            enCours = l.enCours,
            personnes = l.personnes,
            erreur = l.erreur,
            enregistrement = l.enregistrement,
        )
    }

    /** Les photos jamais enregistrées ne restent ni dans le cache ni, en attente, à côté de la semaine. */
    override fun onCleared() {
        local.value.recto?.fichier?.delete()
        local.value.verso?.fichier?.delete()
    }

    companion object {
        private const val COTE_VIGNETTE = 112
        private val HORODATAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        private const val ERREUR_MENU =
            "Le menu de la semaine n'a pas pu être enregistré\u00A0: le téléphone manque peut-être de place."

        /** Les brouillons d'un processus précédent ne sont effacés qu'une fois. */
        private val NETTOYE = AtomicBoolean(false)

        fun fabrique(semaine: SemaineIso, numero: Int?): ViewModelProvider.Factory = viewModelFactory {
            initializer { FicheViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, semaine, numero) }
        }
    }
}
