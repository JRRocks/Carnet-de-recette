package fr.jrrocks.carnetderecette.core.cuisine

import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.audio.Declencheur.FIN_MINUTEUR
import fr.jrrocks.carnetderecette.core.audio.Declencheur.FIN_TACHE_PRECEDENTE
import fr.jrrocks.carnetderecette.core.audio.Declencheur.MANUEL
import fr.jrrocks.carnetderecette.core.audio.LecteurSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentsDuCuisinierTest {

    private val recette = SemainePrepareeFactice.recette

    private fun SegmentsDeLaSeance.ids() = segments.map { it.id }
    private fun SegmentsDeLaSeance.declencheurs() = segments.map { it.declencheur }

    @Test
    fun `a deux, la decoupe n'entend que ses segments, dans l'ordre de Claude`() {
        val decoupe = SegmentsDuCuisinier.aDeux(recette, RoleCuisine.DECOUPE)
        assertEquals(listOf("a1", "a2", "a3", "a4"), decoupe.ids())
        assertEquals(listOf(MANUEL, FIN_TACHE_PRECEDENTE, FIN_TACHE_PRECEDENTE, FIN_TACHE_PRECEDENTE), decoupe.declencheurs())
        // À deux, le texte qui parle de l'autre cuisinier.
        assertEquals("Roule les dés dans les trente grammes de farine, puis passe-les à la chauffe.", decoupe.textes["a2"])
    }

    @Test
    fun `a deux, la chauffe garde ses minuteurs enchaines`() {
        val chauffe = SegmentsDuCuisinier.aDeux(recette, RoleCuisine.CHAUFFE)
        assertEquals(listOf("a5", "a9", "a6", "a7", "a8"), chauffe.ids())
        assertEquals(listOf(MANUEL, FIN_TACHE_PRECEDENTE, FIN_TACHE_PRECEDENTE, FIN_MINUTEUR, FIN_MINUTEUR), chauffe.declencheurs())
        assertEquals(listOf(null, null, 360, 840, null), chauffe.segments.map { it.minuteurSecondes })
    }

    @Test
    fun `seul, les segments suivent le plan seul et parlent a une seule personne`() {
        val seul = SegmentsDuCuisinier.seul(recette)
        // « Attends que la découpe… » n'a pas de sens seul : sauté.
        assertEquals(listOf("a5", "a1", "a2", "a3", "a6", "a7", "a4", "a8"), seul.ids())
        assertEquals("Roule les dés dans les trente grammes de farine.", seul.textes["a2"])
        // « Sors le plat » suit maintenant le lavage des mains, sans minuteur : il se lit sur demande.
        assertEquals(listOf(MANUEL, MANUEL, FIN_TACHE_PRECEDENTE, FIN_TACHE_PRECEDENTE, FIN_TACHE_PRECEDENTE, FIN_MINUTEUR, FIN_TACHE_PRECEDENTE, MANUEL), seul.declencheurs())
    }

    @Test
    fun `la seance ne part jamais toute seule`() {
        val autre = recette.copy(segments = recette.segments.map { it.copy(declencheur = Declencheur.FIN_TACHE_PRECEDENTE) })
        for (role in RoleCuisine.entries) assertEquals(MANUEL, SegmentsDuCuisinier.aDeux(autre, role).segments.first().declencheur)
        assertEquals(MANUEL, SegmentsDuCuisinier.seul(autre).segments.first().declencheur)
        // Et le lecteur l'ouvre sans rien dire.
        val lecteur = LecteurSegments(SegmentsDuCuisinier.aDeux(autre, RoleCuisine.CHAUFFE).segments)
        assertEquals(emptyList<Any>(), lecteur.demarrer())
    }

    @Test
    fun `la voix lit la version orale, l'ecran garde le texte de Claude`() {
        val ecrit = "Ajoute ½ cc de sel."
        val autre = recette.copy(segments = listOf(SegmentPrepare(id = "s1", role = RoleCuisine.CHAUFFE, texte = ecrit)))
        val seance = SegmentsDuCuisinier.aDeux(autre, RoleCuisine.CHAUFFE)
        assertEquals("Ajoute une demi-cuillère à café de sel.", seance.segments.single().texte)
        assertEquals(ecrit, seance.textes["s1"])
    }

    @Test
    fun `un segment sans role ni tache va a la chauffe`() {
        val autre = recette.copy(segments = listOf(SegmentPrepare(id = "s1", texte = "Bon appétit.")))
        assertTrue(SegmentsDuCuisinier.aDeux(autre, RoleCuisine.DECOUPE).segments.isEmpty())
        assertEquals(listOf("s1"), SegmentsDuCuisinier.aDeux(autre, RoleCuisine.CHAUFFE).ids())
    }
}
