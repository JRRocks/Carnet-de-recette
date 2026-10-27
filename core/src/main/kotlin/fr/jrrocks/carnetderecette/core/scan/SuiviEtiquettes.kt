package fr.jrrocks.carnetderecette.core.scan

/**
 * Une box reconnue de façon sûre sur son étiquette : son numéro est confirmé par plusieurs
 * images. Le nombre de personnes, lui, n'est que **proposé** : Claude le confirme le vendredi
 * soir à partir des [elements] bruts (cadrage § 5).
 */
data class BoxValidee(
    val numero: Int,
    /** Le nombre de personnes le plus lu pendant le consensus ; `null` s'il n'a jamais été lu. */
    val personnesProposees: Int?,
    /** Personnes jamais lues, ou lues différemment d'une image à l'autre. */
    val aConfirmer: Boolean,
    /** Les éléments de l'image qui a validé la box, pour que Claude les relise. */
    val elements: List<ElementLu>,
)

/**
 * Le suivi des étiquettes au fil des images (moteur Library `scan-multimode`, chaîne
 * consensus puis anti-rebond).
 *
 * - [ScanConsensus] porte sur le **numéro** : il faut [seuil] lectures identiques pour valider
 *   une box. La garde d'ambiguïté remet à zéro deux numéros à un chiffre près qui ne sont
 *   jamais vus ensemble (« 207 » et « 201 » : la même étiquette mal lue).
 * - Pendant ce temps, chaque image **vote** pour le nombre de personnes qu'elle a lu. Un vote
 *   unanime est proposé tel quel ; des votes partagés ou absents marquent la box « à
 *   confirmer » plutôt que de deviner.
 * - [ScanDebouncer] empêche de revalider aussitôt la box qui reste dans le champ.
 *
 * Non thread-safe : appelé depuis l'unique fil d'analyse de CameraX. [horloge] est injectée
 * pour les tests.
 */
class SuiviEtiquettes(
    private val horloge: () -> Long = System::currentTimeMillis,
    seuil: Int = SEUIL,
    fenetreMs: Long = ScanDebouncer.DEFAULT_WINDOW_MS,
    private val ttlMs: Long = ScanConsensus.DEFAULT_TTL_MS,
) {
    private val consensus = ScanConsensus(threshold = seuil, ambiguityGuard = true, ttlMs = ttlMs)
    private val debouncer = ScanDebouncer(fenetreMs)

    /** Les votes d'un numéro en cours de consensus ; ils expirent avec lui ([ttlMs]). */
    private class Votes(var derniereLecture: Long) {
        val parNombre = HashMap<Int, Int>()
    }

    private val votes = HashMap<Int, Votes>()

    /** L'avancement du consensus le plus avancé (0 à 1) : le contour du réticule se remplit d'autant. */
    val progression: Float get() = consensus.peakProgress()

    /** Une image de plus ; rend la box quand son numéro vient d'être confirmé, `null` sinon. */
    fun soumettre(lecture: LectureEtiquette): BoxValidee? {
        val maintenant = horloge()
        votes.entries.removeAll { maintenant - it.value.derniereLecture >= ttlMs }
        val numero = lecture.numero
        if (numero == null) {
            // Une image sans numéro ne casse pas le compteur en cours, mais fait vieillir les autres.
            consensus.submit(emptyList(), maintenant)
            return null
        }
        val votesDuNumero = votes.getOrPut(numero) { Votes(maintenant) }
        votesDuNumero.derniereLecture = maintenant
        lecture.personnes?.let { votesDuNumero.parNombre[it] = (votesDuNumero.parNombre[it] ?: 0) + 1 }

        val valides = consensus.submit(listOf(ConsensusCandidate(numero.toString())), maintenant)
        if (valides.isEmpty()) return null
        votes.remove(numero)
        if (!debouncer.accept(numero.toString(), maintenant)) return null

        val parNombre = votesDuNumero.parNombre
        val plusHaut = parNombre.values.maxOrNull()
        val enTete = parNombre.filterValues { it == plusHaut }.keys
        return BoxValidee(
            numero = numero,
            personnesProposees = enTete.singleOrNull(),
            aConfirmer = parNombre.size != 1,
            elements = lecture.elements,
        )
    }

    /** Repart de zéro (en ouvrant l'écran). */
    fun reinitialiser() {
        consensus.reset()
        debouncer.reset()
        votes.clear()
    }

    companion object {
        /** Cinq lectures identiques, le seuil éprouvé du moteur en OCR (bruit par image plus fort). */
        const val SEUIL = 5
    }
}
