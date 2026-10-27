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
     * regex : un nombre suivi de « min », « mn » ou « minute(s) », ou un intervalle
     * (« 12 à 14 min », « 12-14 minutes ») dont on garde la borne haute. Plusieurs durées dans
     * une même étape : la plus longue, celle qu'on attend vraiment.
     */
    fun dureeEnSecondes(texte: String): Int? {
        val mots = decouper(texte).filter { it.sorte != SorteJeton.BLANC }
        var plusLongue: Double? = null
        for ((u, mot) in mots.withIndex()) {
            if (mot.sorte != SorteJeton.LETTRES || mot.minuscule !in MINUTES) continue
            val haute = mots.getOrNull(u - 1)?.takeIf { it.sorte == SorteJeton.CHIFFRES } ?: continue
            val basse = mots.getOrNull(u - 3)?.takeIf {
                it.sorte == SorteJeton.CHIFFRES && mots[u - 2].minuscule in LIENS_D_INTERVALLE
            }
            val minutes = maxOf(haute.valeur, basse?.valeur ?: 0.0)
            if (minutes > 0 && minutes <= MINUTES_MAX) plusLongue = maxOf(plusLongue ?: 0.0, minutes)
        }
        return plusLongue?.let { (it * 60).roundToInt() }
    }

    private val MINUTES = setOf("min", "mn", "minute", "minutes")
    private val LIENS_D_INTERVALLE = setOf("à", "a", "-", "–", "ou")

    /** Au-delà de dix heures, ce n'est plus un minuteur de cuisine : sans doute une faute de frappe. */
    private const val MINUTES_MAX = 600.0
}
