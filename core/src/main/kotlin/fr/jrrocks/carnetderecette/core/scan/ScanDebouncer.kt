package fr.jrrocks.carnetderecette.core.scan

/**
 * Verrou anti-rebond du scan à la chaîne (moteur Library `scan-multimode`).
 *
 * Une étiquette reste dans le champ pendant des dizaines d'images : une fois validée par le
 * consensus, elle le serait de nouveau aussitôt. Ce filtre laisse passer une valeur, puis
 * l'ignore pendant [windowMs]. Il passe **après** [ScanConsensus] : placé avant, il écarterait
 * les lectures que le consensus doit compter.
 *
 * Il ne remplace pas le dédoublonnage : il évite le bruit (un « déjà scannée » en continu),
 * la liste des box tranche.
 *
 * Non thread-safe : appelé depuis l'unique fil d'analyse de CameraX.
 */
class ScanDebouncer(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
) {

    private val lastSeen = HashMap<String, Long>()

    /**
     * Vrai si la valeur doit être traitée, faux si elle vient d'être vue. La comparaison
     * utilise la clé de [ScanValue], la même que partout ailleurs.
     */
    fun accept(rawValue: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!ScanValue.isUsable(rawValue)) return false

        prune(now)
        val key = ScanValue.key(rawValue)
        val previous = lastSeen[key]
        if (previous != null && now - previous < windowMs) return false

        lastSeen[key] = now
        return true
    }

    /** Filtre les valeurs d'une même image en gardant leur ordre de lecture. */
    fun acceptAll(rawValues: List<String>, now: Long = System.currentTimeMillis()): List<String> =
        rawValues.filter { accept(it, now) }

    fun reset() = lastSeen.clear()

    /** Purge les entrées sorties de la fenêtre : sans elle, une longue session ferait grossir la table sans fin. */
    private fun prune(now: Long) {
        if (lastSeen.size < PRUNE_THRESHOLD) return
        lastSeen.entries.removeAll { now - it.value >= windowMs }
    }

    companion object {
        /**
         * 1,5 s : assez long pour couvrir le temps où l'étiquette reste visible après la
         * validation, assez court pour rescanner exprès la même box.
         */
        const val DEFAULT_WINDOW_MS = 1_500L
        private const val PRUNE_THRESHOLD = 64
    }
}
