package fr.jrrocks.carnetderecette.core.audio

import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.ARRETER
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.MINUTEUR
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.PAUSE
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.PRECEDENT
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.REPETER
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.REPRENDRE
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale.SUIVANT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnaissanceCommandesTest {

    private fun entendu(vararg alternatives: String) = ReconnaissanceCommandes.reconnaitre(alternatives.toList())

    private fun verifier(attendu: CommandeVocale?, vararg phrases: String) {
        for (phrase in phrases) assertEquals("pour «\u00A0$phrase\u00A0»", attendu, entendu(phrase))
    }

    @Test
    fun `chaque phrase connue donne sa commande`() {
        verifier(SUIVANT, "suivant", "suivante", "c'est fait", "fait", "j'ai fini", "ok", "d'accord", "étape suivante")
        verifier(REPETER, "répète", "répète s'il te plaît", "encore", "redis", "relis")
        verifier(PRECEDENT, "précédent", "retour", "étape précédente")
        verifier(PAUSE, "pause", "attends", "stop", "tais-toi", "silence")
        verifier(REPRENDRE, "reprends", "continue", "vas-y")
        verifier(MINUTEUR, "minuteur", "lance le minuteur", "chrono")
        verifier(ARRETER, "arrête d'écouter", "coupe le micro")
    }

    @Test
    fun `majuscules, accents et ponctuation ne comptent pas`() {
        verifier(SUIVANT, "Suivant", "SUIVANT", "Suivant !", "suivant...", "  suivant  ", "C'est fait.", "c est fait", "C’est fait", "Étape suivante", "etape suivante", "D'ACCORD", "OK.")
        verifier(REPETER, "Répète !", "REPETE", "repete", "Répète, s'il te plaît.", "repete s il te plait")
        verifier(PRECEDENT, "Précédent ?", "precedent", "ÉTAPE PRÉCÉDENTE")
        verifier(PAUSE, "Tais-toi !", "tais toi", "Stop.")
        verifier(REPRENDRE, "Vas-y", "vas y", "Continue !")
        verifier(ARRETER, "Arrête d'écouter.", "arrete d ecouter", "Coupe le micro !")
        verifier(MINUTEUR, "Minuteur !", "lance le minuteur.")
    }

    @Test
    fun `quelques mots autour de la commande passent`() {
        verifier(SUIVANT, "euh suivant", "bon c'est fait", "voilà c'est fait", "ça y est c'est fait")
        verifier(REPETER, "tu peux répète s'il te plaît")
        verifier(MINUTEUR, "allez lance le minuteur")
    }

    @Test
    fun `la phrase la plus longue l'emporte`() {
        // « ok » (suivant) et « vas-y » (reprendre) : la plus longue gagne.
        verifier(REPRENDRE, "ok vas-y")
        verifier(PRECEDENT, "étape précédente")
        verifier(SUIVANT, "étape suivante")
    }

    @Test
    fun `a longueur egale, la premiere dite`() {
        verifier(PAUSE, "stop suivant")
        verifier(SUIVANT, "suivant stop")
    }

    @Test
    fun `une negation juste avant annule la commande`() {
        verifier(null, "c'est pas fait", "pas fait", "ce n'est pas fait", "pas encore", "ne continue pas", "pas suivant", "jamais fait")
    }

    @Test
    fun `une negation dans la premiere alternative ne laisse pas passer les suivantes`() {
        assertNull(entendu("c'est pas fait", "c'est fait"))
    }

    @Test
    fun `la premiere alternative qui donne une commande gagne`() {
        assertEquals(SUIVANT, entendu("suivant", "pause"))
        assertEquals(PAUSE, entendu("bonjour", "pause", "suivant"))
        assertEquals(REPETER, entendu("tu as fait les courses", "répète"))
    }

    @Test
    fun `une phrase inconnue ou de la conversation ne fait rien`() {
        verifier(null, "bonjour", "", "   ", "!!!", "il fait beau", "tu as fait les courses", "suivants", "faite", "défait", "minuteurs", "encore deux minutes et on mange")
        assertNull(entendu())
        assertNull(entendu("bonjour", "au revoir"))
    }

    @Test
    fun `la normalisation retire accents, majuscules et ponctuation`() {
        assertEquals(listOf("c", "est", "fait"), ReconnaissanceCommandes.normaliser("C'est fait !"))
        assertEquals(listOf("tais", "toi"), ReconnaissanceCommandes.normaliser("Tais-toi"))
        assertEquals(listOf("etape", "precedente"), ReconnaissanceCommandes.normaliser("  ÉTAPE   Précédente. "))
        assertEquals(listOf("j", "ai", "fini"), ReconnaissanceCommandes.normaliser("J’ai fini"))
        assertEquals(emptyList<String>(), ReconnaissanceCommandes.normaliser(" ?! "))
    }

    @Test
    fun `chaque commande a un libelle a montrer`() {
        assertEquals("suivant", SUIVANT.libelle)
        assertEquals("arrête d'écouter", ARRETER.libelle)
    }
}
