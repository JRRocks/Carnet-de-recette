package fr.jrrocks.carnetderecette.core.scan

/** Une valeur lue dans une image, soumise au consensus ; aussi ce qu'il rend une fois validée. */
data class ConsensusCandidate(
    val value: String,
    val symbology: String? = null,
)

/**
 * Validation par consensus du scan à la chaîne (moteur Library `scan-multimode`).
 *
 * L'optique se trompe : angle, reflet, étiquette abîmée. Une seule lecture ne distingue pas
 * une vraie valeur d'un artefact. Ce filtre compte les lectures identiques d'une valeur et ne
 * la valide qu'au bout de [threshold] lectures.
 *
 * « Consécutives » ne veut pas dire « à chaque image » : une image sans lecture ne casse pas
 * un compteur en cours (une main qui tremble ne doit pas repousser sans fin la validation),
 * mais une valeur différente qui s'intercale le fait, si la garde d'ambiguïté est active.
 * Chaque compteur expire au bout de [ttlMs] sans nouvelle lecture : un consensus abandonné ne
 * se complète pas avec une lecture bien plus tardive.
 *
 * Non thread-safe : appelé depuis l'unique fil d'analyse de CameraX, comme [ScanDebouncer],
 * qui passe **après** lui.
 */
class ScanConsensus(
    private val threshold: Int,
    private val ambiguityGuard: Boolean,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    private class Counter(
        var count: Int,
        var lastSeen: Long,
        val symbology: String?,
        val firstRawValue: String,
    ) {
        /** Les clés avec lesquelles cette valeur a déjà partagé une image : des objets distincts. */
        val coexistedWith = HashSet<String>()
    }

    private val counters = LinkedHashMap<String, Counter>()

    /**
     * Confronte les candidats d'une image au consensus en cours et rend ceux qui atteignent
     * [threshold] à cet appel. Une valeur validée est aussitôt purgée : elle peut repartir
     * pour un nouveau consensus si le même objet repasse devant la caméra.
     */
    fun submit(candidates: List<ConsensusCandidate>, now: Long = System.currentTimeMillis()): List<ConsensusCandidate> {
        pruneExpired(now)

        val frameKeys = candidates
            .filter { ScanValue.isUsable(it.value) }
            .map { ScanValue.key(it.value) }
            .distinct()

        val newlyCreated = mutableListOf<String>()
        candidates.forEach { candidate ->
            if (!ScanValue.isUsable(candidate.value)) return@forEach
            val key = ScanValue.key(candidate.value)
            val existing = counters[key]
            if (existing == null) {
                counters[key] = Counter(1, now, candidate.symbology, candidate.value)
                newlyCreated += key
            } else {
                existing.count += 1
                existing.lastSeen = now
            }
        }

        if (ambiguityGuard) {
            markCoexistence(frameKeys)
            resolveAmbiguity(newlyCreated)
        }

        val validated = mutableListOf<ConsensusCandidate>()
        frameKeys.forEach { key ->
            val counter = counters[key] ?: return@forEach
            if (counter.count >= threshold) {
                validated += ConsensusCandidate(counter.firstRawValue, counter.symbology)
                counters.remove(key)
            }
        }
        return validated
    }

    /** L'avancement du compteur le plus avancé (0 à 1), pour le remplissage du réticule. */
    fun peakProgress(): Float {
        val best = counters.values.maxOfOrNull { it.count } ?: return 0f
        return (best.toFloat() / threshold).coerceIn(0f, 1f)
    }

    fun reset() {
        counters.clear()
    }

    /**
     * Deux valeurs vues dans la même image sont deux objets distincts : elles ne doivent
     * jamais s'annuler, même proches (deux étiquettes côte à côte).
     */
    private fun markCoexistence(frameKeys: List<String>) {
        if (frameKeys.size < 2) return
        for (i in frameKeys.indices) {
            for (j in i + 1 until frameKeys.size) {
                counters[frameKeys[i]]?.coexistedWith?.add(frameKeys[j])
                counters[frameKeys[j]]?.coexistedWith?.add(frameKeys[i])
            }
        }
    }

    /**
     * Une valeur qui vient d'apparaître, à un caractère près d'une valeur déjà en cours de
     * comptage, sans que les deux aient jamais partagé une image, est sans doute la même
     * étiquette lue de deux façons (`207` et `201`). Les deux compteurs repartent à zéro
     * plutôt que de laisser le hasard choisir laquelle atteint le seuil la première.
     */
    private fun resolveAmbiguity(newlyCreated: List<String>) {
        newlyCreated.forEach { key ->
            val counter = counters[key] ?: return@forEach
            val conflict = counters.keys.firstOrNull { other ->
                other != key &&
                    withinEditDistanceOne(key, other) &&
                    other !in counter.coexistedWith &&
                    key !in (counters[other]?.coexistedWith ?: emptySet())
            }
            if (conflict != null) {
                counters.remove(key)
                counters.remove(conflict)
            }
        }
    }

    /** Purge les compteurs abandonnés : sans elle, une longue session ferait grossir la table sans fin. */
    private fun pruneExpired(now: Long) {
        counters.entries.removeAll { now - it.value.lastSeen >= ttlMs }
    }

    companion object {
        const val DEFAULT_TTL_MS = 800L

        /** Vrai si [a] et [b] diffèrent d'au plus une substitution, une insertion ou une suppression. */
        internal fun withinEditDistanceOne(a: String, b: String): Boolean {
            if (a == b) return false
            val lengthDiff = a.length - b.length
            if (lengthDiff !in -1..1) return false

            if (a.length == b.length) {
                var diffs = 0
                for (i in a.indices) {
                    if (a[i] != b[i]) {
                        diffs++
                        if (diffs > 1) return false
                    }
                }
                return diffs == 1
            }

            val shorter = if (a.length < b.length) a else b
            val longer = if (a.length < b.length) b else a
            var i = 0
            var j = 0
            var skipped = false
            while (i < shorter.length && j < longer.length) {
                if (shorter[i] == longer[j]) {
                    i++
                    j++
                } else {
                    if (skipped) return false
                    skipped = true
                    j++
                }
            }
            return true
        }
    }
}
