package fr.jrrocks.carnetderecette.core.audio

import fr.jrrocks.carnetderecette.core.audio.Declencheur.FIN_MINUTEUR
import fr.jrrocks.carnetderecette.core.audio.Declencheur.FIN_TACHE_PRECEDENTE
import fr.jrrocks.carnetderecette.core.audio.Declencheur.MANUEL
import fr.jrrocks.carnetderecette.core.audio.Effet.AnnulerMinuteur
import fr.jrrocks.carnetderecette.core.audio.Effet.Dire
import fr.jrrocks.carnetderecette.core.audio.Effet.LancerMinuteur
import fr.jrrocks.carnetderecette.core.audio.Effet.Sonner
import fr.jrrocks.carnetderecette.core.audio.Effet.Taire
import fr.jrrocks.carnetderecette.core.audio.Effet.Terminee
import fr.jrrocks.carnetderecette.core.recette.RecetteExemple
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LecteurSegmentsTest {

    private fun seg(id: String, declencheur: Declencheur = FIN_TACHE_PRECEDENTE, minuteur: Int? = null, actif: Boolean = true) =
        SegmentAudio(id = id, texte = "texte $id", declencheur = declencheur, minuteurSecondes = minuteur, actif = actif)

    private fun dire(id: String) = Dire(id, "texte $id")

    /** a (manuel), b (minuteur de 60 s), c (à la fin du minuteur), d. */
    private fun lecteur(vararg segments: SegmentAudio = arrayOf(seg("a", MANUEL), seg("b", minuteur = 60), seg("c", FIN_MINUTEUR), seg("d"))) =
        LecteurSegments(segments.toList())

    private val LecteurSegments.idCourant get() = etat.segmentCourant?.id

    // ── Démarrer ────────────────────────────────────────────────────────────────

    @Test
    fun `un premier segment manuel attend qu'on le demande`() {
        val l = lecteur()
        assertFalse(l.etat.demarree)

        assertEquals(emptyList<Effet>(), l.demarrer())
        assertTrue(l.etat.demarree)
        assertEquals("a", l.idCourant)
        assertFalse(l.etat.parle)
        assertTrue(l.etat.dits.isEmpty())
    }

    @Test
    fun `un premier segment automatique est dit des le debut`() {
        val l = lecteur(seg("a"), seg("b"))
        assertEquals(listOf(dire("a")), l.demarrer())
        assertEquals("a", l.etat.enLecture)
        assertEquals(setOf("a"), l.etat.dits)
    }

    @Test
    fun `demarrer deux fois ne fait rien de plus`() {
        val l = lecteur(seg("a"), seg("b"))
        l.demarrer()
        assertEquals(emptyList<Effet>(), l.demarrer())
    }

    @Test
    fun `le premier segment coupe est saute au demarrage`() {
        val l = lecteur(seg("a", actif = false), seg("b", MANUEL), seg("c"))
        l.demarrer()
        assertEquals("b", l.idCourant)
    }

    @Test
    fun `tous les segments coupes laissent le premier affiche`() {
        val l = lecteur(seg("a", actif = false), seg("b", actif = false))
        assertEquals(emptyList<Effet>(), l.demarrer())
        assertEquals("a", l.idCourant)
        assertEquals(emptyList<Effet>(), l.relire())
        assertEquals(listOf(Terminee), l.valider())
    }

    @Test
    fun `une seance sans segment est terminee tout de suite`() {
        val l = lecteur(*emptyArray<SegmentAudio>())
        assertEquals(listOf(Terminee), l.demarrer())
        assertTrue(l.etat.terminee)
        assertEquals(emptyList<Effet>(), l.valider())
    }

    @Test
    fun `rien ne bouge avant le demarrage`() {
        val l = lecteur()
        assertEquals(emptyList<Effet>(), l.valider())
        assertEquals(emptyList<Effet>(), l.relire())
        assertEquals(emptyList<Effet>(), l.precedent())
        assertEquals(emptyList<Effet>(), l.pause())
        assertEquals(emptyList<Effet>(), l.reprendre())
        assertEquals(emptyList<Effet>(), l.lancerMinuteur())
        assertFalse(l.etat.demarree)
    }

    // ── Valider, relire ─────────────────────────────────────────────────────────

    @Test
    fun `relire dit le segment courant, puis la fin de lecture le libere`() {
        val l = lecteur()
        l.demarrer()

        assertEquals(listOf(dire("a")), l.relire())
        assertTrue(l.etat.parle)
        // Pendant qu'elle parle, relire repart du début.
        assertEquals(listOf(Taire, dire("a")), l.relire())

        l.finDeLecture("autre")
        assertTrue(l.etat.parle)
        l.finDeLecture("a")
        assertFalse(l.etat.parle)
        assertEquals(setOf("a"), l.etat.dits)
    }

    @Test
    fun `valider fait le courant et dit tout de suite le suivant`() {
        val l = lecteur()
        l.demarrer()

        assertEquals(listOf(dire("b")), l.valider())
        assertEquals("b", l.idCourant)
        assertEquals(setOf("a"), l.etat.faits)
        assertEquals("b", l.etat.enLecture)
    }

    @Test
    fun `valider pendant que la voix parle la fait taire d'abord`() {
        val l = lecteur()
        l.demarrer()
        l.relire()
        assertEquals(listOf(Taire, dire("b")), l.valider())
    }

    @Test
    fun `valider dit le suivant quel que soit son declencheur`() {
        val l = lecteur(seg("a", MANUEL), seg("b", MANUEL), seg("c", FIN_MINUTEUR))
        l.demarrer()
        assertEquals(listOf(dire("b")), l.valider())
        l.finDeLecture("b")
        assertEquals(listOf(dire("c")), l.valider())
    }

    @Test
    fun `valider saute les segments coupes`() {
        val l = lecteur(seg("a", MANUEL), seg("b", actif = false), seg("c", actif = false), seg("d"))
        l.demarrer()
        assertEquals(listOf(dire("d")), l.valider())
        assertEquals("d", l.idCourant)
        assertEquals(setOf("a"), l.etat.faits)
    }

    @Test
    fun `le dernier segment valide termine la seance`() {
        val l = lecteur(seg("a", MANUEL), seg("b"))
        l.demarrer()
        l.valider()

        assertEquals(listOf(Taire, Terminee), l.valider())
        assertTrue(l.etat.terminee)
        assertNull(l.etat.segmentCourant)
        assertEquals(setOf("a", "b"), l.etat.faits)
        assertFalse(l.etat.parle)
        // Terminée, plus rien ne part.
        assertEquals(emptyList<Effet>(), l.valider())
        assertEquals(emptyList<Effet>(), l.relire())
        assertEquals(emptyList<Effet>(), l.pause())
        assertEquals(emptyList<Effet>(), l.reprendre())
        assertEquals(emptyList<Effet>(), l.lancerMinuteur())
    }

    @Test
    fun `les segments coupes a la fin ne retardent pas la fin`() {
        val l = lecteur(seg("a", MANUEL), seg("b", actif = false))
        l.demarrer()
        assertEquals(listOf(Terminee), l.valider())
        assertTrue(l.etat.terminee)
    }

    // ── Précédent ───────────────────────────────────────────────────────────────

    @Test
    fun `precedent revient au segment d'avant, qui n'est plus fait`() {
        val l = lecteur()
        l.demarrer()
        l.valider()
        l.valider()
        assertEquals("c", l.idCourant)

        assertEquals(listOf(Taire, dire("b")), l.precedent())
        assertEquals("b", l.idCourant)
        assertEquals(setOf("a"), l.etat.faits)
    }

    @Test
    fun `precedent au premier segment le redit`() {
        val l = lecteur()
        l.demarrer()
        assertEquals(listOf(dire("a")), l.precedent())
        assertEquals("a", l.idCourant)
    }

    @Test
    fun `precedent saute les segments coupes`() {
        val l = lecteur(seg("a", MANUEL), seg("b", actif = false), seg("c"))
        l.demarrer()
        l.valider()
        l.finDeLecture("c")
        assertEquals(listOf(dire("a")), l.precedent())
        assertEquals("a", l.idCourant)
    }

    @Test
    fun `precedent apres la fin rouvre le dernier segment`() {
        val l = lecteur(seg("a", MANUEL), seg("b"))
        l.demarrer()
        l.valider()
        l.valider()
        assertTrue(l.etat.terminee)

        assertEquals(listOf(dire("b")), l.precedent())
        assertFalse(l.etat.terminee)
        assertEquals("b", l.idCourant)
        assertEquals(setOf("a"), l.etat.faits)
    }

    // ── Pause ───────────────────────────────────────────────────────────────────

    @Test
    fun `pause coupe la voix, reprendre redit le courant`() {
        val l = lecteur()
        l.demarrer()
        l.relire()

        assertEquals(listOf(Taire), l.pause())
        assertTrue(l.etat.enPause)
        assertFalse(l.etat.parle)
        assertEquals(emptyList<Effet>(), l.pause())

        assertEquals(listOf(dire("a")), l.reprendre())
        assertFalse(l.etat.enPause)
    }

    @Test
    fun `pause sans voix ne rend rien a faire`() {
        val l = lecteur()
        l.demarrer()
        assertEquals(emptyList<Effet>(), l.pause())
        assertTrue(l.etat.enPause)
    }

    @Test
    fun `reprendre sans pause dit le courant, sauf si la voix parle deja`() {
        val l = lecteur()
        l.demarrer()
        // « Vas-y » à l'ouverture : le premier segment, manuel, est dit.
        assertEquals(listOf(dire("a")), l.reprendre())
        assertEquals(emptyList<Effet>(), l.reprendre())
    }

    @Test
    fun `reprendre sur un segment coupe leve la pause sans rien dire`() {
        val l = lecteur()
        l.demarrer()
        l.basculer("a")
        l.pause()
        assertEquals(emptyList<Effet>(), l.reprendre())
        assertFalse(l.etat.enPause)
    }

    @Test
    fun `un geste qui demande la voix leve la pause`() {
        val l = lecteur()
        l.demarrer()
        l.pause()
        assertEquals(listOf(dire("b")), l.valider())
        assertFalse(l.etat.enPause)

        l.pause()
        assertEquals(listOf(dire("b")), l.relire())
        assertFalse(l.etat.enPause)

        // La pause a déjà fait taire la voix : rien à couper.
        assertEquals(listOf(Taire), l.pause())
        assertEquals(listOf(dire("a")), l.precedent())
        assertFalse(l.etat.enPause)
    }

    // ── Couper, déclencheur ─────────────────────────────────────────────────────

    @Test
    fun `couper le segment en cours de lecture fait taire la voix`() {
        val l = lecteur()
        l.demarrer()
        l.relire()

        assertEquals(listOf(Taire), l.basculer("a"))
        assertFalse(l.etat.segments[0].actif)
        assertFalse(l.etat.parle)
        // Coupé : montré, mais plus jamais lu.
        assertEquals(emptyList<Effet>(), l.relire())

        assertEquals(emptyList<Effet>(), l.basculer("a"))
        assertTrue(l.etat.segments[0].actif)
        assertEquals(listOf(dire("a")), l.relire())
    }

    @Test
    fun `couper un autre segment ne touche pas a la voix`() {
        val l = lecteur()
        l.demarrer()
        l.relire()
        assertEquals(emptyList<Effet>(), l.basculer("c"))
        assertTrue(l.etat.parle)
        assertFalse(l.etat.segments[2].actif)
    }

    @Test
    fun `basculer ou changer un segment inconnu ne fait rien`() {
        val l = lecteur()
        val avant = l.etat
        assertEquals(emptyList<Effet>(), l.basculer("z"))
        assertEquals(emptyList<Effet>(), l.changerDeclencheur("z", MANUEL))
        assertEquals(avant, l.etat)
    }

    @Test
    fun `on peut couper un segment avant le demarrage`() {
        val l = lecteur()
        l.basculer("b")
        l.demarrer()
        assertEquals(listOf(dire("c")), l.valider())
    }

    @Test
    fun `changer le declencheur est garde dans l'etat`() {
        val l = lecteur()
        assertEquals(emptyList<Effet>(), l.changerDeclencheur("c", MANUEL))
        assertEquals(MANUEL, l.etat.segments[2].declencheur)
    }

    // ── Minuteurs ───────────────────────────────────────────────────────────────

    /** Courant sur b (minuteur de 60 s), b dit et fini de dire. */
    private fun surB(): LecteurSegments {
        val l = lecteur()
        l.demarrer()
        l.valider()
        l.finDeLecture("b")
        return l
    }

    @Test
    fun `lancer le minuteur du segment courant`() {
        val l = surB()
        assertEquals(listOf(LancerMinuteur("b", 60)), l.lancerMinuteur())
        assertEquals(setOf("b"), l.etat.minuteurs)
        // Déjà lancé : « minuteur » une seconde fois ne le relance pas.
        assertEquals(emptyList<Effet>(), l.lancerMinuteur())
    }

    @Test
    fun `un segment sans minuteur n'en lance pas`() {
        val l = lecteur()
        l.demarrer()
        assertEquals(emptyList<Effet>(), l.lancerMinuteur())
    }

    @Test
    fun `arreter un minuteur l'annule, et sa fin n'arrive plus`() {
        val l = surB()
        l.lancerMinuteur()
        assertEquals(listOf(AnnulerMinuteur("b")), l.arreterMinuteur("b"))
        assertEquals(emptyList<Effet>(), l.arreterMinuteur("b"))
        assertEquals(emptyList<Effet>(), l.minuteurFini("b"))
        assertEquals("b", l.idCourant)
    }

    @Test
    fun `la fin du minuteur dit le suivant s'il l'attend`() {
        val l = surB()
        l.lancerMinuteur()

        assertEquals(listOf(Sonner("b"), dire("c")), l.minuteurFini("b"))
        assertEquals("c", l.idCourant)
        assertEquals(setOf("a", "b"), l.etat.faits)
        assertTrue(l.etat.minuteurs.isEmpty())
    }

    @Test
    fun `la fin du minuteur pendant que la voix parle la coupe d'abord`() {
        val l = surB()
        l.lancerMinuteur()
        l.relire()
        assertEquals(listOf(Sonner("b"), Taire, dire("c")), l.minuteurFini("b"))
    }

    @Test
    fun `un suivant manuel ne part jamais seul, le minuteur sonne quand meme`() {
        val l = surB()
        l.changerDeclencheur("c", MANUEL)
        l.lancerMinuteur()

        assertEquals(listOf(Sonner("b")), l.minuteurFini("b"))
        assertEquals("b", l.idCourant)
        assertFalse(l.etat.parle)
    }

    @Test
    fun `un suivant qui attend la fin du precedent ne part pas a la fin du minuteur`() {
        val l = surB()
        l.changerDeclencheur("c", FIN_TACHE_PRECEDENTE)
        l.lancerMinuteur()
        assertEquals(listOf(Sonner("b")), l.minuteurFini("b"))
        assertEquals("b", l.idCourant)
    }

    @Test
    fun `le declencheur regarde le prochain segment actif`() {
        val l = lecteur(seg("a", MANUEL, minuteur = 30), seg("b", FIN_MINUTEUR, actif = false), seg("c", MANUEL), seg("d", FIN_MINUTEUR))
        l.demarrer()
        l.lancerMinuteur()
        // b est coupé : le prochain actif, c, est manuel.
        assertEquals(listOf(Sonner("a")), l.minuteurFini("a"))
        assertEquals("a", l.idCourant)
    }

    @Test
    fun `le minuteur d'un segment deja valide sonne sans rien changer`() {
        val l = surB()
        l.lancerMinuteur()
        l.valider()
        l.finDeLecture("c")
        assertEquals("c", l.idCourant)

        assertEquals(listOf(Sonner("b")), l.minuteurFini("b"))
        assertEquals("c", l.idCourant)
        assertFalse(l.etat.parle)
    }

    @Test
    fun `revenir en arriere ne coupe pas un minuteur qui tourne`() {
        val l = surB()
        l.lancerMinuteur()
        l.precedent()
        assertEquals(setOf("b"), l.etat.minuteurs)
        assertEquals(listOf(Sonner("b")), l.minuteurFini("b"))
        assertEquals("a", l.idCourant)
    }

    @Test
    fun `en pause, la fin du minuteur sonne et avance sans rien dire, reprendre dit la suite`() {
        val l = surB()
        l.lancerMinuteur()
        l.pause()

        assertEquals(listOf(Sonner("b")), l.minuteurFini("b"))
        assertEquals("c", l.idCourant)
        assertTrue(l.etat.enPause)
        assertFalse(l.etat.parle)

        assertEquals(listOf(dire("c")), l.reprendre())
    }

    @Test
    fun `le minuteur se lance aussi en pause, sans lever la pause`() {
        val l = surB()
        l.pause()
        assertEquals(listOf(LancerMinuteur("b", 60)), l.lancerMinuteur())
        assertTrue(l.etat.enPause)
    }

    @Test
    fun `la fin d'un minuteur au dernier segment ne termine pas la seance`() {
        val l = lecteur(seg("a", MANUEL), seg("b", minuteur = 10))
        l.demarrer()
        l.valider()
        l.lancerMinuteur()
        assertEquals(listOf(Sonner("b")), l.minuteurFini("b"))
        assertFalse(l.etat.terminee)
    }

    // ── Une séance entière ──────────────────────────────────────────────────────

    @Test
    fun `la recette d'exemple de bout en bout`() {
        val l = LecteurSegments(SegmentsDeRecette.de(RecetteExemple.recette))
        assertEquals(emptyList<Effet>(), l.demarrer())

        // Étape 1, manuelle : on la demande.
        assertEquals("e1", (l.relire().single() as Dire).segmentId)
        l.finDeLecture("e1")
        // « C'est fait » : l'étape 2 est dite, son minuteur de 5 minutes se lance à la demande.
        assertEquals("e2", (l.valider().single() as Dire).segmentId)
        l.finDeLecture("e2")
        assertEquals(listOf(LancerMinuteur("e2", 300)), l.lancerMinuteur())
        // Fin du minuteur : l'étape 3 attend la fin du minuteur, elle part seule.
        val fin = l.minuteurFini("e2")
        assertEquals(Sonner("e2"), fin.first())
        assertEquals("e3", (fin.last() as Dire).segmentId)
        l.finDeLecture("e3")
        // L'astuce de l'étape 5 est coupée : après l'étape 4, on passe à la 6.
        l.basculer("e5")
        assertEquals("e4", (l.valider().single() as Dire).segmentId)
        l.finDeLecture("e4")
        assertEquals("e6", (l.valider().single() as Dire).segmentId)
        l.finDeLecture("e6")
        assertEquals(listOf(Terminee), l.valider())
        assertEquals(setOf("e1", "e2", "e3", "e4", "e6"), l.etat.faits)
        assertTrue(l.etat.terminee)
    }
}
