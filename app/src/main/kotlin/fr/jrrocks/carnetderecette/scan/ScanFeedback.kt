package fr.jrrocks.carnetderecette.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Retour sonore et haptique du scan à la chaîne (moteur Library `scan-multimode`, sans Hilt).
 *
 * Entre deux box, on ne regarde pas l'écran : le signal passe par la main et l'oreille. Deux
 * signatures nettement distinctes, pour qu'un doublon ne soit jamais pris pour une box
 * nouvelle.
 */
class ScanFeedback(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** Créé à la demande : un ToneGenerator retient une piste audio, inutile tant qu'aucune box n'est lue. */
    private var toneGenerator: ToneGenerator? = null

    private fun tones(): ToneGenerator? {
        if (toneGenerator == null) {
            toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME) }.getOrNull()
        }
        return toneGenerator
    }

    /** Box nouvelle : une impulsion courte et un bip bref. */
    fun success() {
        vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        tones()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    /** Box déjà scannée cette semaine : double impulsion et bip double. */
    fun duplicate() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1))
        tones()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
    }

    private fun vibrate(effect: VibrationEffect) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching { device.vibrate(effect) }
    }

    /** À appeler quand l'écran de scan disparaît pour de bon. */
    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private companion object {
        /** Volume relatif du bip, sur 100 : audible dans une cuisine sans être agressif. */
        const val TONE_VOLUME = 85
    }
}
