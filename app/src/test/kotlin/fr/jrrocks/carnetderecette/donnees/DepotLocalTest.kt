package fr.jrrocks.carnetderecette.donnees

import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.core.semaine.StatutSemaine
import fr.jrrocks.carnetderecette.net.PageRecue
import fr.jrrocks.carnetderecette.net.RefusSite
import java.io.File
import java.net.UnknownHostException
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le dépôt local, avec un faux site qui sert des pages **factices** (même structure que les
 * pages publiques, recettes inventées) : sans réseau ni téléphone.
 */
class DepotLocalTest {

    private val semaine = SemaineIso(2026, 44)
    private val racine: File = Files.createTempDirectory("carnet").toFile()
    private val pages = mutableMapOf<String, () -> PageRecue>()
    private val telechargees = mutableListOf<String>()

    private fun depot() = DepotLocal(
        racine,
        lirePage = { adresse -> (pages[adresse] ?: throw RefusSite(404, "absente : $adresse"))() },
        telecharger = { adresse, cible -> telechargees += adresse; cible.parentFile?.mkdirs(); cible.writeText("image") },
        maintenant = { Instant.parse("2026-10-27T08:00:00Z") },
        // Les images partent sur le même fil que le test : tout est fini à la sortie de ajouterBox.
        fond = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun page(donnees: String) =
        """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"ssrPayload":$donnees}}}</script></html>"""

    private val menu = page(
        """{"activeWeek":"2026-W44","courses":[""" + (201..206).joinToString(",") { n ->
            """{"index":$n,"recipe":{"name":"Plat factice $n","websiteUrl":"https://www.hellofresh.fr/recipes/plat-$n","imagePath":"/image/plat-$n.jpg"}}"""
        } + "]}",
    )

    private val recette203 = page(
        """{"recipe":{"id":"r203","name":"Plat factice 203","imagePath":"/image/plat-203.jpg",
           "ingredients":[{"id":"i1","name":"Courge","imagePath":"/ingredient/i1.png","shipped":true}],
           "yields":[{"yields":2,"ingredients":[{"id":"i1","amount":1,"unit":"pièce(s)"}]}],
           "steps":[{"index":1,"instructionsHTML":"<p>Coupe la courge.</p>","images":[{"path":"/r203/step-1.jpg"}]}]}}""",
    )

    private fun box(numero: Int, personnes: Int = 2) = BoxScannee(numero = numero, personnes = personnes, scanneLe = "2026-10-27T08:00:00Z")

    @Test
    fun `menu lu une fois, garde sur le telephone, puis box, recette et images`() = runBlocking {
        pages["https://www.hellofresh.fr/menus/2026-W44"] = { PageRecue(menu, "https://www.hellofresh.fr/menus/2026-W44") }
        // La page de la recette redirige : on garde l'adresse finale.
        pages["https://www.hellofresh.fr/recipes/plat-203"] = { PageRecue(recette203, "https://www.hellofresh.fr/recipes/plat-203-v2") }
        val depot = depot()

        val resultatMenu = depot.chargerMenu(semaine)
        assertTrue(resultatMenu is ResultatMenu.Lu)
        assertTrue(File(racine, "semaines/2026-W44/menu.json").exists())

        val lue = depot.ajouterBox(semaine, box(203, personnes = 2))
        assertEquals("r203", (lue as ResultatRecette.Lue).recette.id)
        val etat = depot.etat(semaine).value
        assertEquals("Plat factice 203", etat.recette(203)!!.titre)
        assertEquals("r203", etat.fichier.box(203)!!.recetteId)
        assertEquals(emptySet<Int>(), etat.enLecture)

        // Les trois sortes d'images, chacune à sa largeur.
        assertEquals(3, telechargees.size)
        assertTrue(telechargees.any { "w_1200" in it && it.endsWith("/image/plat-203.jpg") })
        assertTrue(telechargees.any { "w_800" in it && it.endsWith("/r203/step-1.jpg") })
        assertTrue(telechargees.any { "w_200" in it && it.endsWith("/ingredient/i1.png") })
        assertNotNull(depot.imageLocale("/r203/step-1.jpg"))
        assertNull(depot.imageLocale("/image/absente.jpg"))

        // Un nouveau dépôt (nouveau lancement) relit tout depuis le téléphone, sans le site.
        pages.clear()
        val relu = depot()
        assertEquals("Plat factice 203", relu.etat(semaine).value.recette(203)!!.titre)
        assertTrue(relu.chargerMenu(semaine) is ResultatMenu.Lu)
    }

    @Test
    fun `numero hors menu, pas de reseau, et page changee`() = runBlocking {
        pages["https://www.hellofresh.fr/menus/2026-W44"] = { PageRecue(menu, "u") }
        pages["https://www.hellofresh.fr/recipes/plat-204"] = { throw UnknownHostException("www.hellofresh.fr") }
        pages["https://www.hellofresh.fr/recipes/plat-205"] = { PageRecue("<html>maintenance</html>", "u") }
        val depot = depot()

        assertEquals(ResultatRecette.HorsMenu, depot.ajouterBox(semaine, box(999)))

        val horsLigne = depot.ajouterBox(semaine, box(204))
        assertTrue(horsLigne is ResultatRecette.HorsLigne)
        // La box est gardée : on pourra relire la page plus tard.
        assertNotNull(depot.etat(semaine).value.fichier.box(204))
        assertEquals(StatutSemaine.OK, depot.etat(semaine).value.fichier.statut)

        val rompue = depot.ajouterBox(semaine, box(205))
        assertTrue(rompue is ResultatRecette.AccesRompu)
        val etat = depot.etat(semaine).value
        assertEquals(StatutSemaine.DEGRADE, etat.fichier.statut)
        assertNotNull(etat.accesRompu)
    }

    @Test
    fun `menu absent du site et sonde`() = runBlocking {
        val depot = depot()
        assertTrue(depot.chargerMenu(semaine) is ResultatMenu.AccesRompu)
        assertEquals(StatutSemaine.DEGRADE, depot.etat(semaine).value.fichier.statut)

        pages["https://www.hellofresh.fr/menus/2026-W44"] = { throw UnknownHostException("x") }
        assertTrue(depot.chargerMenu(semaine, forcer = true) is ResultatMenu.HorsLigne)

        // Le site répond, mais le menu n'a plus que deux recettes : la sonde refuse.
        pages["https://www.hellofresh.fr/menus/2026-W44"] = {
            PageRecue(page("""{"activeWeek":"2026-W44","courses":[{"index":201,"recipe":{"name":"a","websiteUrl":"https://www.hellofresh.fr/recipes/a"}}]}"""), "u")
        }
        assertTrue(depot.chargerMenu(semaine, forcer = true) is ResultatMenu.AccesRompu)
    }

    @Test
    fun `corriger, retirer et fiches`() = runBlocking {
        pages["https://www.hellofresh.fr/menus/2026-W44"] = { PageRecue(menu, "u") }
        pages["https://www.hellofresh.fr/recipes/plat-203"] = { PageRecue(recette203, "u") }
        val depot = depot()
        depot.ajouterBox(semaine, box(203).copy(aConfirmer = true))
        depot.corrigerPersonnes(semaine, 203, 4)
        val corrigee = depot.etat(semaine).value.fichier.box(203)!!
        assertEquals(4, corrigee.personnes)
        assertEquals(false, corrigee.aConfirmer)

        depot.retirerBox(semaine, 203)
        val apres = depot.etat(semaine).value.fichier
        assertNull(apres.box(203))
        assertTrue("la recette sans box part aussi", apres.recettes.isEmpty())

        val fiche = fr.jrrocks.carnetderecette.core.fiche.FicheScannee(numero = 206, pages = emptyList(), prisLe = "t1")
        depot.ajouterFiche(semaine, fiche)
        depot.ajouterFiche(semaine, fiche.copy(prisLe = "t2"))
        assertEquals(listOf("t2"), depot.etat(semaine).value.fichier.fiches.map { it.prisLe })
        assertTrue(depot.dossierPhotos(semaine).isDirectory)
    }
}
