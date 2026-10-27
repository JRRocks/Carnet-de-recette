package fr.jrrocks.carnetderecette.ui

import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SemaineTest {

    @Test
    fun `la semaine va du vendredi de livraison au jeudi suivant`() {
        assertEquals(LocalDate.of(2026, 10, 23), vendrediDeReference(LocalDate.of(2026, 10, 29))) // jeudi : box du 23
        assertEquals(LocalDate.of(2026, 10, 30), vendrediDeReference(LocalDate.of(2026, 10, 30))) // vendredi de livraison
        assertEquals(LocalDate.of(2026, 10, 30), vendrediDeReference(LocalDate.of(2026, 10, 31))) // samedi, jour de l'import
        assertEquals(LocalDate.of(2026, 10, 30), vendrediDeReference(LocalDate.of(2026, 11, 1))) // dimanche
        // Du lundi au jeudi, on cuisine les box du vendredi passé : leurs recettes restent là.
        assertEquals(LocalDate.of(2026, 10, 30), vendrediDeReference(LocalDate.of(2026, 11, 2))) // lundi
        assertEquals(LocalDate.of(2026, 10, 30), vendrediDeReference(LocalDate.of(2026, 11, 5))) // jeudi
        assertEquals(LocalDate.of(2026, 11, 6), vendrediDeReference(LocalDate.of(2026, 11, 6))) // vendredi suivant
        // Le 1er janvier 2027 est un vendredi : la semaine 53 de 2026 court jusqu'au jeudi 7.
        assertEquals(LocalDate.of(2027, 1, 1), vendrediDeReference(LocalDate.of(2027, 1, 7)))
    }

    @Test
    fun `le titre et les dates de la ligne parlent de la meme semaine`() {
        for (jour in generateSequence(LocalDate.of(2026, 10, 24)) { it.plusDays(1) }.take(14)) {
            val etat = EtatCarnet(EtatDrive.NonConnecte, EtatMiseAJour.Aucune, "0.2", jour)
            val vendredi = vendrediDeReference(jour)
            val etapes = etapesDeLaSemaine(etat)
            assertEquals(dateCourte(vendredi), etapes.first { it.titre == "Vendredi" }.fin)
            assertEquals(dateCourte(vendredi.plusDays(1)), etapes.first { it.titre == "Samedi" }.fin)
            assertEquals("Semaine ${numeroSemaine(vendredi)}", titreSemaine(jour))
            // Les données (box, recettes, scanner, séance) sont celles de la semaine du titre.
            assertEquals(SemaineIso.de(vendredi), etat.semaineIso)
            assertEquals(numeroSemaine(vendredi), etat.semaineIso.numero)
        }
    }

    @Test
    fun `numero de semaine des agendas et date courte en francais`() {
        assertEquals(44, numeroSemaine(LocalDate.of(2026, 10, 30)))
        assertEquals(53, numeroSemaine(LocalDate.of(2026, 12, 31)))
        assertEquals("30\u00A0oct.", dateCourte(LocalDate.of(2026, 10, 30)))
        assertEquals("1er\u00A0janv.", dateCourte(LocalDate.of(2027, 1, 1)))
        assertEquals("1er\u00A0mai", dateCourte(LocalDate.of(2027, 5, 1)))
    }

    @Test
    fun `l action requise suit le Drive et la mise a jour`() {
        val jour = LocalDate.of(2026, 10, 26)
        fun etat(drive: EtatDrive, maj: EtatMiseAJour = EtatMiseAJour.Aucune) = EtatCarnet(drive, maj, "0.2", jour)
        assertEquals(true, etat(EtatDrive.NonConnecte).actionRequise)
        assertEquals(true, etat(EtatDrive.Erreur("motif")).actionRequise)
        assertEquals(false, etat(EtatDrive.EnCours).actionRequise)
        assertEquals(false, etat(EtatDrive.Pret("foyer@example.org", horsLigne = true)).actionRequise)
        assertEquals(false, etat(EtatDrive.Verification("foyer@example.org")).actionRequise)
        assertEquals(true, etat(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Disponible("0.3")).actionRequise)
        assertEquals(true, etat(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.AAutoriser("0.3")).actionRequise)
        assertEquals(true, etat(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Echec("0.3", "motif")).actionRequise)
        // Rien à faire pendant le téléchargement, ni après « Plus tard ».
        assertEquals(false, etat(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Telechargement("0.3", 0.4f)).actionRequise)
        assertEquals(false, etat(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Ecartee("0.3")).actionRequise)
    }
}
