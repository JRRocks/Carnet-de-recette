package fr.jrrocks.carnetderecette.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SemaineTest {

    @Test
    fun `le prochain vendredi est celui de la semaine, ou aujourd hui le vendredi`() {
        assertEquals(LocalDate.of(2026, 10, 30), prochainVendredi(LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 10, 30), prochainVendredi(LocalDate.of(2026, 10, 30)))
        assertEquals(LocalDate.of(2026, 11, 6), prochainVendredi(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun `numero de semaine des agendas et date courte en francais`() {
        assertEquals(44, numeroSemaine(LocalDate.of(2026, 10, 30)))
        assertEquals(53, numeroSemaine(LocalDate.of(2026, 12, 31)))
        assertEquals("30 oct.", dateCourte(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun `l action requise suit le Drive et la mise a jour`() {
        val jour = LocalDate.of(2026, 10, 26)
        fun etat(drive: EtatDrive, maj: EtatMiseAJour = EtatMiseAJour.Aucune) = EtatCarnet(drive, maj, "0.2", jour)
        assertEquals(true, etat(EtatDrive.NonConnecte).actionRequise)
        assertEquals(true, etat(EtatDrive.Erreur("motif")).actionRequise)
        assertEquals(false, etat(EtatDrive.EnCours).actionRequise)
        assertEquals(false, etat(EtatDrive.Pret("foyer@example.org", horsLigne = true)).actionRequise)
        assertEquals(true, etat(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Disponible("0.3")).actionRequise)
    }
}
