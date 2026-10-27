package fr.jrrocks.carnetderecette.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/** Où en est la synthèse vocale du téléphone. */
enum class EtatVoix {
    PREPARATION,
    PRETE,

    /** Le moteur répond, mais sans voix française : [Voix.installation] propose d'en installer une. */
    LANGUE_ABSENTE,

    /** Aucun moteur de synthèse vocale ne répond. */
    INDISPONIBLE,
}

/**
 * La voix de la séance : la synthèse vocale d'Android (`TextToSpeech`), en français, avec une
 * voix **hors ligne** quand le téléphone en a une : en cuisine, le réseau ne doit rien changer.
 *
 * - Les attributs audio sont ceux d'un guidage (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`) : la
 *   musique du téléphone baisse le temps d'une phrase (focus transitoire avec atténuation),
 *   puis reprend.
 * - Chaque phrase porte un identifiant unique (segment et compteur) : quand on redit le même
 *   segment, la fin de l'ancienne phrase, interrompue, n'est pas prise pour celle de la nouvelle.
 *   Seule la phrase en cours est rapportée à [Suivi] ; une phrase coupée par [taire] ou par une
 *   nouvelle phrase ne l'est pas.
 * - Les rappels du moteur arrivent sur un fil à lui : ils sont ramenés sur le fil principal.
 */
class Voix(contexte: Context, private val suivi: Suivi) {

    interface Suivi {
        fun etatVoix(etat: EtatVoix)
        fun debutDeLecture(segmentId: String)
        fun finDeLecture(segmentId: String)
        fun erreurDeLecture(segmentId: String)
    }

    private val contexte = contexte.applicationContext
    private val principal = Handler(Looper.getMainLooper())
    private val audio = this.contexte.getSystemService(AudioManager::class.java)
    private val attributs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributs)
        .build()

    private var moteur: TextToSpeech? = null
    private var focusTenu = false
    private var liberee = false

    /** L'identifiant de la phrase en cours, ou `null` : la voix se tait. */
    private var enCours: String? = null
    private var compteur = 0

    var etat: EtatVoix = EtatVoix.PREPARATION
        private set(valeur) {
            field = valeur
            suivi.etatVoix(valeur)
        }

    /** Un peu plus lente que la normale : on écoute en coupant des poireaux. */
    var vitesse: Float = VITESSE_PAR_DEFAUT
        set(valeur) {
            field = valeur
            moteur?.setSpeechRate(valeur)
        }

    val parle: Boolean get() = enCours != null

    /**
     * Recrée le moteur si la voix manquait : au retour de l'installation d'une voix française,
     * le moteur déjà ouvert ne la verrait pas toujours.
     */
    fun reessayer() {
        if (liberee || etat == EtatVoix.PRETE || etat == EtatVoix.PREPARATION) return
        moteur?.shutdown()
        moteur = null
        etat = EtatVoix.PREPARATION
        preparer()
    }

    private fun preparer() {
        var cree: TextToSpeech? = null
        // Le moteur peut rappeler `onInit` avant même la fin de son constructeur : on repasse
        // par le fil principal pour que `cree` soit affecté.
        cree = TextToSpeech(contexte) { statut -> principal.post { initialise(cree, statut) } }
        moteur = cree
    }

    private fun initialise(cree: TextToSpeech?, statut: Int) {
        if (liberee || cree == null || cree !== moteur) return
        if (statut != TextToSpeech.SUCCESS) {
            etat = EtatVoix.INDISPONIBLE
            return
        }
        var langue = cree.setLanguage(Locale.FRANCE)
        if (langue < TextToSpeech.LANG_AVAILABLE) langue = cree.setLanguage(Locale.FRENCH)
        if (langue < TextToSpeech.LANG_AVAILABLE) {
            etat = EtatVoix.LANGUE_ABSENTE
            return
        }
        voixHorsLigne(cree)?.let { cree.voice = it }
        cree.setAudioAttributes(attributs)
        cree.setSpeechRate(vitesse)
        cree.setOnUtteranceProgressListener(rapports)
        etat = EtatVoix.PRETE
    }

    /** Une voix française installée sur le téléphone, de France de préférence, la meilleure d'abord. */
    private fun voixHorsLigne(moteur: TextToSpeech): Voice? {
        // Certains moteurs lèvent une exception au lieu de rendre une liste vide.
        val toutes = runCatching { moteur.voices }.getOrNull().orEmpty()
        return toutes
            .filter { it.locale.language == Locale.FRENCH.language && !it.isNetworkConnectionRequired }
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareByDescending<Voice> { it.locale.country == Locale.FRANCE.country }.thenByDescending { it.quality })
            .firstOrNull()
    }

    /** Dit [texte] tout de suite, en coupant la phrase en cours. */
    fun dire(segmentId: String, texte: String) {
        val moteur = moteur
        if (liberee || moteur == null || etat != EtatVoix.PRETE) {
            principal.post { if (!liberee) suivi.erreurDeLecture(segmentId) }
            return
        }
        val phrase = "$segmentId$SEPARATEUR${++compteur}"
        enCours = phrase
        prendreFocus()
        if (moteur.speak(texte, TextToSpeech.QUEUE_FLUSH, null, phrase) != TextToSpeech.SUCCESS) {
            enCours = null
            rendreFocus()
            principal.post { if (!liberee) suivi.erreurDeLecture(segmentId) }
        }
    }

    fun taire() {
        enCours = null
        moteur?.stop()
        rendreFocus()
    }

    fun liberer() {
        liberee = true
        enCours = null
        principal.removeCallbacksAndMessages(null)
        moteur?.stop()
        moteur?.shutdown()
        moteur = null
        rendreFocus()
    }

    private fun prendreFocus() {
        if (focusTenu) return
        focusTenu = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun rendreFocus() {
        if (!focusTenu) return
        audio.abandonAudioFocusRequest(focus)
        focusTenu = false
    }

    private val rapports = object : UtteranceProgressListener() {
        override fun onStart(phrase: String) = rapporter(phrase, fin = false) { suivi.debutDeLecture(it) }

        override fun onDone(phrase: String) = rapporter(phrase, fin = true) { suivi.finDeLecture(it) }

        override fun onStop(phrase: String, interrompue: Boolean) = rapporter(phrase, fin = true) { suivi.finDeLecture(it) }

        override fun onError(phrase: String, code: Int) = rapporter(phrase, fin = true) { suivi.erreurDeLecture(it) }

        @Deprecated("Remplacée par onError(String, Int) ; certains moteurs l'appellent encore.")
        override fun onError(phrase: String) = rapporter(phrase, fin = true) { suivi.erreurDeLecture(it) }
    }

    /** Ramène un rappel du moteur sur le fil principal, s'il concerne la phrase en cours. */
    private fun rapporter(phrase: String, fin: Boolean, rappel: (segmentId: String) -> Unit) {
        principal.post {
            if (liberee || phrase != enCours) return@post
            if (fin) {
                enCours = null
                rendreFocus()
            }
            rappel(phrase.substringBeforeLast(SEPARATEUR))
        }
    }

    // Après toutes les propriétés : le moteur s'en sert dès qu'il répond.
    init {
        preparer()
    }

    companion object {
        const val VITESSE_PAR_DEFAUT = 0.95f
        private const val SEPARATEUR = '#'

        /** L'écran du moteur qui installe une voix. */
        fun installation(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

        /** Les réglages de la synthèse vocale du téléphone (choix du moteur). */
        fun reglages(): Intent = Intent("com.android.settings.TTS_SETTINGS")
    }
}
