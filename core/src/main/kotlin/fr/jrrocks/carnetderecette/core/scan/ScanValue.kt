package fr.jrrocks.carnetderecette.core.scan

/**
 * Normalisation d'une valeur scannée (moteur Library `scan-multimode`).
 *
 * Une seule règle, partagée par le consensus, l'anti-rebond et la reconnaissance de
 * l'étiquette : une valeur écartée comme doublon est exactement une valeur reconnue comme
 * déjà vue.
 *
 * Adaptation du Carnet : les espaces se repèrent caractère par caractère, sans expression
 * régulière (CLAUDE.md, règle 5). Les espaces Unicode (insécable compris) comptent aussi,
 * puisque l'OCR peut en produire.
 */
object ScanValue {

    /**
     * Retire les espaces de bord et réduit toute suite d'espaces internes à un seul.
     * L'OCR insère souvent des espaces parasites dans un marquage imprimé.
     */
    fun normalize(raw: String): String {
        val resultat = StringBuilder(raw.length)
        var espaceEnAttente = false
        for (c in raw) {
            if (c.isWhitespace()) {
                espaceEnAttente = resultat.isNotEmpty()
            } else {
                if (espaceEnAttente) resultat.append(' ')
                espaceEnAttente = false
                resultat.append(c)
            }
        }
        return resultat.toString()
    }

    /**
     * Clé de comparaison. La casse est ignorée par défaut : un marquage relevé par l'OCR ne
     * restitue pas toujours la casse d'origine.
     */
    fun key(raw: String, caseSensitive: Boolean = false): String {
        val normalized = normalize(raw)
        return if (caseSensitive) normalized else normalized.uppercase()
    }

    /** Une valeur vide ou faite seulement d'espaces n'est jamais retenue. */
    fun isUsable(raw: String): Boolean = normalize(raw).isNotEmpty()
}
