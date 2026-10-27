package fr.jrrocks.carnetderecette.core.hellofresh

/**
 * Des pages **factices** qui reprennent la structure des pages publiques (relevée le
 * 2026-10-27), avec des recettes inventées : aucun contenu HelloFresh dans ce dépôt
 * (CLAUDE.md, règle 1).
 */
object PagesFactices {

    fun page(donnees: String, avant: String = ""): String =
        """<!DOCTYPE html><html lang="fr-FR"><head><meta charSet="utf-8"/>$avant</head><body><div id="__next"></div>""" +
            """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"ssrPayload":$donnees}},"page":"/x"}</script></body></html>"""

    fun menu(semaine: String = "2026-W44", plats: List<Triple<Int, String, String>> = PLATS): String = page(
        """{"activeWeek":"$semaine","currentWeek":"2026-W45","courses":[""" +
            plats.joinToString(",") { (index, nom, slug) ->
                """{"index":$index,"isSoldOut":false,"recipe":{"id":"id$index","name":"$nom","headline":"avec un sous-titre",""" +
                    """"websiteUrl":"https://www.hellofresh.fr/recipes/$slug-id$index","imagePath":"/image/plat-$index.jpg","ingredients":[],"steps":[],"yields":[]}}"""
            } +
            """,{"index":0,"recipe":null}]}""",
    )

    val PLATS = listOf(
        Triple(201, "Gratin factice de courge", "gratin-factice"),
        Triple(202, "Soupe imaginaire aux pois", "soupe-imaginaire"),
        Triple(203, "Poêlée d’essai au sésame", "poelee-essai"),
        Triple(207, "Poulet fictif croustillant", "poulet-fictif"),
        Triple(210, "Risotto d’exemple", "risotto-exemple"),
        Triple(290, "Dessert inventé", "dessert-invente"),
    )

    /** Une recette inventée : 2 et 4 personnes, deux étapes en HTML, une photo d'étape. */
    val RECETTE = page(
        """{"recipe":{"id":"abc123-fr-FR","name":"Poulet fictif croustillant","headline":"et sa sauce d&#39;essai",
            "descriptionMarkdown":"Une recette <strong>inventée</strong> pour les tests.",
            "websiteUrl":"https://www.hellofresh.fr/recipes/poulet-fictif-abc123","imagePath":"/image/poulet-fictif.jpg",
            "totalTime":"PT35M","prepTime":"PT1H5M","difficulty":2,
            "ingredients":[
              {"id":"ing1","name":"Filet de poulet","imagePath":"/ingredient/ing1.png","shipped":true,"allergens":[]},
              {"id":"ing2","name":"Farine","imagePath":"/ingredient/ing2.png","shipped":true,"allergens":[{"name":"Gluten"}]},
              {"id":"ing3","name":"Huile d’olive","imagePath":null,"shipped":false,"allergens":[]}],
            "yields":[
              {"yields":4,"ingredients":[{"id":"ing1","amount":4,"unit":"pièce(s)"},{"id":"ing2","amount":60,"unit":"g"},{"id":"ing3","amount":null,"unit":""}]},
              {"yields":2,"ingredients":[{"id":"ing1","amount":2,"unit":"pièce(s)"},{"id":"ing2","amount":30,"unit":"g"},{"id":"ing3","amount":2,"unit":"cc"}]}],
            "steps":[
              {"index":2,"instructionsHTML":"<ul>\n<li>Fais dorer le <strong>poulet</strong> 5-6 min.&nbsp;</li>\n<li>Ajoute ⅓ de la sauce.</li></ul>","images":[]},
              {"index":1,"instructionsHTML":"<p>Préchauffe le four à 200&nbsp;°C.</p><p>Coupe le poulet.</p>","images":[{"path":"/abc123/step-1.jpg","caption":"Coupe !"}]}],
            "utensils":[{"name":"Poêle"},{"name":"Plaque de four"}],
            "allergens":[{"name":"Gluten"}]}}""",
    )

    /** La même recette, sans bloc de données : seul le bloc schema.org reste. */
    val RECETTE_SCHEMA_ORG =
        """<html><head><script type="application/ld+json">{"@type":"BreadcrumbList"}</script>""" +
            """<script type="application/ld+json">{"@context":"http://schema.org","@type":"Recipe","name":"Soupe imaginaire",""" +
            """"recipeYield":2,"totalTime":"PT20M","recipeIngredient":["1 pièce(s) Oignon","200 g Pois"],""" +
            """"recipeInstructions":[{"@type":"HowToStep","text":"<ul><li>Émince l&#39;oignon.</li></ul>"},{"@type":"HowToStep","text":"Mixe."}]}</script>""" +
            """</head><body></body></html>"""
}
