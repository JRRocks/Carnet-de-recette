package fr.jrrocks.carnetderecette.core.recette

import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.core.source.TypeSource
import kotlinx.serialization.Serializable

/*
 * Le modèle d'une recette, tel que l'app le garde sur le téléphone et le dépose sur le Drive
 * du foyer (`recettes-AAAA-Sww.json`). Contenu HelloFresh : usage privé seulement, jamais dans
 * ce dépôt (CLAUDE.md, règles 1 et 2). Les images sont désignées par leur **chemin** sur le
 * site (« /image/…jpg ») ; l'adresse complète se construit au moment de les télécharger.
 */

/** Une recette du menu de la semaine, telle que la page du menu l'annonce. */
@Serializable
data class EntreeMenu(
    /** Le numéro imprimé sur l'étiquette de la box (« 207 »). */
    val numero: Int,
    val titre: String,
    val sousTitre: String? = null,
    /** Adresse publique de la page de la recette. */
    val url: String,
    /** Chemin de l'image du plat sur le site. */
    val image: String? = null,
)

@Serializable
data class MenuSemaine(
    val semaine: SemaineIso,
    val entrees: List<EntreeMenu>,
    /** Instant de lecture, ISO 8601. */
    val luLe: String,
) {
    fun entree(numero: Int): EntreeMenu? = entrees.firstOrNull { it.numero == numero }
    val numeros: Set<Int> get() = entrees.mapTo(LinkedHashSet()) { it.numero }
}

@Serializable
data class Recette(
    /** Identifiant de la recette sur le site. */
    val id: String,
    val titre: String,
    val sousTitre: String? = null,
    val description: String? = null,
    /** Adresse publique finale (après redirection) de la page. */
    val url: String,
    /** Chemin de l'image du plat. */
    val image: String? = null,
    val dureeTotaleMin: Int? = null,
    val dureePreparationMin: Int? = null,
    /** 1 (facile) à 3. */
    val difficulte: Int? = null,
    val ingredients: List<Ingredient> = emptyList(),
    /** Les quantités pour chaque nombre de personnes proposé (souvent 1 à 6). */
    val portions: List<Portion> = emptyList(),
    val etapes: List<EtapeRecette> = emptyList(),
    val ustensiles: List<String> = emptyList(),
    val allergenes: List<String> = emptyList(),
    val source: TypeSource = TypeSource.HELLOFRESH,
) {
    fun portion(personnes: Int): Portion? = portions.firstOrNull { it.personnes == personnes }
    val personnesPossibles: List<Int> get() = portions.map { it.personnes }.sorted()
    fun ingredient(id: String): Ingredient? = ingredients.firstOrNull { it.id == id }
}

@Serializable
data class Ingredient(
    val id: String,
    val nom: String,
    /** Chemin de l'image de l'ingrédient. */
    val image: String? = null,
    /** Livré dans la box (sinon : « à avoir chez soi », comme le sel ou l'huile). */
    val livre: Boolean = true,
    val allergenes: List<String> = emptyList(),
)

@Serializable
data class Portion(val personnes: Int, val quantites: List<Quantite>)

@Serializable
data class Quantite(
    /** [Ingredient.id]. */
    val ingredient: String,
    val montant: Double? = null,
    val unite: String? = null,
)

@Serializable
data class EtapeRecette(
    val numero: Int,
    /** Le texte de l'étape, en texte simple (une ligne par puce). */
    val texte: String,
    val images: List<ImageEtape> = emptyList(),
)

@Serializable
data class ImageEtape(val chemin: String, val legende: String? = null)
