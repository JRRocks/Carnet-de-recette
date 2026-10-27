package fr.jrrocks.carnetderecette.donnees

import fr.jrrocks.carnetderecette.core.fiche.FicheScannee
import fr.jrrocks.carnetderecette.core.hellofresh.PageIllisible
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.core.semaine.StatutSemaine
import fr.jrrocks.carnetderecette.net.PageRecue
import fr.jrrocks.carnetderecette.net.RefusSite
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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

    /** Le réseau tombe pendant les images (on s'éloigne du Wi-Fi) : chacune échoue. */
    private var imagesEnPanne = false

    private fun depot(
        lirePage: suspend (String) -> PageRecue = { adresse -> (pages[adresse] ?: throw RefusSite(404, "absente : $adresse"))() },
    ) = DepotLocal(
        racine,
        lirePage = lirePage,
        telecharger = { adresse, cible ->
            telechargees += adresse
            if (imagesEnPanne) throw SocketTimeoutException("coupé")
            cible.parentFile?.mkdirs()
            cible.writeText("image")
        },
        maintenant = { Instant.parse("2026-10-27T08:00:00Z") },
        // Les images partent sur le même fil que le test : tout est fini à la sortie de ajouterBox.
        fond = CoroutineScope(Dispatchers.Unconfined),
    )

    private val MENU = "https://www.hellofresh.fr/menus/2026-W44"
    private fun url(numero: Int) = "https://www.hellofresh.fr/recipes/plat-$numero"

    /** Une recette factice complète pour la box [numero]. */
    private fun recette(numero: Int) = page(
        """{"recipe":{"id":"r$numero","name":"Plat factice $numero",
           "ingredients":[{"id":"i1","name":"Courge","shipped":true}],
           "yields":[{"yields":2,"ingredients":[{"id":"i1","amount":1,"unit":"pièce(s)"}]}],
           "steps":[{"index":1,"instructionsHTML":"<p>Coupe la courge.</p>"}]}}""",
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

        // Le site se répare : « Réessayer » (menu relu de force) lève l'accès rompu.
        pages[MENU] = { PageRecue(menu, "u") }
        assertTrue(depot.chargerMenu(semaine, forcer = true) is ResultatMenu.Lu)
        assertNull(depot.etat(semaine).value.accesRompu)
        assertEquals(StatutSemaine.OK, depot.etat(semaine).value.fichier.statut)
        assertNull(depot.etat(semaine).value.fichier.message)
    }

    @Test
    fun `un refus passager du site n est pas un acces rompu`() = runBlocking {
        pages[MENU] = { throw RefusSite(429, "trop de demandes") }
        val depot = depot()
        assertTrue(depot.chargerMenu(semaine) is ResultatMenu.HorsLigne)

        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(204)] = { throw RefusSite(503, "maintenance") }
        pages[url(206)] = { throw RefusSite(504, "passerelle") }
        assertTrue(depot.ajouterBox(semaine, box(204)) is ResultatRecette.HorsLigne)
        assertTrue(depot.ajouterBox(semaine, box(206)) is ResultatRecette.HorsLigne)
        assertNull(depot.etat(semaine).value.accesRompu)
        assertEquals(StatutSemaine.OK, depot.etat(semaine).value.fichier.statut)

        // Une page partie (410), elle, rompt l'accès.
        pages[url(205)] = { throw RefusSite(410, "partie") }
        assertTrue(depot.ajouterBox(semaine, box(205)) is ResultatRecette.AccesRompu)
    }

    @Test
    fun `redirection hors des pages publiques ou page demesuree, acces rompu et pas coupure`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        // Ce que lève PagesHelloFresh dans ces deux cas.
        pages[url(204)] = { throw RefusSite(302, "Le site renvoie hors des pages publiques (code 302).") }
        pages[url(205)] = { throw PageIllisible("La page dépasse 16 Mo\u00A0: le site a sans doute changé.") }
        val depot = depot()
        val redirigee = depot.ajouterBox(semaine, box(204))
        assertEquals("Le site renvoie hors des pages publiques (code 302).", (redirigee as ResultatRecette.AccesRompu).motif)
        assertTrue(depot.ajouterBox(semaine, box(205)) is ResultatRecette.AccesRompu)
        assertEquals(StatutSemaine.DEGRADE, depot.etat(semaine).value.fichier.statut)
    }

    @Test
    fun `une relecture reussie leve l acces rompu, qui survit au redemarrage`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(205)] = { PageRecue("<html>maintenance</html>", "u") }
        assertTrue(depot().ajouterBox(semaine, box(205)) is ResultatRecette.AccesRompu)

        // Nouveau lancement : le fichier dit « degrade », le bandeau revient avec son motif.
        val relu = depot()
        val apresRedemarrage = relu.etat(semaine).value
        assertEquals(StatutSemaine.DEGRADE, apresRedemarrage.fichier.statut)
        assertNotNull(apresRedemarrage.accesRompu)
        assertEquals(apresRedemarrage.fichier.message, apresRedemarrage.accesRompu)

        // La page se lit de nouveau : bandeau levé, fichier « ok ».
        pages[url(205)] = { PageRecue(recette(205), "u") }
        assertTrue(relu.relireRecette(semaine, 205) is ResultatRecette.Lue)
        val etat = relu.etat(semaine).value
        assertNull(etat.accesRompu)
        assertEquals(StatutSemaine.OK, etat.fichier.statut)
        assertNull(etat.fichier.message)
    }

    @Test
    fun `apres un redemarrage, une autre page lue ne leve pas l acces rompu`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(205)] = { PageRecue("<html>maintenance</html>", "u") }
        assertTrue(depot().ajouterBox(semaine, box(205)) is ResultatRecette.AccesRompu)

        // Nouveau lancement : la 206 se lit, la 205 reste cassée.
        val relu = depot()
        pages[url(206)] = { PageRecue(recette(206), "u") }
        assertTrue(relu.ajouterBox(semaine, box(206)) is ResultatRecette.Lue)
        assertNotNull(relu.etat(semaine).value.accesRompu)
        assertEquals(StatutSemaine.DEGRADE, relu.etat(semaine).value.fichier.statut)

        // « Réessayer » relit aussi la 205, retrouvée d'après le fichier.
        pages[url(205)] = { PageRecue(recette(205), "u") }
        relu.chargerMenu(semaine, forcer = true)
        assertNull(relu.etat(semaine).value.accesRompu)
        assertEquals(StatutSemaine.OK, relu.etat(semaine).value.fichier.statut)
    }

    @Test
    fun `retirer la seule box en echec leve l acces rompu`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(204)] = { PageRecue(recette(204), "u") }
        pages[url(205)] = { PageRecue("<html>maintenance</html>", "u") }
        val depot = depot()
        depot.ajouterBox(semaine, box(204))
        depot.ajouterBox(semaine, box(205))
        assertNotNull(depot.etat(semaine).value.accesRompu)

        depot.retirerBox(semaine, 205)
        assertNull(depot.etat(semaine).value.accesRompu)
        assertEquals(StatutSemaine.OK, depot.etat(semaine).value.fichier.statut)
    }

    @Test
    fun `l acces reste rompu tant qu une page est en echec, Reessayer les relit`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(204)] = { PageRecue("<html>maintenance</html>", "u") }
        pages[url(205)] = { PageRecue("<html>maintenance</html>", "u") }
        val depot = depot()
        depot.ajouterBox(semaine, box(204))
        depot.ajouterBox(semaine, box(205))

        // 205 se relit, 204 reste illisible : l'accès reste rompu.
        pages[url(205)] = { PageRecue(recette(205), "u") }
        assertTrue(depot.relireRecette(semaine, 205) is ResultatRecette.Lue)
        assertNotNull(depot.etat(semaine).value.accesRompu)
        assertEquals(StatutSemaine.DEGRADE, depot.etat(semaine).value.fichier.statut)

        // « Réessayer » de l'accès rompu : le menu, puis les pages en échec.
        pages[url(204)] = { PageRecue(recette(204), "u") }
        assertTrue(depot.chargerMenu(semaine, forcer = true) is ResultatMenu.Lu)
        val etat = depot.etat(semaine).value
        assertEquals("Plat factice 204", etat.recette(204)!!.titre)
        assertNull(etat.accesRompu)
        assertEquals(StatutSemaine.OK, etat.fichier.statut)
    }

    @Test
    fun `box retiree pendant la lecture de sa page, pas de recette orpheline`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(203)] = { PageRecue(recette203, "u") }
        lateinit var d: DepotLocal
        // Le bouton « retirer » touché pendant que la page se lit.
        d = depot(lirePage = { adresse ->
            if (adresse == url(203)) d.retirerBox(semaine, 203)
            (pages[adresse] ?: throw RefusSite(404, adresse))()
        })
        assertTrue(d.ajouterBox(semaine, box(203)) is ResultatRecette.Lue)
        val fichier = d.etat(semaine).value.fichier
        assertNull(fichier.box(203))
        assertTrue("pas de recette sans box", fichier.recettes.isEmpty())
        assertTrue("pas d'images pour une box retirée", telechargees.isEmpty())
    }

    @Test
    fun `les images en echec sont redemandees`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(203)] = { PageRecue(recette203, "u") }
        imagesEnPanne = true
        val depot = depot()
        assertTrue(depot.ajouterBox(semaine, box(203)) is ResultatRecette.Lue)
        assertNull(depot.imageLocale("/image/plat-203.jpg"))

        // Le réseau revient : l'ouverture de la fiche redemande ce qui manque.
        imagesEnPanne = false
        telechargees.clear()
        depot.completerImages(semaine)
        assertEquals(3, telechargees.size)
        assertNotNull(depot.imageLocale("/image/plat-203.jpg"))
        assertNotNull(depot.imageLocale("/r203/step-1.jpg"))
        // Déjà là : plus rien à télécharger.
        depot.completerImages(semaine)
        assertEquals(3, telechargees.size)
    }

    @Test
    fun `au lancement, les images manquantes repartent`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(203)] = { PageRecue(recette203, "u") }
        imagesEnPanne = true
        depot().ajouterBox(semaine, box(203))

        imagesEnPanne = false
        telechargees.clear()
        val relu = depot()
        relu.etat(semaine)
        assertEquals(3, telechargees.size)
        assertNotNull(relu.imageLocale("/ingredient/i1.png"))
    }

    @Test
    fun `une image en cours n est pas demandee deux fois`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(203)] = { PageRecue(recette203, "u") }
        val porte = CompletableDeferred<Unit>()
        val appels = mutableListOf<String>()
        val depot = DepotLocal(
            racine,
            lirePage = { adresse -> (pages[adresse] ?: throw RefusSite(404, adresse))() },
            // Chaque téléchargement attend la porte : tous sont « en cours » en même temps.
            telecharger = { adresse, cible -> appels += adresse; porte.await(); cible.parentFile?.mkdirs(); cible.writeText("image") },
            fond = CoroutineScope(Dispatchers.Unconfined),
        )
        depot.ajouterBox(semaine, box(203))
        depot.completerImages(semaine)
        depot.completerImages(semaine)
        porte.complete(Unit)
        assertEquals(3, appels.size)
        assertEquals(3, appels.toSet().size)
        assertNotNull(depot.imageLocale("/image/plat-203.jpg"))
    }

    @Test
    fun `telephone plein, rien ne plante et l etat le dit`() = runBlocking {
        pages[MENU] = { PageRecue(menu, "u") }
        pages[url(203)] = { PageRecue(recette203, "u") }
        val depot = depot()
        // Un dossier non vide à la place du fichier temporaire : l'écriture échoue comme sur un
        // téléphone plein.
        fun boucher(nom: String) = File(racine, "semaines/2026-W44/$nom.tmp").also { File(it, "plein").apply { parentFile?.mkdirs(); writeText("x") } }

        // Le menu lu reste en mémoire : le scan marche quand même.
        val bouchonMenu = boucher("menu.json")
        assertTrue(depot.chargerMenu(semaine) is ResultatMenu.Lu)
        assertNotNull(depot.etat(semaine).value.menu)
        assertEquals(EcritureImpossible.MOTIF_ECRITURE, depot.etat(semaine).value.ecritureImpossible)
        bouchonMenu.deleteRecursively()

        val bouchon = boucher("recettes.json")
        assertEquals(ResultatRecette.HorsLigne(EcritureImpossible.MOTIF_ECRITURE), depot.ajouterBox(semaine, box(203)))
        assertNull(depot.etat(semaine).value.fichier.box(203))
        depot.corrigerPersonnes(semaine, 203, 4)
        depot.retirerBox(semaine, 203)
        // La fiche papier lève l'erreur : son écran garde les photos et propose de réessayer.
        val fiche = FicheScannee(numero = 206, pages = emptyList(), prisLe = "t1")
        assertTrue(runCatching { depot.ajouterFiche(semaine, fiche) }.exceptionOrNull() is EcritureImpossible)
        assertEquals(EcritureImpossible.MOTIF_ECRITURE, depot.etat(semaine).value.ecritureImpossible)

        // De la place est faite : tout repart, et l'alerte s'efface.
        bouchon.deleteRecursively()
        assertTrue(depot.ajouterBox(semaine, box(203)) is ResultatRecette.Lue)
        assertNull(depot.etat(semaine).value.ecritureImpossible)
        assertNotNull(depot.etat(semaine).value.fichier.box(203))
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
