package fr.jrrocks.carnetderecette.core.recette

/**
 * Une recette **factice**, écrite pour le Carnet (aucun contenu HelloFresh dans ce dépôt,
 * CLAUDE.md règle 1) : de quoi essayer la lecture à voix haute avant d'avoir scanné une box,
 * et le support des tests et des captures d'écran.
 *
 * Ses étapes portent exprès les écritures que la voix doit savoir dire (« ½ cc », « 1 cs »,
 * « 150 g », « 50 ml », « 12 à 14 min ») et deux durées : la séance y trouve deux minuteurs.
 */
object RecetteExemple {

    val recette = Recette(
        id = "exemple",
        titre = "Risotto aux poireaux (exemple)",
        url = "",
        dureeTotaleMin = 35,
        dureePreparationMin = 10,
        difficulte = 1,
        ingredients = listOf(
            Ingredient("riz", "Riz rond"),
            Ingredient("poireau", "Poireau"),
            Ingredient("echalote", "Échalote"),
            Ingredient("vin", "Vin blanc"),
            Ingredient("bouillon", "Cube de bouillon de légumes"),
            Ingredient("parmesan", "Parmesan"),
            Ingredient("citron", "Citron"),
            Ingredient("huile", "Huile d'olive", livre = false),
            Ingredient("beurre", "Beurre", livre = false),
        ),
        portions = listOf(
            Portion(
                personnes = 2,
                quantites = listOf(
                    Quantite("riz", 150.0, "g"),
                    Quantite("poireau", 1.0),
                    Quantite("echalote", 1.0),
                    Quantite("vin", 50.0, "ml"),
                    Quantite("bouillon", 1.0),
                    Quantite("parmesan", 30.0, "g"),
                    Quantite("citron", 0.5),
                    Quantite("huile", 1.0, "cs"),
                    Quantite("beurre", 10.0, "g"),
                ),
            ),
        ),
        etapes = listOf(
            EtapeRecette(1, "Fais chauffer 600 ml d'eau avec le cube de bouillon. Émince le poireau et l'échalote, râpe le parmesan."),
            EtapeRecette(2, "Dans une sauteuse, fais chauffer 1 cs d'huile d'olive à feu moyen. Ajoute le poireau et l'échalote et fais-les revenir 5 min."),
            EtapeRecette(3, "Ajoute 150 g de riz et remue jusqu'à ce que les grains deviennent translucides. Verse 50 ml de vin blanc et laisse-le s'évaporer."),
            EtapeRecette(4, "Verse le bouillon chaud louche par louche, en attendant qu'il soit absorbé avant d'en ajouter. Laisse cuire 12 à 14 min en remuant souvent."),
            EtapeRecette(5, "Hors du feu, ajoute le parmesan, 10 g de beurre et ½ cc de zeste de citron. Couvre et laisse reposer."),
            EtapeRecette(6, "Goûte, sale, poivre et sers aussitôt dans des assiettes chaudes."),
        ),
        ustensiles = listOf("Sauteuse", "Casserole", "Louche", "Râpe"),
    )
}
