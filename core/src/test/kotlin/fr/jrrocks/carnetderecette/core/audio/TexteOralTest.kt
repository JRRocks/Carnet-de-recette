package fr.jrrocks.carnetderecette.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class TexteOralTest {

    private fun oral(texte: String) = TexteOral.pourLOral(texte)

    private fun verifier(vararg cas: Pair<String, String>) {
        for ((ecrit, attendu) in cas) assertEquals("pour «\u00A0$ecrit\u00A0»", attendu, oral(ecrit))
    }

    @Test
    fun `les fractions seules`() {
        verifier(
            "½" to "un demi",
            "¼" to "un quart",
            "¾" to "trois quarts",
            "⅓" to "un tiers",
            "⅔" to "deux tiers",
            "Coupe le citron en ½." to "Coupe le citron en un demi.",
        )
    }

    @Test
    fun `une fraction devant une mesure ou un nom s'accorde`() {
        verifier(
            "½ cc de sel" to "une demi-cuillère à café de sel",
            "½ c. à c." to "une demi-cuillère à café",
            "¼ cc" to "un quart de cuillère à café",
            "½ l de lait" to "un demi-litre de lait",
            "¼ l de lait" to "un quart de litre de lait",
            "¾ h" to "trois quarts d'heure",
            "½ min" to "une demi-minute",
            "½ gousse d'ail" to "une demi-gousse d'ail",
            "½ citron" to "un demi-citron",
            "¼ oignon" to "un quart d'oignon",
            "⅓ courgette" to "un tiers de courgette",
            "¼ haricot" to "un quart de haricot",
            "¼ du bouillon" to "un quart du bouillon",
            "½ d'un citron" to "un demi d'un citron",
        )
    }

    @Test
    fun `une fraction ecrite avec une barre, mais pas une date`() {
        verifier(
            "1/2 cc" to "une demi-cuillère à café",
            "3/4 l" to "trois quarts de litre",
            "1/2" to "un demi",
            "le 1/2/2026" to "le 1/2/2026",
            "x1/2" to "x1/2",
        )
    }

    @Test
    fun `un entier et une fraction`() {
        verifier(
            "1 ½ cc" to "une cuillère à café et demie",
            "2 ½ cs" to "2 cuillères à soupe et demie",
            "1 ½ l" to "1 litre et demi",
            "1½ oignon" to "1 oignon et demi",
            "1 ½ gousse" to "une gousse et demie",
            "2 ¼ kg" to "2 kilos et quart",
            "1 ½" to "1 et demi",
            "1 ¾, pas plus" to "1 et trois quarts, pas plus",
        )
    }

    @Test
    fun `les cuilleres sous toutes leurs ecritures`() {
        verifier(
            "1 cc" to "une cuillère à café",
            "2 cc" to "2 cuillères à café",
            "1 càc" to "une cuillère à café",
            "1 c. à c. de cumin" to "une cuillère à café de cumin",
            "1 c.à.c. de cumin" to "une cuillère à café de cumin",
            "1 c. à c de cumin" to "une cuillère à café de cumin",
            "1 c.c. de cumin" to "une cuillère à café de cumin",
            "1 CC" to "une cuillère à café",
            "1 cs d'huile" to "une cuillère à soupe d'huile",
            "3 càs" to "3 cuillères à soupe",
            "1 c. à s. d'huile" to "une cuillère à soupe d'huile",
            "1 c.à.s." to "une cuillère à soupe",
            "1,5 cs" to "1,5 cuillère à soupe",
            "Ajoute une cc de sel" to "Ajoute une cuillère à café de sel",
        )
    }

    @Test
    fun `les poids et volumes apres un nombre, au singulier sous deux`() {
        verifier(
            "200 g de farine" to "200 grammes de farine",
            "200g" to "200 grammes",
            "1 g" to "1 gramme",
            "250 gr" to "250 grammes",
            "1,5 kg" to "1,5 kilo",
            "2 kg" to "2 kilos",
            "50 ml" to "50 millilitres",
            "20 cl" to "20 centilitres",
            "3 dl" to "3 décilitres",
            "1 l" to "1 litre",
            "2 L" to "2 litres",
            "0,5 l" to "0,5 litre",
            "200\u00A0g" to "200 grammes",
        )
    }

    @Test
    fun `les durees et les temperatures`() {
        verifier(
            "5 min" to "5 minutes",
            "5min" to "5 minutes",
            "1 min" to "une minute",
            "3 mn" to "3 minutes",
            "quelques min" to "quelques minutes",
            "12 à 14 min" to "12 à 14 minutes",
            "12-14 min" to "12 à 14 minutes",
            "12 – 14 min" to "12 à 14 minutes",
            "1 h 30" to "une heure 30",
            "2 h" to "2 heures",
            "180 °C" to "180 degrés",
            "180°C" to "180 degrés",
            "200°" to "200 degrés",
            "1 °C" to "1 degré",
            "200 ºC" to "200 degrés",
            "thermostat 6-7" to "thermostat 6 à 7",
        )
    }

    @Test
    fun `environ et fois`() {
        verifier(
            "env. 10 min" to "environ 10 minutes",
            "env 10 min" to "environ 10 minutes",
            "Double la sauce (x2)." to "Double la sauce (fois deux).",
            "x2" to "fois deux",
            "X3" to "fois trois",
            "×4, pas plus" to "fois quatre, pas plus",
        )
    }

    @Test
    fun `fois seulement quand c'est sur`() {
        verifier(
            "x 2" to "x 2",
            "max2" to "max2",
            "x2a" to "x2a",
            "x20" to "x20",
            "x2/3" to "x2/3",
            "le x" to "le x",
        )
    }

    @Test
    fun `aucun mot n'est casse`() {
        verifier(
            "Mélange bien." to "Mélange bien.",
            "Ajoute le gingembre et accompagne de riz." to "Ajoute le gingembre et accompagne de riz.",
            "la lettre g" to "la lettre g",
            "la touche h" to "la touche h",
            "C'est prêt, c'est chaud." to "C'est prêt, c'est chaud.",
            "Laisse cuire 5 minutes." to "Laisse cuire 5 minutes.",
            "Hache l'ail." to "Hache l'ail.",
            "Ajoute 2 l’oignon" to "Ajoute 2 l’oignon",
            "Ajoute 2 l'oignon" to "Ajoute 2 l'oignon",
            "n°3" to "n°3",
            "°C" to "°C",
            "Mixe 2 à 3 fois." to "Mixe 2 à 3 fois.",
            "1 oignon" to "1 oignon",
            "" to "",
        )
    }

    @Test
    fun `un devient une devant un nom feminin connu`() {
        verifier(
            "1 gousse d'ail" to "une gousse d'ail",
            "1 pincée de sel" to "une pincée de sel",
            "1 oignon" to "1 oignon",
            "11 gousses" to "11 gousses",
        )
    }

    @Test
    fun `une puce par ligne devient une phrase`() {
        verifier(
            "Émince le poireau\nRâpe le parmesan" to "Émince le poireau. Râpe le parmesan",
            "• Émince le poireau.\n• Râpe le parmesan" to "Émince le poireau. Râpe le parmesan",
            "- Émince\n\n- Râpe !\n- Sers" to "Émince. Râpe ! Sers",
            "  Une seule ligne  " to "Une seule ligne",
        )
    }

    @Test
    fun `les accents decomposes sont recomposes`() {
        verifier("Répe 1 cc" to "Répe une cuillère à café")
    }

    @Test
    fun `une etape entiere`() {
        assertEquals(
            "Dans une sauteuse, fais chauffer une cuillère à soupe d'huile d'olive à feu moyen. " +
                "Ajoute 150 grammes de riz, une demi-cuillère à café de sel et laisse cuire 12 à 14 minutes à 180 degrés.",
            oral(
                "Dans une sauteuse, fais chauffer 1 cs d'huile d'olive à feu moyen. " +
                    "Ajoute 150 g de riz, ½ cc de sel et laisse cuire 12-14 min à 180 °C.",
            ),
        )
    }
}
