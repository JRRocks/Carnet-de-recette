package fr.jrrocks.carnetderecette.core.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde-fou de la règle 4 de CLAUDE.md : tant que l'accès HelloFresh tient, aucun moteur de
 * mode dégradé n'est déclaré disponible. Si ce test casse, c'est qu'un moteur a été ajouté :
 * la décision doit d'abord être consignée dans docs/cadrage.md.
 */
class TypeSourceTest {

    @Test
    fun `seul HelloFresh a un moteur, les autres sources restent reservees`() {
        assertEquals(
            listOf(TypeSource.HELLOFRESH),
            TypeSource.entries.filter { it.moteurDisponible },
        )
    }
}
