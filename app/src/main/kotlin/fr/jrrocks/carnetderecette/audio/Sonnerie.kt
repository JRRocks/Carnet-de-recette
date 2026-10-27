package fr.jrrocks.carnetderecette.audio

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * La sonnerie de fin de minuteur : une tonalité du système sur le volume des alarmes, qui
 * s'entend même quand la voix est coupée ou la lecture en pause.
 */
class Sonnerie {

    // Certains téléphones refusent un générateur de tonalités (plus de canal libre) : la
    // séance continue alors sans sonnerie plutôt que de planter.
    private val tonalites: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, VOLUME) }.getOrNull()

    fun sonner() {
        tonalites?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, DUREE_MS)
    }

    fun liberer() {
        tonalites?.release()
    }

    private companion object {
        const val VOLUME = 90
        const val DUREE_MS = 1_500
    }
}
