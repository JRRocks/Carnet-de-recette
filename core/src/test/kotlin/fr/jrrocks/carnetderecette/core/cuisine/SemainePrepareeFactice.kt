package fr.jrrocks.carnetderecette.core.cuisine

/*
 * Une semaine préparée **factice**, telle que Cowork l'écrirait : recette, tâches et segments
 * inventés (CLAUDE.md, règle 1 : aucun contenu HelloFresh dans le dépôt).
 *
 * Le plan à deux attendu, minute par minute (chacun prend la première tâche prête de son rôle) :
 *
 *   découpe  t2 0-4   t3 4-6   t4 6-8   t8 8-11
 *   chauffe  t1 0-1            t5 6-12           t6 12-13   (t7 four 13-27)   t9 27-29
 *
 * Seul : t1 0-1, t2 1-5, t3 5-7, t4 7-9, t5 9-15, t6 15-16, (t7 four 16-30), t8 16-19, t9 30-32.
 */
object SemainePrepareeFactice {

    val json = """
        {
          "type": "semaine",
          "version": 1,
          "semaine": "2026-W44",
          "statut": "ok",
          "message": null,
          "recettes": [
            {
              "numero": 207,
              "personnes": 2,
              "a_confirmer": false,
              "confirmation": "Le numéro et le 2 se lisent nettement sur l'étiquette.",
              "source": { "type": "hellofresh", "id": "poulet-fictif", "url": "https://www.hellofresh.fr/recipes/poulet-fictif" },
              "taches": [
                { "id": "t1", "type": "chauffe", "role": "chauffe", "duree_min": 1, "apres": [], "ingredients": [], "ustensile": "four", "texte": "Préchauffer le four", "etape_origine": 1, "materiel": null },
                { "id": "t2", "type": "decoupe", "role": "decoupe", "duree_min": 4, "apres": [], "ingredients": ["2 filets de poulet"], "ustensile": "planche et couteau", "texte": "Couper le poulet en dés", "etape_origine": 1, "materiel": null },
                { "id": "t3", "type": "decoupe", "role": "decoupe", "duree_min": 2, "apres": ["t2"], "ingredients": ["30 g de farine"], "ustensile": "assiette creuse", "texte": "Fariner le poulet", "etape_origine": 1, "materiel": null },
                { "id": "t4", "type": "decoupe", "role": "decoupe", "duree_min": 2, "apres": [], "ingredients": ["1 oignon nouveau"], "ustensile": "hachoir", "texte": "Hacher l'oignon nouveau", "etape_origine": 3,
                  "materiel": { "outil": "hachoir", "conseil": "Quelques impulsions suffisent.", "avec": [], "mode": null } },
                { "id": "t5", "type": "chauffe", "role": "chauffe", "duree_min": 6, "apres": ["t3"], "ingredients": ["2 cc d'huile d'olive"], "ustensile": "poêle", "texte": "Dorer le poulet", "etape_origine": 2, "materiel": null },
                { "id": "t6", "type": "chauffe", "role": "chauffe", "duree_min": 1, "apres": ["t5", "t4", "t1"], "ingredients": ["½ sachet de miel"], "ustensile": "plaque de four", "texte": "Ajouter miel et oignon, enfourner", "etape_origine": 3, "materiel": null },
                { "id": "t7", "type": "attente", "role": "chauffe", "duree_min": 14, "apres": ["t6"], "ingredients": [], "ustensile": "four", "texte": "Cuisson au four", "etape_origine": 3, "materiel": null },
                { "id": "t8", "type": "decoupe", "role": "decoupe", "duree_min": 3, "apres": ["t3"], "ingredients": [], "ustensile": "évier", "texte": "Laver la planche et ses mains", "etape_origine": 1, "materiel": null },
                { "id": "t9", "type": "dressage", "role": "chauffe", "duree_min": 2, "apres": ["t7"], "ingredients": [], "ustensile": "assiettes", "texte": "Servir", "etape_origine": 3, "materiel": null }
              ],
              "segments_audio": [
                { "id": "a1", "tache": "t2", "role": "decoupe", "texte": "Coupe les deux filets de poulet en gros dés.", "declencheur": "manuel", "minuteur_s": null },
                { "id": "a5", "tache": "t1", "role": "chauffe", "texte": "Préchauffe le four à deux cents degrés.", "declencheur": "manuel", "minuteur_s": null },
                { "id": "a9", "tache": null, "role": "chauffe", "texte": "Attends que la découpe t'apporte le poulet fariné.", "texte_seul": "", "declencheur": "fin_tache_precedente", "minuteur_s": null },
                { "id": "a2", "tache": "t3", "role": "decoupe", "texte": "Roule les dés dans les trente grammes de farine, puis passe-les à la chauffe.", "texte_seul": "Roule les dés dans les trente grammes de farine.", "declencheur": "fin_tache_precedente", "minuteur_s": null },
                { "id": "a3", "tache": "t4", "role": "decoupe", "texte": "Passe l'oignon nouveau au hachoir, quelques impulsions.", "declencheur": "fin_tache_precedente", "minuteur_s": null },
                { "id": "a6", "tache": "t5", "role": "chauffe", "texte": "Fais dorer le poulet six minutes dans un filet d'huile, en le retournant.", "declencheur": "fin_tache_precedente", "minuteur_s": 360 },
                { "id": "a4", "tache": "t8", "role": "decoupe", "texte": "Lave la planche et le couteau, puis lave-toi les mains : le poulet était cru.", "declencheur": "fin_tache_precedente", "minuteur_s": null },
                { "id": "a7", "tache": "t6", "role": "chauffe", "texte": "Ajoute le demi-sachet de miel et l'oignon, puis enfourne quatorze minutes.", "declencheur": "fin_minuteur", "minuteur_s": 840 },
                { "id": "a8", "tache": "t9", "role": "chauffe", "texte": "Sors le plat : le poulet doit être cuit à cœur, sans rose. Sers aussitôt.", "declencheur": "fin_minuteur", "minuteur_s": null }
              ]
            }
          ]
        }
    """.trimIndent()

    /** La recette 207 telle que l'app la lit. */
    val recette: RecettePreparee by lazy {
        (LecteurSemaine.lire(json) as LectureSemaine.Lue).fichier.recette(207)!!
    }
}
