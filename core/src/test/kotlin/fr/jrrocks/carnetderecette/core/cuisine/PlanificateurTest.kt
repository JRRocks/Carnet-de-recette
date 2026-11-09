package fr.jrrocks.carnetderecette.core.cuisine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanificateurTest {

    private val recette = SemainePrepareeFactice.recette

    /** « t2 0-4 » : chaque tâche du plan, dans l'ordre du plan. */
    private fun Plan.resume(): List<String> = places.map { "${it.tache.id} ${it.debut}-${it.fin}" }

    private fun tache(id: String, type: TypeTache, minutes: Int, vararg apres: String, role: RoleCuisine? = null) =
        Tache(id = id, type = type, role = role, dureeMin = minutes.toDouble(), apres = apres.toList(), texte = id)

    @Test
    fun `a deux, chacun prend la premiere tache prete de son role`() {
        val plan = Planificateur.planifier(recette, ModeCuisine.A_DEUX)
        assertEquals(
            listOf("t2 0-4", "t1 0-1", "t3 4-6", "t4 6-8", "t5 6-12", "t8 8-11", "t6 12-13", "t7 13-27", "t9 27-29"),
            plan.resume(),
        )
        assertEquals(29, plan.duree)
        // Les mains prises de chacun, sans le four qui cuit seul.
        assertEquals(4 + 2 + 2 + 3, plan.occupation(RoleCuisine.DECOUPE))
        assertEquals(1 + 6 + 1 + 2, plan.occupation(RoleCuisine.CHAUFFE))
        assertEquals(listOf("t2", "t3", "t4", "t8"), plan.duRole(RoleCuisine.DECOUPE).map { it.tache.id })
    }

    @Test
    fun `seul, on avance le reste pendant que le four cuit`() {
        val plan = Planificateur.planifier(recette, ModeCuisine.SEUL)
        assertEquals(
            listOf("t1 0-1", "t2 1-5", "t3 5-7", "t4 7-9", "t5 9-15", "t6 15-16", "t8 16-19", "t7 16-30", "t9 30-32"),
            plan.resume(),
        )
        assertEquals(32, plan.duree)
    }

    @Test
    fun `le plan a deux montre les passages de main avant la tache qui en a besoin`() {
        val lignes = Planificateur.planifier(recette, ModeCuisine.A_DEUX).lignes().map {
            when (it) {
                is LignePlan.Etape -> it.place.tache.id
                is LignePlan.Passage -> "${it.source.tache.id}>${it.vers}"
            }
        }
        assertEquals(listOf("t2", "t1", "t3", "t4", "t3>CHAUFFE", "t5", "t8", "t4>CHAUFFE", "t6", "t7", "t9"), lignes)
        // Seul, personne ne passe la main à personne.
        assertTrue(Planificateur.planifier(recette, ModeCuisine.SEUL).lignes().all { it is LignePlan.Etape })
    }

    @Test
    fun `une attente ne retient personne`() {
        val taches = listOf(
            tache("four", TypeTache.ATTENTE, 20, role = RoleCuisine.CHAUFFE),
            tache("sauce", TypeTache.CHAUFFE, 5),
            tache("servir", TypeTache.DRESSAGE, 2, "four", "sauce"),
        )
        val plan = Planificateur.planifier(taches, ModeCuisine.A_DEUX)
        assertEquals(listOf("four 0-20", "sauce 0-5", "servir 20-22"), plan.resume())
        assertEquals(5 + 2, plan.occupation(RoleCuisine.CHAUFFE))
    }

    @Test
    fun `le meme plan a chaque calcul, sur les deux telephones`() {
        val une = Planificateur.planifier(recette, ModeCuisine.A_DEUX)
        repeat(5) { assertEquals(une, Planificateur.planifier(recette, ModeCuisine.A_DEUX)) }
    }

    @Test
    fun `une dependance circulaire ne bloque pas le plan`() {
        val taches = listOf(
            tache("a", TypeTache.DECOUPE, 2, "b"),
            tache("b", TypeTache.DECOUPE, 3, "a"),
            tache("c", TypeTache.CHAUFFE, 4, "b"),
        )
        val plan = Planificateur.planifier(taches, ModeCuisine.A_DEUX)
        // « a » part sans attendre « b », qui suit ; « c » attend « b » comme prévu.
        assertEquals(listOf("a 0-2", "b 2-5", "c 5-9"), plan.resume())
    }

    @Test
    fun `des donnees de travers ne font ni boucler ni planter`() {
        val taches = listOf(
            tache("x", TypeTache.DECOUPE, 2, "x", "inconnue"),
            tache("x", TypeTache.CHAUFFE, 9),
            Tache(id = "y", texte = "sans durée"),
            tache("z", TypeTache.ATTENTE, 0, "y"),
        )
        val plan = Planificateur.planifier(taches, ModeCuisine.A_DEUX)
        assertEquals(listOf("x 0-2", "y 0-1", "z 1-2"), plan.resume())
        assertEquals(0, Planificateur.planifier(emptyList(), ModeCuisine.SEUL).duree)
    }

    @Test
    fun `un role sans tache laisse l'autre faire seul le sien`() {
        val taches = listOf(tache("a", TypeTache.CHAUFFE, 3), tache("b", TypeTache.CHAUFFE, 4, "a"))
        val plan = Planificateur.planifier(taches, ModeCuisine.A_DEUX)
        assertEquals(listOf("a 0-3", "b 3-7"), plan.resume())
        assertEquals(0, plan.occupation(RoleCuisine.DECOUPE))
    }
}
