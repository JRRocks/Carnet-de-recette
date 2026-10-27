package fr.jrrocks.carnetderecette.core.source

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde-fou de la règle 4 de CLAUDE.md : tant que l'accès HelloFresh tient, seul le scan de
 * la fiche papier (décision du 2026-10-27) s'ajoute à HelloFresh. Si ce test casse, c'est
 * qu'un autre moteur a été ajouté : la décision doit d'abord être consignée dans
 * docs/cadrage.md.
 */
class TypeSourceTest {

    @Test
    fun `HelloFresh et la fiche papier ont un moteur, les autres sources restent reservees`() {
        assertEquals(
            listOf(TypeSource.HELLOFRESH, TypeSource.PHOTO_FICHE),
            TypeSource.entries.filter { it.moteurDisponible },
        )
    }
}
