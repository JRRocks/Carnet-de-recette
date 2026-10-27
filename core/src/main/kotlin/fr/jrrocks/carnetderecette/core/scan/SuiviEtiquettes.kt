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
 * - **Anti-rebond glissant** (adaptation du Carnet, à la place du verrou à durée fixe de
 *   [ScanDebouncer]) : un numéro validé est verrouillé tant qu'il reste dans le champ. Chaque
 *   lecture repousse le verrou, qui ne tombe qu'après [fenetreMs] sans le lire. L'étiquette
 *   gardée en vue pendant qu'on corrige les personnes n'est donc pas revalidée (pas de « déjà
 *   scannée » en boucle), et la même box se rescanne exprès en la sortant un instant du champ.
 *   Un verrou à durée fixe tombait 1,5 s après la validation, étiquette toujours en vue.
 * - Un numéro à un chiffre près d'un numéro verrouillé (« 201 » quand « 207 » est en vue) est
 *   écarté sans repousser le verrou : la garde d'ambiguïté du consensus ne le voit plus, puisque
 *   le numéro verrouillé n'y est plus compté. Une vraie box voisine passe au plus [fenetreMs]
 *   après que la première a quitté le champ.
 * - [ignorer] pose le même verrou sur une box que l'utilisateur vient de retirer : elle ne
 *   revient pas toute seule si son étiquette est encore dans le réticule.
 *
 * [soumettre] est appelé depuis l'unique fil d'analyse de CameraX, [ignorer] et
 * [reinitialiser] depuis le fil principal : les méthodes publiques sont synchronisées.
 * [horloge] est injectée pour les tests.
 */
class SuiviEtiquettes(
    private val horloge: () -> Long = System::currentTimeMillis,
    seuil: Int = SEUIL,
    private val fenetreMs: Long = ScanDebouncer.DEFAULT_WINDOW_MS,
    private val ttlMs: Long = ScanConsensus.DEFAULT_TTL_MS,
) {
    private val consensus = ScanConsensus(threshold = seuil, ambiguityGuard = true, ttlMs = ttlMs)

    /** Les votes d'un numéro en cours de consensus ; ils expirent avec lui ([ttlMs]). */
    private class Votes(var derniereLecture: Long) {
        val parNombre = HashMap<Int, Int>()
    }

    private val votes = HashMap<Int, Votes>()

    /** Les numéros verrouillés (validés ou retirés), avec l'heure de leur dernière lecture. */
    private val verrous = HashMap<Int, Long>()

    /** L'avancement du consensus le plus avancé (0 à 1) : le contour du réticule se remplit d'autant. */
    val progression: Float
        @Synchronized get() = consensus.peakProgress()

    /** Une image de plus ; rend la box quand son numéro vient d'être confirmé, `null` sinon. */
    @Synchronized
    fun soumettre(lecture: LectureEtiquette): BoxValidee? {
        val maintenant = horloge()
        votes.entries.removeAll { maintenant - it.value.derniereLecture >= ttlMs }
        verrous.entries.removeAll { maintenant - it.value >= fenetreMs }
        val numero = lecture.numero
        if (numero == null || numero in verrous) {
            // Un numéro verrouillé encore dans le champ : le verrou glisse, rien n'est revalidé.
            if (numero != null) verrous[numero] = maintenant
            // Une image sans numéro à compter ne casse pas le compteur en cours, mais fait vieillir les autres.
            consensus.submit(emptyList(), maintenant)
            return null
        }
        if (verrous.keys.any { ScanConsensus.withinEditDistanceOne(numero.toString(), it.toString()) }) {
            // À un chiffre près d'un numéro verrouillé encore dans le champ : la même étiquette
            // mal lue (« 201 » pour « 207 »). Tant que l'original est en vue, sa garde d'ambiguïté
            // ne joue plus (il n'est plus compté) : on écarte le voisin sans repousser le verrou.
            consensus.submit(emptyList(), maintenant)
            return null
        }
        val votesDuNumero = votes.getOrPut(numero) { Votes(maintenant) }
        votesDuNumero.derniereLecture = maintenant
        lecture.personnes?.let { votesDuNumero.parNombre[it] = (votesDuNumero.parNombre[it] ?: 0) + 1 }

        val valides = consensus.submit(listOf(ConsensusCandidate(numero.toString())), maintenant)
        if (valides.isEmpty()) return null
        votes.remove(numero)
        verrous[numero] = maintenant

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

    /**
     * La box [numero] vient d'être retirée : elle n'est plus validée tant que son étiquette
     * reste dans le champ (relue à moins de [fenetreMs] d'intervalle).
     */
    @Synchronized
    fun ignorer(numero: Int) {
        verrous[numero] = horloge()
    }

    /** Repart de zéro (en ouvrant l'écran). */
    @Synchronized
    fun reinitialiser() {
        consensus.reset()
        votes.clear()
        verrous.clear()
    }

    companion object {
        /** Cinq lectures identiques, le seuil éprouvé du moteur en OCR (bruit par image plus fort). */
        const val SEUIL = 5
    }
}
