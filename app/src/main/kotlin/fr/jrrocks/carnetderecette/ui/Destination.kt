package fr.jrrocks.carnetderecette.ui

import androidx.compose.runtime.saveable.Saver
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso

/**
 * Les écrans poussés par-dessus les onglets, en plein écran (direction « Correspondance ») :
 * le scanner du vendredi, la fiche papier, la fiche d'une recette et la séance audio. Le
 * retour système les dépile un par un.
 */
sealed interface Destination {
    data class Scanner(val semaine: SemaineIso) : Destination

    /** Photographier la fiche papier, pour la box [numero] si on la connaît. */
    data class Fiche(val semaine: SemaineIso, val numero: Int?) : Destination

    data class Recette(val semaine: SemaineIso, val numero: Int) : Destination

    /**
     * La séance lue à voix haute ; sans [numero], la recette d'exemple. [qui] : les étapes de la
     * recette, ou les segments que Claude a préparés pour un cuisinier.
     */
    data class Seance(val semaine: SemaineIso, val numero: Int?, val qui: Qui = Qui.RECETTE) : Destination

    companion object {
        /** La pile survit à une rotation : chaque destination s'écrit en une chaîne. */
        val SauvegardePile: Saver<List<Destination>, ArrayList<String>> = Saver(
            save = { pile -> ArrayList(pile.map(::ecrire)) },
            restore = { lignes -> lignes.mapNotNull(::lire) },
        )

        internal fun ecrire(d: Destination): String = when (d) {
            is Scanner -> "scanner|${d.semaine}"
            is Fiche -> "fiche|${d.semaine}|${d.numero ?: ""}"
            is Recette -> "recette|${d.semaine}|${d.numero}"
            is Seance -> "seance|${d.semaine}|${d.numero ?: ""}|${d.qui.name}"
        }

        internal fun lire(ligne: String): Destination? {
            val m = ligne.split('|')
            val semaine = m.getOrNull(1)?.let(SemaineIso::lire) ?: return null
            val numero = m.getOrNull(2)?.toIntOrNull()
            return when (m[0]) {
                "scanner" -> Scanner(semaine)
                "fiche" -> Fiche(semaine, numero)
                "recette" -> numero?.let { Recette(semaine, it) }
                // Une pile sauvée avant le lot 4 n'a pas de quatrième morceau : les étapes de la recette.
                "seance" -> Seance(semaine, numero, m.getOrNull(3)?.let { q -> Qui.entries.firstOrNull { it.name == q } } ?: Qui.RECETTE)
                else -> null
            }
        }
    }
}

/** Ce que lit une séance. */
enum class Qui {
    /** Les étapes de la recette, une par segment (la fiche, la recette d'exemple). */
    RECETTE,

    /** Tous les segments de Claude, pour qui cuisine seul. */
    SEUL,

    /** Les segments de Claude pour la découpe, à deux. */
    DECOUPE,

    /** Les segments de Claude pour la chauffe, à deux. */
    CHAUFFE,
}
