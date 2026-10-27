package fr.jrrocks.carnetderecette.core.scan

import kotlinx.serialization.Serializable

/**
 * Un morceau de texte lu par l'OCR dans le réticule, avec sa place : la hauteur des lettres
 * et la position, rapportées au réticule (0 à 1). Le grand numéro de l'étiquette se reconnaît
 * à sa hauteur, sans motif figé (CLAUDE.md, règle 5) ; Claude relit ces éléments bruts le
 * vendredi soir.
 */
@Serializable
data class ElementLu(
    val texte: String,
    /** Hauteur des lettres, en part de la hauteur du réticule. */
    val hauteur: Float,
    /** Centre de l'élément, en part de la largeur et de la hauteur du réticule. */
    val x: Float,
    val y: Float,
)
