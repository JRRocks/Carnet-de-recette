package fr.jrrocks.carnetderecette.core.fiche

import kotlinx.serialization.Serializable

/*
 * La fiche recette papier, photographiée en secours (cadrage § 9, décision du 2026-10-27).
 * L'app ne l'interprète pas : elle garde les photos et le texte brut de l'OCR, avec sa
 * place sur la page. Claude structure la recette le vendredi soir.
 */

@Serializable
data class FicheScannee(
    /** Le numéro de la box, s'il est connu (lu sur l'étiquette ou choisi dans le menu). */
    val numero: Int? = null,
    val personnes: Int? = null,
    val pages: List<PageFiche>,
    /** Instant de la prise, ISO 8601. */
    val prisLe: String,
)

@Serializable
data class PageFiche(
    /** Nom du fichier de la photo, à côté du fichier de la fiche. */
    val fichier: String,
    val largeur: Int,
    val hauteur: Int,
    val blocs: List<BlocTexte>,
)

/** Un bloc de texte tel que l'OCR le découpe (un paragraphe, une colonne d'ingrédients…). */
@Serializable
data class BlocTexte(val texte: String, val boite: Boite, val lignes: List<LigneTexte> = emptyList())

@Serializable
data class LigneTexte(val texte: String, val boite: Boite)

/** Rectangle en pixels de la photo. */
@Serializable
data class Boite(val gauche: Int, val haut: Int, val droite: Int, val bas: Int)
