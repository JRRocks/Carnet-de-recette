package fr.jrrocks.carnetderecette.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.jrrocks.carnetderecette.core.scan.ElementLu
import kotlin.math.roundToInt

/**
 * Reconnaissance de texte sur le flux de la caméra : adaptation d'`OcrAnalyzer` (moteur Library
 * `scan-multimode`) pour l'étiquette des box (cadrage § 5).
 *
 * `OcrAnalyzer` ne remontait que le texte brut. Celui-ci remonte, pour chaque image, les
 * **éléments** reconnus par ML Kit (un mot, en général) dont le centre est **dans le
 * réticule**, avec leur hauteur et leur position rapportées au réticule ([ElementLu]) : le
 * grand numéro de l'étiquette se reconnaît à sa taille, sans motif figé.
 *
 * Le réticule est [reticule], en fractions de l'aperçu affiché : la même zone que celle
 * dessinée à l'écran ([Reticule]). L'aperçu montre `ImageProxy.cropRect` (CameraX le calcule
 * à partir du `ViewPort` posé par [CameraPreview]) ; la rotation est prise en compte
 * ([GeometrieImage]).
 *
 * La reconnaissance attend son résultat **sur le fil d'analyse** (`Tasks.await`) : le
 * récepteur, donc le consensus et l'anti-rebond, tourne sur ce seul fil, et l'image est encore
 * ouverte quand il la reçoit ([Cliche]). Avec `STRATEGY_KEEP_ONLY_LATEST`, les images arrivées
 * entre-temps sont abandonnées : rien ne prend de retard.
 *
 * Modèle latin embarqué dans l'APK : aucun téléchargement, marche hors ligne. Une seule
 * instance pour toute la durée de l'écran ; [close] à la sortie.
 */
class OcrElements(
    private val reticule: Zone = Reticule.ZONE,
    private val recepteur: (elements: List<ElementLu>, cliche: Cliche) -> Unit,
) : ImageAnalysis.Analyzer {

    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        try {
            val media = image.image ?: return
            val rotation = image.imageInfo.rotationDegrees
            val recadrage = image.cropRect.let { Zone(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            val zone = GeometrieImage.reticuleDansImage(image.width, image.height, rotation, recadrage, reticule)
            val texte = Tasks.await(recognizer.process(InputImage.fromMediaImage(media, rotation)))
            val elements = texte.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                val boite = element.boundingBox ?: return@mapNotNull null
                GeometrieImage.elementLu(
                    element.text,
                    Zone(boite.left.toFloat(), boite.top.toFloat(), boite.right.toFloat(), boite.bottom.toFloat()),
                    zone,
                )
            }
            recepteur(elements, Cliche(image, zone))
        } catch (e: Exception) {
            // Règle du moteur : sur erreur (image floue, modèle fermé), ne rien faire. Le scan continue.
        } finally {
            // Toujours : sans quoi CameraX n'envoie plus d'image une fois son tampon plein.
            image.close()
        }
    }

    /** À appeler à la sortie de l'écran : libère le modèle ML Kit. */
    fun close() = recognizer.close()
}

/**
 * L'image qui vient d'être lue, encore ouverte : de quoi en tirer la petite photo de
 * l'étiquette, envoyée en secours avec le scan (cadrage § 5). Valable **seulement** pendant
 * l'appel du récepteur d'[OcrElements], sur le fil d'analyse.
 */
class Cliche internal constructor(private val image: ImageProxy, private val reticule: Zone) {

    /**
     * La zone du réticule, droite, ramenée à [largeur] pixels de large au plus ; `null` si la
     * conversion échoue (on garde alors le scan sans photo).
     */
    fun etiquette(largeur: Int = LARGEUR_PHOTO): Bitmap? = runCatching {
        val brute = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        val dansTampon = GeometrieImage.versTampon(reticule, image.width, image.height, rotation)
        val gauche = dansTampon.gauche.roundToInt().coerceIn(0, brute.width - 1)
        val haut = dansTampon.haut.roundToInt().coerceIn(0, brute.height - 1)
        val droite = dansTampon.droite.roundToInt().coerceIn(gauche + 1, brute.width)
        val bas = dansTampon.bas.roundToInt().coerceIn(haut + 1, brute.height)
        val echelle = (largeur / reticule.largeur).coerceAtMost(1f)
        val matrice = Matrix().apply {
            postRotate(rotation.toFloat())
            postScale(echelle, echelle)
        }
        Bitmap.createBitmap(brute, gauche, haut, droite - gauche, bas - haut, matrice, true)
    }.getOrNull()

    companion object {
        /** Assez pour relire l'étiquette, assez peu pour quelques dizaines de Ko en JPEG. */
        const val LARGEUR_PHOTO = 640
    }
}
