package fr.jrrocks.carnetderecette.core.audio

/**
 * Les minuteurs d'une séance, en comptabilité pure : chaque appel reçoit l'heure
 * (`maintenant`, en millisecondes d'une horloge qui ne recule pas). L'app lui passe
 * `SystemClock.elapsedRealtime()`, les tests une valeur fixe : aucun fil, aucune attente.
 *
 * Plusieurs minuteurs tournent à la fois : les poireaux cuisent encore quand on passe à
 * l'étape suivante.
 */
class Minuteurs {

    /** Identifiant (celui du segment) → instant de fin. */
    private val fins = LinkedHashMap<String, Long>()

    /** Lance (ou relance depuis le début) le minuteur [id]. */
    fun lancer(id: String, secondes: Int, maintenant: Long) {
        require(secondes > 0) { "Un minuteur dure au moins une seconde." }
        fins[id] = maintenant + secondes * 1000L
    }

    /**
     * Les secondes restantes, arrondies au-dessus (« 4:59 » une seconde après le lancement
     * d'un minuteur de 5 minutes, « 0:01 » jusqu'à la dernière milliseconde) ; `null` si [id]
     * ne tourne pas.
     */
    fun restant(id: String, maintenant: Long): Int? {
        val fin = fins[id] ?: return null
        val reste = (fin - maintenant).coerceAtLeast(0)
        return ((reste + 999) / 1000).toInt()
    }

    /** Les minuteurs finis depuis le dernier appel, du premier fini au dernier ; chacun une seule fois. */
    fun expires(maintenant: Long): List<String> {
        val finis = fins.entries.filter { it.value <= maintenant }.sortedBy { it.value }.map { it.key }
        finis.forEach(fins::remove)
        return finis
    }

    fun annuler(id: String) {
        fins.remove(id)
    }

    /** Les minuteurs qui tournent : identifiant → instant de fin. */
    fun tous(): Map<String, Long> = fins.toMap()
}
