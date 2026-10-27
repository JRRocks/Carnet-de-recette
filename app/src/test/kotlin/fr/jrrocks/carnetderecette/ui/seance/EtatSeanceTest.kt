package fr.jrrocks.carnetderecette.ui.seance

import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale
import fr.jrrocks.carnetderecette.core.audio.LecteurSegments
import fr.jrrocks.carnetderecette.core.audio.SegmentsDeRecette
import fr.jrrocks.carnetderecette.core.recette.RecetteExemple
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtatSeanceTest {

    private val recette = RecetteExemple.recette
    private val lecteur = LecteurSegments(SegmentsDeRecette.de(recette)).apply { demarrer() }
    private val textes = recette.etapes.associate { SegmentsDeRecette.idDe(it) to it.texte }

    private fun etat(restants: Map<String, Int> = emptyMap(), entendu: Entendu? = null) = etatSeance(
        titre = recette.titre,
        lecteur = lecteur.etat,
        textes = textes,
        restants = restants,
        voix = EtatVoix.PRETE,
        voixCoupee = false,
        micro = EtatEcoute.Arretee,
        entendu = entendu,
    )

    @Test
    fun `au debut, la premiere etape et les deux suivantes`() {
        val e = etat()
        assertNull(e.precedent)
        assertEquals("e1", e.courant?.id)
        assertEquals(EtatSegment.EN_COURS, e.courant?.etat)
        assertEquals(listOf("e2", "e3"), e.suivants.map { it.id })
        assertTrue(e.suivants.all { it.etat == EtatSegment.A_VENIR })
        assertEquals(3, e.restants)
        assertFalse(e.dejaDit)
        assertFalse(e.terminee)
        // L'étape 1 n'a pas de durée : pas de minuteur au dock.
        assertNull(e.minuteur)
    }

    @Test
    fun `l'ecran garde le texte d'origine, la voix lit la version orale`() {
        repeat(4) { lecteur.valider() }
        val e = etat()
        assertEquals("e5", e.courant?.id)
        assertTrue(e.courant!!.texte, "½ cc" in e.courant!!.texte)
        assertTrue("une demi-cuillère à café" in lecteur.etat.segmentCourant!!.texte)
    }

    @Test
    fun `apres valider, l'etape faite est rappelee et le minuteur propose`() {
        lecteur.valider()
        val e = etat()
        assertEquals("e1", e.precedent?.id)
        assertEquals(EtatSegment.FAIT, e.precedent?.etat)
        assertEquals("e2", e.courant?.id)
        assertTrue(e.dejaDit)
        assertEquals(MinuteurEcran("e2", 2, 300, enMarche = false), e.minuteur)
        assertTrue(e.autresMinuteurs.isEmpty())
    }

    @Test
    fun `un minuteur qui tourne montre le temps restant`() {
        lecteur.valider()
        lecteur.lancerMinuteur()
        assertEquals(MinuteurEcran("e2", 2, 247, enMarche = true), etat(mapOf("e2" to 247)).minuteur)
    }

    @Test
    fun `le minuteur d'une etape deja validee reste au dock`() {
        lecteur.valider()
        lecteur.lancerMinuteur()
        lecteur.valider()
        val e = etat(mapOf("e2" to 200))
        assertEquals("e3", e.courant?.id)
        assertEquals(MinuteurEcran("e2", 2, 200, enMarche = true), e.minuteur)
        assertTrue(e.autresMinuteurs.isEmpty())
    }

    @Test
    fun `le minuteur du courant passe devant, les autres s'affichent a part`() {
        lecteur.valider()
        lecteur.lancerMinuteur()
        lecteur.valider()
        lecteur.valider()
        val e = etat(mapOf("e2" to 60))
        assertEquals("e4", e.courant?.id)
        assertEquals(MinuteurEcran("e4", 4, 840, enMarche = false), e.minuteur)
        assertEquals(listOf(MinuteurEcran("e2", 2, 60, enMarche = true)), e.autresMinuteurs)
    }

    @Test
    fun `le minuteur du courant reste au dock meme si un autre finit avant`() {
        lecteur.valider()
        val e = etat(mapOf("e2" to 90, "e4" to 30))
        assertEquals("e2", e.courant?.id)
        assertEquals(MinuteurEcran("e2", 2, 90, enMarche = true), e.minuteur)
        assertEquals(listOf(MinuteurEcran("e4", 4, 30, enMarche = true)), e.autresMinuteurs)
    }

    @Test
    fun `vers la fin, moins de suivants et plus rien apres`() {
        repeat(4) { lecteur.valider() }
        val e = etat()
        assertEquals("e5", e.courant?.id)
        assertEquals(listOf("e6"), e.suivants.map { it.id })
        assertEquals(0, e.restants)
    }

    @Test
    fun `un segment coupe est montre comme tel`() {
        lecteur.basculer("e3")
        val e = etat()
        assertFalse(e.suivants.single { it.id == "e3" }.actif)
        assertTrue(e.suivants.single { it.id == "e2" }.actif)
    }

    @Test
    fun `la seance terminee`() {
        repeat(6) { lecteur.valider() }
        val e = etat()
        assertTrue(e.terminee)
        assertNull(e.courant)
        assertTrue(e.suivants.isEmpty())
        assertEquals(0, e.restants)
        assertEquals("e6", e.precedent?.id)
    }

    @Test
    fun `ce que le micro a compris passe tel quel`() {
        val compris = Entendu.Compris(CommandeVocale.SUIVANT)
        assertEquals(compris, etat(entendu = compris).entendu)
    }

    @Test
    fun `les durees a lire et a entendre`() {
        assertEquals("5:00", duree(300))
        assertEquals("14:00", duree(840))
        assertEquals("4:07", duree(247))
        assertEquals("0:07", duree(7))
        assertEquals("1:05:00", duree(3900))
        assertEquals("4 minutes 59", dureeDite(299))
        assertEquals("1 minute", dureeDite(60))
        assertEquals("1 minute 1", dureeDite(61))
        assertEquals("14 minutes", dureeDite(840))
        assertEquals("45 secondes", dureeDite(45))
        assertEquals("1 seconde", dureeDite(1))
    }
}
