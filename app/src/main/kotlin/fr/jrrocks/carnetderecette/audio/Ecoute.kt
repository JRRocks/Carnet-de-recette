package fr.jrrocks.carnetderecette.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale
import fr.jrrocks.carnetderecette.core.audio.ReconnaissanceCommandes

/** Où en est le micro. [Erreur] porte la cause, en français, à afficher telle quelle. */
sealed interface EtatEcoute {
    data object Arretee : EtatEcoute
    data object Ecoute : EtatEcoute
    data object PermissionRequise : EtatEcoute
    data object Indisponible : EtatEcoute
    data class Erreur(val motif: String) : EtatEcoute
}

/**
 * Les commandes vocales de la séance (cadrage § 8) : la reconnaissance vocale d'Android,
 * **sur l'appareil** quand il le permet (Android 12 et plus, modèle français présent), sinon le
 * service du téléphone en préférant le hors-ligne. Ce qui est entendu passe par
 * [ReconnaissanceCommandes] : des phrases connues, aucune IA.
 *
 * Android n'écoute qu'une phrase à la fois : l'écoute « continue » relance une écoute après
 * chaque résultat et après chaque silence. Le micro n'écoute que sur demande, pendant une
 * séance ; [suspendre] le coupe pendant que la voix parle, pour qu'elle ne s'entende pas
 * elle-même. Tout se passe sur le fil principal, comme l'exige `SpeechRecognizer`.
 */
class Ecoute(contexte: Context, private val suivi: Suivi) {

    interface Suivi {
        fun etatEcoute(etat: EtatEcoute)

        /** Ce que le micro entend, phrase en cours : pour l'affichage seulement. */
        fun partiel(texte: String)

        /** Une phrase finie : la commande reconnue (`null` si ce n'en est pas une) et ce qui a été entendu. */
        fun entendu(commande: CommandeVocale?, texte: String)
    }

    private val contexte = contexte.applicationContext
    private val principal = Handler(Looper.getMainLooper())
    private var reconnaisseur: SpeechRecognizer? = null
    private var surAppareil = false

    /** Le modèle de l'appareil n'a pas le français : on passe au service du téléphone. */
    private var surAppareilRefuse = false
    private var suspendue = false
    private var enCours = false
    private var erreursDeSuite = 0
    private var liberee = false

    var etat: EtatEcoute = EtatEcoute.Arretee
        private set(valeur) {
            field = valeur
            if (!liberee) suivi.etatEcoute(valeur)
        }

    private val intention = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.contexte.packageName)

    private val relance = Runnable {
        if (liberee || etat != EtatEcoute.Ecoute || suspendue || enCours) return@Runnable
        val r = reconnaisseur ?: return@Runnable
        enCours = true
        r.startListening(intention)
    }

    fun demarrer() {
        if (liberee || etat == EtatEcoute.Ecoute) return
        if (ContextCompat.checkSelfPermission(contexte, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            etat = EtatEcoute.PermissionRequise
            return
        }
        if (reconnaisseur == null && creer() == null) {
            etat = EtatEcoute.Indisponible
            return
        }
        erreursDeSuite = 0
        etat = EtatEcoute.Ecoute
        relancer(0)
    }

    fun arreter() {
        principal.removeCallbacks(relance)
        if (enCours) reconnaisseur?.cancel()
        enCours = false
        if (etat == EtatEcoute.Ecoute) etat = EtatEcoute.Arretee
    }

    /** Coupe le micro le temps que la voix parle ; l'écoute reste ouverte. */
    fun suspendre() {
        suspendue = true
        principal.removeCallbacks(relance)
        if (enCours) reconnaisseur?.cancel()
        enCours = false
    }

    /** Rouvre le micro après la voix, un court instant plus tard : l'écho de la dernière syllabe passe. */
    fun reprendre() {
        if (!suspendue) return
        suspendue = false
        relancer(PAUSE_APRES_VOIX)
    }

    fun liberer() {
        liberee = true
        principal.removeCallbacks(relance)
        reconnaisseur?.destroy()
        reconnaisseur = null
    }

    private fun creer(): SpeechRecognizer? {
        val r = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !surAppareilRefuse &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(contexte) -> {
                surAppareil = true
                SpeechRecognizer.createOnDeviceSpeechRecognizer(contexte)
            }
            SpeechRecognizer.isRecognitionAvailable(contexte) -> {
                surAppareil = false
                SpeechRecognizer.createSpeechRecognizer(contexte)
            }
            else -> return null
        }
        r.setRecognitionListener(rapports)
        reconnaisseur = r
        return r
    }

    private fun recreer() {
        reconnaisseur?.destroy()
        reconnaisseur = null
        if (creer() == null) etat = EtatEcoute.Indisponible
    }

    private fun relancer(delai: Long) {
        principal.removeCallbacks(relance)
        principal.postDelayed(relance, delai)
    }

    private fun echouer(motif: String) {
        principal.removeCallbacks(relance)
        etat = EtatEcoute.Erreur(motif)
    }

    private val rapports = object : RecognitionListener {
        override fun onResults(resultats: Bundle?) {
            enCours = false
            erreursDeSuite = 0
            val alternatives = resultats?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (alternatives.isNotEmpty()) suivi.entendu(ReconnaissanceCommandes.reconnaitre(alternatives), alternatives.first())
            relancer(PAUSE_ENTRE_ECOUTES)
        }

        override fun onPartialResults(resultats: Bundle?) {
            resultats?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(suivi::partiel)
        }

        override fun onError(code: Int) {
            enCours = false
            // Un `cancel()` de notre part (suspendre, arrêter) finit souvent en erreur : sans objet.
            if (liberee || suspendue || etat != EtatEcoute.Ecoute) return
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> relancer(PAUSE_APRES_SILENCE)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    principal.removeCallbacks(relance)
                    etat = EtatEcoute.PermissionRequise
                }
                SpeechRecognizer.ERROR_AUDIO ->
                    echouer("Le micro ne répond pas. Une autre app l'utilise peut-être\u00A0: ferme-la, puis réessaie.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    echouer(
                        "La reconnaissance vocale demande le réseau sur ce téléphone. Installe le français hors ligne " +
                            "dans les réglages de la saisie vocale, ou reconnecte-toi.",
                    )
                ERREUR_LANGUE_NON_PRISE_EN_CHARGE, ERREUR_LANGUE_INDISPONIBLE ->
                    if (surAppareil) {
                        // Le modèle de l'appareil n'a pas le français : le service du téléphone prend le relais.
                        surAppareilRefuse = true
                        recreer()
                        relancer(PAUSE_APRES_SILENCE)
                    } else {
                        echouer("La reconnaissance vocale du téléphone ne connaît pas encore le français. Installe-le dans les réglages de la saisie vocale.")
                    }
                ERREUR_TROP_DE_DEMANDES ->
                    echouer("Le service de reconnaissance vocale est saturé. Réessaie dans un moment.")
                else -> {
                    // Occupé, déconnecté, client perdu : on repart d'un reconnaisseur neuf, sans boucler sans fin.
                    erreursDeSuite++
                    if (erreursDeSuite >= ERREURS_MAX) {
                        echouer("La reconnaissance vocale s'arrête sans cesse. Réessaie, ou sers-toi des boutons.")
                    } else {
                        if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == ERREUR_SERVICE_DECONNECTE) recreer()
                        relancer(PAUSE_APRES_ERREUR)
                    }
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val PAUSE_ENTRE_ECOUTES = 150L
        const val PAUSE_APRES_SILENCE = 300L
        const val PAUSE_APRES_VOIX = 400L
        const val PAUSE_APRES_ERREUR = 1_000L
        const val ERREURS_MAX = 5

        // Codes ajoutés à Android 12 (API 31), écrits en clair pour compiler sans avertissement
        // sur les versions plus anciennes, où ils ne sont simplement jamais rendus.
        const val ERREUR_TROP_DE_DEMANDES = 10
        const val ERREUR_SERVICE_DECONNECTE = 11
        const val ERREUR_LANGUE_NON_PRISE_EN_CHARGE = 12
        const val ERREUR_LANGUE_INDISPONIBLE = 13
    }
}
