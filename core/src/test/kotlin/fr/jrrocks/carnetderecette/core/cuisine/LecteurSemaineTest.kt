package fr.jrrocks.carnetderecette.core.cuisine

import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LecteurSemaineTest {

    private val s44 = SemaineIso(2026, 44)

    private fun lue(texte: String, attendue: SemaineIso? = s44): LectureSemaine.Lue {
        val lecture = LecteurSemaine.lire(texte, attendue)
        assertTrue("lecture refusée : $lecture", lecture is LectureSemaine.Lue)
        return lecture as LectureSemaine.Lue
    }

    private fun motif(texte: String, attendue: SemaineIso? = s44): String {
        val lecture = LecteurSemaine.lire(texte, attendue)
        assertTrue("lecture acceptée : $lecture", lecture is LectureSemaine.Refusee)
        return (lecture as LectureSemaine.Refusee).motif
    }

    /** Un fichier minimal autour d'une recette écrite à la main. */
    private fun avecRecette(recette: String, entete: String = "\"type\": \"semaine\", \"version\": 1, \"semaine\": \"2026-W44\"") =
        "{ $entete, \"recettes\": [ $recette ] }"

    @Test
    fun `le fichier de Cowork se lit en entier`() {
        val lecture = lue(SemainePrepareeFactice.json)
        assertEquals(emptyList<String>(), lecture.remarques)
        val fichier = lecture.fichier
        assertEquals(s44, fichier.semaine)
        assertEquals(StatutPreparation.OK, fichier.statut)
        val recette = fichier.recette(207)!!
        assertEquals(2, recette.personnes)
        assertFalse(recette.aConfirmer)
        assertEquals("poulet-fictif", recette.source?.id)
        assertEquals(9, recette.taches.size)
        assertEquals(9, recette.segments.size)

        val hacher = recette.tache("t4")!!
        assertEquals(TypeTache.DECOUPE, hacher.sorte)
        assertEquals(RoleCuisine.DECOUPE, hacher.qui)
        assertEquals(Outil.HACHOIR, hacher.materiel?.outil)
        assertEquals(listOf("t5", "t4", "t1"), recette.tache("t6")!!.apres)
        assertTrue(recette.tache("t7")!!.passive)

        val sortir = recette.segments.first { it.id == "a8" }
        assertEquals(Declencheur.FIN_MINUTEUR, sortir.declencheur)
        assertEquals(840, recette.segments.first { it.id == "a7" }.minuteurSecondes)
        assertEquals("", recette.segments.first { it.id == "a9" }.texteSeul)
        assertNull(recette.segments.first { it.id == "a1" }.texteSeul)
    }

    @Test
    fun `l'exemple du skill de Cowork se lit tel quel`() {
        // Le skill et l'app changent de forme ensemble (cowork/README.md) : son exemple doit se lire.
        val skill = File("../cowork/carnet-semaine/SKILL.md").readText()
        val debut = skill.indexOf("```json\n{\n  \"type\": \"semaine\"")
        assertTrue("exemple introuvable dans le skill", debut >= 0)
        val exemple = skill.substring(debut + "```json\n".length, skill.indexOf("```", debut + 7))
        val lecture = lue(exemple)
        assertEquals(emptyList<String>(), lecture.remarques)
        val recette = lecture.fichier.recette(207)!!
        assertEquals(listOf("t1", "t2"), recette.taches.map { it.id })
        assertEquals(Outil.HACHOIR, recette.tache("t2")!!.materiel?.outil)
        assertEquals("Coupe les deux filets de poulet en gros dés.", recette.segments.single().texteSeul)
    }

    @Test
    fun `ce qui s'ecrit se relit`() {
        val fichier = lue(SemainePrepareeFactice.json).fichier
        assertEquals(fichier, lue(LecteurSemaine.ecrire(fichier)).fichier)
    }

    @Test
    fun `un autre fichier est refuse avec ce qu'il faut choisir`() {
        assertTrue(motif("pas du JSON").startsWith("Ce fichier n'est pas une semaine préparée par Claude"))
        assertTrue(motif("[1, 2]").startsWith("Ce fichier n'est pas une semaine"))
        assertTrue(motif("{\"type\": \"profil\"}").startsWith("Ce fichier n'est pas une semaine"))
        // Le fichier des recettes, déposé par l'app à côté : l'erreur la plus probable du samedi.
        assertTrue(motif("{\"type\": \"recettes\", \"version\": 1, \"semaine\": \"2026-W44\"}").startsWith("C'est le fichier des recettes déposé par l'app"))
        assertTrue(motif("x".repeat(LecteurSemaine.TAILLE_MAX + 1)).startsWith("Ce fichier n'est pas une semaine"))
    }

    @Test
    fun `une version plus recente demande une mise a jour`() {
        val texte = SemainePrepareeFactice.json.replace("\"version\": 1", "\"version\": 2")
        assertTrue(motif(texte).contains("mets l'app à jour"))
    }

    @Test
    fun `la semaine doit etre celle qu'on cuisine`() {
        val texte = SemainePrepareeFactice.json.replace("2026-W44", "2026-W43")
        assertEquals(
            "Ce fichier prépare la semaine 43, et la semaine en cours est la semaine 44\u00A0: choisis «\u00A0semaine-2026-S44.json\u00A0».",
            motif(texte),
        )
        assertTrue(motif(SemainePrepareeFactice.json.replace("2026-W44", "2025-W44")).contains("la semaine 44 de 2025"))
        assertTrue(motif(SemainePrepareeFactice.json.replace("\"semaine\": \"2026-W44\",", "")).contains("ne dit pas de quelle semaine"))
        // Sans semaine attendue (relecture du fichier déjà importé), toute semaine se lit.
        assertEquals(SemaineIso(2026, 43), lue(texte, attendue = null).fichier.semaine)
        // La forme des noms de fichiers, « 2026-S44 », se lit aussi.
        assertEquals(s44, lue(SemainePrepareeFactice.json.replace("2026-W44", "2026-S44")).fichier.semaine)
    }

    @Test
    fun `un fichier sans recette n'est accepte qu'en mode degrade`() {
        assertTrue(motif("{\"type\": \"semaine\", \"version\": 1, \"semaine\": \"2026-W44\", \"recettes\": []}").contains("aucune recette"))
        val degrade = lue("{\"type\": \"semaine\", \"version\": 1, \"semaine\": \"2026-W44\", \"statut\": \"degrade\", \"message\": \" Accès au site rompu. \", \"recettes\": []}")
        assertEquals(StatutPreparation.DEGRADE, degrade.fichier.statut)
        assertEquals("Accès au site rompu.", degrade.fichier.message)
    }

    @Test
    fun `une recette illisible est laissee de cote, les autres restent`() {
        val texte = avecRecette(
            """
            { "personnes": 2, "taches": [] },
            "pas une recette",
            { "numero": 224, "personnes": 4, "taches": [ { "id": "t1", "texte": "Râper le fromage", "type": "decoupe" } ], "segments_audio": [] },
            { "numero": 224, "personnes": 2, "taches": [], "segments_audio": [] }
            """,
        )
        val lecture = lue(texte)
        assertEquals(listOf(224), lecture.fichier.recettes.map { it.numero })
        assertEquals(4, lecture.fichier.recette(224)!!.personnes)
        assertEquals(
            listOf(
                "Une recette sans numéro est laissée de côté.",
                "Une recette sans numéro est laissée de côté.",
                "La recette 224 apparaît deux fois\u00A0: seule la première est gardée.",
            ),
            lecture.remarques,
        )
        assertTrue(motif(avecRecette("{ \"personnes\": 2 }")).startsWith("Aucune recette de ce fichier ne se lit"))
    }

    @Test
    fun `une tache ou un segment abime n'emporte pas la recette`() {
        val texte = avecRecette(
            """
            { "numero": 207, "personnes": "quatre", "a_confirmer": "oui",
              "taches": [
                { "id": "t1", "texte": "Couper", "type": "decoupe" },
                { "id": "t2", "texte": "Cuire", "apres": { "pas": "une liste" } }
              ],
              "segments_audio": [
                { "id": "a1", "tache": "t1", "texte": "Coupe.", "declencheur": "manuel" },
                { "id": "a2", "texte": ["pas", "un texte"] }
              ] }
            """,
        )
        val lecture = lue(texte)
        val recette = lecture.fichier.recette(207)!!
        assertEquals(listOf("t1"), recette.taches.map { it.id })
        assertEquals(listOf("a1"), recette.segments.map { it.id })
        // Un nombre de personnes illisible reste inconnu ; le doute illisible, levé.
        assertNull(recette.personnes)
        assertFalse(recette.aConfirmer)
        assertEquals(listOf("Recette 207\u00A0: 2 éléments illisibles laissés de côté."), lecture.remarques)
    }

    @Test
    fun `les valeurs inconnues reprennent leur valeur par defaut`() {
        val texte = avecRecette(
            """
            { "numero": "207", "personnes": 4.0, "champ_futur": { "x": 1 },
              "taches": [
                { "id": "t1", "texte": "Peler", "type": "épluchage", "role": "découpe", "duree_min": "5", "materiel": { "outil": "mandoline" } },
                { "id": "t2", "texte": "Cuire", "type": "chauffe", "role": "commis", "duree_min": 2.5 }
              ],
              "segments_audio": [
                { "id": "a1", "tache": "t1", "texte": "Pèle.", "declencheur": "quand_tu_veux", "minuteur_s": 600.0 }
              ] }
            """,
            entete = "\"type\": \"semaine\", \"version\": \"1\", \"semaine\": \"2026-W44\", \"statut\": \"en_cours\"",
        )
        val fichier = lue(texte).fichier
        assertEquals(StatutPreparation.OK, fichier.statut)
        val recette = fichier.recette(207)!!
        assertEquals(4, recette.personnes)
        val peler = recette.tache("t1")!!
        // Type inconnu : déduit du rôle ; outil inconnu : aucun.
        assertNull(peler.type)
        assertEquals(TypeTache.DECOUPE, peler.sorte)
        assertEquals(RoleCuisine.DECOUPE, peler.qui)
        assertEquals(5, peler.minutes)
        assertNull(peler.materiel?.outil)
        // Rôle inconnu : déduit du type ; une durée de 2,5 minutes en prend 3.
        val cuire = recette.tache("t2")!!
        assertEquals(RoleCuisine.CHAUFFE, cuire.qui)
        assertEquals(3, cuire.minutes)
        val segment = recette.segments.single()
        assertEquals(Declencheur.MANUEL, segment.declencheur)
        assertEquals(600, segment.minuteurSecondes)
    }

    @Test
    fun `la recette est remise d'aplomb pour le plan et la seance`() {
        val texte = avecRecette(
            """
            { "numero": 207, "personnes": 99,
              "taches": [
                { "id": "t1", "texte": "Couper", "type": "decoupe", "apres": ["t1", "t9", " t2 ", "t2"] },
                { "id": "t2", "texte": "  ", "type": "chauffe" },
                { "id": "t1", "texte": "Couper encore", "type": "decoupe" },
                { "texte": "Sans identifiant", "type": "dressage", "duree_min": 0 }
              ],
              "segments_audio": [
                { "id": "a1", "tache": "t1", "texte": " Coupe. " },
                { "id": "a1", "tache": "t2", "texte": "Chauffe." },
                { "id": "a2", "tache": "inconnue", "texte": "Sans tâche connue." },
                { "id": "a3", "tache": "t1", "texte": "   " },
                { "tache": "t1", "texte": "Sans identifiant.", "role": "chauffe" }
              ] }
            """,
        )
        val recette = lue(texte).fichier.recette(207)!!
        assertNull("99 personnes : une faute, pas une box", recette.personnes)
        // Des identifiants uniques ; un doublon garde son nom avec un suffixe.
        assertEquals(listOf("t1", "t2", "t1~2", "t-4"), recette.taches.map { it.id })
        // Les dépendances : vers des tâches connues, sans elle-même ni doublon.
        assertEquals(listOf("t2"), recette.tache("t1")!!.apres)
        assertEquals("Tâche 2", recette.tache("t2")!!.texte)
        assertEquals(1, recette.tache("t-4")!!.minutes)
        // Un segment vide disparaît ; les autres ont un identifiant unique et un rôle.
        assertEquals(listOf("a1", "a1~2", "a2", "a-4"), recette.segments.map { it.id })
        assertEquals("Coupe.", recette.segments[0].texte)
        assertEquals(RoleCuisine.DECOUPE, recette.segments[0].role)
        assertEquals(RoleCuisine.CHAUFFE, recette.segments[1].role)
        // Une tâche inconnue : le segment reste, sans tâche, lu par la chauffe.
        assertNull(recette.segments[2].tache)
        assertEquals(RoleCuisine.CHAUFFE, recette.segments[2].role)
        // Le rôle écrit l'emporte sur celui de la tâche.
        assertEquals(RoleCuisine.CHAUFFE, recette.segments[3].role)
    }

    @Test
    fun `un fichier aberrant est ramene a la taille d'une semaine`() {
        val taches = (1..1_000).joinToString(",") { """{ "id": "t$it", "texte": "Tâche $it", "apres": ["t${it - 1}"] }""" }
        val segments = (1..1_000).joinToString(",") { """{ "id": "a$it", "texte": "Segment $it." }""" }
        val recette = """{ "numero": 1, "taches": [ $taches ], "segments_audio": [ $segments ] }"""
        val autres = (2..60).joinToString(",") { """{ "numero": $it, "taches": [ { "id": "t1", "texte": "Couper" } ] }""" }
        val lecture = lue(avecRecette("$recette, $autres"))
        assertEquals(LecteurSemaine.RECETTES_MAX, lecture.fichier.recettes.size)
        val longue = lecture.fichier.recette(1)!!
        assertEquals(LecteurSemaine.TACHES_MAX, longue.taches.size)
        assertEquals(LecteurSemaine.SEGMENTS_MAX, longue.segments.size)
        assertEquals(2, lecture.remarques.size)
        // Et son plan se calcule en un instant.
        val debut = System.nanoTime()
        assertEquals(LecteurSemaine.TACHES_MAX, Planificateur.planifier(longue, ModeCuisine.A_DEUX).duree)
        assertTrue((System.nanoTime() - debut) / 1_000_000 < 500)
    }

    @Test
    fun `le nom des fichiers de la semaine`() {
        assertEquals("semaine-2026-S44.json", s44.nomDeFichier("semaine"))
        assertEquals("recettes-2027-S01.json", SemaineIso(2027, 1).nomDeFichier("recettes"))
    }
}
