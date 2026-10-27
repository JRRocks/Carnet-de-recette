package fr.jrrocks.carnetderecette.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Repris du moteur Library `scan-multimode` (tests de ScanList), packages renommés. */
class ScanConsensusTest {

    private fun candidate(value: String, symbology: String? = null) =
        listOf(ConsensusCandidate(value, symbology))

    @Test
    fun `en dessous du seuil rien n est valide`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        val secondAppel = consensus.submit(candidate("BAT-001"), now = 10)

        assertEquals(emptyList<ConsensusCandidate>(), secondAppel)
    }

    @Test
    fun `le seuil atteint valide la valeur`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)
        val troisieme = consensus.submit(candidate("BAT-001"), now = 20)

        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), troisieme)
    }

    @Test
    fun `la validation conserve la symbologie et le texte de la premiere lecture`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("bat-001", "EAN_13"), now = 0)
        consensus.submit(candidate("BAT-001", "EAN_13"), now = 10)
        val valide = consensus.submit(candidate("Bat-001", "EAN_13"), now = 20)

        // Même clé (casse ignorée), mais c'est le texte de la toute première lecture qui est gardé.
        assertEquals(listOf(ConsensusCandidate("bat-001", "EAN_13")), valide)
    }

    @Test
    fun `une valeur validee est aussitot purgee et peut repartir pour un nouveau consensus`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)
        consensus.submit(candidate("BAT-001"), now = 20)

        consensus.submit(candidate("BAT-001"), now = 30)
        val second = consensus.submit(candidate("BAT-001"), now = 40)

        assertEquals(emptyList<ConsensusCandidate>(), second)
    }

    @Test
    fun `une image sans lecture ne casse pas le compteur en cours`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(emptyList(), now = 50)
        consensus.submit(emptyList(), now = 90)
        val troisieme = consensus.submit(candidate("BAT-001"), now = 120)

        // Deux lectures seulement : le seuil n'est pas atteint, mais les images vides
        // intercalées n'ont pas remis le compteur à zéro.
        assertEquals(emptyList<ConsensusCandidate>(), troisieme)
        val quatrieme = consensus.submit(candidate("BAT-001"), now = 150)
        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), quatrieme)
    }

    @Test
    fun `un compteur expire au bout du TTL sans nouvelle lecture`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false, ttlMs = 800)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 100)
        // Plus de 800 ms depuis la dernière lecture : le compteur repart de zéro.
        consensus.submit(candidate("BAT-001"), now = 1_000)
        consensus.submit(candidate("BAT-001"), now = 1_100)
        val troisiemeDepuisRedemarrage = consensus.submit(candidate("BAT-001"), now = 1_200)

        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), troisiemeDepuisRedemarrage)
    }

    @Test
    fun `deux valeurs differentes progressent independamment`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        repeat(2) { i -> consensus.submit(candidate("BAT-001"), now = i * 10L) }
        val troisiemeA = consensus.submit(candidate("BAT-001"), now = 20)
        assertEquals(listOf(ConsensusCandidate("BAT-001", null)), troisiemeA)

        // XYZ-999 n'a été lu qu'une fois : il ne doit pas être validé.
        val etatXyz = consensus.submit(candidate("XYZ-999"), now = 30)
        assertEquals(emptyList<ConsensusCandidate>(), etatXyz)
    }

    @Test
    fun `une valeur vide ou illisible n est jamais validee`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        repeat(5) { i -> consensus.submit(candidate("   "), now = i * 10L) }

        assertEquals(0f, consensus.peakProgress())
    }

    // ── Garde d'ambiguïté ─────────────────────────────────────────────────────────

    @Test
    fun `deux valeurs a un caractere pres jamais vues ensemble repartent a zero`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = true)

        consensus.submit(candidate("AB123"), now = 0)
        // « AB1B3 » arrive seul, jamais vu à côté de « AB123 » : lecture ambiguë d'une même
        // étiquette, les deux compteurs repartent à zéro.
        consensus.submit(candidate("AB1B3"), now = 10)

        consensus.submit(candidate("AB123"), now = 20)
        val encoreInsuffisant = consensus.submit(candidate("AB123"), now = 30)
        assertEquals(emptyList<ConsensusCandidate>(), encoreInsuffisant)

        val troisiemeDepuisRemiseAZero = consensus.submit(candidate("AB123"), now = 40)
        assertEquals(listOf(ConsensusCandidate("AB123", null)), troisiemeDepuisRemiseAZero)
    }

    @Test
    fun `deux valeurs proches vues dans la meme image ne s annulent jamais`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = true)

        // Deux numéros consécutifs, vus ensemble à chaque image : deux objets distincts.
        val ensemble = listOf(ConsensusCandidate("47828"), ConsensusCandidate("47829"))
        consensus.submit(ensemble, now = 0)
        consensus.submit(ensemble, now = 10)
        val troisieme = consensus.submit(ensemble, now = 20)

        assertEquals(
            setOf(ConsensusCandidate("47828", null), ConsensusCandidate("47829", null)),
            troisieme.toSet(),
        )
    }

    @Test
    fun `la garde d ambiguite se desactive`() {
        val consensus = ScanConsensus(threshold = 2, ambiguityGuard = false)

        // Sans garde, deux valeurs à un caractère près ne s'influencent pas.
        consensus.submit(candidate("AAAA"), now = 0)
        consensus.submit(candidate("AAAB"), now = 10)
        val premiere = consensus.submit(candidate("AAAA"), now = 20)
        val seconde = consensus.submit(candidate("AAAB"), now = 30)

        assertEquals(listOf(ConsensusCandidate("AAAA", null)), premiere)
        assertEquals(listOf(ConsensusCandidate("AAAB", null)), seconde)
    }

    @Test
    fun `l ambiguite couvre aussi l insertion ou la suppression d un caractere`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = true)

        consensus.submit(candidate("BAT0001"), now = 0)
        // Un chiffre en moins : même étiquette, lecture tronquée.
        consensus.submit(candidate("BAT001"), now = 10)

        consensus.submit(candidate("BAT0001"), now = 20)
        val insuffisant = consensus.submit(candidate("BAT0001"), now = 30)

        assertEquals(emptyList<ConsensusCandidate>(), insuffisant)
    }

    @Test
    fun `distance d edition d au plus un`() {
        assertTrue(ScanConsensus.withinEditDistanceOne("207", "201"))
        assertTrue(ScanConsensus.withinEditDistanceOne("207", "2077"))
        assertTrue(ScanConsensus.withinEditDistanceOne("207", "27"))
        assertFalse(ScanConsensus.withinEditDistanceOne("207", "207"))
        assertFalse(ScanConsensus.withinEditDistanceOne("207", "315"))
        assertFalse(ScanConsensus.withinEditDistanceOne("207", "20777"))
    }

    // ── Avancement (réticule) ─────────────────────────────────────────────────────

    @Test
    fun `peakProgress suit le compteur le plus avance`() {
        val consensus = ScanConsensus(threshold = 4, ambiguityGuard = false)

        assertEquals(0f, consensus.peakProgress())

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)

        assertEquals(0.5f, consensus.peakProgress())
    }

    @Test
    fun `reset vide tous les compteurs et l avancement`() {
        val consensus = ScanConsensus(threshold = 3, ambiguityGuard = false)

        consensus.submit(candidate("BAT-001"), now = 0)
        consensus.submit(candidate("BAT-001"), now = 10)
        consensus.reset()

        assertEquals(0f, consensus.peakProgress())
        val apresReset = consensus.submit(candidate("BAT-001"), now = 20)
        assertTrue(apresReset.isEmpty())
    }
}
