package fr.jrrocks.carnetderecette.core.semaine

import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemaineIsoTest {

    @Test
    fun `semaine des agendas, vendredi et ecriture`() {
        val s = SemaineIso.de(LocalDate.of(2026, 10, 30))
        assertEquals(SemaineIso(2026, 44), s)
        assertEquals("2026-W44", s.toString())
        assertEquals(LocalDate.of(2026, 10, 26), s.lundi())
        assertEquals(LocalDate.of(2026, 10, 30), s.vendredi())
        // Le 1er janvier 2027 est un vendredi de la semaine 53 de 2026.
        assertEquals(SemaineIso(2026, 53), SemaineIso.de(LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2027, 1, 1), SemaineIso(2026, 53).vendredi())
    }

    @Test
    fun `chiffres latins quelle que soit la langue du telephone`() {
        val avant = Locale.getDefault()
        try {
            // Un téléphone réglé en arabe (chiffres ٠-٩) ou en persan : l'adresse du menu ne change pas.
            for (langue in listOf("ar-EG-u-nu-arab", "fa-IR")) {
                Locale.setDefault(Locale.forLanguageTag(langue))
                assertEquals("2026-W44", SemaineIso(2026, 44).toString())
                assertEquals("2026-W04", SemaineIso(2026, 4).toString())
            }
        } finally {
            Locale.setDefault(avant)
        }
    }

    @Test
    fun `lecture et JSON`() {
        assertEquals(SemaineIso(2026, 4), SemaineIso.lire("2026-W04"))
        assertNull(SemaineIso.lire("2026-44"))
        assertNull(SemaineIso.lire("2026-W60"))
        assertEquals("\"2026-W44\"", Json.encodeToString(SemaineIso.serializer(), SemaineIso(2026, 44)))
        assertEquals(SemaineIso(2026, 44), Json.decodeFromString(SemaineIso.serializer(), "\"2026-W44\""))
    }
}
