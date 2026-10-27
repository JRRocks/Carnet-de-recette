package fr.jrrocks.carnetderecette.core.source

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * D'où vient une recette.
 *
 * [HELLOFRESH] lit les pages publiques du site le vendredi (cadrage § 5). [PHOTO_FICHE] garde
 * les photos et le texte brut de la fiche papier, que Claude structure ensuite (décision du
 * 2026-10-27). Les autres sont des **places réservées** pour le mode dégradé (cadrage § 9) :
 * on ne les code pas tant que l'accès HelloFresh tient (CLAUDE.md, règle 4). Elles existent
 * pour que le modèle et les fichiers de la semaine sachent déjà les nommer.
 */
@Serializable
enum class TypeSource(val moteurDisponible: Boolean) {
    /** Pages publiques de hellofresh.fr : menu de la semaine et page de chaque recette. */
    @SerialName("hellofresh") HELLOFRESH(moteurDisponible = true),

    /** Réservé : PDF imprimé depuis le site. */
    @SerialName("pdf") PDF(moteurDisponible = false),

    /** Photos de la fiche papier et texte brut de l'OCR, structurés par Claude (décision du 2026-10-27). */
    @SerialName("photo_fiche") PHOTO_FICHE(moteurDisponible = true),

    /** Réservé : autres sites de recettes, au format standard `schema.org/Recipe`. */
    @SerialName("json_ld") JSON_LD(moteurDisponible = false),
}

/**
 * Un moteur qui produit des recettes à partir d'une [TypeSource].
 *
 * La méthode de lecture arrive au lot 2, avec le modèle de recette qu'elle rendra : la
 * définir avant serait deviner sa forme.
 */
interface SourceRecette {
    val type: TypeSource
}
