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

    /**
     * Les pages dont la lecture a rompu l'accès, par semaine : [PAGE_MENU] pour le menu, sinon
     * le numéro de la box. L'accès rompu est levé quand la dernière se relit. Sous [verrou].
     */
    private val pagesRompues = HashMap<SemaineIso, MutableSet<Int>>()

    /** Les images en cours de téléchargement, par chemin : jamais deux fois la même à la fois. */
    private val imagesEnCours: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun etat(semaine: SemaineIso): StateFlow<EtatSemaine> = flux(semaine)

    private fun flux(semaine: SemaineIso): MutableStateFlow<EtatSemaine> {
        etats[semaine]?.let { return it }
        // Lu du disque au premier appel : quelques dizaines de Ko au plus.
        val fichier = lireFichier(semaine)
        // Le statut « degrade » survit au redémarrage : le bandeau de l'accès rompu aussi.
        val rompu = if (fichier.statut == StatutSemaine.DEGRADE) fichier.message ?: MOTIF_DEGRADE else null
        val nouveau = MutableStateFlow(EtatSemaine(semaine, fichier, lireMenu(semaine), accesRompu = rompu))
        etats.putIfAbsent(semaine, nouveau)?.let { return it }
        // Les images qu'une coupure ou une app fermée trop tôt ont laissées en route.
        completerImages(semaine)
        return nouveau
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
                    marquerRompu(semaine, PAGE_MENU, cause.motif)
                    ResultatMenu.AccesRompu(cause.motif)
                }
            }
        }
        Sonde.verifierMenu(menu)?.let { motif ->
            marquerRompu(semaine, PAGE_MENU, motif)
            return ResultatMenu.AccesRompu(motif)
        }
        verrou.withLock {
            // Gardé en mémoire même si le téléphone est plein : le scan marche quand même.
            etat.update { it.copy(menu = menu) }
            sansPlanter { ecrireDe(semaine, fichierMenu(semaine), json.encodeToString(MenuSemaine.serializer(), menu)) }
            sansPlanter { leverEchec(semaine, PAGE_MENU) }
        }
        // « Réessayer » : les pages de recette qui avaient rompu l'accès sont relues aussi ; si
        // elles se lisent, l'accès rompu est levé.
        if (forcer) {
            val enEchec = verrou.withLock { pagesRompues[semaine].orEmpty().toList() }
            enEchec.forEach { lireRecetteDe(semaine, it) }
        }
        return ResultatMenu.Lu(menu)
    }

    override suspend fun ajouterBox(semaine: SemaineIso, box: BoxScannee): ResultatRecette {
        val gardee = verrou.withLock {
            sansPlanter { modifier(semaine) { f -> f.copy(boxes = f.boxes.filterNot { it.numero == box.numero } + box) } }
        }
        if (!gardee) return ResultatRecette.HorsLigne(EcritureImpossible.MOTIF_ECRITURE)
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
                        marquerRompu(semaine, numero, cause.motif)
                        ResultatRecette.AccesRompu(cause.motif)
                    }
                }
            }
            Sonde.verifierRecette(recette)?.let { motif ->
                marquerRompu(semaine, numero, motif)
                return ResultatRecette.AccesRompu(motif)
            }
            val gardee = try {
                verrou.withLock {
                    // La box a pu être retirée pendant la lecture : pas de recette sans box.
                    val existe = etat.value.fichier.box(numero) != null
                    if (existe) {
                        modifier(semaine) { f ->
                            f.copy(
                                recettes = f.recettes.filterNot { it.id == recette.id } + recette,
                                boxes = f.boxes.map { if (it.numero == numero) it.copy(recetteId = recette.id) else it },
                            )
                        }
                    }
                    // La page s'est lue : le site répond comme attendu.
                    sansPlanter { leverEchec(semaine, numero) }
                    existe
                }
            } catch (e: EcritureImpossible) {
                return ResultatRecette.HorsLigne(EcritureImpossible.MOTIF_ECRITURE)
            }
            if (gardee) telechargerImages(semaine, listOf(recette))
            return ResultatRecette.Lue(recette)
        } finally {
            etat.update { it.copy(enLecture = it.enLecture - numero) }
        }
    }

    override fun completerImages(semaine: SemaineIso) {
        val fichier = flux(semaine).value.fichier
        telechargerImages(semaine, fichier.recettes.filter { r -> fichier.boxes.any { it.recetteId == r.id } })
    }

    /**
     * Les images de [recettes], en arrière-plan : la box est déjà affichée, les images
     * arrivent ensuite. Une image qui échoue n'empêche pas les autres ; [completerImages] la
     * redemandera. Celles déjà là, ou déjà en cours, sont sautées.
     */
    private fun telechargerImages(semaine: SemaineIso, recettes: List<Recette>) {
        val voulues = recettes.flatMap { recette ->
            buildList {
                recette.image?.let { add(it to LARGEUR_PLAT) }
                recette.etapes.flatMap { it.images }.forEach { add(it.chemin to LARGEUR_ETAPE) }
                recette.ingredients.mapNotNull { it.image }.forEach { add(it to LARGEUR_INGREDIENT) }
            }
        }.distinctBy { it.first }
        if (voulues.isEmpty()) return
        fond.launch {
            voulues.forEach { (chemin, largeur) ->
                val cible = fichierImage(chemin)
                if (cible.exists() || !imagesEnCours.add(chemin)) return@forEach
                try {
                    // Revue une fois réservée : une autre tâche a pu la finir entre-temps.
                    if (cible.exists()) return@forEach
                    runCatching { telecharger(AdressesHelloFresh.image(chemin, largeur), cible) }
                        .onSuccess { flux(semaine).update { it.copy(versionImages = it.versionImages + 1) } }
                } finally {
                    imagesEnCours.remove(chemin)
                }
            }
        }
    }

    override suspend fun corrigerPersonnes(semaine: SemaineIso, numero: Int, personnes: Int) {
        verrou.withLock {
            // Corrigé à la main : plus de doute à lever.
            sansPlanter {
                modifier(semaine) { f -> f.copy(boxes = f.boxes.map { if (it.numero == numero) it.copy(personnes = personnes, aConfirmer = false) else it }) }
            }
        }
    }

    override suspend fun retirerBox(semaine: SemaineIso, numero: Int) {
        verrou.withLock {
            // Sa page ne compte plus parmi celles qui rompent l'accès.
            pagesRompues[semaine]?.remove(numero)
            sansPlanter {
                modifier(semaine) { f ->
                    val restantes = f.boxes.filterNot { it.numero == numero }
                    val utiles = restantes.mapNotNull { it.recetteId }.toSet()
                    f.copy(boxes = restantes, recettes = f.recettes.filter { it.id in utiles })
                }
            }
        }
    }

    // Lève EcritureImpossible : l'écran de la fiche garde alors ses photos et propose de réessayer.
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

    /** Modifie le fichier de la semaine et l'écrit ; à appeler sous [verrou]. Lève [EcritureImpossible]. */
    private suspend fun modifier(semaine: SemaineIso, changement: (FichierRecettes) -> FichierRecettes) {
        val etat = flux(semaine)
        val nouveau = changement(etat.value.fichier)
        ecrireDe(semaine, fichierRecettes(semaine), json.encodeToString(FichierRecettes.serializer(), nouveau))
        etat.update { it.copy(fichier = nouveau) }
    }

    /**
     * La page [page] s'est lue : elle ne rompt plus l'accès. Quand plus aucune ne le rompt,
     * le bandeau disparaît et le fichier repasse « ok ». Sous [verrou] ; lève [EcritureImpossible].
     */
    private suspend fun leverEchec(semaine: SemaineIso, page: Int) {
        val restantes = pagesRompues[semaine]?.apply { remove(page) }
        if (!restantes.isNullOrEmpty()) return
        pagesRompues.remove(semaine)
        val etat = flux(semaine)
        val fichier = etat.value.fichier
        if (etat.value.accesRompu == null && fichier.statut == StatutSemaine.OK && fichier.message == null) return
        etat.update { it.copy(accesRompu = null) }
        modifier(semaine) { it.copy(statut = StatutSemaine.OK, message = null) }
    }

    private suspend fun marquerRompu(semaine: SemaineIso, page: Int, motif: String) = verrou.withLock {
        pagesRompues.getOrPut(semaine) { mutableSetOf() } += page
        flux(semaine).update { it.copy(accesRompu = motif) }
        sansPlanter { modifier(semaine) { it.copy(statut = StatutSemaine.DEGRADE, message = motif) } }
    }

    /** Écrit un fichier de [semaine] ; l'état dit si le téléphone manque de place. Lève [EcritureImpossible]. */
    private suspend fun ecrireDe(semaine: SemaineIso, fichier: File, contenu: String) {
        try {
            ecrire(fichier, contenu)
        } catch (e: EcritureImpossible) {
            flux(semaine).update { it.copy(ecritureImpossible = e.message) }
            throw e
        }
        flux(semaine).update { it.copy(ecritureImpossible = null) }
    }

    /**
     * Écrit via un fichier temporaire : un arrêt brutal ne laisse jamais un fichier à moitié
     * écrit. Un échec (téléphone plein, le plus souvent) devient [EcritureImpossible].
     */
    private suspend fun ecrire(fichier: File, contenu: String) = withContext(Dispatchers.IO) {
        val temporaire = File(fichier.parentFile, fichier.name + ".tmp")
        try {
            fichier.parentFile?.mkdirs()
            temporaire.writeText(contenu)
            if (!temporaire.renameTo(fichier)) {
                fichier.delete()
                if (!temporaire.renameTo(fichier)) throw IOException("Impossible de ranger ${fichier.name}.")
            }
        } catch (e: IOException) {
            temporaire.delete()
            throw EcritureImpossible(e)
        }
    }

    /** Une écriture impossible reste dans l'état de la semaine, sans faire planter l'écran : faux si elle a échoué. */
    private inline fun sansPlanter(ecriture: () -> Unit): Boolean = try {
        ecriture()
        true
    } catch (e: EcritureImpossible) {
        false
    }

    private sealed interface Cause {
        data class Reseau(val motif: String) : Cause
        data class Rompu(val motif: String) : Cause
    }

    /** Pas de réseau ou refus passager (on réessaiera), ou site changé (mode dégradé) ? */
    private fun cause(e: Exception, quoi: String): Cause = when {
        e is PageIllisible -> Cause.Rompu(e.message ?: "La page $quoi est illisible.")
        // 429, 503… : le site est occupé, pas changé. Un vrai blocage répond 403.
        e is RefusSite && Sonde.refusPassager(e.code) ->
            Cause.Reseau("Le site ne répond pas pour l'instant (code ${e.code})\u00A0: réessaie dans un moment.")
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

        /** La page du menu, parmi les [pagesRompues] (les box portent un numéro positif). */
        private const val PAGE_MENU = 0

        /** Un fichier « degrade » sans motif écrit (version ancienne, écriture interrompue). */
        private const val MOTIF_DEGRADE = "La dernière lecture du site n'a pas abouti."

        @Volatile private var instance: DepotLocal? = null

        /** Le dépôt de l'app, un seul pour tout le processus. */
        fun de(contexte: Context): DepotLocal =
            instance ?: synchronized(this) { instance ?: DepotLocal(contexte.applicationContext).also { instance = it } }
    }
}
