package fr.jrrocks.carnetderecette.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Le passage du tampon de la caméra à l'image droite, puis au réticule : c'est lui qui décide
 * quels mots de l'étiquette comptent. Tampon d'analyse courant : 1280 × 720, couché.
 */
class GeometrieImageTest {

    private val l = 1280
    private val h = 720

    private fun assertZone(attendue: Zone, obtenue: Zone) {
        assertEquals("gauche", attendue.gauche, obtenue.gauche, 0.01f)
        assertEquals("haut", attendue.haut, obtenue.haut, 0.01f)
        assertEquals("droite", attendue.droite, obtenue.droite, 0.01f)
        assertEquals("bas", attendue.bas, obtenue.bas, 0.01f)
    }

    @Test
    fun `a 90 et 270 degres la largeur et la hauteur s inversent`() {
        assertEquals(720 to 1280, GeometrieImage.dimensionsDroites(l, h, 90))
        assertEquals(720 to 1280, GeometrieImage.dimensionsDroites(l, h, 270))
        assertEquals(1280 to 720, GeometrieImage.dimensionsDroites(l, h, 0))
        assertEquals(1280 to 720, GeometrieImage.dimensionsDroites(l, h, 180))
    }

    @Test
    fun `le coin haut gauche du tampon suit la rotation`() {
        val coin = Zone(0f, 0f, 100f, 50f)

        assertZone(coin, GeometrieImage.versDroite(coin, l, h, 0))
        // Un quart de tour horaire : le haut gauche passe en haut à droite.
        assertZone(Zone(670f, 0f, 720f, 100f), GeometrieImage.versDroite(coin, l, h, 90))
        assertZone(Zone(1180f, 670f, 1280f, 720f), GeometrieImage.versDroite(coin, l, h, 180))
        // Trois quarts de tour : en bas à gauche.
        assertZone(Zone(0f, 1180f, 50f, 1280f), GeometrieImage.versDroite(coin, l, h, 270))
    }

    @Test
    fun `versTampon defait versDroite pour chaque rotation`() {
        val zone = Zone(130f, 40f, 410f, 333f)
        listOf(0, 90, 180, 270).forEach { rotation ->
            val droite = GeometrieImage.versDroite(zone, l, h, rotation)
            assertZone(zone, GeometrieImage.versTampon(droite, l, h, rotation))
        }
    }

    @Test
    fun `le reticule couvre la meme part de l image que de l ecran`() {
        val toutLeTampon = Zone(0f, 0f, l.toFloat(), h.toFloat())

        val reticule = GeometrieImage.reticuleDansImage(l, h, 90, toutLeTampon, Reticule.ZONE)

        // Image droite 720 × 1280 ; réticule de 10 % à 90 % en largeur, 22 % à 78 % en hauteur.
        assertZone(Zone(72f, 281.6f, 648f, 998.4f), reticule)
    }

    @Test
    fun `le recadrage de l aperçu deplace le reticule`() {
        // Aperçu carré sur un tampon 16:9 tenu en portrait : CameraX recadre le milieu du tampon.
        val recadrage = Zone(280f, 0f, 1000f, 720f)

        val reticule = GeometrieImage.reticuleDansImage(l, h, 90, recadrage, Reticule.ZONE)

        // Dans l'image droite, le recadrage est le carré (0, 280)-(720, 1000).
        assertZone(Zone(72f, 280f + 0.22f * 720f, 648f, 280f + 0.78f * 720f), reticule)
    }

    @Test
    fun `un element se rapporte au reticule`() {
        val reticule = Zone(100f, 200f, 500f, 400f)

        val element = GeometrieImage.elementLu("207", Zone(200f, 250f, 300f, 350f), reticule)

        assertNotNull(element)
        assertEquals("207", element!!.texte)
        assertEquals(0.5f, element.hauteur, 0.0001f)
        assertEquals(0.375f, element.x, 0.0001f)
        assertEquals(0.5f, element.y, 0.0001f)
    }

    @Test
    fun `un element dont le centre sort du reticule est ignore`() {
        val reticule = Zone(100f, 200f, 500f, 400f)

        // Il déborde dans le réticule, mais son centre est dehors : il ne compte pas.
        assertNull(GeometrieImage.elementLu("LOT", Zone(20f, 250f, 160f, 300f), reticule))
        assertNull(GeometrieImage.elementLu("12/10", Zone(200f, 390f, 300f, 450f), reticule))
    }

    @Test
    fun `la rotation de la photo suit le telephone, pas l ecran`() {
        // Valeurs de Surface.ROTATION_0 (0) à ROTATION_270 (3), table de la doc CameraX.
        listOf(0 to 0, 30 to 0, 44 to 0, 315 to 0, 359 to 0).forEach { (degres, attendue) ->
            assertEquals("$degres°", attendue, GeometrieImage.rotationCible(degres))
        }
        // Côté gauche du téléphone en haut (90°) : l'écran, s'il tournait, serait en ROTATION_270.
        listOf(45 to 3, 90 to 3, 134 to 3).forEach { (degres, attendue) ->
            assertEquals("$degres°", attendue, GeometrieImage.rotationCible(degres))
        }
        listOf(135 to 2, 180 to 2, 224 to 2, 225 to 1, 270 to 1, 314 to 1).forEach { (degres, attendue) ->
            assertEquals("$degres°", attendue, GeometrieImage.rotationCible(degres))
        }
    }

    @Test
    fun `une orientation inconnue garde la rotation precedente`() {
        // OrientationEventListener.ORIENTATION_UNKNOWN : téléphone posé à plat.
        assertNull(GeometrieImage.rotationCible(-1))
    }
}
