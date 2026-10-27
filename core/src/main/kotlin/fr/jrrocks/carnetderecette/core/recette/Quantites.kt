package fr.jrrocks.carnetderecette.core.recette

/**
 * Une quantité lisible d'un coup d'œil : « ½ sachet », « 2 pièces », « 150 g », « 1¼ cc ».
 * Les fractions courantes s'écrivent en fraction ; le reste avec une virgule.
 */
object Quantites {

    private val FRACTIONS = mapOf(0.25 to "¼", 0.5 to "½", 0.75 to "¾", 1.0 / 3 to "⅓", 2.0 / 3 to "⅔")

    fun formater(montant: Double?, unite: String?): String {
        val nombre = montant?.let(::nombre)
        val mesure = unite?.trim()?.takeIf { it.isNotEmpty() }?.let { accorder(it, montant) }
        return listOfNotNull(nombre, mesure).joinToString("\u00A0")
    }

    internal fun nombre(montant: Double): String {
        val entier = montant.toLong()
        val reste = montant - entier
        if (reste < 1e-6) return entier.toString()
        val fraction = FRACTIONS.entries.firstOrNull { (valeur, _) -> kotlin.math.abs(valeur - reste) < 0.01 }?.value
        return when {
            fraction != null && entier == 0L -> fraction
            fraction != null -> "$entier$fraction"
            else -> "%.2f".format(java.util.Locale.FRANCE, montant).trimEnd('0').trimEnd(',')
        }
    }

    /** « pièce(s) » → « pièce » jusqu'à 1, « pièces » au-delà. */
    internal fun accorder(unite: String, montant: Double?): String {
        if (!unite.endsWith("(s)")) return unite
        val racine = unite.removeSuffix("(s)")
        return if (montant != null && montant > 1.0 + 1e-6) racine + "s" else racine
    }
}
