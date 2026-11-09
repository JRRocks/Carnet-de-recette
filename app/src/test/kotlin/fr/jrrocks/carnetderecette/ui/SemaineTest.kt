package fr.jrrocks.carnetderecette.ui

import fr.jrrocks.carnetderecette.capture.SemaineFactice
import fr.jrrocks.carnetderecette.core.cuisine.ModeCuisine
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `la ligne suit la semaine, du scan a la cuisine`() {
        val jour = LocalDate.of(2026, 10, 31)
        fun etapes(semaine: EtatSemaine?, mode: ModeCuisine = ModeCuisine.A_DEUX) =
            etapesDeLaSemaine(EtatCarnet(EtatDrive.NonConnecte, EtatMiseAJour.Aucune, "0.6", jour, semaine, mode = mode)).associateBy { it.titre }

        // Tout est dans l'app : plus aucune étape annoncée « pas encore dans l'app ».
        val vide = etapes(SemaineFactice.etat(avecBoxes = false))
        assertEquals(listOf(EtatEtape.EN_COURS, EtatEtape.A_FAIRE, EtatEtape.A_FAIRE, EtatEtape.A_FAIRE), listOf("Vendredi", "Vendredi soir", "Samedi", "En cuisine").map { vide.getValue(it).etat })
        assertTrue(etapes(null).values.none { it.etat == EtatEtape.PAS_ENCORE })

        // Box scannées : on envoie leurs recettes à Claude ; l'import viendra ensuite.
        val scannees = etapes(SemaineFactice.etat())
        assertEquals(listOf(EtatEtape.FAITE, EtatEtape.EN_COURS, EtatEtape.A_FAIRE, EtatEtape.A_FAIRE), listOf("Vendredi", "Vendredi soir", "Samedi", "En cuisine").map { scannees.getValue(it).etat })

        // La semaine importée : ce soir, la première recette que Claude conseille.
        val importee = etapes(SemaineFactice.etatImporte())
        assertEquals("Claude a préparé les 2 recettes\u00A0: 14\u00A0tâches pour vous deux, 14\u00A0segments à écouter.", importee.getValue("Vendredi soir").detail)
        assertEquals(EtatEtape.FAITE, importee.getValue("Samedi").etat)
        assertEquals(EtatEtape.EN_COURS, importee.getValue("En cuisine").etat)
        assertEquals("Ce soir\u00A0: la 207, environ 29\u00A0min à deux.", importee.getValue("En cuisine").detail)
        assertEquals("Ce soir\u00A0: la 207, environ 32\u00A0min seul.", etapes(SemaineFactice.etatImporte(), ModeCuisine.SEUL).getValue("En cuisine").detail)
        assertEquals("Ce soir\u00A0: la 203, environ 14\u00A0min à deux.", etapes(SemaineFactice.etatImporte(cuisinees = setOf(207))).getValue("En cuisine").detail)
        // Le téléphone qui n'a rien scanné : une fois la semaine importée, le vendredi est fait.
        val secondTelephone = etapes(SemaineFactice.etat(avecBoxes = false).copy(travail = SemaineFactice.travail))
        assertEquals(EtatEtape.FAITE, secondTelephone.getValue("Vendredi").etat)
        assertEquals(listOf("En cuisine"), secondTelephone.values.filter { it.etat == EtatEtape.EN_COURS }.map { it.titre })

        // Le choix de ce soir dans la Cuisine vaut aussi pour la Semaine.
        assertEquals("Ce soir\u00A0: la 203, environ 14\u00A0min à deux.", etapes(SemaineFactice.etatImporte().copy(choisie = 203)).getValue("En cuisine").detail)

        // Claude n'a rien pu préparer (accès au site rompu, sans fiche) : rien n'est « cuisiné ».
        val sansRecette = SemaineFactice.etat().copy(travail = SemaineFactice.travail.copy(recettes = emptyList(), message = "Le site ne répondait pas."))
        val rien = etapes(sansRecette)
        assertEquals(EtatEtape.EN_COURS, rien.getValue("Vendredi soir").etat)
        assertEquals(EtatEtape.A_FAIRE, rien.getValue("Samedi").etat)
        assertEquals(EtatEtape.A_FAIRE, rien.getValue("En cuisine").etat)
        assertEquals(listOf("Vendredi soir"), rien.values.filter { it.etat == EtatEtape.EN_COURS }.map { it.titre })

        // Le menu cassé : seulement des fiches papier, qui partent chez Claude comme des box.
        val fiche = fr.jrrocks.carnetderecette.core.fiche.FicheScannee(numero = 207, pages = emptyList(), prisLe = "t")
        val fiches = etapes(SemaineFactice.etat(avecBoxes = false).let { it.copy(fichier = it.fichier.copy(fiches = listOf(fiche))) })
        assertEquals("1\u00A0fiche photographiée.", fiches.getValue("Vendredi").detail)
        assertEquals(EtatEtape.EN_COURS, fiches.getValue("Vendredi soir").etat)

        val finie = etapes(SemaineFactice.etatImporte(cuisinees = setOf(207, 203))).getValue("En cuisine")
        assertEquals(EtatEtape.FAITE, finie.etat)
        assertEquals("Toutes les recettes de la semaine sont cuisinées.", finie.detail)
    }

    @Test
    fun `durees et pluriels`() {
        assertEquals("29\u00A0min", dureeEnClair(29))
        assertEquals("1\u00A0h", dureeEnClair(60))
        assertEquals("1\u00A0h\u00A005", dureeEnClair(65))
        assertEquals("2\u00A0h\u00A030", dureeEnClair(150))
        assertEquals("1\u00A0tâche", pluriel(1, "tâche", "tâches"))
        assertEquals("0\u00A0tâches", pluriel(0, "tâche", "tâches"))
        assertEquals("4\u00A0tâches", pluriel(4, "tâche", "tâches"))
    }

    @Test
    fun `la pile d'ecrans garde le cuisinier de la seance`() {
        val semaine = SemaineIso(2026, 44)
        for (qui in Qui.entries) {
            val seance = Destination.Seance(semaine, 207, qui)
            assertEquals(seance, Destination.lire(Destination.ecrire(seance)))
        }
        assertEquals(Destination.Seance(semaine, null), Destination.lire(Destination.ecrire(Destination.Seance(semaine, null))))
        // Une pile sauvée par la version 0.5 : la séance lisait les étapes de la recette.
        assertEquals(Destination.Seance(semaine, 207, Qui.RECETTE), Destination.lire("seance|2026-W44|207"))
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
