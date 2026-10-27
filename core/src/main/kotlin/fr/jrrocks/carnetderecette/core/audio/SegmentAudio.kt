package fr.jrrocks.carnetderecette.core.audio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Un segment lu à voix haute : une action courte, jamais la recette de A à Z (cadrage § 8).
 * Même forme que dans le fichier de la semaine écrit par Cowork (`segments_audio`).
 */
@Serializable
data class SegmentAudio(
    val id: String,
    val texte: String,
    /** La tâche (ou l'étape) à laquelle le segment appartient. */
    val tache: String? = null,
    val declencheur: Declencheur = Declencheur.MANUEL,
    /** Durée du minuteur à lancer avec ce segment, en secondes. */
    val minuteurSecondes: Int? = null,
    /** L'utilisateur peut couper un segment (par exemple une astuce) : il est alors sauté. */
    val actif: Boolean = true,
)

@Serializable
enum class Declencheur {
    /** Lu quand on le demande (« suivant », « c'est fait »). */
    @SerialName("manuel") MANUEL,

    /** Lu dès que le segment précédent est validé. */
    @SerialName("fin_tache_precedente") FIN_TACHE_PRECEDENTE,

    /** Lu à la fin du minuteur du segment précédent. */
    @SerialName("fin_minuteur") FIN_MINUTEUR,
}
