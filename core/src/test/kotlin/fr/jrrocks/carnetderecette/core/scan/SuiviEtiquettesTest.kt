package fr.jrrocks.carnetderecette.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le consensus sur le numéro, le vote des personnes et l'anti-rebond, avec une horloge factice. */
class SuiviEtiquettesTest {

    private var maintenant = 0L
    private val suivi = SuiviEtiquettes(horloge = { maintenant })

    /** Une image toutes les 100 ms, comme un OCR à une dizaine d'images par seconde. */
    private fun image(numero: Int?, personnes: Int? = null, texte: String = "image $maintenant"): BoxValidee? {
        val validee = suivi.soumettre(LectureEtiquette(numero, personnes, listOf(ElementLu(texte, 0.1f, 0.5f, 0.5f))))
        maintenant += 100
        return validee
    }

    @Test
    fun `cinq lectures identiques valident la box`() {
        repeat(4) { assertNull(image(207, 4)) }
        assertEquals(0.8f, suivi.progression)

        val box = image(207, 4, texte = "derniere")

        assertNotNull(box)
        assertEquals(207, box!!.numero)
        assertEquals(4, box.personnesProposees)
        assertFalse(box.aConfirmer)
        // Les éléments de l'image qui a validé.
        assertEquals("derniere", box.elements.single().texte)
        // Le compteur est purgé : le réticule se vide.
        assertEquals(0f, suivi.progression)
    }

    @Test
    fun `une image sans numero ne casse pas le compteur`() {
        image(207, 4)
        image(null)
        image(207, 4)
        image(null)
        image(207, 4)
        image(207, 4)

        assertNotNull(image(207, 4))
    }

    @Test
    fun `des votes partages proposent le plus lu et marquent la box a confirmer`() {
        image(207, 4)
        image(207, 2)
        image(207, 4)
        image(207, 4)
        val box = image(207, 2)!!

        assertEquals(4, box.personnesProposees)
        assertTrue(box.aConfirmer)
    }

    @Test
    fun `des votes a egalite ne proposent rien`() {
        image(207, 4)
        image(207, 2)
        image(207, 4)
        image(207, 2)
        val box = image(207)!!

        assertNull(box.personnesProposees)
        assertTrue(box.aConfirmer)
    }

    @Test
    fun `des personnes jamais lues marquent la box a confirmer`() {
        repeat(4) { image(207) }
        val box = image(207)!!

        assertNull(box.personnesProposees)
        assertTrue(box.aConfirmer)
    }

    @Test
    fun `quelques images sans personnes ne font pas douter`() {
        image(207, 4)
        image(207)
        image(207, 4)
        image(207)
        val box = image(207, 4)!!

        assertEquals(4, box.personnesProposees)
        assertFalse(box.aConfirmer)
    }

    @Test
    fun `la box restee dans le champ n est jamais revalidee`() {
        repeat(5) { image(207, 4) }
        // Validée à 400 ms, puis lue sans trêve pendant 10 s : le verrou glisse avec chaque lecture.
        repeat(100) { assertNull(image(207, 4)) }
        // Le réticule ne se remplit pas de nouveau.
        assertEquals(0f, suivi.progression)
    }

    @Test
    fun `quelques images sans numero ne liberent pas le verrou`() {
        repeat(5) { image(207, 4) }
        repeat(30) { i -> assertNull(image(if (i % 10 < 8) null else 207, 4)) }
        repeat(5) { assertNull(image(207, 4)) }
    }

    @Test
    fun `apres un trou de la fenetre la meme box se revalide`() {
        repeat(5) { image(207, 4) }
        repeat(20) { image(207, 4) }
        // L'étiquette sort du champ 1,5 s : la même box rescannée exprès se valide de nouveau.
        maintenant += 1_500
        repeat(4) { assertNull(image(207, 4)) }
        assertEquals(207, image(207, 4)?.numero)
    }

    @Test
    fun `une autre box se valide pendant que la premiere est verrouillee`() {
        repeat(5) { image(207, 4) }
        repeat(4) { image(315, 2) }
        assertEquals(315, image(315, 2)?.numero)
        // La première, revue aussitôt, reste verrouillée.
        repeat(5) { assertNull(image(207, 4)) }
    }

    @Test
    fun `une box retiree reste ignoree tant qu elle est dans le champ`() {
        repeat(4) { image(207, 4) }
        suivi.ignorer(207)
        repeat(50) { assertNull(image(207, 4)) }

        maintenant += 1_500
        repeat(4) { image(207, 4) }
        assertNotNull(image(207, 4))
    }

    @Test
    fun `une box retiree hors du champ se rescanne apres la fenetre`() {
        suivi.ignorer(207)
        maintenant += 1_500
        repeat(4) { image(207, 4) }
        assertNotNull(image(207, 4))
    }

    @Test
    fun `deux box l une apres l autre`() {
        repeat(4) { image(207, 4) }
        assertEquals(207, image(207, 4)?.numero)
        repeat(4) { image(315, 2) }
        val seconde = image(315, 2)!!

        assertEquals(315, seconde.numero)
        assertEquals(2, seconde.personnesProposees)
    }

    @Test
    fun `deux numeros a un chiffre pres qui alternent repartent a zero`() {
        image(207, 4)
        image(207, 4)
        // « 208 » arrive seul : même étiquette mal lue, les deux compteurs repartent à zéro.
        image(208, 4)
        repeat(4) { assertNull(image(207, 4)) }

        assertNotNull(image(207, 4))
    }

    @Test
    fun `les votes expirent avec le compteur abandonne`() {
        image(207, 2)
        maintenant += 1_000
        repeat(4) { image(207, 4) }
        val box = image(207, 4)!!

        assertEquals(4, box.personnesProposees)
        assertFalse(box.aConfirmer)
    }

    @Test
    fun `reinitialiser vide le consensus et le verrou`() {
        repeat(5) { image(207, 4) }
        repeat(3) { image(315, 2) }
        suivi.reinitialiser()

        assertEquals(0f, suivi.progression)
        repeat(4) { image(207, 4) }
        assertNotNull(image(207, 4))
    }
}
