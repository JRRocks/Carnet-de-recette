package fr.jrrocks.carnetderecette.donnees

import fr.jrrocks.carnetderecette.core.fiche.FicheScannee
import fr.jrrocks.carnetderecette.core.recette.MenuSemaine
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.core.semaine.FichierRecettes
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Ce que le téléphone sait d'une semaine, et comment le compléter : le menu lu sur le site,
 * les box scannées, les recettes et leurs images, les fiches papier. Tout est gardé **sur le
 * téléphone** (décision du 2026-10-27 : les outils marchent sans Drive) ; le dépôt sur le
 * Drive viendra une fois branché.
 *
 * Toutes les méthodes `suspend` font leurs entrées-sorties hors du fil principal.
 */
interface DepotSemaine {

    /** L'état de [semaine], mis à jour à chaque changement. */
    fun etat(semaine: SemaineIso): StateFlow<EtatSemaine>

    /**
     * Le menu de [semaine] : depuis le téléphone s'il a déjà été lu, sinon depuis la page
     * publique du site, contrôlée par la sonde (cadrage § 9). [forcer] relit le site.
     */
    suspend fun chargerMenu(semaine: SemaineIso, forcer: Boolean = false): ResultatMenu

    /**
     * Ajoute (ou remplace) une box, puis lit la page de sa recette et télécharge ses images.
     * La box est gardée même si la lecture de la page échoue : on pourra réessayer.
     */
    suspend fun ajouterBox(semaine: SemaineIso, box: BoxScannee): ResultatRecette

    /** Relit la page de la recette d'une box déjà scannée (après un échec réseau). */
    suspend fun relireRecette(semaine: SemaineIso, numero: Int): ResultatRecette

    suspend fun corrigerPersonnes(semaine: SemaineIso, numero: Int, personnes: Int)

    suspend fun retirerBox(semaine: SemaineIso, numero: Int)

    /** Range une fiche papier photographiée ; ses photos sont déjà dans [dossierFiches]. */
    suspend fun ajouterFiche(semaine: SemaineIso, fiche: FicheScannee)

    /** Où écrire les photos (étiquettes, fiches) de [semaine]. */
    fun dossierPhotos(semaine: SemaineIso): File

    /**
     * Le fichier local d'une image du site ([chemin] : « /image/…jpg »), s'il a déjà été
     * téléchargé ; `null` sinon.
     */
    fun imageLocale(chemin: String): File?
}

/** L'état d'une semaine, tel que les écrans l'affichent. */
data class EtatSemaine(
    val semaine: SemaineIso,
    val fichier: FichierRecettes,
    val menu: MenuSemaine? = null,
    /** Le dernier problème d'accès au site, en clair ; `null` si tout va bien. */
    val accesRompu: String? = null,
    /** Les numéros dont la page est en cours de lecture. */
    val enLecture: Set<Int> = emptySet(),
) {
    val boxes: List<BoxScannee> get() = fichier.boxes
    fun recette(numero: Int): Recette? = fichier.recette(fichier.box(numero)?.recetteId)
}

sealed interface ResultatMenu {
    data class Lu(val menu: MenuSemaine) : ResultatMenu

    /** Pas de réseau : rien n'est cassé, il faudra réessayer. */
    data class HorsLigne(val motif: String) : ResultatMenu

    /** La page a répondu mais la sonde n'y trouve pas ce qu'il faut : mode dégradé. */
    data class AccesRompu(val motif: String) : ResultatMenu
}

sealed interface ResultatRecette {
    data class Lue(val recette: Recette) : ResultatRecette

    /** Le numéro n'est pas dans le menu de la semaine (ou le menu n'est pas lu). */
    data object HorsMenu : ResultatRecette

    data class HorsLigne(val motif: String) : ResultatRecette

    data class AccesRompu(val motif: String) : ResultatRecette
}
