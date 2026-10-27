package fr.jrrocks.carnetderecette.capture

import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.core.recette.EtapeRecette
import fr.jrrocks.carnetderecette.core.recette.Ingredient
import fr.jrrocks.carnetderecette.core.recette.MenuSemaine
import fr.jrrocks.carnetderecette.core.recette.Portion
import fr.jrrocks.carnetderecette.core.recette.Quantite
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.core.semaine.FichierRecettes
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.donnees.EtatSemaine

/** Une semaine **factice** pour les captures : menu, box et recette inventés (CLAUDE.md, règle 1). */
object SemaineFactice {

    val semaine = SemaineIso(2026, 44)

    val menu = MenuSemaine(
        semaine,
        listOf(
            EntreeMenu(201, "Gratin factice de courge", "et sa chapelure d'essai", "https://www.hellofresh.fr/recipes/gratin-factice"),
            EntreeMenu(203, "Poêlée d'essai au sésame", null, "https://www.hellofresh.fr/recipes/poelee-essai"),
            EntreeMenu(207, "Poulet fictif croustillant", "sauce imaginaire au miel", "https://www.hellofresh.fr/recipes/poulet-fictif"),
        ),
        "2026-10-27T08:00:00Z",
    )

    val recette = Recette(
        id = "poulet-fictif",
        titre = "Poulet fictif croustillant",
        sousTitre = "sauce imaginaire au miel",
        url = "https://www.hellofresh.fr/recipes/poulet-fictif",
        dureeTotaleMin = 35,
        difficulte = 2,
        ingredients = listOf(
            Ingredient("i1", "Filet de poulet"),
            Ingredient("i2", "Farine"),
            Ingredient("i3", "Miel"),
            Ingredient("i4", "Oignon nouveau"),
            Ingredient("i5", "Huile d'olive", livre = false),
            Ingredient("i6", "Sel et poivre", livre = false),
        ),
        portions = listOf(
            Portion(2, listOf(Quantite("i1", 2.0, "pièce(s)"), Quantite("i2", 30.0, "g"), Quantite("i3", 0.5, "sachet(s)"), Quantite("i4", 1.0, "pièce(s)"), Quantite("i5", 2.0, "cc"), Quantite("i6", null, "selon ton goût"))),
            Portion(4, listOf(Quantite("i1", 4.0, "pièce(s)"), Quantite("i2", 60.0, "g"), Quantite("i3", 1.0, "sachet(s)"), Quantite("i4", 2.0, "pièce(s)"), Quantite("i5", 4.0, "cc"), Quantite("i6", null, "selon ton goût"))),
        ),
        etapes = listOf(
            EtapeRecette(1, "Préchauffe le four à 200 °C.\nCoupe le poulet en morceaux et roule-les dans la farine."),
            EtapeRecette(2, "Fais dorer le poulet 5 à 6 min dans un filet d'huile."),
            EtapeRecette(3, "Ajoute le miel et l'oignon émincé, puis enfourne 12 à 14 min."),
        ),
        ustensiles = listOf("Poêle", "Plaque de four"),
        allergenes = listOf("Gluten"),
    )

    val boxes = listOf(
        BoxScannee(207, 2, recetteId = recette.id, scanneLe = "2026-10-27T08:00:00Z"),
        BoxScannee(203, 4, aConfirmer = true, scanneLe = "2026-10-27T08:01:00Z"),
    )

    fun etat(avecBoxes: Boolean = true) = EtatSemaine(
        semaine,
        FichierRecettes(semaine = semaine, boxes = if (avecBoxes) boxes else emptyList(), recettes = if (avecBoxes) listOf(recette) else emptyList()),
        menu,
    )
}
