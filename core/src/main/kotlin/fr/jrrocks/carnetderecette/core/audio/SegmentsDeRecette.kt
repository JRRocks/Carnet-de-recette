package fr.jrrocks.carnetderecette.core.audio

import fr.jrrocks.carnetderecette.core.recette.EtapeRecette
import fr.jrrocks.carnetderecette.core.recette.Recette
import kotlin.math.roundToInt

/**
 * Les segments lus d'une recette, **en attendant ceux de Cowork** (cadrage § 8, lot 3) : un
 * segment par étape, de quoi essayer la voix et les commandes dès maintenant.
 *
 * Le minuteur se déduit de la durée écrite dans l'étape (« 12 à 14 min » → 14 minutes : on
 * attend la borne haute, on pourra toujours dire « suivant » avant). L'étape qui suit une
 * étape minutée démarre à la fin du minuteur ; les autres, quand la précédente est faite.
 * La première attend qu'on la demande : la voix ne part pas toute seule à l'ouverture.
 */
object SegmentsDeRecette {

    fun de(recette: Recette): List<SegmentAudio> {
        val minuteurs = recette.etapes.map { dureeEnSecondes(it.texte) }
        return recette.etapes.mapIndexed { i, etape ->
            SegmentAudio(
                id = idDe(etape),
                texte = "Étape ${etape.numero}. ${TexteOral.pourLOral(etape.texte)}",
                tache = "etape-${etape.numero}",
                declencheur = when {
                    i == 0 -> Declencheur.MANUEL
                    minuteurs[i - 1] != null -> Declencheur.FIN_MINUTEUR
                    else -> Declencheur.FIN_TACHE_PRECEDENTE
                },
                minuteurSecondes = minuteurs[i],
            )
        }
    }

    /** L'identifiant du segment d'une étape : l'écran s'en sert pour afficher le texte d'origine. */
    fun idDe(etape: EtapeRecette): String = "e${etape.numero}"

    /**
     * La durée écrite dans [texte], en secondes ; `null` s'il n'y en a pas. Lue mot à mot, sans
     * regex : un nombre suivi de « min », « mn », « minute(s) », « h » ou « heure(s) », ou un
     * intervalle (« 12 à 14 min », « 12-14 minutes », « 1 à 2 h ») dont on garde la borne
     * haute. Un nombre juste après l'unité la complète : « 1 h 30 » fait 90 minutes,
     * « 4 min 30 » quatre minutes trente. Plusieurs durées dans une même étape : la plus
     * longue, celle qu'on attend vraiment.
     *
     * Pas de minuteur plutôt qu'un faux : une fraction (« 1/2 min », « ½ h ») ou un nombre
     * douteux après l'unité (« 4 min 30 de chaque côté ») ne donnent rien.
     */
    fun dureeEnSecondes(texte: String): Int? {
        val mots = decouper(texte).filter { it.sorte != SorteJeton.BLANC }
        return mots.indices.mapNotNull { dureeA(mots, it) }.filter { it in 1..SECONDES_MAX }.maxOrNull()
    }

    /** La durée dont l'unité est le mot [u], en secondes ; `null` si ce n'est pas une unité, ou si la durée est douteuse. */
    private fun dureeA(mots: List<Jeton>, u: Int): Int? {
        val unite = secondesParUnite(mots[u]) ?: return null
        val haute = nombreAvant(mots, u) ?: return null
        // La borne basse d'un intervalle ; mais dans « 4 min 30 à 5 min », 30 finit la durée d'avant.
        val basse = nombreAvant(mots, u - 2)?.takeIf {
            mots[u - 2].minuscule in LIENS_D_INTERVALLE && mots.getOrNull(u - 4)?.let(::secondesParUnite) == null
        }
        val complement = complementApres(mots, u, unite) ?: return null
        return maxOf(haute * unite + complement, (basse ?: 0.0) * unite).takeIf { it.isFinite() }?.roundToInt()
    }

    private fun secondesParUnite(mot: Jeton): Int? = when {
        mot.sorte != SorteJeton.LETTRES -> null
        mot.minuscule in MINUTES -> 60
        mot.minuscule in HEURES -> SECONDES_PAR_HEURE
        else -> null
    }

    /** Le nombre juste avant le mot [i], s'il n'est pas le bas d'une fraction (« 1/2 min »). */
    private fun nombreAvant(mots: List<Jeton>, i: Int): Double? =
        mots.getOrNull(i - 1)?.takeIf { it.sorte == SorteJeton.CHIFFRES && mots.getOrNull(i - 2)?.texte != "/" }?.valeur

    /**
     * Le nombre qui complète l'unité [u], en secondes : « 1 h 30 » (avec ou sans « min » ensuite)
     * ajoute 30 minutes, « 4 min 30 » 30 secondes, mais seulement suivi d'une ponctuation, de
     * « s » ou de « secondes », ou en fin de texte. 0 sans nombre après, `null` s'il est douteux.
     */
    private fun complementApres(mots: List<Jeton>, u: Int, unite: Int): Int? {
        val nombre = mots.getOrNull(u + 1)?.takeIf { it.sorte == SorteJeton.CHIFFRES } ?: return 0
        val n = nombre.texte.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val suite = mots.getOrNull(u + 2)
        // « 1 h 1/2 », « 4 min 1/2 » : une fraction de quoi ? On ne devine pas.
        if (suite?.texte == "/") return null
        return when {
            unite == SECONDES_PAR_HEURE -> n * 60
            suite == null || suite.sorte == SorteJeton.SIGNE || suite.minuscule in SECONDES -> n
            else -> null
        }
    }

    private val MINUTES = setOf("min", "mn", "minute", "minutes")
    private val HEURES = setOf("h", "heure", "heures")
    private val SECONDES = setOf("s", "sec", "seconde", "secondes")
    private val LIENS_D_INTERVALLE = setOf("à", "a", "-", "–", "ou")
    private const val SECONDES_PAR_HEURE = 3_600

    /** Au-delà de dix heures, ce n'est plus un minuteur de cuisine : sans doute une faute de frappe. */
    private const val SECONDES_MAX = 10 * SECONDES_PAR_HEURE
}
