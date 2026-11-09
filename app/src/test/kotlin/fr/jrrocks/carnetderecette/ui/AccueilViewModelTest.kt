package fr.jrrocks.carnetderecette.ui

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class AccueilViewModelTest {

    @Test
    fun `une version trouvee a l ouverture ne derange pas un geste en cours`() {
        // Rien ou une version déjà proposée : la plus récente s'affiche.
        assertEquals(EtatMiseAJour.Disponible("0.8"), fusionnerMiseAJour(EtatMiseAJour.Aucune, "0.8"))
        assertEquals(EtatMiseAJour.Disponible("0.9"), fusionnerMiseAJour(EtatMiseAJour.Disponible("0.8"), "0.9"))
        // « Plus tard » et un échec tiennent pour cette version ; une plus récente se propose.
        assertEquals(EtatMiseAJour.Ecartee("0.8"), fusionnerMiseAJour(EtatMiseAJour.Ecartee("0.8"), "0.8"))
        assertEquals(EtatMiseAJour.Disponible("0.9"), fusionnerMiseAJour(EtatMiseAJour.Ecartee("0.8"), "0.9"))
        assertEquals(EtatMiseAJour.Echec("0.8", "motif"), fusionnerMiseAJour(EtatMiseAJour.Echec("0.8", "motif"), "0.8"))
        assertEquals(EtatMiseAJour.Disponible("0.9"), fusionnerMiseAJour(EtatMiseAJour.Echec("0.8", "motif"), "0.9"))
        // Pendant un téléchargement ou l'autorisation d'installer, rien ne bouge.
        assertEquals(EtatMiseAJour.Telechargement("0.8", 0.5f), fusionnerMiseAJour(EtatMiseAJour.Telechargement("0.8", 0.5f), "0.9"))
        assertEquals(EtatMiseAJour.AAutoriser("0.8"), fusionnerMiseAJour(EtatMiseAJour.AAutoriser("0.8"), "0.9"))
    }

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
