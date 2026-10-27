package fr.jrrocks.carnetderecette.ui.seance

import android.Manifest
import android.app.Application
import android.os.Looper
import android.os.PowerManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.Sonnerie
import fr.jrrocks.carnetderecette.core.recette.EtapeRecette
import fr.jrrocks.carnetderecette.core.recette.Recette
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowToneGenerator

/**
 * La séance avec ses vrais morceaux Android (Robolectric) : verrou d'éveil des minuteurs,
 * sonnerie, micro coupé en fin de recette, micro autorisé au retour des réglages. Recette
 * factice ; la voix reste « en préparation » (aucun moteur ne répond sous Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeanceViewModelTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val magasin = ViewModelStore()

    private val recette = Recette(
        id = "essai",
        titre = "Essai",
        url = "",
        etapes = listOf(
            EtapeRecette(1, "Fais chauffer la poêle 1 min."),
            EtapeRecette(2, "Fais revenir les légumes 5 min."),
            EtapeRecette(3, "Sers."),
        ),
    )

    private val vm: SeanceViewModel by lazy {
        ViewModelProvider(magasin, viewModelFactory { initializer { SeanceViewModel(application, recette) } })[SeanceViewModel::class.java]
    }

    @Before
    fun preparer() {
        shadowOf(application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
    }

    @After
    fun fermer() = magasin.clear()

    private fun attendre(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun salves() = ShadowToneGenerator.getPlayedTones().size

    private fun lancerLeMinuteur(): PowerManager.WakeLock {
        val minuteur = vm.etat.minuteur
        assertNotNull(minuteur)
        vm.minuteur(minuteur!!)
        val verrou = ShadowPowerManager.getLatestWakeLock()
        assertEquals("carnetderecette:minuteurs", shadowOf(verrou).tag)
        return verrou
    }

    @Test
    fun `un minuteur qui tourne garde le telephone eveille jusqu'a sa fin, pas au-dela`() {
        vm.valider()
        val verrou = lancerLeMinuteur()
        assertTrue(verrou.isHeld)
        attendre(4 * 60_000L)
        assertTrue("toujours éveillé à 4 minutes sur 5", verrou.isHeld)
        // Avant l'échéance du verrou (fin + marge) : c'est la boucle qui le relâche.
        attendre(62_000L)
        assertFalse("relâché une fois le minuteur fini", verrou.isHeld)
        assertTrue(salves() >= 1)
    }

    @Test
    fun `quitter la seance relache le verrou`() {
        vm.valider()
        val verrou = lancerLeMinuteur()
        attendre(10_000)
        magasin.clear()
        assertFalse(verrou.isHeld)
    }

    @Test
    fun `le minuteur d'une etape passee sonne longtemps, et un geste le fait taire`() {
        lancerLeMinuteur()
        vm.valider()
        attendre(61_000)
        assertEquals(1, salves())
        attendre(3 * Sonnerie.PERIODE_MS)
        assertEquals(4, salves())
        vm.relire()
        attendre(20_000)
        assertEquals(4, salves())
    }

    @Test
    fun `le minuteur de l'etape en cours sonne une fois, la voix enchaine`() {
        lancerLeMinuteur()
        attendre(61_000)
        assertEquals("e2", vm.etat.courant?.id)
        attendre(20_000)
        assertEquals(Sonnerie.SALVES_AVANT_LA_VOIX, salves())
    }

    @Test
    fun `la recette finie ferme le micro et le dit`() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        vm.ouvrirMicro()
        attendre(1_000)
        assertEquals(EtatEcoute.Ecoute, vm.etat.micro)
        repeat(3) { vm.valider() }
        assertTrue(vm.etat.terminee)
        assertEquals(EtatEcoute.Arretee, vm.etat.micro)
        assertTrue(vm.etat.microCoupeEnFin)
        // Revenu à la dernière étape : plus un mot sur la fin, le micro se rouvre d'un geste.
        vm.precedent()
        assertFalse(vm.etat.microCoupeEnFin)
        vm.ouvrirMicro()
        assertEquals(EtatEcoute.Ecoute, vm.etat.micro)
    }

    @Test
    fun `sans micro ouvert, la fin n'en parle pas`() {
        repeat(3) { vm.valider() }
        assertTrue(vm.etat.terminee)
        assertFalse(vm.etat.microCoupeEnFin)
    }

    @Test
    fun `micro autorise dans les reglages, l'ecoute s'ouvre au retour`() {
        vm.permissionRefusee()
        assertEquals(EtatEcoute.PermissionRequise, vm.etat.micro)
        vm.enArrierePlan()
        vm.auRetour()
        assertEquals("toujours refusé", EtatEcoute.PermissionRequise, vm.etat.micro)

        vm.enArrierePlan()
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        vm.auRetour()
        attendre(1_000)
        assertEquals(EtatEcoute.Ecoute, vm.etat.micro)
    }

    @Test
    fun `micro jamais demande, le retour ne l'ouvre pas`() {
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        vm.enArrierePlan()
        vm.auRetour()
        assertEquals(EtatEcoute.Arretee, vm.etat.micro)
    }
}
