package fr.jrrocks.carnetderecette.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La reconnaissance de l'étiquette sans regex (CLAUDE.md, règle 5). Étiquettes **factices** :
 * un grand numéro, un petit nombre de personnes, et du bruit (lot, date, mots du titre).
 */
class ReconnaissanceEtiquetteTest {

    private val menu = setOf(12, 45, 207, 208, 315)

    private fun el(texte: String, hauteur: Float = 0.1f, x: Float = 0.5f, y: Float = 0.5f) = ElementLu(texte, hauteur, x, y)

    private fun lire(vararg elements: ElementLu, possibles: Set<Int> = ReconnaissanceEtiquette.PERSONNES_POSSIBLES) =
        ReconnaissanceEtiquette.reconnaitre(elements.toList(), menu, possibles)

    /** L'étiquette type : « 207 » en grand, « 4p » en petit, et du bruit autour. */
    private val etiquette = listOf(
        el("207", hauteur = 0.45f, x = 0.3f, y = 0.4f),
        el("4p", hauteur = 0.12f, x = 0.8f, y = 0.45f),
        el("Poulet", hauteur = 0.08f, x = 0.2f, y = 0.8f),
        el("fictif", hauteur = 0.08f, x = 0.45f, y = 0.8f),
        el("LOT", hauteur = 0.05f, x = 0.1f, y = 0.95f),
        el("12/10", hauteur = 0.05f, x = 0.3f, y = 0.95f),
        el("45", hauteur = 0.05f, x = 0.5f, y = 0.95f),
    )

    @Test
    fun `l etiquette factice donne la recette et le nombre de personnes`() {
        val lecture = ReconnaissanceEtiquette.reconnaitre(etiquette, menu)

        assertEquals(207, lecture.numero)
        assertEquals(4, lecture.personnes)
        // Les éléments bruts sont gardés tels quels, pour que Claude les relise.
        assertEquals(etiquette, lecture.elements)
    }

    @Test
    fun `un numero hors du menu ne designe aucune recette`() {
        val lecture = lire(el("999", hauteur = 0.4f), el("4p"))

        assertNull(lecture.numero)
        assertEquals(4, lecture.personnes)
    }

    @Test
    fun `sans menu rien n est reconnu`() {
        val lecture = ReconnaissanceEtiquette.reconnaitre(etiquette, numerosDuMenu = emptySet())

        assertNull(lecture.numero)
    }

    @Test
    fun `de deux numeros du menu le plus grand l emporte`() {
        assertEquals(207, lire(el("207", hauteur = 0.40f), el("315", hauteur = 0.20f)).numero)
        assertEquals(315, lire(el("207", hauteur = 0.20f), el("315", hauteur = 0.40f)).numero)
    }

    @Test
    fun `deux numeros de meme taille a 10 pour cent pres laissent un doute`() {
        assertNull(lire(el("207", hauteur = 0.40f), el("315", hauteur = 0.40f)).numero)
        assertNull(lire(el("207", hauteur = 0.40f), el("315", hauteur = 0.37f)).numero)
        assertEquals(207, lire(el("207", hauteur = 0.40f), el("315", hauteur = 0.35f)).numero)
    }

    @Test
    fun `le meme numero lu deux fois n est pas un doute`() {
        assertEquals(207, lire(el("207", hauteur = 0.40f), el("207", hauteur = 0.10f)).numero)
    }

    @Test
    fun `un numero se lit parmi d autres mots du meme element`() {
        assertEquals(208, lire(el("Box 208 fictive", hauteur = 0.3f)).numero)
    }

    @Test
    fun `la casse ne compte pas`() {
        listOf("4P", "4p", "4PERS", "4pers.", "4Personnes", "4 portions", "x4", "X 4", "×4").forEach { texte ->
            assertEquals(texte, 4, lire(el("207", 0.4f), el(texte)).personnes)
        }
    }

    @Test
    fun `les espaces parasites ne genent pas`() {
        val lecture = lire(el("  207\u00A0", hauteur = 0.4f), el("\t4   p "))

        assertEquals(207, lecture.numero)
        assertEquals(4, lecture.personnes)
    }

    @Test
    fun `2O7 avec la lettre O ne passe pas`() {
        assertNull(lire(el("2O7", hauteur = 0.4f), el("4p")).numero)
        // Rien n'est corrigé : ni « l » pour « 1 », ni zéro de tête, ni ponctuation collée.
        assertNull(lire(el("2l5", hauteur = 0.4f)).numero)
        assertNull(lire(el("0207", hauteur = 0.4f)).numero)
        assertNull(lire(el("207.", hauteur = 0.4f)).numero)
    }

    @Test
    fun `un numero colle a un autre texte ne passe pas`() {
        assertNull(lire(el("207g", hauteur = 0.4f)).numero)
        assertNull(lire(el("12/10", hauteur = 0.4f)).numero)
    }

    @Test
    fun `deux nombres de personnes differents laissent un doute`() {
        assertNull(lire(el("207", 0.4f), el("4p"), el("2p")).personnes)
    }

    @Test
    fun `le meme nombre de personnes lu deux fois reste sur`() {
        assertEquals(2, lire(el("207", 0.4f), el("2p"), el("2 pers.")).personnes)
    }

    @Test
    fun `un nombre seul ne dit pas le nombre de personnes`() {
        assertNull(lire(el("207", 0.4f), el("4")).personnes)
        assertNull(lire(el("207", 0.4f), el("p")).personnes)
    }

    @Test
    fun `4 pers en deux elements voisins sur la meme ligne`() {
        val lecture = lire(
            el("207", hauteur = 0.4f, x = 0.3f, y = 0.4f),
            el("4", hauteur = 0.1f, x = 0.7f, y = 0.8f),
            el("pers.", hauteur = 0.1f, x = 0.82f, y = 0.81f),
        )

        assertEquals(4, lecture.personnes)
    }

    @Test
    fun `deux elements sur deux lignes differentes ne se lisent pas ensemble`() {
        val lecture = lire(
            el("4", hauteur = 0.1f, x = 0.7f, y = 0.5f),
            el("pers.", hauteur = 0.1f, x = 0.8f, y = 0.8f),
        )

        assertNull(lecture.personnes)
    }

    @Test
    fun `un nombre de personnes hors des possibles est ignore`() {
        assertNull(lire(el("207", 0.4f), el("8p")).personnes)
        assertNull(lire(el("207", 0.4f), el("1p"), possibles = setOf(2, 4)).personnes)
        assertEquals(2, lire(el("207", 0.4f), el("2p"), possibles = setOf(2, 4)).personnes)
    }

    @Test
    fun `les formes connues sont une liste de valeurs construite a partir des nombres possibles`() {
        val formes = ReconnaissanceEtiquette.formesPersonnes(setOf(4))

        listOf("4P", "4 P", "4PERS", "4PERS.", "4PERSONNES", "4PORTIONS", "X4").forEach { forme ->
            assertEquals(forme, 4, formes[forme])
        }
        assertTrue(formes.values.all { it == 4 })
    }

    @Test
    fun `une etiquette vide ne donne rien`() {
        val lecture = lire()

        assertNull(lecture.numero)
        assertNull(lecture.personnes)
    }
}
