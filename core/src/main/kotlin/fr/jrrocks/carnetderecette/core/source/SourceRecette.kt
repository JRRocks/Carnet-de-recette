package fr.jrrocks.carnetderecette.core.source

/**
 * D'où vient une recette.
 *
 * Seul [HELLOFRESH] a un moteur : les pages publiques du site, lues par l'app le vendredi
 * (cadrage § 5). Les autres sont des **places réservées** pour le mode dégradé
 * (cadrage § 9) : on ne les code pas tant que l'accès HelloFresh tient (CLAUDE.md, règle 4).
 * Elles existent dès maintenant pour que le modèle et les fichiers de la semaine sachent
 * déjà les nommer, et qu'un moteur ajouté plus tard n'oblige pas à tout renommer.
 */
enum class TypeSource(val moteurDisponible: Boolean) {
    /** Pages publiques de hellofresh.fr : menu de la semaine et page de chaque recette. */
    HELLOFRESH(moteurDisponible = true),

    /** Réservé : PDF imprimé depuis le site. */
    PDF(moteurDisponible = false),

    /** Réservé : photo de la fiche papier, lue par OCR ou par vision. */
    PHOTO_FICHE(moteurDisponible = false),

    /** Réservé : autres sites de recettes, au format standard `schema.org/Recipe`. */
    JSON_LD(moteurDisponible = false),
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
