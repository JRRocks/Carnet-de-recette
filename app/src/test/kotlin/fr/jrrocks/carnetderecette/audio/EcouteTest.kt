package fr.jrrocks.carnetderecette.audio

import android.Manifest
import android.app.Application
import android.os.Looper
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer

/** L'écoute des commandes vocales, sous Robolectric : les erreurs du service de reconnaissance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EcouteTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val suivi = object : Ecoute.Suivi {
        override fun etatEcoute(etat: EtatEcoute) = Unit
        override fun partiel(texte: String) = Unit
        override fun entendu(commande: CommandeVocale?, texte: String) = Unit
    }
    private lateinit var ecoute: Ecoute

    @Before
    fun ouvrir() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        ecoute = Ecoute(application, suivi)
        ecoute.demarrer()
        attendre()
        assertEquals(EtatEcoute.Ecoute, ecoute.etat)
    }

    @After
    fun liberer() = ecoute.liberer()

    private fun attendre() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

    private fun erreur(code: Int) {
        shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnError(code)
        attendre()
    }

    @Test
    fun `des erreurs isolees entre des silences n'arretent pas l'ecoute`() {
        repeat(10) {
            erreur(SpeechRecognizer.ERROR_CLIENT)
            erreur(SpeechRecognizer.ERROR_NO_MATCH)
            erreur(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        }
        assertEquals(EtatEcoute.Ecoute, ecoute.etat)
    }

    @Test
    fun `cinq erreurs de suite arretent l'ecoute et le disent`() {
        repeat(5) { erreur(SpeechRecognizer.ERROR_CLIENT) }
        val etat = ecoute.etat
        assertTrue(etat.toString(), etat is EtatEcoute.Erreur && "s'arrête sans cesse" in etat.motif)
    }

    @Test
    fun `le service vocal prive de micro, alors que Carnet l'a, ne renvoie pas vers les reglages de Carnet`() {
        erreur(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        val etat = ecoute.etat
        assertTrue(etat.toString(), etat is EtatEcoute.Erreur && "saisie vocale" in etat.motif)
    }

    @Test
    fun `sans la permission de Carnet, on la demande`() {
        shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        erreur(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertEquals(EtatEcoute.PermissionRequise, ecoute.etat)
    }
}
