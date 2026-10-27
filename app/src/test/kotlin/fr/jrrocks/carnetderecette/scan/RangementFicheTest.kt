package fr.jrrocks.carnetderecette.scan

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Le rangement des photos de la fiche papier : un enregistrement qui échoue entre le
 * déplacement et l'écriture du fichier de la semaine se reprend sans perdre une photo.
 */
class RangementFicheTest {

    @get:Rule val dossiers = TemporaryFolder()

    private fun fichier(dossier: File, nom: String, contenu: String) = File(dossier, nom).apply { writeText(contenu) }

    @Test
    fun `la photo passe du cache au dossier de la semaine`() {
        val cache = dossiers.newFolder("cache")
        val photos = dossiers.newFolder("photos")
        val source = fichier(cache, "recto-1.jpg", "nouvelle")
        val cible = File(photos, RangementFiche.enAttente("fiche-207-recto.jpg"))

        RangementFiche.deplacer(source, cible)

        assertFalse(source.exists())
        assertEquals("nouvelle", cible.readText())
    }

    @Test
    fun `un nouvel essai ne redeplace rien et ne supprime rien`() {
        val photos = dossiers.newFolder("photos")
        val rangee = fichier(photos, RangementFiche.enAttente("fiche-207-recto.jpg"), "nouvelle")

        // La prise pointe déjà sur la photo rangée : source et cible sont le même fichier.
        RangementFiche.deplacer(rangee, File(photos, rangee.name))
        assertEquals("nouvelle", rangee.readText())

        // La prise pointe encore sur le cache, vidé par le premier essai : la photo rangée reste.
        val cache = dossiers.newFolder("cache")
        RangementFiche.deplacer(File(cache, "recto-1.jpg"), rangee)
        assertEquals("nouvelle", rangee.readText())
    }

    @Test
    fun `sans source ni cible rien n est touche`() {
        val photos = dossiers.newFolder("photos")
        val autre = fichier(photos, "fiche-207-recto.jpg", "ancienne")

        val erreur = runCatching { RangementFiche.deplacer(File(photos, "absente.jpg"), File(photos, "cible.jpg")) }.exceptionOrNull()

        assertTrue(erreur is PhotoIntrouvable)
        assertFalse(File(photos, "cible.jpg").exists())
        assertEquals("ancienne", autre.readText())
    }

    @Test
    fun `la photo en attente remplace celle de la fiche precedente`() {
        val photos = dossiers.newFolder("photos")
        val ancienne = fichier(photos, "fiche-207-recto.jpg", "ancienne")
        val attente = fichier(photos, RangementFiche.enAttente("fiche-207-recto.jpg"), "nouvelle")

        RangementFiche.deplacer(attente, ancienne)

        assertFalse(attente.exists())
        assertEquals("nouvelle", ancienne.readText())
        // Rejoué (échec juste après) : la photo définitive reste.
        RangementFiche.deplacer(attente, ancienne)
        assertEquals("nouvelle", ancienne.readText())
    }

    @Test
    fun `les photos en attente oubliees se retrouvent`() {
        val photos = dossiers.newFolder("photos")
        val oubliee = fichier(photos, RangementFiche.enAttente("fiche-207-recto.jpg"), "x")
        fichier(photos, "fiche-207-recto.jpg", "y")
        fichier(photos, "etiquette-207.jpg", "z")

        assertEquals(listOf(oubliee), RangementFiche.oubliees(photos))
    }
}
