package fr.jrrocks.carnetderecette.core.audio

import fr.jrrocks.carnetderecette.core.recette.EtapeRecette
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.recette.RecetteExemple
import fr.jrrocks.carnetderecette.core.source.TypeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentsDeRecetteTest {

    private fun duree(texte: String) = SegmentsDeRecette.dureeEnSecondes(texte)

    @Test
    fun `une duree simple`() {
        assertEquals(300, duree("Fais-les revenir 5 min."))
        assertEquals(300, duree("5min"))
        assertEquals(300, duree("pendant 5 minutes"))
        assertEquals(60, duree("1 minute"))
        assertEquals(180, duree("3 mn"))
        assertEquals(90, duree("1,5 min"))
        assertEquals(300, duree("5 MIN"))
    }

    @Test
    fun `un intervalle donne la borne haute`() {
        assertEquals(840, duree("Laisse cuire 12 à 14 min en remuant."))
        assertEquals(840, duree("12-14 minutes"))
        assertEquals(840, duree("12 – 14 min"))
        assertEquals(840, duree("12 ou 14 min"))
        assertEquals(840, duree("14 à 12 min"))
        assertEquals(180, duree("2 a 3 min"))
    }

    @Test
    fun `plusieurs durees donnent la plus longue`() {
        assertEquals(600, duree("Fais revenir 2 min, puis laisse mijoter 10 min."))
        assertEquals(900, duree("Enfourne 15 min. Pendant ce temps, fais revenir 5 à 6 min."))
    }

    @Test
    fun `pas de duree`() {
        assertNull(duree("Préchauffe le four à 200 °C."))
        assertNull(duree("Étape 3"))
        assertNull(duree("Verse 600 ml de bouillon."))
        assertNull(duree("12 à 14"))
        assertNull(duree("quelques minutes"))
        assertNull(duree("min 5"))
        assertNull(duree("0 min"))
        assertNull(duree("1000 min"))
        assertNull(duree(""))
    }

    @Test
    fun `un segment par etape, dans l'ordre`() {
        val segments = SegmentsDeRecette.de(RecetteExemple.recette)

        assertEquals(listOf("e1", "e2", "e3", "e4", "e5", "e6"), segments.map { it.id })
        assertEquals((1..6).map { "etape-$it" }, segments.map { it.tache })
        segments.forEachIndexed { i, segment -> assertTrue(segment.texte, segment.texte.startsWith("Étape ${i + 1}. ")) }
        assertTrue(segments.all { it.actif })
    }

    @Test
    fun `les minuteurs et les declencheurs se deduisent des durees`() {
        val segments = SegmentsDeRecette.de(RecetteExemple.recette)

        assertEquals(listOf(null, 300, null, 840, null, null), segments.map { it.minuteurSecondes })
        assertEquals(
            listOf(
                Declencheur.MANUEL,
                Declencheur.FIN_TACHE_PRECEDENTE,
                Declencheur.FIN_MINUTEUR,
                Declencheur.FIN_TACHE_PRECEDENTE,
                Declencheur.FIN_MINUTEUR,
                Declencheur.FIN_TACHE_PRECEDENTE,
            ),
            segments.map { it.declencheur },
        )
    }

    @Test
    fun `le texte est ecrit pour l'oral`() {
        val segments = SegmentsDeRecette.de(RecetteExemple.recette)

        assertEquals(
            "Étape 2. Dans une sauteuse, fais chauffer une cuillère à soupe d'huile d'olive à feu moyen. " +
                "Ajoute le poireau et l'échalote et fais-les revenir 5 minutes.",
            segments[1].texte,
        )
        assertTrue(segments[4].texte, "une demi-cuillère à café de zeste" in segments[4].texte)
        assertTrue(segments[0].texte, "600 millilitres d'eau" in segments[0].texte)
    }

    @Test
    fun `une premiere etape minutee ne change pas le declencheur de la premiere`() {
        val recette = Recette(
            id = "essai",
            titre = "Essai",
            url = "",
            etapes = listOf(EtapeRecette(1, "Cuis 3 min."), EtapeRecette(2, "Sers.")),
        )
        val segments = SegmentsDeRecette.de(recette)
        assertEquals(listOf(Declencheur.MANUEL, Declencheur.FIN_MINUTEUR), segments.map { it.declencheur })
        assertEquals(listOf(180, null), segments.map { it.minuteurSecondes })
    }

    @Test
    fun `une recette sans etape ne donne aucun segment`() {
        assertEquals(emptyList<SegmentAudio>(), SegmentsDeRecette.de(Recette(id = "vide", titre = "Vide", url = "")))
    }

    @Test
    fun `la recette d'exemple est factice et complete`() {
        val recette = RecetteExemple.recette
        assertEquals("exemple", recette.id)
        assertEquals("Risotto aux poireaux (exemple)", recette.titre)
        assertEquals(TypeSource.HELLOFRESH, recette.source)
        assertEquals(listOf(2), recette.personnesPossibles)
        assertEquals((1..recette.etapes.size).toList(), recette.etapes.map { it.numero })
        assertTrue(recette.etapes.size in 5..6)
        assertTrue(recette.etapes.any { "12 à 14 min" in it.texte })
        assertTrue(recette.etapes.any { "5 min" in it.texte })
        // Chaque quantité désigne un ingrédient de la recette.
        val ids = recette.ingredients.map { it.id }.toSet()
        assertTrue(recette.portions.flatMap { it.quantites }.all { it.ingredient in ids })
    }
}
