package fr.jrrocks.carnetderecette.ui.seance

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import fr.jrrocks.carnetderecette.audio.Ecoute
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.audio.Sonnerie
import fr.jrrocks.carnetderecette.audio.Voix
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale
import fr.jrrocks.carnetderecette.core.audio.Declencheur
import fr.jrrocks.carnetderecette.core.audio.Effet
import fr.jrrocks.carnetderecette.core.audio.LecteurSegments
import fr.jrrocks.carnetderecette.core.audio.Minuteurs
import fr.jrrocks.carnetderecette.core.audio.SegmentsDeRecette
import fr.jrrocks.carnetderecette.core.recette.Recette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La séance lue à voix haute : le [LecteurSegments] (pur) décide, ce ViewModel exécute ses
 * [Effet]s avec la [Voix], l'[Ecoute], les [Minuteurs] et la [Sonnerie], puis lui rapporte ce
 * qui s'est passé. Aucune IA, rien sur le réseau : tout marche hors ligne si le téléphone a une
 * voix française.
 *
 * Dans un ViewModel pour survivre à une rotation : la voix ne se coupe pas et les minuteurs
 * continuent. Il vit le temps de l'écran ([MagasinSeance]) ; [onCleared] libère tout.
 */
class SeanceViewModel(application: Application, private val recette: Recette) :
    AndroidViewModel(application), Voix.Suivi, Ecoute.Suivi {

    private val lecteur = LecteurSegments(SegmentsDeRecette.de(recette))
    private val textes = recette.etapes.associate { SegmentsDeRecette.idDe(it) to it.texte }
    private val minuteurs = Minuteurs()

    private var etatVoix = EtatVoix.PREPARATION
    private var voixCoupee = false
    private var micro: EtatEcoute = EtatEcoute.Arretee
    private var entendu: Entendu? = null
    private var auPremierPlan = true

    /** Une phrase demandée avant que la voix soit prête : dite dès qu'elle l'est. */
    private var enAttenteDeVoix: Effet.Dire? = null
    private var tic: Job? = null

    var etat by mutableStateOf(construire())
        private set

    private val voix = Voix(application, this)
    private val sonnerie = Sonnerie()

    /** Créée au premier appui sur le micro : rien n'écoute tant qu'on ne l'a pas demandé. */
    private var ecoute: Ecoute? = null

    init {
        executer(lecteur.demarrer())
        publier()
    }

    // ── Gestes ──────────────────────────────────────────────────────────────────

    fun valider() = agir(lecteur.valider())
    fun relire() = agir(lecteur.relire())
    fun precedent() = agir(lecteur.precedent())
    fun pause() = agir(lecteur.pause())
    fun reprendre() = agir(lecteur.reprendre())
    fun basculer(segmentId: String) = agir(lecteur.basculer(segmentId))
    fun changerDeclencheur(segmentId: String, declencheur: Declencheur) = agir(lecteur.changerDeclencheur(segmentId, declencheur))

    fun minuteur(minuteur: MinuteurEcran) =
        agir(if (minuteur.enMarche) lecteur.arreterMinuteur(minuteur.segmentId) else lecteur.lancerMinuteur())

    /** Coupe ou rétablit la voix ; coupée, les segments avancent en silence (la sonnerie reste). */
    fun basculerVoix() {
        voixCoupee = !voixCoupee
        if (voixCoupee) {
            voix.taire()
            enAttenteDeVoix = null
            lecteur.etat.enLecture?.let { executer(lecteur.finDeLecture(it)) }
            reprendreEcoute()
        }
        publier()
    }

    /** La permission du micro est accordée. */
    fun ouvrirMicro() {
        val e = ecoute ?: Ecoute(getApplication(), this).also { ecoute = it }
        // Si la voix parle déjà, le micro attend qu'elle ait fini.
        if (voix.parle) e.suspendre()
        e.demarrer()
    }

    fun fermerMicro() {
        ecoute?.arreter()
        entendu = null
        publier()
    }

    fun permissionRefusee() {
        micro = EtatEcoute.PermissionRequise
        publier()
    }

    /** Retour au premier plan, peut-être depuis l'installation d'une voix française. */
    fun auRetour() {
        auPremierPlan = true
        if (etatVoix == EtatVoix.LANGUE_ABSENTE || etatVoix == EtatVoix.INDISPONIBLE) voix.reessayer()
        reprendreEcoute()
    }

    /** L'app passe en arrière-plan : Android n'y laisse plus le micro ouvert. */
    fun enArrierePlan() {
        auPremierPlan = false
        ecoute?.suspendre()
    }

    private fun agir(effets: List<Effet>) {
        executer(effets)
        publier()
    }

    // ── Exécution des effets ────────────────────────────────────────────────────

    private fun executer(effets: List<Effet>) {
        for (effet in effets) {
            when (effet) {
                is Effet.Dire -> parler(effet)
                Effet.Taire -> {
                    enAttenteDeVoix = null
                    voix.taire()
                    reprendreEcoute()
                }
                is Effet.LancerMinuteur -> {
                    minuteurs.lancer(effet.segmentId, effet.secondes, maintenant())
                    faireTourner()
                }
                is Effet.AnnulerMinuteur -> minuteurs.annuler(effet.segmentId)
                is Effet.Sonner -> sonnerie.sonner()
                // L'écran lit la fin dans l'état du lecteur.
                Effet.Terminee -> Unit
            }
        }
    }

    private fun parler(dire: Effet.Dire) {
        when {
            // Rien à dire : le segment est « dit », la séance continue en silence.
            voixCoupee || etatVoix == EtatVoix.LANGUE_ABSENTE || etatVoix == EtatVoix.INDISPONIBLE ->
                executer(lecteur.finDeLecture(dire.segmentId))
            etatVoix == EtatVoix.PREPARATION -> enAttenteDeVoix = dire
            else -> {
                // Le micro se coupe avant la première syllabe, pas au début annoncé par le moteur.
                ecoute?.suspendre()
                voix.dire(dire.segmentId, dire.texte)
            }
        }
    }

    private fun reprendreEcoute() {
        if (auPremierPlan && !voix.parle) ecoute?.reprendre()
    }

    /**
     * Un tic à chaque changement de seconde d'un minuteur : l'affichage passe de « 5:00 » à
     * « 4:59 » au bon moment, et la fin tombe à l'heure, même pour un minuteur lancé en
     * cours de seconde.
     */
    private fun faireTourner() {
        if (tic?.isActive == true) return
        tic = viewModelScope.launch {
            while (minuteurs.tous().isNotEmpty()) {
                val maintenant = maintenant()
                delay(minuteurs.tous().values.minOf { fin -> attenteJusquA(fin - maintenant) })
                for (id in minuteurs.expires(maintenant())) executer(lecteur.minuteurFini(id))
                publier()
            }
        }
    }

    private fun attenteJusquA(reste: Long): Long = if (reste <= 0) 0 else (reste - 1) % 1_000 + 1

    private fun maintenant() = SystemClock.elapsedRealtime()

    private fun publier() {
        etat = construire()
    }

    private fun construire(): EtatSeance {
        val maintenant = maintenant()
        return etatSeance(
            titre = recette.titre,
            lecteur = lecteur.etat,
            textes = textes,
            restants = minuteurs.tous().keys.associateWith { minuteurs.restant(it, maintenant) ?: 0 },
            voix = etatVoix,
            voixCoupee = voixCoupee,
            micro = micro,
            entendu = entendu,
        )
    }

    // ── Rapports de la voix et du micro ─────────────────────────────────────────

    override fun etatVoix(etat: EtatVoix) {
        etatVoix = etat
        val attente = enAttenteDeVoix
        if (attente != null && etat != EtatVoix.PREPARATION) {
            enAttenteDeVoix = null
            // Toujours la phrase attendue par le lecteur ? Elle a pu être remplacée entre-temps.
            if (lecteur.etat.enLecture == attente.segmentId) parler(attente)
        }
        publier()
    }

    override fun debutDeLecture(segmentId: String) = Unit

    override fun finDeLecture(segmentId: String) {
        executer(lecteur.finDeLecture(segmentId))
        reprendreEcoute()
        publier()
    }

    override fun erreurDeLecture(segmentId: String) = finDeLecture(segmentId)

    override fun etatEcoute(etat: EtatEcoute) {
        micro = etat
        if (etat != EtatEcoute.Ecoute) entendu = null
        publier()
    }

    override fun partiel(texte: String) {
        entendu = Entendu.EnCours(texte)
        publier()
    }

    override fun entendu(commande: CommandeVocale?, texte: String) {
        entendu = if (commande == null) Entendu.PasCompris(texte) else Entendu.Compris(commande)
        when (commande) {
            CommandeVocale.SUIVANT -> valider()
            CommandeVocale.REPETER -> relire()
            CommandeVocale.PRECEDENT -> precedent()
            CommandeVocale.PAUSE -> pause()
            CommandeVocale.REPRENDRE -> reprendre()
            CommandeVocale.MINUTEUR -> agir(lecteur.lancerMinuteur())
            CommandeVocale.ARRETER -> fermerMicro()
            null -> publier()
        }
    }

    override fun onCleared() {
        voix.liberer()
        ecoute?.liberer()
        sonnerie.liberer()
    }
}

/**
 * Le magasin du [SeanceViewModel], gardé par l'Activity : il survit à une rotation, et l'écran
 * le vide en partant (la voix se tait, le micro se ferme, les minuteurs s'arrêtent). Sans lui,
 * le ViewModel vivrait aussi longtemps que l'Activity, voix comprise.
 */
class MagasinSeance : ViewModel() {
    val magasin = ViewModelStore()

    override fun onCleared() = magasin.clear()
}
