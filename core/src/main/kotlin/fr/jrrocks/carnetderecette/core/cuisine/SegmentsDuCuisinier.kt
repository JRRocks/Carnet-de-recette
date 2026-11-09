package fr.jrrocks.carnetderecette.core.cuisine

import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.audio.SegmentAudio
import fr.jrrocks.carnetderecette.core.audio.TexteOral

/** Ce que la séance d'un cuisinier lit, et le texte que l'écran affiche pour chaque segment. */
data class SegmentsDeLaSeance(val segments: List<SegmentAudio>, val textes: Map<String, String>)

/**
 * Les segments de Claude que lit le téléphone d'un cuisinier (cadrage § 8 : chaque téléphone
 * ne lit que les segments de son cuisinier).
 *
 * - **À deux**, les segments du rôle, **dans l'ordre où Claude les a écrits** : il les a pensés
 *   à la suite, avec leurs déclencheurs (« à la fin du minuteur » suit le segment qui le lance).
 * - **Seul**, tous les segments, rangés selon le plan seul : ceux d'une tâche viennent quand elle
 *   commence, un segment sans tâche reste après celui qui le précède. Le texte « seul » de Claude
 *   remplace celui qui parle de l'autre cuisinier ; vide, le segment est sauté.
 *
 * Dans les deux cas, la séance ne part pas toute seule (le premier segment attend qu'on le
 * demande), et un segment « à la fin du minuteur » qui ne suit pas un segment minuté se lit sur
 * demande : c'est ce qui se passerait, autant l'afficher. La voix lit le texte remis pour
 * l'oral ([TexteOral]) ; l'écran garde celui de Claude.
 */
object SegmentsDuCuisinier {

    fun aDeux(recette: RecettePreparee, role: RoleCuisine): SegmentsDeLaSeance =
        enSeance(recette.segments.filter { roleDe(recette, it) == role }.map { it to it.texte })

    fun seul(recette: RecettePreparee, plan: Plan = Planificateur.planifier(recette, ModeCuisine.SEUL)): SegmentsDeLaSeance {
        var moment = 0
        val ranges = recette.segments.mapIndexed { i, segment ->
            plan.place(segment.tache)?.let { moment = it.debut }
            Triple(moment, i, segment)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        return enSeance(
            ranges.mapNotNull { (_, _, segment) ->
                (segment.texteSeul ?: segment.texte).takeIf { it.isNotBlank() }?.let { segment to it }
            },
        )
    }

    /** Le rôle d'un segment : le sien, sinon celui de sa tâche, sinon la chauffe. */
    fun roleDe(recette: RecettePreparee, segment: SegmentPrepare): RoleCuisine =
        segment.role ?: recette.tache(segment.tache)?.qui ?: RoleCuisine.CHAUFFE

    private fun enSeance(choisis: List<Pair<SegmentPrepare, String>>): SegmentsDeLaSeance {
        val segments = choisis.mapIndexed { i, (segment, texte) ->
            val precedent = choisis.getOrNull(i - 1)?.first
            SegmentAudio(
                id = segment.id,
                texte = TexteOral.pourLOral(texte),
                tache = segment.tache,
                declencheur = when {
                    i == 0 -> Declencheur.MANUEL
                    segment.declencheur == Declencheur.FIN_MINUTEUR && precedent?.minuteurSecondes == null -> Declencheur.MANUEL
                    else -> segment.declencheur
                },
                minuteurSecondes = segment.minuteurSecondes,
            )
        }
        return SegmentsDeLaSeance(segments, choisis.associate { (segment, texte) -> segment.id to texte.trim() })
    }
}
