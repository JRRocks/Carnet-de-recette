package fr.jrrocks.carnetderecette.core.hellofresh

import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LecteurHelloFreshTest {

    private val semaine = SemaineIso(2026, 44)

    @Test
    fun `le menu associe chaque numero d etiquette a sa recette`() {
        val menu = LecteurHelloFresh.lireMenu(PagesFactices.menu(), semaine, "2026-10-27T08:00:00Z")
        assertEquals(listOf(201, 202, 203, 207, 210, 290), menu.entrees.map { it.numero })
        val poulet = menu.entree(207)!!
        assertEquals("Poulet fictif croustillant", poulet.titre)
        assertEquals("avec un sous-titre", poulet.sousTitre)
        assertEquals("https://www.hellofresh.fr/recipes/poulet-fictif-id207", poulet.url)
        assertEquals("/image/plat-207.jpg", poulet.image)
        assertNull(Sonde.verifierMenu(menu))
    }

    @Test
    fun `un menu d une autre semaine est refuse`() {
        val erreur = assertThrows(PageIllisible::class.java) {
            LecteurHelloFresh.lireMenu(PagesFactices.menu(semaine = "2026-W45"), semaine, "x")
        }
        assertTrue(erreur.message!!.contains("semaine 45"))
    }

    @Test
    fun `une page sans donnees est illisible`() {
        assertThrows(PageIllisible::class.java) { LecteurHelloFresh.lireMenu("<html>maintenance</html>", semaine, "x") }
        assertThrows(PageIllisible::class.java) { LecteurHelloFresh.lireRecette("<html></html>", "https://www.hellofresh.fr/recipes/x") }
    }

    @Test
    fun `la recette donne quantites par portion, etapes en texte et photos`() {
        val r = LecteurHelloFresh.lireRecette(PagesFactices.RECETTE, "https://www.hellofresh.fr/recipes/poulet-fictif-abc123")
        assertEquals("abc123-fr-FR", r.id)
        assertEquals("et sa sauce d'essai", r.sousTitre)
        assertEquals("Une recette inventée pour les tests.", r.description)
        assertEquals(35, r.dureeTotaleMin)
        assertEquals(65, r.dureePreparationMin)
        assertEquals(listOf(2, 4), r.personnesPossibles)
        assertEquals(30.0, r.portion(2)!!.quantites.first { it.ingredient == "ing2" }.montant)
        assertEquals("g", r.portion(2)!!.quantites.first { it.ingredient == "ing2" }.unite)
        assertNull(r.portion(4)!!.quantites.first { it.ingredient == "ing3" }.unite)
        assertFalse(r.ingredient("ing3")!!.livre)
        assertEquals(listOf("Gluten"), r.ingredient("ing2")!!.allergenes)
        // Étapes remises dans l'ordre, une ligne par puce ou paragraphe, entités décodées.
        assertEquals(listOf(1, 2), r.etapes.map { it.numero })
        assertEquals("Préchauffe le four à 200 °C.\nCoupe le poulet.", r.etapes[0].texte)
        assertEquals("Fais dorer le poulet 5-6 min.\nAjoute ⅓ de la sauce.", r.etapes[1].texte)
        assertEquals("/abc123/step-1.jpg", r.etapes[0].images.single().chemin)
        assertEquals("Coupe !", r.etapes[0].images.single().legende)
        assertEquals(listOf("Poêle", "Plaque de four"), r.ustensiles)
        assertNull(Sonde.verifierRecette(r))
    }

    @Test
    fun `sans donnees de page, le bloc schema org prend le relais`() {
        val r = LecteurHelloFresh.lireRecette(PagesFactices.RECETTE_SCHEMA_ORG, "https://www.hellofresh.fr/recipes/soupe-imaginaire")
        assertEquals("Soupe imaginaire", r.titre)
        assertEquals(listOf("1 pièce(s) Oignon", "200 g Pois"), r.ingredients.map { it.nom })
        assertEquals(listOf("Émince l'oignon.", "Mixe."), r.etapes.map { it.texte })
        assertEquals(listOf(2), r.personnesPossibles)
        assertEquals(20, r.dureeTotaleMin)
    }

    @Test
    fun `la sonde dit ce qui manque`() {
        val r = LecteurHelloFresh.lireRecette(PagesFactices.RECETTE, "u").copy(etapes = emptyList(), portions = emptyList())
        assertEquals("La page de « Poulet fictif croustillant » ne donne plus les étapes, les portions.", Sonde.verifierRecette(r))
        // Ce que la sonde de GitHub publie : nos mots seulement, rien du titre.
        assertEquals(listOf("les étapes", "les portions"), Sonde.champsManquants(r))
        assertTrue(Sonde.champsManquants(LecteurHelloFresh.lireRecette(PagesFactices.RECETTE, "u")).isEmpty())
        val menu = LecteurHelloFresh.lireMenu(PagesFactices.menu(plats = PagesFactices.PLATS.take(2)), semaine, "x")
        assertTrue(Sonde.verifierMenu(menu)!!.contains("que 2 recettes"))
    }

    @Test
    fun `refus passagers et refus qui rompent l acces`() {
        for (code in listOf(408, 429, 502, 503, 504)) assertTrue("$code", Sonde.refusPassager(code))
        for (code in listOf(301, 400, 403, 404, 410, 500)) assertFalse("$code", Sonde.refusPassager(code))
    }

    @Test
    fun `durees ISO`() {
        assertEquals(80, LecteurHelloFresh.minutesIso("PT1H20M"))
        assertEquals(25, LecteurHelloFresh.minutesIso("PT25M"))
        assertNull(LecteurHelloFresh.minutesIso("PT0M"))
        assertNull(LecteurHelloFresh.minutesIso("25 min"))
    }

    @Test
    fun `adresses publiques seulement`() {
        assertTrue(AdressesHelloFresh.estPagePublique("https://www.hellofresh.fr/recipes/x-1"))
        assertFalse(AdressesHelloFresh.estPagePublique("https://www.hellofresh.fr/gw/api/recipes"))
        assertFalse(AdressesHelloFresh.estPagePublique("http://www.hellofresh.fr/recipes/x"))
        assertFalse(AdressesHelloFresh.estPagePublique("https://exemple.org/recipes/x"))
        assertEquals("https://www.hellofresh.fr/menus/2026-W44", AdressesHelloFresh.menu(semaine))
        assertEquals(
            "https://img.hellofresh.com/f_auto,fl_lossy,q_auto,w_800/hellofresh_s3/image/a.jpg",
            AdressesHelloFresh.image("/image/a.jpg", 800),
        )
        assertThrows(IllegalArgumentException::class.java) { AdressesHelloFresh.image("../x", 800) }
    }

    @Test
    fun `texte html`() {
        assertEquals("a & b\nc'd «e»", TexteHtml.enTexte("<p>a &amp; b</p><p>c&#39;d &laquo;e&raquo;</p>"))
        assertEquals("x", TexteHtml.enTexte("<li> <strong>x</strong> </li>"))
        assertEquals("& seul", TexteHtml.enTexte("& seul"))
    }
}
