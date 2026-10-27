package fr.jrrocks.carnetderecette.audio

import android.content.Context
import android.media.ToneGenerator
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowToneGenerator

/** La sonnerie de fin de minuteur, sous Robolectric : les salves jouées et le verrou d'éveil. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SonnerieTest {

    private val contexte: Context = ApplicationProvider.getApplicationContext()
    private val sonnerie = Sonnerie(contexte)

    private fun attendre(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun salves() = ShadowToneGenerator.getPlayedTones().size
    private fun eveil() = ShadowPowerManager.getLatestWakeLock()

    @After
    fun liberer() = sonnerie.liberer()

    @Test
    fun `seule, elle sonne plusieurs secondes, salve apres salve, telephone eveille`() {
        sonnerie.sonner()
        assertEquals(1, salves())
        assertTrue(eveil().isHeld)
        attendre(2 * Sonnerie.PERIODE_MS)
        assertEquals(3, salves())
        assertTrue(eveil().isHeld)
        attendre(20_000)
        assertEquals(Sonnerie.SALVES_SEULE, salves())
        assertFalse("la dernière salve relâche le téléphone", eveil().isHeld)
        assertTrue(ShadowToneGenerator.getPlayedTones().all { it.type() == ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD })
    }

    @Test
    fun `avant la voix, une seule salve`() {
        sonnerie.sonner(Sonnerie.SALVES_AVANT_LA_VOIX)
        attendre(20_000)
        assertEquals(1, salves())
        assertFalse(eveil().isHeld)
    }

    @Test
    fun `arreter la fait taire tout de suite`() {
        sonnerie.sonner()
        attendre(Sonnerie.PERIODE_MS)
        assertEquals(2, salves())
        sonnerie.arreter()
        assertFalse(eveil().isHeld)
        attendre(20_000)
        assertEquals(2, salves())
    }

    @Test
    fun `un second minuteur fini pendant qu'elle sonne la prolonge sans la doubler`() {
        sonnerie.sonner()
        attendre(2 * Sonnerie.PERIODE_MS)
        assertEquals(3, salves())
        sonnerie.sonner()
        assertEquals("pas de salve en double", 3, salves())
        attendre(20_000)
        assertEquals(3 + Sonnerie.SALVES_SEULE, salves())
    }

    @Test
    fun `liberee, elle ne sonne plus`() {
        sonnerie.liberer()
        sonnerie.sonner()
        attendre(20_000)
        assertEquals(0, salves())
    }
}
