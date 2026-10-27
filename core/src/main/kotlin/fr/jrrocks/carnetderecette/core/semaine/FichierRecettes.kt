package fr.jrrocks.carnetderecette.core.semaine

import fr.jrrocks.carnetderecette.core.fiche.FicheScannee
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.scan.ElementLu
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ce que l'app sait d'une semaine : les box scannées et les recettes lues sur le site. C'est
 * le fichier `recettes-AAAA-Sww.json`, gardé sur le téléphone et déposé sur le Drive une fois
 * branché ; Cowork le lit le vendredi soir (cadrage § 6).
 */
@Serializable
data class FichierRecettes(
    val type: String = "recettes",
    val version: Int = 1,
    val semaine: SemaineIso,
    val statut: StatutSemaine = StatutSemaine.OK,
    /** Ce qui s'est mal passé, en clair (mode dégradé). */
    val message: String? = null,
    val boxes: List<BoxScannee> = emptyList(),
    /** Les recettes lues sur le site, par identifiant. */
    val recettes: List<Recette> = emptyList(),
    /** Les fiches papier photographiées en secours. */
    val fiches: List<FicheScannee> = emptyList(),
) {
    fun recette(id: String?): Recette? = id?.let { cle -> recettes.firstOrNull { it.id == cle } }
    fun box(numero: Int): BoxScannee? = boxes.firstOrNull { it.numero == numero }
}

@Serializable
enum class StatutSemaine {
    @SerialName("ok") OK,

    /** L'accès au site est rompu : Cowork passe en mode dégradé (cadrage § 9). */
    @SerialName("degrade") DEGRADE,
}

/** Une box de la semaine, reconnue sur son étiquette (ou choisie dans le menu). */
@Serializable
data class BoxScannee(
    /** Le numéro de la recette, celui du menu de la semaine. */
    val numero: Int,
    val personnes: Int,
    /** Doute sur la lecture : Claude confirme le vendredi soir (cadrage § 5). */
    val aConfirmer: Boolean = false,
    /** [Recette.id] une fois la page lue. */
    val recetteId: String? = null,
    /** Les éléments bruts lus sur l'étiquette, pour que Claude les relise. */
    val elements: List<ElementLu> = emptyList(),
    /** Comment la box a été désignée. */
    val origine: OrigineBox = OrigineBox.SCAN,
    /** Nom du fichier de la petite photo de l'étiquette, en secours. */
    val photo: String? = null,
    /** Instant du scan, ISO 8601. */
    val scanneLe: String,
)

@Serializable
enum class OrigineBox {
    /** Numéro lu sur l'étiquette. */
    @SerialName("scan") SCAN,

    /** Étiquette illisible : la recette a été touchée dans le menu. */
    @SerialName("menu") MENU,
}
