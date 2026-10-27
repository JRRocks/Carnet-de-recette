package fr.jrrocks.carnetderecette.core.hellofresh

import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.core.recette.EtapeRecette
import fr.jrrocks.carnetderecette.core.recette.ImageEtape
import fr.jrrocks.carnetderecette.core.recette.Ingredient
import fr.jrrocks.carnetderecette.core.recette.MenuSemaine
import fr.jrrocks.carnetderecette.core.recette.Portion
import fr.jrrocks.carnetderecette.core.recette.Quantite
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** La page a répondu, mais on n'y trouve pas ce qu'il faut : le site a peut-être changé. */
class PageIllisible(message: String) : Exception(message)

/**
 * Le lecteur des pages publiques de hellofresh.fr (cadrage § 5). Il lit les données que la
 * page envoie à tout visiteur : le bloc `__NEXT_DATA__` (celui que le navigateur utilise pour
 * afficher la page), et à défaut le bloc `schema.org/Recipe` prévu pour les moteurs de
 * recherche. C'est **la** pièce fragile : si le site change, c'est ici que ça casse, et la
 * sonde ([Sonde]) le dit aussitôt.
 *
 * Structure relevée le 2026-10-27 :
 * - menu (`/menus/2026-W44`) : `props.pageProps.ssrPayload.courses[]`, chaque entrée porte
 *   `index` (le numéro de l'étiquette, « 207 ») et `recipe` (nom, sous-titre, adresse, image) ;
 * - recette : `props.pageProps.ssrPayload.recipe`, avec `yields[]` (quantités pour 1 à 6
 *   personnes), `ingredients[]`, `steps[]` (texte en HTML et photos), `utensils[]`, `allergens[]`.
 */
object LecteurHelloFresh {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun lireMenu(html: String, semaine: SemaineIso, luLe: String): MenuSemaine {
        val charge = charge(html) ?: throw PageIllisible("La page du menu ne contient plus ses données.")
        // Le site peut rediriger vers une autre semaine (menu pas encore en ligne, ou passé).
        val affichee = charge.texte("activeWeek")?.let(SemaineIso::lire)
        if (affichee != null && affichee != semaine) {
            throw PageIllisible("Le site montre le menu de la semaine ${affichee.numero}, pas celui de la semaine ${semaine.numero}.")
        }
        val plats = charge["courses"] as? JsonArray ?: throw PageIllisible("Le menu ne contient plus la liste des recettes.")
        val entrees = plats.mapNotNull { plat ->
            val o = plat as? JsonObject ?: return@mapNotNull null
            val numero = o.entier("index")?.takeIf { it > 0 } ?: return@mapNotNull null
            val recette = o["recipe"] as? JsonObject ?: return@mapNotNull null
            val titre = recette.texte("name") ?: return@mapNotNull null
            val url = recette.texte("websiteUrl")?.takeIf(AdressesHelloFresh::estPagePublique) ?: return@mapNotNull null
            EntreeMenu(
                numero = numero,
                titre = propre(titre),
                sousTitre = recette.texte("headline")?.let(::propre)?.takeIf { it.isNotEmpty() },
                url = url,
                image = recette.texte("imagePath")?.takeIf { it.startsWith("/") },
            )
        }.distinctBy { it.numero }.sortedBy { it.numero }
        return MenuSemaine(semaine, entrees, luLe)
    }

    fun lireRecette(html: String, urlFinale: String): Recette {
        val recette = (charge(html)?.get("recipe") as? JsonObject)?.let { lireDonnees(it, urlFinale) }
        return recette ?: lireSchemaOrg(html, urlFinale)
            ?: throw PageIllisible("La page de la recette ne contient plus ses données.")
    }

    private fun lireDonnees(r: JsonObject, urlFinale: String): Recette? {
        val id = r.texte("id") ?: return null
        val titre = r.texte("name") ?: return null
        val ingredients = r.liste("ingredients").mapNotNull { i ->
            Ingredient(
                id = i.texte("id") ?: return@mapNotNull null,
                nom = i.texte("name")?.let(::propre) ?: return@mapNotNull null,
                image = i.texte("imagePath")?.takeIf { it.startsWith("/") },
                livre = (i["shipped"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                allergenes = i.liste("allergens").mapNotNull { it.texte("name")?.let(::propre) },
            )
        }
        val portions = r.liste("yields").mapNotNull { y ->
            Portion(
                personnes = y.entier("yields")?.takeIf { it > 0 } ?: return@mapNotNull null,
                quantites = y.liste("ingredients").mapNotNull { q ->
                    Quantite(
                        ingredient = q.texte("id") ?: return@mapNotNull null,
                        montant = (q["amount"] as? JsonPrimitive)?.doubleOrNull,
                        unite = q.texte("unit")?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }.sortedBy { it.personnes }
        val etapes = r.liste("steps").mapIndexedNotNull { rang, e ->
            val texte = (e.texte("instructionsHTML") ?: e.texte("instructions"))?.let(TexteHtml::enTexte)
            if (texte.isNullOrBlank()) return@mapIndexedNotNull null
            EtapeRecette(
                numero = e.entier("index") ?: (rang + 1),
                texte = texte,
                images = e.liste("images").mapNotNull { im ->
                    val chemin = (im.texte("path") ?: im.texte("link"))?.takeIf { it.startsWith("/") } ?: return@mapNotNull null
                    ImageEtape(chemin, im.texte("caption")?.let(::propre)?.takeIf { it.isNotBlank() })
                },
            )
        }.sortedBy { it.numero }
        return Recette(
            id = id,
            titre = propre(titre),
            sousTitre = r.texte("headline")?.let(::propre)?.takeIf { it.isNotEmpty() },
            description = (r.texte("descriptionMarkdown") ?: r.texte("description"))?.let(TexteHtml::enTexte)?.takeIf { it.isNotBlank() },
            url = r.texte("websiteUrl")?.takeIf(AdressesHelloFresh::estPagePublique) ?: urlFinale,
            image = r.texte("imagePath")?.takeIf { it.startsWith("/") },
            dureeTotaleMin = r.texte("totalTime")?.let(::minutesIso),
            dureePreparationMin = r.texte("prepTime")?.let(::minutesIso),
            difficulte = r.entier("difficulty"),
            ingredients = ingredients,
            portions = portions,
            etapes = etapes,
            ustensiles = r.liste("utensils").mapNotNull { it.texte("name")?.let(::propre) },
            allergenes = r.liste("allergens").mapNotNull { it.texte("name")?.let(::propre) },
        )
    }

    /**
     * Le bloc `schema.org/Recipe`, en secours : moins riche (une seule portion, ingrédients en
     * texte libre), mais fait pour durer, puisque les moteurs de recherche le lisent.
     */
    private fun lireSchemaOrg(html: String, urlFinale: String): Recette? {
        val recette = blocsJsonLd(html).firstNotNullOfOrNull { bloc ->
            val candidats = (bloc as? JsonArray)?.toList() ?: listOf(bloc)
            candidats.filterIsInstance<JsonObject>().firstOrNull { it.texte("@type") == "Recipe" }
        } ?: return null
        val titre = recette.texte("name") ?: return null
        val personnes = (recette["recipeYield"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()
        val ingredients = (recette["recipeIngredient"] as? JsonArray).orEmpty().mapIndexedNotNull { rang, i ->
            (i as? JsonPrimitive)?.contentOrNull?.let(::propre)?.let { Ingredient(id = "i$rang", nom = it) }
        }
        val etapes = (recette["recipeInstructions"] as? JsonArray).orEmpty().mapIndexedNotNull { rang, e ->
            val texte = when (e) {
                is JsonPrimitive -> e.contentOrNull
                is JsonObject -> e.texte("text")
                else -> null
            }?.let(TexteHtml::enTexte)?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            EtapeRecette(rang + 1, texte)
        }
        return Recette(
            id = recette.texte("@id") ?: urlFinale,
            titre = propre(titre),
            description = recette.texte("description")?.let(TexteHtml::enTexte),
            url = urlFinale,
            dureeTotaleMin = recette.texte("totalTime")?.let(::minutesIso),
            ingredients = ingredients,
            portions = if (personnes != null) listOf(Portion(personnes, emptyList())) else emptyList(),
            etapes = etapes,
        )
    }

    /** `props.pageProps.ssrPayload` du bloc `__NEXT_DATA__`, ou `null` s'il n'y est plus. */
    private fun charge(html: String): JsonObject? {
        val texte = contenuScript(html, "id=\"__NEXT_DATA__\"") ?: return null
        val racine = runCatching { json.parseToJsonElement(texte) }.getOrNull() as? JsonObject ?: return null
        val props = (racine["props"] as? JsonObject)?.get("pageProps") as? JsonObject ?: return null
        return props["ssrPayload"] as? JsonObject ?: props
    }

    private fun blocsJsonLd(html: String): List<JsonElement> {
        val blocs = mutableListOf<JsonElement>()
        var depart = 0
        while (true) {
            val balise = html.indexOf("application/ld+json", depart)
            if (balise < 0) break
            val texte = contenuScript(html.substring(maxOf(0, html.lastIndexOf("<script", balise))), "application/ld+json")
            texte?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }?.let(blocs::add)
            depart = balise + 1
        }
        return blocs
    }

    /** Le contenu du premier `<script …marque…>` de [html]. */
    private fun contenuScript(html: String, marque: String): String? {
        val position = html.indexOf(marque)
        if (position < 0) return null
        val debut = html.indexOf('>', position).takeIf { it >= 0 } ?: return null
        val fin = html.indexOf("</script>", debut).takeIf { it >= 0 } ?: return null
        return html.substring(debut + 1, fin)
    }

    /** Un nom ou un titre : entités décodées (la page en laisse parfois), espaces nettoyées. */
    private fun propre(texte: String): String = TexteHtml.decoderEntites(texte).replace('\u00A0', ' ').trim()

    /** « PT1H20M » → 80 ; `null` si la durée est illisible. */
    internal fun minutesIso(duree: String): Int? {
        if (!duree.startsWith("PT")) return null
        var minutes = 0
        var nombre = ""
        for (c in duree.drop(2)) {
            when {
                c.isDigit() -> nombre += c
                c == 'H' -> { minutes += (nombre.toIntOrNull() ?: return null) * 60; nombre = "" }
                c == 'M' -> { minutes += nombre.toIntOrNull() ?: return null; nombre = "" }
                c == 'S' -> nombre = ""
                else -> return null
            }
        }
        return minutes.takeIf { it > 0 }
    }

    private fun JsonObject.texte(cle: String): String? = (this[cle] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.entier(cle: String): Int? = (this[cle] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.liste(cle: String): List<JsonObject> = (this[cle] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
