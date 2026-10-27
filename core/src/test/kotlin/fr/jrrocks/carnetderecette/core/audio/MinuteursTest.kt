package fr.jrrocks.carnetderecette.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinuteursTest {

    private val minuteurs = Minuteurs()

    @Test
    fun `le restant s'arrondit a la seconde superieure`() {
        minuteurs.lancer("e2", 300, maintenant = 10_000)

        assertEquals(300, minuteurs.restant("e2", 10_000))
        assertEquals(300, minuteurs.restant("e2", 10_001))
        assertEquals(299, minuteurs.restant("e2", 11_000))
        assertEquals(1, minuteurs.restant("e2", 309_999))
        assertEquals(0, minuteurs.restant("e2", 310_000))
        assertEquals(0, minuteurs.restant("e2", 400_000))
        assertNull(minuteurs.restant("e3", 10_000))
    }

    @Test
    fun `un minuteur fini n'est rendu qu'une fois`() {
        minuteurs.lancer("e2", 5, maintenant = 0)

        assertEquals(emptyList<String>(), minuteurs.expires(4_999))
        assertEquals(listOf("e2"), minuteurs.expires(5_000))
        assertEquals(emptyList<String>(), minuteurs.expires(6_000))
        assertNull(minuteurs.restant("e2", 6_000))
        assertTrue(minuteurs.tous().isEmpty())
    }

    @Test
    fun `plusieurs minuteurs tournent ensemble et finissent dans l'ordre`() {
        minuteurs.lancer("long", 60, maintenant = 0)
        minuteurs.lancer("court", 10, maintenant = 0)
        minuteurs.lancer("moyen", 30, maintenant = 0)

        assertEquals(mapOf("long" to 60_000L, "court" to 10_000L, "moyen" to 30_000L), minuteurs.tous())
        assertEquals(listOf("court", "moyen"), minuteurs.expires(45_000))
        assertEquals(15, minuteurs.restant("long", 45_000))
        assertEquals(listOf("long"), minuteurs.expires(60_000))
    }

    @Test
    fun `annuler retire le minuteur, relancer repart du debut`() {
        minuteurs.lancer("e2", 10, maintenant = 0)
        minuteurs.annuler("e2")
        assertEquals(emptyList<String>(), minuteurs.expires(20_000))
        minuteurs.annuler("inconnu")

        minuteurs.lancer("e4", 10, maintenant = 0)
        minuteurs.lancer("e4", 10, maintenant = 8_000)
        assertEquals(emptyList<String>(), minuteurs.expires(10_000))
        assertEquals(8, minuteurs.restant("e4", 10_000))
        assertEquals(listOf("e4"), minuteurs.expires(18_000))
    }

    @Test
    fun `tous rend une copie`() {
        minuteurs.lancer("e2", 10, maintenant = 0)
        val avant = minuteurs.tous()
        minuteurs.annuler("e2")
        assertEquals(setOf("e2"), avant.keys)
    }

    @Test
    fun `un minuteur dure au moins une seconde`() {
        assertThrows(IllegalArgumentException::class.java) { minuteurs.lancer("e2", 0, maintenant = 0) }
    }
}
