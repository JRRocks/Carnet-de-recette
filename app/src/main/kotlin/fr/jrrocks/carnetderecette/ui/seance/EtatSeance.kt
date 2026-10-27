package fr.jrrocks.carnetderecette.ui.seance

import androidx.compose.runtime.Immutable
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale
import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.audio.EtatLecteur

/*
 * Ce que l'écran de la séance affiche, sans lien vers Android : le ViewModel le construit avec
 * [etatSeance], les captures aussi. L'écran ne montre que le voisinage du segment courant (le
 * dernier fait, le courant, les deux suivants) : un panneau lu de loin, pas une liste.
 */

enum class EtatSegment { FAIT, EN_COURS, A_VENIR }

@Immutable
data class SegmentEcran(
    val id: String,
    /** Le texte d'origine de l'étape : la voix lit sa version orale, l'écran garde l'écrit. */
    val texte: String,
    /** Sa place dans la séance, à partir de 1. */
    val numero: Int,
    val etat: EtatSegment,
    /** Coupé : affiché, jamais lu. */
    val actif: Boolean,
    val declencheur: Declencheur,
    val minuteurSecondes: Int?,
)

/** Un minuteur à l'écran : [secondes] restantes s'il tourne, sa durée sinon. */
@Immutable
data class MinuteurEcran(val segmentId: String, val numero: Int, val secondes: Int, val enMarche: Boolean)

/** Ce que le micro a entendu en dernier. */
sealed interface Entendu {
    /** La phrase en cours, telle qu'Android la devine. */
    data class EnCours(val texte: String) : Entendu

    data class Compris(val commande: CommandeVocale) : Entendu

    /** Une phrase finie qui n'est pas une commande : elle n'a rien fait. */
    data class PasCompris(val texte: String) : Entendu
}

@Immutable
data class EtatSeance(
    val titre: String,
    /** Le dernier segment fait avant le courant, montré réduit. */
    val precedent: SegmentEcran?,
    val courant: SegmentEcran?,
    /** Les deux segments qui suivent le courant. */
    val suivants: List<SegmentEcran>,
    /** Le nombre de segments après [suivants]. */
    val restants: Int,
    val terminee: Boolean,
    val enPause: Boolean,
    val parle: Boolean,
    /** Le courant a déjà été dit : le bouton dit « Relire » plutôt que « Lire ». */
    val dejaDit: Boolean,
    /** Le minuteur du dock : celui du segment courant, sinon celui qui finit le plus tôt. */
    val minuteur: MinuteurEcran?,
    /** Les autres minuteurs qui tournent (une étape déjà validée qui cuit encore). */
    val autresMinuteurs: List<MinuteurEcran>,
    val voix: EtatVoix,
    val voixCoupee: Boolean,
    val micro: EtatEcoute,
    val entendu: Entendu?,
)

/** Les gestes de la séance, remontés au ViewModel. */
@Immutable
data class ActionsSeance(
    val valider: () -> Unit = {},
    val relire: () -> Unit = {},
    val precedent: () -> Unit = {},
    val reprendre: () -> Unit = {},
    /** Lance le minuteur s'il est arrêté, l'arrête s'il tourne. */
    val minuteur: (MinuteurEcran) -> Unit = {},
    val basculer: (segmentId: String) -> Unit = {},
    val changerDeclencheur: (segmentId: String, Declencheur) -> Unit = { _, _ -> },
    val basculerVoix: () -> Unit = {},
    val basculerMicro: () -> Unit = {},
    /** Installe une voix française, ou ouvre les réglages de la synthèse vocale. */
    val reglerVoix: () -> Unit = {},
    /** Les réglages de l'app, pour autoriser le micro. */
    val reglerMicro: () -> Unit = {},
    val revenir: () -> Unit = {},
)

/**
 * L'état de l'écran à partir de celui du lecteur. [textes] : le texte d'origine de chaque
 * segment (à défaut, celui que lit la voix) ; [restants] : les secondes restantes de chaque
 * minuteur qui tourne.
 */
fun etatSeance(
    titre: String,
    lecteur: EtatLecteur,
    textes: Map<String, String>,
    restants: Map<String, Int>,
    voix: EtatVoix,
    voixCoupee: Boolean,
    micro: EtatEcoute,
    entendu: Entendu?,
): EtatSeance {
    val segments = lecteur.segments.mapIndexed { i, segment ->
        SegmentEcran(
            id = segment.id,
            texte = textes[segment.id] ?: segment.texte,
            numero = i + 1,
            etat = when {
                segment.id in lecteur.faits -> EtatSegment.FAIT
                i == lecteur.courant -> EtatSegment.EN_COURS
                else -> EtatSegment.A_VENIR
            },
            actif = segment.actif,
            declencheur = segment.declencheur,
            minuteurSecondes = segment.minuteurSecondes,
        )
    }
    val courant = segments.getOrNull(lecteur.courant)
    val debutSuivants = (lecteur.courant + 1).coerceAtLeast(0)
    val suivants = if (courant == null) emptyList() else segments.drop(debutSuivants).take(SUIVANTS_MONTRES)

    val enMarche = segments.filter { it.id in restants }
        .map { MinuteurEcran(it.id, it.numero, restants.getValue(it.id), enMarche = true) }
        .sortedBy { it.secondes }
    val duCourant = courant?.minuteurSecondes?.let { duree ->
        enMarche.firstOrNull { it.segmentId == courant.id } ?: MinuteurEcran(courant.id, courant.numero, duree, enMarche = false)
    }
    val minuteur = duCourant ?: enMarche.firstOrNull()

    return EtatSeance(
        titre = titre,
        precedent = segments.take(lecteur.courant.coerceIn(0, segments.size)).lastOrNull { it.etat == EtatSegment.FAIT },
        courant = courant,
        suivants = suivants,
        restants = if (courant == null) 0 else (segments.size - debutSuivants - suivants.size).coerceAtLeast(0),
        terminee = lecteur.terminee,
        enPause = lecteur.enPause,
        parle = lecteur.parle,
        dejaDit = courant != null && courant.id in lecteur.dits,
        minuteur = minuteur,
        autresMinuteurs = enMarche.filter { it.segmentId != minuteur?.segmentId },
        voix = voix,
        voixCoupee = voixCoupee,
        micro = micro,
        entendu = entendu,
    )
}

private const val SUIVANTS_MONTRES = 2
