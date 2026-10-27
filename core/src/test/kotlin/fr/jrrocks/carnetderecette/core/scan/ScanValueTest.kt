package fr.jrrocks.carnetderecette.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La normalisation sans regex garde le comportement du moteur d'origine. */
class ScanValueTest {

    @Test
    fun `espaces de bord retires et espaces internes reduits a un seul`() {
        assertEquals("4 PERS.", ScanValue.key("  4   pers.  "))
        assertEquals("207", ScanValue.normalize("\t207\n"))
        assertEquals("a b c", ScanValue.normalize("a \t b\n\nc"))
    }

    @Test
    fun `l espace insecable compte comme un espace`() {
        assertEquals("207", ScanValue.key("207\u00A0"))
        assertEquals("4 P", ScanValue.key("4\u00A0p"))
    }

    @Test
    fun `la casse est ignoree sauf demande`() {
        assertEquals("4P", ScanValue.key("4p"))
        assertEquals("4p", ScanValue.key("4p", caseSensitive = true))
    }

    @Test
    fun `une valeur faite d espaces est inutilisable`() {
        assertFalse(ScanValue.isUsable(" \u00A0\t"))
        assertFalse(ScanValue.isUsable(""))
        assertTrue(ScanValue.isUsable(" 7 "))
    }
}
