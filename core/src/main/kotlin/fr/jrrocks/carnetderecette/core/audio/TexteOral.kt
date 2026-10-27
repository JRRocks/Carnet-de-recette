package fr.jrrocks.carnetderecette.core.audio

import java.text.Normalizer

/**
 * Rend le texte d'une étape lisible à voix haute (cadrage § 8 : « ½ cc » devient « une
 * demi-cuillère à café »). La synthèse vocale lit mal les écritures de cuisine : elle épelle
 * « cc », dit « g » comme une lettre, lit « 12-14 » comme une soustraction.
 *
 * Remplacement de **mots entiers** tirés d'une table connue, jamais de motif : le texte est
 * découpé en jetons (lettres, chiffres, blancs, signes) et seule une suite de jetons connue
 * change. Un « g » au milieu d'un mot ne bouge donc jamais, et les unités qui peuvent être
 * autre chose (« g », « l », « h ») ne changent qu'après un nombre. Un mot suivi d'une
 * apostrophe (« l'huile ») est un article, jamais une unité.
 *
 * Le texte affiché à l'écran reste celui d'origine : seule la voix lit cette version.
 */
object TexteOral {

    fun pourLOral(texte: String): String {
        val phrases = lignesEnPhrases(Normalizer.normalize(texte, Normalizer.Form.NFC))
        return rendre(morceaux(decouper(phrases)))
    }

    /**
     * Une étape s'écrit souvent une puce par ligne. Lues bout à bout, les lignes se
     * mélangeraient : chacune devient une phrase, sans sa puce.
     */
    private fun lignesEnPhrases(texte: String): String =
        texte.lines()
            .map { it.trim().trimStart('•', '-', '–', '*').trim() }
            .filter { it.isNotEmpty() }
            .fold("") { acc, ligne ->
                when {
                    acc.isEmpty() -> ligne
                    acc.last() in FINS_DE_PHRASE -> "$acc $ligne"
                    else -> "$acc. $ligne"
                }
            }

    // ── Les tables ──────────────────────────────────────────────────────────────

    /** Une écriture abrégée et ce que la voix en dit. */
    private class Unite(
        val singulier: String,
        val pluriel: String = singulier,
        val feminin: Boolean = false,
        /** « g », « l », « h » : ailleurs qu'après un nombre, ce sont peut-être des lettres. */
        val apresNombre: Boolean = false,
        /** Sans nombre devant : « quelques min » → « quelques minutes ». */
        val seul: String = singulier,
        /** Une quantité qu'une fraction peut prendre (« ½ l ») ; pas « environ ». */
        val mesure: Boolean = true,
    ) {
        /** Singulier sous 2, comme en français (« 1,5 kilo », « 2 kilos »). */
        fun pour(valeur: Double) = if (valeur < 2) singulier else pluriel
    }

    private val CUILLERE_CAFE = Unite("cuillère à café", "cuillères à café", feminin = true)
    private val CUILLERE_SOUPE = Unite("cuillère à soupe", "cuillères à soupe", feminin = true)

    /** Chaque écriture connue, découpée en jetons comme le texte (blancs ôtés), et son unité. */
    private val ECRITURES: List<Pair<List<String>, Unite>> = listOf(
        listOf("cc", "càc", "c. à c.", "c.à.c.", "c. à c", "c.c.") to CUILLERE_CAFE,
        listOf("cs", "càs", "c. à s.", "c.à.s.", "c. à s", "c.s.") to CUILLERE_SOUPE,
        listOf("env.", "env") to Unite("environ", mesure = false),
        // Après un nombre seulement : « n°3 » est un numéro.
        listOf("°c", "°", "ºc", "º") to Unite("degré", "degrés", apresNombre = true),
        listOf("min", "mn") to Unite("minute", "minutes", feminin = true, seul = "minutes"),
        listOf("g", "gr") to Unite("gramme", "grammes", apresNombre = true),
        listOf("kg") to Unite("kilo", "kilos", apresNombre = true),
        listOf("ml") to Unite("millilitre", "millilitres", apresNombre = true),
        listOf("cl") to Unite("centilitre", "centilitres", apresNombre = true),
        listOf("dl") to Unite("décilitre", "décilitres", apresNombre = true),
        listOf("l") to Unite("litre", "litres", apresNombre = true),
        listOf("h") to Unite("heure", "heures", feminin = true, apresNombre = true),
    ).flatMap { (ecritures, unite) ->
        ecritures.map { ecriture -> decouper(ecriture).filter { it.sorte != SorteJeton.BLANC }.map { it.minuscule } to unite }
    }.sortedByDescending { it.first.size }

    private enum class Fraction(
        val valeur: Double,
        val seule: String,
        /** Après un entier seul : « 1 ½ » → « 1 et demi ». */
        val apresEntier: String,
        /** Devant un nom : « une demi-cuillère », « un quart de litre ». */
        val devant: (nom: String, feminin: Boolean) -> String,
        /** Après le nom qui suit un entier : « 1 cuillère et demie ». */
        val apresNom: (feminin: Boolean) -> String = { apresEntier },
    ) {
        DEMI(0.5, "un demi", "et demi", { nom, feminin -> (if (feminin) "une demi-" else "un demi-") + nom }, { if (it) "et demie" else "et demi" }),
        QUART(0.25, "un quart", "et quart", { nom, _ -> "un quart ${de(nom)}" }),
        TROIS_QUARTS(0.75, "trois quarts", "et trois quarts", { nom, _ -> "trois quarts ${de(nom)}" }),
        TIERS(1.0 / 3, "un tiers", "et un tiers", { nom, _ -> "un tiers ${de(nom)}" }),
        DEUX_TIERS(2.0 / 3, "deux tiers", "et deux tiers", { nom, _ -> "deux tiers ${de(nom)}" }),
    }

    private val FRACTIONS = mapOf(
        "½" to Fraction.DEMI, "1/2" to Fraction.DEMI,
        "¼" to Fraction.QUART, "1/4" to Fraction.QUART,
        "¾" to Fraction.TROIS_QUARTS, "3/4" to Fraction.TROIS_QUARTS,
        "⅓" to Fraction.TIERS, "1/3" to Fraction.TIERS,
        "⅔" to Fraction.DEUX_TIERS, "2/3" to Fraction.DEUX_TIERS,
    )

    /** Des noms féminins courants en cuisine : « une demi-gousse », « une gousse ». */
    private val FEMININS = setOf(
        "cuillère", "gousse", "pincée", "tasse", "botte", "tranche", "boîte", "branche", "feuille",
        "noix", "échalote", "tomate", "carotte", "courgette", "aubergine", "pomme", "poire", "orange",
        "minute", "heure",
    )

    /** Ce qui suit une fraction sans en être le nom (« ¼ du bouillon »). */
    private val MOTS_OUTILS = setOf(
        "de", "d", "du", "des", "à", "au", "aux", "le", "la", "les", "l", "et", "ou", "un", "une",
        "en", "pour", "par", "avec", "sans",
    )

    /** H muet : « un quart d'heure », mais « un quart de haricot ». */
    private val H_MUETS = setOf("heure", "heures", "huile", "herbe", "herbes")

    private const val VOYELLES = "aeiouyàâäéèêëîïôöùûüœ"
    private const val FINS_DE_PHRASE = ".!?:;…"

    private fun de(nom: String): String {
        val premier = nom.first().lowercaseChar()
        val elision = premier in VOYELLES || (premier == 'h' && nom.substringBefore(' ').lowercase() in H_MUETS)
        return if (elision) "d'$nom" else "de $nom"
    }

    private val EN_LETTRES = listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix")

    // ── Première passe : les jetons deviennent des morceaux reconnus ─────────────

    private sealed interface Morceau {
        val texte: String

        data class Blanc(override val texte: String) : Morceau

        /** Un mot ou un signe gardé tel quel. [mot] : une suite de lettres ; [elide] : suivie d'une apostrophe. */
        data class Brut(override val texte: String, val mot: Boolean = false, val elide: Boolean = false) : Morceau

        data class Nombre(override val texte: String, val valeur: Double) : Morceau

        data class Fractionnaire(override val texte: String, val fraction: Fraction) : Morceau

        data class Abrege(override val texte: String, val unite: Unite) : Morceau

        /** « x2 » : deux fois la quantité. */
        data class Fois(override val texte: String, val nombre: Int) : Morceau
    }

    private fun morceaux(jetons: List<Jeton>): List<Morceau> {
        val sortie = ArrayList<Morceau>()
        var i = 0
        while (i < jetons.size) {
            val jeton = jetons[i]
            if (jeton.sorte == SorteJeton.BLANC) {
                sortie += Morceau.Blanc(jeton.texte)
                i++
                continue
            }
            val fraction = fractionA(jetons, i)
            val fois = foisA(jetons, i)
            val ecriture = ecritureA(jetons, i)
            when {
                fraction != null -> {
                    sortie += Morceau.Fractionnaire(fraction.second, fraction.first)
                    i += if (fraction.second.length == 1) 1 else 3
                }
                fois != null -> {
                    sortie += Morceau.Fois(jeton.texte + jetons[i + 1].texte, fois)
                    i += 2
                }
                ecriture != null -> {
                    sortie += Morceau.Abrege(jetons.subList(i, ecriture.second).joinToString("") { it.texte }, ecriture.first)
                    i = ecriture.second
                }
                jeton.sorte == SorteJeton.CHIFFRES -> {
                    sortie += Morceau.Nombre(jeton.texte, jeton.valeur)
                    // « 12-14 » est un intervalle, pas une soustraction : « 12 à 14 ».
                    val tiret = prochainNonBlanc(jetons, i + 1)
                    val second = prochainNonBlanc(jetons, tiret + 1)
                    val estTiret = jetons.getOrNull(tiret)?.texte in TIRETS
                    i = if (estTiret && jetons.getOrNull(second)?.sorte == SorteJeton.CHIFFRES) {
                        sortie += Morceau.Brut(" à ")
                        second
                    } else {
                        i + 1
                    }
                }
                else -> {
                    val mot = jeton.sorte == SorteJeton.LETTRES
                    sortie += Morceau.Brut(jeton.texte, mot = mot, elide = mot && jetons.getOrNull(i + 1)?.estApostrophe == true)
                    i++
                }
            }
        }
        return sortie
    }

    private val TIRETS = setOf("-", "–")

    private fun prochainNonBlanc(jetons: List<Jeton>, depuis: Int): Int {
        var i = depuis
        while (i < jetons.size && jetons[i].sorte == SorteJeton.BLANC) i++
        return i
    }

    /** « ½ », ou « 1/2 » écrit d'un bloc (mais pas une date « 1/2/2026 », ni « x2/3 »). */
    private fun fractionA(jetons: List<Jeton>, i: Int): Pair<Fraction, String>? {
        val jeton = jetons[i]
        if (jeton.sorte == SorteJeton.SIGNE) return FRACTIONS[jeton.texte]?.let { it to jeton.texte }
        if (jeton.sorte != SorteJeton.CHIFFRES || jetons.getOrNull(i + 1)?.texte != "/") return null
        val bas = jetons.getOrNull(i + 2)?.takeIf { it.sorte == SorteJeton.CHIFFRES } ?: return null
        val avant = jetons.getOrNull(i - 1)
        if (avant?.texte == "/" || avant?.sorte == SorteJeton.LETTRES || jetons.getOrNull(i + 3)?.texte == "/") return null
        val ecrite = "${jeton.texte}/${bas.texte}"
        return FRACTIONS[ecrite]?.let { it to ecrite }
    }

    /**
     * « x2 » seulement quand c'est sûr : un « x » (ou « × ») collé à un entier, en début de mot,
     * et rien de collé derrière. « x 2 » ou « max2 » ne bougent pas.
     */
    private fun foisA(jetons: List<Jeton>, i: Int): Int? {
        val jeton = jetons[i]
        val signe = (jeton.sorte == SorteJeton.LETTRES && jeton.minuscule == "x") || jeton.texte == "×"
        if (!signe) return null
        val nombre = jetons.getOrNull(i + 1)?.takeIf { it.sorte == SorteJeton.CHIFFRES && it.texte.all(Char::isDigit) } ?: return null
        val avant = jetons.getOrNull(i - 1)
        val apres = jetons.getOrNull(i + 2)
        val debutDeMot = avant == null || avant.sorte == SorteJeton.BLANC || avant.texte == "("
        val finDeMot = apres == null || apres.sorte == SorteJeton.BLANC || (apres.sorte == SorteJeton.SIGNE && apres.texte != "/")
        return nombre.texte.toInt().takeIf { debutDeMot && finDeMot && it in 1..EN_LETTRES.lastIndex }
    }

    /** L'écriture connue qui commence en [i] (la plus longue), et la fin de ses jetons. */
    private fun ecritureA(jetons: List<Jeton>, i: Int): Pair<Unite, Int>? {
        ecritures@ for ((ecriture, unite) in ECRITURES) {
            var j = i
            for ((n, attendu) in ecriture.withIndex()) {
                if (n > 0) j = prochainNonBlanc(jetons, j)
                if (j >= jetons.size || jetons[j].minuscule != attendu) continue@ecritures
                j++
            }
            val elide = jetons[j - 1].sorte == SorteJeton.LETTRES && jetons.getOrNull(j)?.estApostrophe == true
            if (!elide) return unite to j
        }
        return null
    }

    // ── Seconde passe : chaque morceau dit selon ses voisins ─────────────────────

    /** Le morceau suivant, par-dessus un blanc au plus. */
    private fun suivant(morceaux: List<Morceau>, k: Int): Int =
        if (morceaux.getOrNull(k + 1) is Morceau.Blanc) k + 2 else k + 1

    private fun rendre(morceaux: List<Morceau>): String {
        val sortie = StringBuilder()
        var k = 0
        while (k < morceaux.size) {
            val morceau = morceaux[k]
            val n1 = suivant(morceaux, k)
            val apres = morceaux.getOrNull(n1)
            k = when (morceau) {
                is Morceau.Nombre -> when {
                    apres is Morceau.Fractionnaire -> {
                        // « 1 ½ cc » se dit « une cuillère à café et demie », « 1 ½ oignon »
                        // « 1 oignon et demi » : la fraction passe après le nom.
                        val n2 = suivant(morceaux, n1)
                        val nom = morceaux.getOrNull(n2)
                        val unite = (nom as? Morceau.Abrege)?.unite?.takeIf { it.mesure }
                        when {
                            unite != null -> {
                                val valeur = morceau.valeur + apres.fraction.valeur
                                sortie.append("${nombre(morceau, unite.feminin)} ${unite.pour(valeur)} ${apres.fraction.apresNom(unite.feminin)}")
                                n2 + 1
                            }
                            nom is Morceau.Brut && nom.estNom() -> {
                                val feminin = nom.texte.lowercase() in FEMININS
                                sortie.append("${nombre(morceau, feminin)} ${nom.texte} ${apres.fraction.apresNom(feminin)}")
                                n2 + 1
                            }
                            else -> {
                                sortie.append("${morceau.texte} ${apres.fraction.apresEntier}")
                                n1 + 1
                            }
                        }
                    }
                    apres is Morceau.Abrege -> {
                        sortie.append("${nombre(morceau, apres.unite.feminin)} ${apres.unite.pour(morceau.valeur)}")
                        n1 + 1
                    }
                    else -> {
                        val devantFeminin = apres is Morceau.Brut && apres.mot && apres.texte.lowercase() in FEMININS
                        sortie.append(nombre(morceau, devantFeminin))
                        k + 1
                    }
                }
                is Morceau.Fractionnaire -> when {
                    apres is Morceau.Abrege && apres.unite.mesure -> {
                        sortie.append(morceau.fraction.devant(apres.unite.singulier, apres.unite.feminin))
                        n1 + 1
                    }
                    apres is Morceau.Brut && apres.estNom() -> {
                        sortie.append(morceau.fraction.devant(apres.texte, apres.texte.lowercase() in FEMININS))
                        n1 + 1
                    }
                    else -> {
                        sortie.append(morceau.fraction.seule)
                        k + 1
                    }
                }
                is Morceau.Abrege -> {
                    sortie.append(if (morceau.unite.apresNombre) morceau.texte else morceau.unite.seul)
                    k + 1
                }
                is Morceau.Fois -> {
                    sortie.append("fois ${EN_LETTRES[morceau.nombre]}")
                    k + 1
                }
                else -> {
                    sortie.append(morceau.texte)
                    k + 1
                }
            }
        }
        return sortie.toString()
    }

    /** Un mot qui peut être le nom d'une quantité : ni article, ni préposition, ni élidé. */
    private fun Morceau.Brut.estNom(): Boolean = mot && !elide && texte.lowercase() !in MOTS_OUTILS

    /** « 1 » devant un nom féminin se dit « une » : la synthèse dirait « un cuillère ». */
    private fun nombre(nombre: Morceau.Nombre, feminin: Boolean): String =
        if (feminin && nombre.texte == "1") "une" else nombre.texte
}
