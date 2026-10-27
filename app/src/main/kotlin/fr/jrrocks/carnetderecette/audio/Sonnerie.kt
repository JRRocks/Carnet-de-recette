package fr.jrrocks.carnetderecette.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * La sonnerie de fin de minuteur : une tonalité du système sur le volume des alarmes, qui
 * s'entend même quand la voix est coupée ou la lecture en pause.
 *
 * Une tonalité ne dure qu'un instant (trois bips en trois quarts de seconde) : la sonnerie la
 * rejoue par **salves**, une toutes les [PERIODE_MS], pour qu'on l'entende en cuisine. Elle
 * garde le téléphone éveillé le temps de sonner, écran éteint compris, et se tait d'elle-même
 * après la dernière salve, ou plus tôt sur [arreter].
 */
class Sonnerie(contexte: Context) {

    private val principal = Handler(Looper.getMainLooper())
    private val eveil: PowerManager.WakeLock? = contexte.applicationContext.getSystemService(PowerManager::class.java)
        ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "carnetderecette:sonnerie")
        ?.apply { setReferenceCounted(false) }

    // Certains téléphones refusent un générateur de tonalités (plus de canal libre) : la séance
    // continue alors sans cette salve plutôt que de planter, et la suivante réessaie.
    private var tonalites: ToneGenerator? = null
    private var restantes = 0
    private var liberee = false

    private val salve = object : Runnable {
        override fun run() {
            if (liberee || restantes <= 0) return
            restantes--
            jouer()
            if (restantes > 0) principal.postDelayed(this, PERIODE_MS) else relacher()
        }
    }

    /** Sonne [salves] fois ; une sonnerie en cours se prolonge au besoin, sans repartir de zéro. */
    fun sonner(salves: Int = SALVES_SEULE) {
        if (liberee || salves <= 0) return
        val enCours = restantes > 0
        restantes = maxOf(restantes, salves)
        eveil?.acquire(restantes * PERIODE_MS + MARGE_EVEIL_MS)
        if (!enCours) salve.run()
    }

    /** Fait taire la sonnerie tout de suite : on l'a entendue. */
    fun arreter() {
        if (restantes > 0) tonalites?.stopTone()
        restantes = 0
        principal.removeCallbacks(salve)
        relacher()
    }

    fun liberer() {
        arreter()
        liberee = true
        tonalites?.release()
        tonalites = null
    }

    private fun jouer() {
        val generateur = tonalites
            ?: runCatching { ToneGenerator(AudioManager.STREAM_ALARM, VOLUME) }.getOrNull()?.also { tonalites = it }
            ?: return
        if (!generateur.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, DUREE_SALVE_MS)) {
            // Générateur en panne : on en recrée un à la salve suivante.
            generateur.release()
            tonalites = null
        }
    }

    private fun relacher() {
        if (eveil?.isHeld == true) eveil.release()
    }

    companion object {
        /** Seule, la sonnerie dit qu'un minuteur est fini : une dizaine de secondes. */
        const val SALVES_SEULE = 6

        /** La voix enchaîne sur l'étape suivante : elle aussi s'entend, une salve suffit. */
        const val SALVES_AVANT_LA_VOIX = 1

        const val PERIODE_MS = 1_500L
        private const val DUREE_SALVE_MS = 1_000
        private const val MARGE_EVEIL_MS = 1_000L
        private const val VOLUME = 90
    }
}
