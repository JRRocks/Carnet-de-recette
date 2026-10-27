package fr.jrrocks.carnetderecette.core.audio

/**
 * Ce que la séance demande au téléphone : le [LecteurSegments] décide, l'app exécute (la voix,
 * les minuteurs, la sonnerie) puis lui rapporte ce qui s'est passé ([LecteurSegments.finDeLecture],
 * [LecteurSegments.minuteurFini]). Le lecteur reste ainsi testable sans Android ni attente.
 */
sealed interface Effet {
    /** Dire le segment ; l'app rappelle [LecteurSegments.finDeLecture] quand la voix a fini. */
    data class Dire(val segmentId: String, val texte: String) : Effet

    /** Couper la voix tout de suite. */
    data object Taire : Effet

    data class LancerMinuteur(val segmentId: String, val secondes: Int) : Effet

    data class AnnulerMinuteur(val segmentId: String) : Effet

    /**
     * Le minuteur du segment vient de finir : une sonnerie, même en pause ou voix coupée. Un
     * minuteur qui finit en silence, c'est un plat qui brûle.
     */
    data class Sonner(val segmentId: String) : Effet

    /** Le dernier segment est fait. */
    data object Terminee : Effet
}

/** L'état d'une séance, immuable : ce que l'écran affiche. */
data class EtatLecteur(
    /** Les segments, avec ce que l'utilisateur en a fait (coupés, déclencheurs changés). */
    val segments: List<SegmentAudio>,
    /** Le segment en cours ; [AVANT_DEBUT] avant [LecteurSegments.demarrer], `segments.size` une fois terminée. */
    val courant: Int = AVANT_DEBUT,
    val faits: Set<String> = emptySet(),
    val enPause: Boolean = false,
    /** Le segment que la voix est en train de dire. */
    val enLecture: String? = null,
    /** Les segments déjà dits au moins une fois (« Lire » devient « Relire »). */
    val dits: Set<String> = emptySet(),
    /** Les segments dont le minuteur tourne. */
    val minuteurs: Set<String> = emptySet(),
) {
    val demarree: Boolean get() = courant != AVANT_DEBUT
    val terminee: Boolean get() = courant >= segments.size
    val parle: Boolean get() = enLecture != null
    val segmentCourant: SegmentAudio? get() = segments.getOrNull(courant)

    companion object {
        const val AVANT_DEBUT = -1
    }
}

/**
 * La séance lue à voix haute, en machine d'états **pure** : aucun Android, aucune coroutine,
 * aucune horloge. Chaque entrée change l'[etat] et rend les [Effet]s à exécuter, dans l'ordre.
 *
 * Les règles (cadrage § 8) :
 * - **valider** (« c'est fait », « suivant ») : le segment courant est fait ; on passe au
 *   prochain segment **actif** et la voix le dit tout de suite, quel que soit son déclencheur :
 *   on vient de le demander. Après le dernier, la séance est terminée.
 * - **Déclencheurs**, pour ce qui part tout seul : un segment [Declencheur.FIN_MINUTEUR] est lu
 *   quand le minuteur du segment courant finit (le courant compte alors comme fait) ; un
 *   segment [Declencheur.MANUEL] ne part jamais seul, pas même le premier à l'ouverture.
 *   [Declencheur.FIN_TACHE_PRECEDENTE] se lit dès que le précédent est validé : seul, c'est
 *   valider ; à deux (lot 6), ce sera aussi quand l'autre cuisinier valide.
 * - Un segment **coupé** reste affiché mais n'est jamais lu, et valider le saute.
 * - **pause** coupe la voix et plus rien ne part seul jusqu'à **reprendre**, qui redit le
 *   segment courant. Les minuteurs, eux, continuent : le plat cuit toujours. Un geste qui
 *   demande la voix (valider, relire, précédent) lève la pause.
 * - Les minuteurs se lancent à la demande (bouton, « minuteur ») : la voix lit l'étape avant
 *   qu'on ait mis les poireaux dans la poêle.
 */
class LecteurSegments(segments: List<SegmentAudio>) {

    var etat = EtatLecteur(segments)
        private set

    private val segments get() = etat.segments

    /** Ouvre la séance sur le premier segment actif, sans le lire s'il attend qu'on le demande. */
    fun demarrer(): List<Effet> {
        if (etat.demarree) return emptyList()
        if (segments.isEmpty()) {
            etat = etat.copy(courant = 0)
            return listOf(Effet.Terminee)
        }
        val premier = suivantActif(EtatLecteur.AVANT_DEBUT) ?: 0
        etat = etat.copy(courant = premier)
        return if (segments[premier].declencheur == Declencheur.MANUEL) emptyList() else dire(premier)
    }

    /** « C'est fait », « suivant ». */
    fun valider(): List<Effet> {
        val courant = etat.segmentCourant ?: return emptyList()
        val effets = taireSiParle()
        val suivant = suivantActif(etat.courant)
        etat = etat.copy(courant = suivant ?: segments.size, faits = etat.faits + courant.id, enPause = false)
        return effets + if (suivant == null) listOf(Effet.Terminee) else dire(suivant)
    }

    /** « Répète » : redit le segment courant depuis le début. */
    fun relire(): List<Effet> {
        if (etat.segmentCourant?.actif != true) return emptyList()
        val effets = taireSiParle()
        etat = etat.copy(enPause = false)
        return effets + dire(etat.courant)
    }

    /** « Précédent » : revient au segment actif d'avant, qui n'est plus fait, et le dit. Au premier, le redit. */
    fun precedent(): List<Effet> {
        if (!etat.demarree) return emptyList()
        val precedent = precedentActif(etat.courant) ?: return relire()
        val effets = taireSiParle()
        etat = etat.copy(courant = precedent, faits = etat.faits - segments[precedent].id, enPause = false)
        return effets + dire(precedent)
    }

    fun pause(): List<Effet> {
        if (!etat.demarree || etat.terminee || etat.enPause) return emptyList()
        val effets = taireSiParle()
        etat = etat.copy(enPause = true)
        return effets
    }

    /** « Reprends », « vas-y » : lève la pause et dit le segment courant. */
    fun reprendre(): List<Effet> {
        if (!etat.demarree || etat.terminee || etat.parle) return emptyList()
        etat = etat.copy(enPause = false)
        return if (etat.segmentCourant?.actif == true) dire(etat.courant) else emptyList()
    }

    /** Coupe ou réactive la lecture d'un segment. Coupé pendant qu'il est dit, la voix se tait. */
    fun basculer(id: String): List<Effet> {
        val i = indexDe(id) ?: return emptyList()
        val segment = segments[i]
        val coupeEnParlant = segment.actif && etat.enLecture == id
        val effets = if (coupeEnParlant) taireSiParle() else emptyList()
        etat = etat.copy(segments = segments.remplace(i, segment.copy(actif = !segment.actif)))
        return effets
    }

    fun changerDeclencheur(id: String, declencheur: Declencheur): List<Effet> {
        val i = indexDe(id) ?: return emptyList()
        etat = etat.copy(segments = segments.remplace(i, segments[i].copy(declencheur = declencheur)))
        return emptyList()
    }

    /** La voix a fini de dire [id] (ou s'est arrêtée). Un rapport d'un ancien segment est ignoré. */
    fun finDeLecture(id: String): List<Effet> {
        if (etat.enLecture == id) etat = etat.copy(enLecture = null)
        return emptyList()
    }

    /** « Minuteur » : lance celui du segment courant, s'il en a un et qu'il ne tourne pas déjà. */
    fun lancerMinuteur(): List<Effet> {
        val courant = etat.segmentCourant ?: return emptyList()
        val secondes = courant.minuteurSecondes ?: return emptyList()
        if (courant.id in etat.minuteurs) return emptyList()
        etat = etat.copy(minuteurs = etat.minuteurs + courant.id)
        return listOf(Effet.LancerMinuteur(courant.id, secondes))
    }

    fun arreterMinuteur(id: String): List<Effet> {
        if (id !in etat.minuteurs) return emptyList()
        etat = etat.copy(minuteurs = etat.minuteurs - id)
        return listOf(Effet.AnnulerMinuteur(id))
    }

    /**
     * Le minuteur de [id] est fini : il sonne. Si c'est celui du segment courant et que le
     * prochain segment actif attend la fin du minuteur, le courant est fait et le prochain est
     * dit (en pause, il devient courant sans être dit : [reprendre] le dira).
     */
    fun minuteurFini(id: String): List<Effet> {
        if (id !in etat.minuteurs) return emptyList()
        etat = etat.copy(minuteurs = etat.minuteurs - id)
        val effets = mutableListOf<Effet>(Effet.Sonner(id))
        if (etat.segmentCourant?.id != id) return effets
        val suivant = suivantActif(etat.courant) ?: return effets
        if (segments[suivant].declencheur != Declencheur.FIN_MINUTEUR) return effets
        effets += taireSiParle()
        etat = etat.copy(courant = suivant, faits = etat.faits + id)
        if (!etat.enPause) effets += dire(suivant)
        return effets
    }

    // ── Outils ──────────────────────────────────────────────────────────────────

    private fun dire(i: Int): List<Effet> {
        val segment = segments[i]
        if (!segment.actif) return emptyList()
        etat = etat.copy(enLecture = segment.id, dits = etat.dits + segment.id)
        return listOf(Effet.Dire(segment.id, segment.texte))
    }

    private fun taireSiParle(): List<Effet> {
        if (!etat.parle) return emptyList()
        etat = etat.copy(enLecture = null)
        return listOf(Effet.Taire)
    }

    private fun suivantActif(depuis: Int): Int? =
        ((depuis + 1)..segments.lastIndex).firstOrNull { segments[it].actif }

    private fun precedentActif(depuis: Int): Int? =
        (minOf(depuis, segments.size) - 1 downTo 0).firstOrNull { segments[it].actif }

    private fun indexDe(id: String): Int? = segments.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    private fun <T> List<T>.remplace(i: Int, valeur: T): List<T> = toMutableList().also { it[i] = valeur }
}
