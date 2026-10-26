package fr.jrrocks.carnetderecette.ui

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class AccueilViewModelTest {

    @Test
    fun `la cause d un telechargement rate se lit en francais`() {
        assertEquals(
            "pas de réseau, réessaie une fois connecté",
            causeDuTelechargement(UnknownHostException("Unable to resolve host \"github.com\"")),
        )
        assertEquals("téléchargement refusé (code 404)", causeDuTelechargement(IOException("Téléchargement refusé (code 404).")))
        assertEquals("le téléchargement s'est coupé", causeDuTelechargement(IOException("unexpected end of stream")))
        assertEquals("le téléchargement s'est coupé", causeDuTelechargement(IOException()))
    }
}
