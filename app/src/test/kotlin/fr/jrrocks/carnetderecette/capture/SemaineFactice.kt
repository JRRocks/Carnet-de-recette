package fr.jrrocks.carnetderecette.capture

import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.cuisine.FichierSemaine
import fr.jrrocks.carnetderecette.core.cuisine.LecteurSemaine
import fr.jrrocks.carnetderecette.core.cuisine.Materiel
import fr.jrrocks.carnetderecette.core.cuisine.Outil
import fr.jrrocks.carnetderecette.core.cuisine.RecettePreparee
import fr.jrrocks.carnetderecette.core.cuisine.RoleCuisine
import fr.jrrocks.carnetderecette.core.cuisine.SegmentPrepare
import fr.jrrocks.carnetderecette.core.cuisine.SourcePreparee
import fr.jrrocks.carnetderecette.core.cuisine.Tache
import fr.jrrocks.carnetderecette.core.cuisine.TypeTache
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

    // ── La semaine préparée par « Claude », factice elle aussi ────────────────────

    private fun tache(
        id: String,
        type: TypeTache,
        role: RoleCuisine,
        minutes: Int,
        texte: String,
        vararg apres: String,
        materiel: Materiel? = null,
    ) = Tache(id = id, type = type, role = role, dureeMin = minutes.toDouble(), apres = apres.toList(), texte = texte, materiel = materiel)

    private fun segment(
        id: String,
        tache: String?,
        role: RoleCuisine,
        texte: String,
        declencheur: Declencheur = Declencheur.FIN_TACHE_PRECEDENTE,
        minuteur: Int? = null,
        seul: String? = null,
    ) = SegmentPrepare(id = id, tache = tache, role = role, texte = texte, texteSeul = seul, declencheur = declencheur, minuteurS = minuteur?.toDouble())

    private val D = RoleCuisine.DECOUPE
    private val C = RoleCuisine.CHAUFFE

    /** La 207 à deux : le plan finit vers 29 minutes ; seul, vers 32. */
    val poulet = RecettePreparee(
        numero = 207,
        personnes = 2,
        confirmation = "Le numéro et le 2 se lisent nettement sur l'étiquette.",
        source = SourcePreparee("hellofresh", "poulet-fictif", "https://www.hellofresh.fr/recipes/poulet-fictif"),
        taches = listOf(
            tache("t1", TypeTache.CHAUFFE, C, 1, "Préchauffer le four"),
            tache("t2", TypeTache.DECOUPE, D, 4, "Couper le poulet en dés"),
            tache("t3", TypeTache.DECOUPE, D, 2, "Fariner le poulet", "t2"),
            tache("t4", TypeTache.DECOUPE, D, 2, "Hacher l'oignon nouveau", materiel = Materiel(Outil.HACHOIR, "Quelques impulsions suffisent.")),
            tache("t5", TypeTache.CHAUFFE, C, 6, "Dorer le poulet", "t3"),
            tache("t6", TypeTache.CHAUFFE, C, 1, "Ajouter miel et oignon, enfourner", "t5", "t4", "t1"),
            tache("t7", TypeTache.ATTENTE, C, 14, "Cuisson au four", "t6"),
            tache("t8", TypeTache.DECOUPE, D, 3, "Laver la planche et ses mains", "t3"),
            tache("t9", TypeTache.DRESSAGE, C, 2, "Servir", "t7"),
        ),
        segments = listOf(
            segment("a1", "t2", D, "Coupe les deux filets de poulet en gros dés.", Declencheur.MANUEL),
            segment("a5", "t1", C, "Préchauffe le four à deux cents degrés.", Declencheur.MANUEL),
            segment("a9", null, C, "Attends que la découpe t'apporte le poulet fariné.", seul = ""),
            segment("a2", "t3", D, "Roule les dés dans les trente grammes de farine, puis passe-les à la chauffe.", seul = "Roule les dés dans les trente grammes de farine."),
            segment("a3", "t4", D, "Passe l'oignon nouveau au hachoir, quelques impulsions."),
            segment("a6", "t5", C, "Fais dorer le poulet six minutes dans un filet d'huile, en le retournant.", minuteur = 360),
            segment("a4", "t8", D, "Lave la planche et le couteau, puis lave-toi les mains\u00A0: le poulet était cru."),
            segment("a7", "t6", C, "Ajoute le demi-sachet de miel et l'oignon, puis enfourne quatorze minutes.", Declencheur.FIN_MINUTEUR, minuteur = 840),
            segment("a8", "t9", C, "Sors le plat\u00A0: le poulet doit être cuit à cœur, sans rose. Sers aussitôt.", Declencheur.FIN_MINUTEUR),
        ),
    )

    /** La 203, dont Claude doute du nombre de personnes. */
    val poelee = RecettePreparee(
        numero = 203,
        personnes = 4,
        aConfirmer = true,
        confirmation = "le 4 est à moitié effacé sur l'étiquette\u00A0; vérifie que ce n'est pas un 2.",
        taches = listOf(
            tache("t1", TypeTache.DECOUPE, D, 4, "Émincer le poivron"),
            tache("t2", TypeTache.DECOUPE, D, 3, "Râper la carotte", materiel = Materiel(Outil.RAPE_ELECTRIQUE, "Le disque fin.")),
            tache("t3", TypeTache.CHAUFFE, C, 2, "Chauffer le wok"),
            tache("t4", TypeTache.CHAUFFE, C, 5, "Saisir les légumes", "t1", "t2", "t3"),
            tache("t5", TypeTache.DRESSAGE, C, 2, "Ajouter le sésame et servir", "t4"),
        ),
        segments = listOf(
            segment("b1", "t1", D, "Émince le poivron en fines lanières.", Declencheur.MANUEL),
            segment("b2", "t2", D, "Passe la carotte à la râpe électrique, puis donne les légumes à la chauffe.", seul = "Passe la carotte à la râpe électrique."),
            segment("b3", "t3", C, "Fais chauffer le wok à feu vif, sans matière grasse.", Declencheur.MANUEL),
            segment("b4", "t4", C, "Saisis les légumes cinq minutes en remuant sans arrêt.", minuteur = 300),
            segment("b5", "t5", C, "Ajoute les graines de sésame, mélange et sers.", Declencheur.FIN_MINUTEUR),
        ),
    )

    /** La semaine telle que Claude l'écrirait : la 207 d'abord, puis la 203. */
    val travail = FichierSemaine(semaine = semaine, recettes = listOf(poulet, poelee))

    /** Le fichier `semaine-2026-S44.json` correspondant. */
    val travailJson: String get() = LecteurSemaine.ecrire(travail)

    /** Le samedi, après l'import ; [cuisinees] : les recettes déjà faites. */
    fun etatImporte(cuisinees: Set<Int> = emptySet()) = etat().copy(travail = travail, cuisinees = cuisinees)
}
