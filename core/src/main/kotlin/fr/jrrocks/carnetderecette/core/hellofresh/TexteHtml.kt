package fr.jrrocks.carnetderecette.core.hellofresh

/**
 * Le texte d'un fragment HTML de la page (les étapes sont des listes `<ul><li>`), en texte
 * simple : une ligne par puce ou par paragraphe, balises retirées, entités décodées.
 */
object TexteHtml {

    private val FINS_DE_LIGNE = listOf("</li>", "</p>", "<br>", "<br/>", "<br />", "</div>", "</h1>", "</h2>", "</h3>")

    fun enTexte(html: String): String {
        var texte = html
        FINS_DE_LIGNE.forEach { texte = texte.replace(it, "\n", ignoreCase = true) }
        val sansBalises = StringBuilder()
        var dansBalise = false
        for (c in texte) {
            when {
                c == '<' -> dansBalise = true
                c == '>' && dansBalise -> dansBalise = false
                !dansBalise -> sansBalises.append(c)
            }
        }
        return decoderEntites(sansBalises.toString())
            .lines()
            .map { ligne -> ligne.replace('\u00A0', ' ').trim().replace(Regex(" {2,}"), " ") }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private val ENTITES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»",
        "hellip" to "…", "ndash" to "–", "mdash" to "—", "deg" to "°", "eacute" to "é", "egrave" to "è",
        "agrave" to "à", "ccedil" to "ç", "ecirc" to "ê", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
    )

    internal fun decoderEntites(texte: String): String {
        val sortie = StringBuilder()
        var i = 0
        while (i < texte.length) {
            val c = texte[i]
            val fin = if (c == '&') texte.indexOf(';', i).takeIf { it in (i + 2)..(i + 10) } else null
            if (fin == null) {
                sortie.append(c)
                i++
                continue
            }
            val nom = texte.substring(i + 1, fin)
            val remplacement = when {
                nom.startsWith("#x") || nom.startsWith("#X") -> nom.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                nom.startsWith("#") -> nom.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> ENTITES[nom]
            }
            if (remplacement == null) {
                sortie.append(c)
                i++
            } else {
                sortie.append(remplacement)
                i = fin + 1
            }
        }
        return sortie.toString()
    }
}
