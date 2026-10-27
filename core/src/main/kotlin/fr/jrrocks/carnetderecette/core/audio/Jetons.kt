package fr.jrrocks.carnetderecette.core.audio

/*
 * Découpe d'un texte en jetons, partagée par la lecture des durées ([SegmentsDeRecette]) et la
 * mise en forme pour l'oral ([TexteOral]). Ni l'une ni l'autre n'emploie de regex : elles
 * comparent des suites de jetons à des tables connues. Un jeton de lettres est toujours un mot
 * **entier** : « g » dans « mélange » n'est jamais un jeton à lui seul.
 */

internal enum class SorteJeton { LETTRES, CHIFFRES, BLANC, SIGNE }

internal data class Jeton(val texte: String, val sorte: SorteJeton) {
    val minuscule: String get() = texte.lowercase()
    val estApostrophe: Boolean get() = sorte == SorteJeton.SIGNE && (texte == "'" || texte == "’")
}

/**
 * Les chiffres de 0 à 9 seulement : un chiffre d'une autre écriture (pleine chasse, arabe,
 * devanagari) est un signe, gardé tel quel, jamais lu comme un nombre.
 */
private fun Char.estChiffre(): Boolean = this in '0'..'9'

private fun sorteDe(c: Char): SorteJeton = when {
    c.isLetter() -> SorteJeton.LETTRES
    c.estChiffre() -> SorteJeton.CHIFFRES
    c.isWhitespace() -> SorteJeton.BLANC
    else -> SorteJeton.SIGNE
}

/**
 * Lettres, chiffres et blancs se groupent en suites ; chaque signe est un jeton à lui seul.
 * Un nombre décimal (« 1,5 », « 0.5 ») reste un seul jeton : sa virgule n'est pas une pause.
 * Un seul séparateur par nombre : « 1,2,3 » donne « 1,2 », « , » et « 3 », jamais un jeton
 * qu'on ne saurait pas lire.
 */
internal fun decouper(texte: String): List<Jeton> {
    val jetons = ArrayList<Jeton>()
    var debut = 0
    while (debut < texte.length) {
        val sorte = sorteDe(texte[debut])
        var fin = debut + 1
        if (sorte != SorteJeton.SIGNE) {
            while (fin < texte.length && sorteDe(texte[fin]) == sorte) fin++
            if (sorte == SorteJeton.CHIFFRES && fin + 1 < texte.length && texte[fin] in ",." && texte[fin + 1].estChiffre()) {
                fin++
                while (fin < texte.length && texte[fin].estChiffre()) fin++
            }
        }
        jetons += Jeton(texte.substring(debut, fin), sorte)
        debut = fin
    }
    return jetons
}

/**
 * La valeur d'un jeton de chiffres (« 1,5 » → 1.5) ; `null` si elle ne se lit pas. Le découpage
 * ne produit que des nombres lisibles, mais un texte d'étape ne doit jamais faire planter la
 * séance : un `null` laisse le jeton tel quel.
 */
internal val Jeton.valeur: Double? get() = texte.replace(',', '.').toDoubleOrNull()
