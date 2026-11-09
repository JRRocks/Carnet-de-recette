@file:OptIn(ExperimentalSerializationApi::class)

package fr.jrrocks.carnetderecette.core.cuisine

import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import kotlin.math.ceil
import kotlin.math.round
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/*
 * Le fichier de la semaine écrit par Cowork le vendredi soir (`semaine-AAAA-Sww.json`,
 * cadrage § 6) : pour chaque box, les tâches des deux cuisiniers et les segments que la voix
 * leur lit. Il ne contient que le travail de Claude, avec ses mots, et des références aux
 * recettes : jamais une copie du contenu d'origine. Le skill qui l'écrit est dans
 * `cowork/carnet-semaine/SKILL.md` ; tout changement de forme se fait des deux côtés.
 */

@Serializable
data class FichierSemaine(
    val type: String = TYPE,
    val version: Int = VERSION,
    val semaine: SemaineIso,
    val statut: StatutPreparation = StatutPreparation.OK,
    /** Ce qui s'est mal passé, en clair (une box illisible, le mode dégradé). */
    val message: String? = null,
    /** Dans l'ordre où Claude conseille de les cuisiner : la première est celle de ce soir. */
    val recettes: List<RecettePreparee> = emptyList(),
) {
    fun recette(numero: Int): RecettePreparee? = recettes.firstOrNull { it.numero == numero }

    companion object {
        const val TYPE = "semaine"

        /** La version que cette app sait lire ; un fichier plus récent demande une mise à jour. */
        const val VERSION = 1
    }
}

@Serializable
enum class StatutPreparation {
    @SerialName("ok") OK,

    /** Une partie des box n'a pas pu être préparée : [FichierSemaine.message] dit lesquelles. */
    @SerialName("partiel") PARTIEL,

    /** L'app avait signalé l'accès au site rompu : Claude n'a rien préparé (cadrage § 9). */
    @SerialName("degrade") DEGRADE,
}

/** Une box préparée par Claude. */
@Serializable
data class RecettePreparee(
    /** Le numéro de la recette au menu, celui de l'étiquette. */
    val numero: Int,
    /** Le nombre de personnes confirmé : les quantités des tâches sont celles de cette portion. */
    val personnes: Int? = null,
    @SerialName("a_confirmer") val aConfirmer: Boolean = false,
    /** Pourquoi Claude confirme, ou ce qui reste à vérifier, en une phrase. */
    val confirmation: String? = null,
    val source: SourcePreparee? = null,
    /** Dans l'ordre où Claude les ferait : chaque cuisinier prend la première prête de son rôle. */
    val taches: List<Tache> = emptyList(),
    /** Dans l'ordre où la voix les dit à chaque cuisinier. */
    @SerialName("segments_audio") val segments: List<SegmentPrepare> = emptyList(),
) {
    fun tache(id: String?): Tache? = id?.let { cle -> taches.firstOrNull { it.id == cle } }
}

/** La recette d'origine : une référence, jamais son contenu. */
@Serializable
data class SourcePreparee(val type: String? = null, val id: String? = null, val url: String? = null)

/** Les deux rôles en cuisine : l'un prépare les ingrédients, l'autre tient les feux. */
@Serializable
enum class RoleCuisine {
    @SerialName("decoupe") @JsonNames("découpe") DECOUPE,
    @SerialName("chauffe") CHAUFFE,
}

@Serializable
enum class TypeTache {
    /** Éplucher, couper, hacher, râper, presser. */
    @SerialName("decoupe") @JsonNames("découpe") DECOUPE,

    /** Four, feu, assaisonner pendant la cuisson. */
    @SerialName("chauffe") CHAUFFE,

    /** Cuisson passive (four, mijotage, repos) : elle court sans occuper personne. */
    @SerialName("attente") ATTENTE,

    @SerialName("dressage") DRESSAGE,
}

/** Une action courte qu'une seule personne fait d'un trait (cadrage § 7). */
@Serializable
data class Tache(
    val id: String = "",
    val type: TypeTache? = null,
    /** Qui la fait à deux ; à défaut, la découpe pour une tâche de découpe, la chauffe sinon. */
    val role: RoleCuisine? = null,
    @SerialName("duree_min") val dureeMin: Double? = null,
    /** Les tâches qui doivent être finies avant. */
    val apres: List<String> = emptyList(),
    /** Avec leur quantité pour le nombre de personnes confirmé (« 2 échalotes »). */
    val ingredients: List<String> = emptyList(),
    val ustensile: String? = null,
    /** Le libellé court affiché sur le plan (« Émincer les échalotes »). */
    val texte: String = "",
    @SerialName("etape_origine") val etapeOrigine: Int? = null,
    val materiel: Materiel? = null,
) {
    /** Le type, déduit du rôle s'il manque. */
    val sorte: TypeTache get() = type ?: if (role == RoleCuisine.DECOUPE) TypeTache.DECOUPE else TypeTache.CHAUFFE

    /** Le rôle, déduit du type s'il manque. */
    val qui: RoleCuisine get() = role ?: if (sorte == TypeTache.DECOUPE) RoleCuisine.DECOUPE else RoleCuisine.CHAUFFE

    /** Une attente ne retient personne : le plat cuit pendant qu'on fait autre chose. */
    val passive: Boolean get() = sorte == TypeTache.ATTENTE

    /** En minutes entières, au moins une : une tâche sans durée prend quand même un moment. */
    val minutes: Int
        get() = dureeMin?.takeIf { it.isFinite() && it > 0 }?.let { ceil(it).toInt() }?.coerceIn(1, MINUTES_MAX) ?: 1

    companion object {
        /** Au-delà de dix heures, ce n'est plus une tâche de cuisine : une faute de frappe. */
        const val MINUTES_MAX = 600
    }
}

/** Le matériel du foyer pour une tâche (`profil-cuisine.json`) : hachoir, râpe électrique. */
@Serializable
data class Materiel(
    val outil: Outil? = null,
    val conseil: String? = null,
    /** Les tâches faites avec le même outil, ensemble ou à la suite. */
    val avec: List<String> = emptyList(),
    val mode: ModeMateriel? = null,
)

@Serializable
enum class Outil {
    @SerialName("hachoir") HACHOIR,
    @SerialName("rape_electrique") @JsonNames("râpe_électrique", "rape") RAPE_ELECTRIQUE,
    @SerialName("aucun") AUCUN,
}

@Serializable
enum class ModeMateriel {
    /** Dans la cuve en même temps. */
    @SerialName("ensemble") ENSEMBLE,

    /** À la suite sans rincer, du plus sec au plus parfumé. */
    @SerialName("a_la_suite") @JsonNames("à_la_suite") A_LA_SUITE,
}

/** Ce que la voix dit à un cuisinier : une action courte, écrite pour l'oreille. */
@Serializable
data class SegmentPrepare(
    val id: String = "",
    val tache: String? = null,
    /** À qui il est lu ; à défaut, au rôle de sa tâche. */
    val role: RoleCuisine? = null,
    val texte: String = "",
    /**
     * Le même segment pour qui cuisine seul, quand [texte] parle de l'autre cuisinier (« passe
     * les oignons à la chauffe ») ; vide, le segment n'a pas de sens seul et n'est pas lu.
     * Absent : [texte] convient aussi seul.
     */
    @SerialName("texte_seul") val texteSeul: String? = null,
    val declencheur: Declencheur = Declencheur.MANUEL,
    /** La cuisson minutée que ce segment lance, en secondes. */
    @SerialName("minuteur_s") val minuteurS: Double? = null,
) {
    /** Le minuteur en secondes entières, s'il est plausible (une seconde à dix heures). */
    val minuteurSecondes: Int?
        get() = minuteurS?.takeIf { it.isFinite() }?.let { round(it).toInt() }?.takeIf { it in 1..MINUTEUR_MAX }

    companion object {
        const val MINUTEUR_MAX = 10 * 3_600
    }
}
