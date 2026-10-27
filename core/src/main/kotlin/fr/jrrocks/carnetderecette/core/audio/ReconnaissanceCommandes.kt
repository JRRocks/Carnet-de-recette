package fr.jrrocks.carnetderecette.core.audio

import java.text.Normalizer

/** Ce qu'on peut dire au téléphone pendant une séance (cadrage § 8). [libelle] : ce que l'écran rappelle avoir compris. */
enum class CommandeVocale(val libelle: String) {
    SUIVANT("suivant"),
    REPETER("répète"),
    PRECEDENT("précédent"),
    PAUSE("pause"),
    REPRENDRE("reprends"),

    /** Lancer le minuteur du segment courant. */
    MINUTEUR("minuteur"),

    /** Couper l'écoute. */
    ARRETER("arrête d'écouter"),
}

/**
 * Reconnaît une commande dans ce que la reconnaissance vocale d'Android a entendu, **sans IA
 * ni regex** : chaque phrase connue est cherchée comme une suite de mots entiers, après
 * normalisation (minuscules, sans accents ni ponctuation). Une phrase inconnue ne fait rien.
 *
 * Les alternatives (les hypothèses d'Android, de la plus probable à la moins probable) sont
 * lues dans l'ordre : la première qui donne une commande gagne. Dans une même alternative, la
 * phrase la plus longue l'emporte (« étape suivante » plutôt que « suivante »).
 *
 * Trois garde-fous, parce qu'on parle aussi d'autre chose en cuisine :
 * - une négation juste avant (« c'est pas fait », « ne continue pas ») ou juste après
 *   (« continue pas ») annule tout, sans regarder les alternatives suivantes : mieux vaut ne
 *   rien faire que l'inverse ;
 * - deux commandes dans la même phrase (« ok attends ») : « pause » ou « arrête d'écouter »
 *   l'emporte, le choix prudent ;
 * - une phrase trop longue autour de la commande (« tu as fait les courses ? ») est de la
 *   conversation, pas une commande.
 */
object ReconnaissanceCommandes {

    private val PHRASES: List<Pair<List<String>, CommandeVocale>> = listOf(
        CommandeVocale.SUIVANT to listOf("suivant", "suivante", "c'est fait", "fait", "j'ai fini", "ok", "d'accord", "étape suivante"),
        CommandeVocale.REPETER to listOf("répète", "répète s'il te plaît", "encore", "redis", "relis"),
        CommandeVocale.PRECEDENT to listOf("précédent", "retour", "étape précédente"),
        CommandeVocale.PAUSE to listOf("pause", "attends", "stop", "tais-toi", "silence"),
        CommandeVocale.REPRENDRE to listOf("reprends", "continue", "vas-y"),
        CommandeVocale.MINUTEUR to listOf("minuteur", "lance le minuteur", "chrono"),
        CommandeVocale.ARRETER to listOf("arrête d'écouter", "coupe le micro"),
    ).flatMap { (commande, phrases) -> phrases.map { normaliser(it) to commande } }

    private val NEGATIONS = setOf("pas", "ne", "n", "jamais")

    /** Juste après la commande, à l'oral : « continue pas » pour « ne continue pas ». */
    private val NEGATIONS_APRES = setOf("pas", "jamais")

    /** Celles qui arrêtent quelque chose : dites avec une autre, elles l'emportent. */
    private val PRUDENTES = setOf(CommandeVocale.PAUSE, CommandeVocale.ARRETER)

    /**
     * Les mots tolérés autour d'une phrase : autant qu'elle en compte, trois au plus
     * (« bon, c'est fait », « euh suivant »). Plus la phrase est courte, plus elle se glisse
     * dans une conversation : « il fait beau » n'est pas « fait ».
     */
    private fun motsEnPlus(longueur: Int) = minOf(longueur, 3)

    fun reconnaitre(alternatives: List<String>): CommandeVocale? {
        for (alternative in alternatives) {
            when (val lecture = lire(normaliser(alternative))) {
                is Lecture.Commande -> return lecture.commande
                Lecture.Annulee -> return null
                Lecture.Rien -> continue
            }
        }
        return null
    }

    private sealed interface Lecture {
        data class Commande(val commande: CommandeVocale) : Lecture
        data object Annulee : Lecture
        data object Rien : Lecture
    }

    private class Trouvaille(val debut: Int, val longueur: Int, val commande: CommandeVocale)

    private fun lire(mots: List<String>): Lecture {
        val trouvailles = PHRASES.flatMap { (phrase, commande) ->
            mots.indices.filter { debut -> mots.subList(debut, mots.size).take(phrase.size) == phrase }
                .map { Trouvaille(it, phrase.size, commande) }
        }
        if (trouvailles.isEmpty()) return Lecture.Rien
        val niee = trouvailles.any {
            mots.getOrNull(it.debut - 1) in NEGATIONS || mots.getOrNull(it.debut + it.longueur) in NEGATIONS_APRES
        }
        if (niee) return Lecture.Annulee
        // « ok attends » : deux commandes qui se contredisent, on retient la prudente.
        val retenues = trouvailles.filter { it.commande in PRUDENTES }.ifEmpty { trouvailles }
        val meilleure = retenues.sortedWith(compareByDescending<Trouvaille> { it.longueur }.thenBy { it.debut }).first()
        if (mots.size > meilleure.longueur + motsEnPlus(meilleure.longueur)) return Lecture.Rien
        return Lecture.Commande(meilleure.commande)
    }

    /** « C'est fait ! » → [c, est, fait] : minuscules, accents retirés, ponctuation et apostrophes en espaces. */
    internal fun normaliser(texte: String): List<String> {
        val sansAccents = Normalizer.normalize(texte.lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return sansAccents.map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }
    }
}
