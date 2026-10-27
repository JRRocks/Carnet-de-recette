package fr.jrrocks.carnetderecette.donnees

import android.content.Context
import fr.jrrocks.carnetderecette.core.fiche.FicheScannee
import fr.jrrocks.carnetderecette.core.hellofresh.AdressesHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.LecteurHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.PageIllisible
import fr.jrrocks.carnetderecette.core.hellofresh.Sonde
import fr.jrrocks.carnetderecette.core.recette.MenuSemaine
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.core.semaine.FichierRecettes
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.core.semaine.StatutSemaine
import fr.jrrocks.carnetderecette.net.PageRecue
import fr.jrrocks.carnetderecette.net.PagesHelloFresh
import fr.jrrocks.carnetderecette.net.RefusSite
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Le dépôt de la semaine, sur le téléphone. Une semaine = un dossier :
 *
 * ```
 * semaines/2026-W44/menu.json       le menu lu sur le site (numéro → recette)
 * semaines/2026-W44/recettes.json   FichierRecettes : box scannées, recettes, fiches
 * semaines/2026-W44/photos/         étiquettes et fiches papier photographiées
 * images/                           images du site, téléchargées une fois (usage privé)
 * ```
 *
 * Le contenu HelloFresh reste sur le téléphone de l'utilisateur (CLAUDE.md, règle 2). Le
 * dépôt sur le Drive viendra une fois branché (décision du 2026-10-27).
 *
 * [lirePage] et [telecharger] sont remplaçables pour les tests.
 */
class DepotLocal(
    private val racine: File,
    private val lirePage: suspend (String) -> PageRecue = PagesHelloFresh::lire,
    private val telecharger: suspend (String, File) -> Unit = PagesHelloFresh::telechargerImage,
    private val maintenant: () -> Instant = Instant::now,
    private val fond: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DepotSemaine {

    constructor(contexte: Context) : this(contexte.filesDir)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val verrou = Mutex()
    private val etats = ConcurrentHashMap<SemaineIso, MutableStateFlow<EtatSemaine>>()
    private val dossierImages = File(racine, "images")

    override fun etat(semaine: SemaineIso): StateFlow<EtatSemaine> = flux(semaine)

    private fun flux(semaine: SemaineIso): MutableStateFlow<EtatSemaine> = etats.getOrPut(semaine) {
        // Lu du disque au premier appel : quelques dizaines de Ko au plus.
        MutableStateFlow(EtatSemaine(semaine, lireFichier(semaine), lireMenu(semaine)))
    }

    override suspend fun chargerMenu(semaine: SemaineIso, forcer: Boolean): ResultatMenu {
        val etat = flux(semaine)
        if (!forcer) etat.value.menu?.let { return ResultatMenu.Lu(it) }
        val menu = try {
            val page = lirePage(AdressesHelloFresh.menu(semaine))
            LecteurHelloFresh.lireMenu(page.html, semaine, maintenant().toString())
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (e: Exception) {
            return when (val cause = cause(e, "du menu")) {
                is Cause.Reseau -> ResultatMenu.HorsLigne(cause.motif)
                is Cause.Rompu -> {
                    marquerRompu(semaine, cause.motif)
                    ResultatMenu.AccesRompu(cause.motif)
                }
            }
        }
        Sonde.verifierMenu(menu)?.let { motif ->
            marquerRompu(semaine, motif)
            return ResultatMenu.AccesRompu(motif)
        }
        verrou.withLock {
            ecrire(fichierMenu(semaine), json.encodeToString(MenuSemaine.serializer(), menu))
            etat.update { it.copy(menu = menu, accesRompu = null) }
            modifier(semaine) { it.copy(statut = StatutSemaine.OK, message = null) }
        }
        return ResultatMenu.Lu(menu)
    }

    override suspend fun ajouterBox(semaine: SemaineIso, box: BoxScannee): ResultatRecette {
        verrou.withLock {
            modifier(semaine) { f -> f.copy(boxes = f.boxes.filterNot { it.numero == box.numero } + box) }
        }
        return lireRecetteDe(semaine, box.numero)
    }

    override suspend fun relireRecette(semaine: SemaineIso, numero: Int): ResultatRecette = lireRecetteDe(semaine, numero)

    private suspend fun lireRecetteDe(semaine: SemaineIso, numero: Int): ResultatRecette {
        val etat = flux(semaine)
        val menu = etat.value.menu ?: (chargerMenu(semaine) as? ResultatMenu.Lu)?.menu
        val entree = menu?.entree(numero) ?: return ResultatRecette.HorsMenu
        etat.update { it.copy(enLecture = it.enLecture + numero) }
        try {
            val recette = try {
                val page = lirePage(entree.url)
                LecteurHelloFresh.lireRecette(page.html, page.adresse)
            } catch (annulation: CancellationException) {
                throw annulation
            } catch (e: Exception) {
                return when (val cause = cause(e, "de la recette $numero")) {
                    is Cause.Reseau -> ResultatRecette.HorsLigne(cause.motif)
                    is Cause.Rompu -> {
                        marquerRompu(semaine, cause.motif)
                        ResultatRecette.AccesRompu(cause.motif)
                    }
                }
            }
            Sonde.verifierRecette(recette)?.let { motif ->
                marquerRompu(semaine, motif)
                return ResultatRecette.AccesRompu(motif)
            }
            verrou.withLock {
                modifier(semaine) { f ->
                    f.copy(
                        recettes = f.recettes.filterNot { it.id == recette.id } + recette,
                        boxes = f.boxes.map { if (it.numero == numero) it.copy(recetteId = recette.id) else it },
                    )
                }
            }
            telechargerImages(semaine, recette)
            return ResultatRecette.Lue(recette)
        } finally {
            etat.update { it.copy(enLecture = it.enLecture - numero) }
        }
    }

    /**
     * Les images d'une recette, en arrière-plan : la box est déjà affichée, les images
     * arrivent ensuite. Une image qui échoue n'empêche pas les autres ; on réessaiera.
     */
    private fun telechargerImages(semaine: SemaineIso, recette: Recette) {
        val voulues = buildList {
            recette.image?.let { add(it to LARGEUR_PLAT) }
            recette.etapes.flatMap { it.images }.forEach { add(it.chemin to LARGEUR_ETAPE) }
            recette.ingredients.mapNotNull { it.image }.forEach { add(it to LARGEUR_INGREDIENT) }
        }.distinctBy { it.first }
        fond.launch {
            voulues.forEach { (chemin, largeur) ->
                val cible = fichierImage(chemin)
                if (cible.exists()) return@forEach
                runCatching { telecharger(AdressesHelloFresh.image(chemin, largeur), cible) }
                    .onSuccess { flux(semaine).update { it.copy(versionImages = it.versionImages + 1) } }
            }
        }
    }

    override suspend fun corrigerPersonnes(semaine: SemaineIso, numero: Int, personnes: Int) = verrou.withLock {
        // Corrigé à la main : plus de doute à lever.
        modifier(semaine) { f -> f.copy(boxes = f.boxes.map { if (it.numero == numero) it.copy(personnes = personnes, aConfirmer = false) else it }) }
    }

    override suspend fun retirerBox(semaine: SemaineIso, numero: Int) = verrou.withLock {
        modifier(semaine) { f ->
            val restantes = f.boxes.filterNot { it.numero == numero }
            val utiles = restantes.mapNotNull { it.recetteId }.toSet()
            f.copy(boxes = restantes, recettes = f.recettes.filter { it.id in utiles })
        }
    }

    override suspend fun ajouterFiche(semaine: SemaineIso, fiche: FicheScannee) = verrou.withLock {
        modifier(semaine) { f ->
            val autres = if (fiche.numero == null) f.fiches else f.fiches.filterNot { it.numero == fiche.numero }
            f.copy(fiches = autres + fiche)
        }
    }

    override fun dossierPhotos(semaine: SemaineIso): File = File(dossier(semaine), "photos").apply { mkdirs() }

    override fun imageLocale(chemin: String): File? = fichierImage(chemin).takeIf { it.exists() }

    // ── Fichiers ────────────────────────────────────────────────────────────────

    private fun dossier(semaine: SemaineIso) = File(racine, "semaines/$semaine")
    private fun fichierMenu(semaine: SemaineIso) = File(dossier(semaine), "menu.json")
    private fun fichierRecettes(semaine: SemaineIso) = File(dossier(semaine), "recettes.json")

    /** « /image/a b.jpg » → « image_a_b.jpg » : un nom de fichier sûr par chemin d'image. */
    private fun fichierImage(chemin: String) =
        File(dossierImages, chemin.trimStart('/').map { if (it.isLetterOrDigit() || it in ".-_") it else '_' }.joinToString(""))

    private fun lireFichier(semaine: SemaineIso): FichierRecettes =
        lire(fichierRecettes(semaine), FichierRecettes.serializer()) ?: FichierRecettes(semaine = semaine)

    private fun lireMenu(semaine: SemaineIso): MenuSemaine? = lire(fichierMenu(semaine), MenuSemaine.serializer())

    private fun <T> lire(fichier: File, serialiseur: kotlinx.serialization.KSerializer<T>): T? =
        fichier.takeIf { it.exists() }?.let { runCatching { json.decodeFromString(serialiseur, it.readText()) }.getOrNull() }

    /** Modifie le fichier de la semaine et l'écrit ; à appeler sous [verrou]. */
    private suspend fun modifier(semaine: SemaineIso, changement: (FichierRecettes) -> FichierRecettes) {
        val etat = flux(semaine)
        val nouveau = changement(etat.value.fichier)
        ecrire(fichierRecettes(semaine), json.encodeToString(FichierRecettes.serializer(), nouveau))
        etat.update { it.copy(fichier = nouveau) }
    }

    private suspend fun marquerRompu(semaine: SemaineIso, motif: String) {
        flux(semaine).update { it.copy(accesRompu = motif) }
        verrou.withLock { modifier(semaine) { it.copy(statut = StatutSemaine.DEGRADE, message = motif) } }
    }

    /** Écrit via un fichier temporaire : un arrêt brutal ne laisse jamais un fichier à moitié écrit. */
    private suspend fun ecrire(fichier: File, contenu: String) = withContext(Dispatchers.IO) {
        fichier.parentFile?.mkdirs()
        val temporaire = File(fichier.parentFile, fichier.name + ".tmp")
        temporaire.writeText(contenu)
        if (!temporaire.renameTo(fichier)) {
            fichier.delete()
            if (!temporaire.renameTo(fichier)) throw IOException("Impossible d'écrire ${fichier.name}.")
        }
    }

    private sealed interface Cause {
        data class Reseau(val motif: String) : Cause
        data class Rompu(val motif: String) : Cause
    }

    /** Pas de réseau (on réessaiera) ou site changé (mode dégradé) ? */
    private fun cause(e: Exception, quoi: String): Cause = when {
        e is PageIllisible -> Cause.Rompu(e.message ?: "La page $quoi est illisible.")
        e is RefusSite && e.code == 404 -> Cause.Rompu("Le site ne trouve pas la page $quoi (code 404).")
        e is RefusSite -> Cause.Rompu(e.message ?: "Le site refuse la page $quoi.")
        generateSequence<Throwable>(e) { it.cause }.take(8).any {
            it is UnknownHostException || it is SocketTimeoutException || it is ConnectException || it is SocketException || it is SSLException
        } -> Cause.Reseau("Pas de réseau\u00A0: réessaie une fois connecté.")
        else -> Cause.Reseau("La lecture $quoi s'est coupée\u00A0: réessaie.")
    }

    companion object {
        const val LARGEUR_PLAT = 1200
        const val LARGEUR_ETAPE = 800
        const val LARGEUR_INGREDIENT = 200

        @Volatile private var instance: DepotLocal? = null

        /** Le dépôt de l'app, un seul pour tout le processus. */
        fun de(contexte: Context): DepotLocal =
            instance ?: synchronized(this) { instance ?: DepotLocal(contexte.applicationContext).also { instance = it } }
    }
}
