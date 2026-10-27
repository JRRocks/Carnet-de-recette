package fr.jrrocks.carnetderecette.core.recette

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantitesTest {

    @Test
    fun `quantites lisibles`() {
        assertEquals("½\u00A0sachet", Quantites.formater(0.5, "sachet(s)"))
        assertEquals("1\u00A0pièce", Quantites.formater(1.0, "pièce(s)"))
        assertEquals("2\u00A0pièces", Quantites.formater(2.0, "pièce(s)"))
        assertEquals("1½\u00A0pièces", Quantites.formater(1.5, "pièce(s)"))
        assertEquals("150\u00A0g", Quantites.formater(150.0, "g"))
        assertEquals("⅓\u00A0cc", Quantites.formater(1.0 / 3, "cc"))
        assertEquals("0,15\u00A0l", Quantites.formater(0.15, "l"))
        assertEquals("selon ton goût", Quantites.formater(null, "selon ton goût"))
        assertEquals("", Quantites.formater(null, null))
        assertEquals("3", Quantites.formater(3.0, " "))
        // Tel que le site l'écrit pour le sel et le poivre, et un tiers arrondi par le site.
        assertEquals("selon le goût", Quantites.formater(0.0, "selon le goût"))
        assertEquals("1⅓\u00A0sachets", Quantites.formater(1.3300000429153442, "sachet(s)"))
    }
}
