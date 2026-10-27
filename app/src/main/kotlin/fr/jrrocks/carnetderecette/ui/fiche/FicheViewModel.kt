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
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
 * erreur ne les perd pas, l'écran les retrouve.
 */
class FicheViewModel(
    application: Application,
    private val semaine: SemaineIso,
    private val numeroDonne: Int?,
) : AndroidViewModel(application) {

    private val depot = DepotLocal.de(application)
    private val brouillons = File(application.cacheDir, "fiche").apply { mkdirs() }

    /** Une face prise : la photo en attente, ce que l'OCR y a lu, sa vignette. */
    private class Prise(val fichier: File, val page: PageFiche, val vue: PageVue)

    private data class Local(
        val saisie: String = "",
        val personnes: Int? = null,
        val recto: Prise? = null,
        val verso: Prise? = null,
        val enCours: Face? = null,
        val erreur: String? = null,
        val menuOuvert: Boolean = false,
        val enregistrement: Boolean = false,
    )

    private val local = MutableStateFlow(
        // Venue du scanner, la box est connue : son nombre de personnes aussi.
        Local(personnes = numeroDonne?.let { depot.etat(semaine).value.fichier.box(it)?.personnes }),
    )

    /** Vrai une fois la fiche enregistrée : l'écran se referme ([retourFait] l'acquitte). */
    private val enregistree = MutableStateFlow(false)
    val fini: StateFlow<Boolean> = enregistree

    val etat: StateFlow<EtatFiche> = combine(depot.etat(semaine), local, ::vue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), vue(depot.etat(semaine).value, local.value))

    /** À l'ouverture de l'écran : le menu est lu s'il ne l'est pas, pour proposer les recettes. */
    fun demarrer() {
        local.update { it.copy(erreur = null, menuOuvert = false) }
        viewModelScope.launch { depot.chargerMenu(semaine) }
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
                    viewModelScope.launch { lire(face, fichier) }
                }

                override fun onError(erreur: ImageCaptureException) {
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
            throw annulation
        } catch (e: Exception) {
            fichier.delete()
            local.update { it.copy(enCours = null, erreur = "La photo est illisible\u00A0: reprends-la.") }
            return
        }
        local.update { if (face == Face.RECTO) it.copy(recto = prise, enCours = null) else it.copy(verso = prise, enCours = null) }
    }

    fun reprendre(face: Face) {
        val l = local.value
        (if (face == Face.RECTO) l.recto else l.verso)?.fichier?.delete()
        local.update { if (face == Face.RECTO) it.copy(recto = null) else it.copy(verso = null) }
    }

    /** Le numéro tapé : des chiffres seulement, quatre au plus. */
    fun saisir(texte: String) = local.update { it.copy(saisie = texte.filter(Char::isDigit).take(4)) }

    fun ouvrirMenu(ouvert: Boolean) = local.update { it.copy(menuOuvert = ouvert) }

    fun choisir(numero: Int) {
        val box = depot.etat(semaine).value.fichier.box(numero)
        local.update { it.copy(saisie = numero.toString(), menuOuvert = false, personnes = it.personnes ?: box?.personnes) }
    }

    fun corrigerPersonnes(personnes: Int?) = local.update { it.copy(personnes = personnes) }

    /** Range les photos à côté du fichier de la semaine, puis la fiche dans le dépôt. */
    fun enregistrer() {
        val l = local.value
        val recto = l.recto ?: return
        if (l.enregistrement || l.enCours != null) return
        local.update { it.copy(enregistrement = true, erreur = null) }
        viewModelScope.launch {
            try {
                val numero = numero(l)
                val base = "fiche-" + (numero?.toString() ?: HORODATAGE.format(Instant.now()))
                val pages = withContext(Dispatchers.IO) {
                    val dossier = depot.dossierPhotos(semaine)
                    // Une fiche refaite pour la même box remplace la précédente : pas de verso orphelin.
                    if (l.verso == null) File(dossier, "$base-verso.jpg").delete()
                    listOfNotNull(recto to "recto", l.verso?.let { it to "verso" }).map { (prise, face) ->
                        val nom = "$base-$face.jpg"
                        deplacer(prise.fichier, File(dossier, nom))
                        prise.page.copy(fichier = nom)
                    }
                }
                depot.ajouterFiche(semaine, FicheScannee(numero, l.personnes, pages, prisLe = Instant.now().toString()))
                local.value = Local()
                enregistree.value = true
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (e: Exception) {
                local.update { it.copy(enregistrement = false, erreur = "La fiche n'a pas été enregistrée\u00A0: réessaie.") }
            }
        }
    }

    fun retourFait() {
        enregistree.value = false
    }

    private fun numero(l: Local): Int? = numeroDonne ?: l.saisie.toIntOrNull()

    private fun vue(semaineEtat: EtatSemaine, l: Local): EtatFiche {
        val numero = numero(l)
        return EtatFiche(
            numeroDonne = numeroDonne != null,
            numero = numero,
            titre = numero?.let { semaineEtat.menu?.entree(it)?.titre ?: semaineEtat.recette(it)?.titre },
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

    /** Les photos jamais enregistrées ne restent pas dans le cache. */
    override fun onCleared() {
        local.value.recto?.fichier?.delete()
        local.value.verso?.fichier?.delete()
    }

    companion object {
        private const val COTE_VIGNETTE = 112
        private val HORODATAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

        fun fabrique(semaine: SemaineIso, numero: Int?): ViewModelProvider.Factory = viewModelFactory {
            initializer { FicheViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!, semaine, numero) }
        }

        /** Le cache et les fichiers de l'app sont sur le même volume : un renommage suffit, sinon une copie. */
        private fun deplacer(source: File, cible: File) {
            cible.delete()
            if (source.renameTo(cible)) return
            source.copyTo(cible, overwrite = true)
            source.delete()
        }
    }
}
