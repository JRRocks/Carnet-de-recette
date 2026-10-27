package fr.jrrocks.carnetderecette.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.jrrocks.carnetderecette.core.fiche.BlocTexte
import fr.jrrocks.carnetderecette.core.fiche.Boite
import fr.jrrocks.carnetderecette.core.fiche.LigneTexte
import fr.jrrocks.carnetderecette.core.fiche.PageFiche
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La lecture d'une photo de la fiche papier (cadrage § 9, décision du 2026-10-27) : le **texte
 * brut** que l'OCR y trouve, bloc par bloc et ligne par ligne, avec sa place sur la page.
 * **Aucune interprétation, aucune regex** (CLAUDE.md, règle 4) : ni titre, ni ingrédients, ni
 * étapes. Claude structure la recette le vendredi soir, à partir de la photo et de ce texte.
 *
 * La photo garde sa pleine résolution et son orientation EXIF, telle que CameraX l'écrit. Les
 * rectangles sont en pixels de la photo **redressée** (orientation EXIF appliquée), comme
 * l'affiche n'importe quelle visionneuse : [PageFiche.largeur] et [PageFiche.hauteur] aussi.
 */
object OcrPage {

    /**
     * Au-delà, l'OCR travaille sur une copie réduite (par puissances de deux) : une page tient
     * largement dans 3000 pixels, et la mémoire d'un vieux téléphone aussi.
     */
    private const val COTE_MAX_OCR = 3000

    /**
     * Lit [fichier] ; [nom] est le nom que la photo portera à côté du fichier de la semaine.
     * Lève une exception seulement si la photo elle-même est illisible.
     */
    suspend fun lire(fichier: File, nom: String): PageFiche = withContext(Dispatchers.IO) {
        val rotation = rotationExif(fichier)
        val (largeurBrute, hauteurBrute) = dimensions(fichier)
        val reduction = reductionPour(maxOf(largeurBrute, hauteurBrute), COTE_MAX_OCR)
        val image = BitmapFactory.decodeFile(fichier.path, BitmapFactory.Options().apply { inSampleSize = reduction })
            ?: throw IllegalStateException("Photo illisible.")
        val echelle = largeurBrute.toFloat() / image.width
        val (largeur, hauteur) = GeometrieImage.dimensionsDroites(largeurBrute, hauteurBrute, rotation)
        val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // La photo compte plus que son texte : si l'OCR échoue, la page part sans texte, Claude lira la photo.
            val texte = runCatching { Tasks.await(ocr.process(InputImage.fromBitmap(image, rotation))) }.getOrNull()
            val blocs = texte?.textBlocks.orEmpty().mapNotNull { bloc ->
                val boite = bloc.boundingBox?.let { boite(it, echelle) } ?: return@mapNotNull null
                BlocTexte(
                    texte = bloc.text,
                    boite = boite,
                    lignes = bloc.lines.mapNotNull { ligne -> ligne.boundingBox?.let { LigneTexte(ligne.text, boite(it, echelle)) } },
                )
            }
            PageFiche(fichier = nom, largeur = largeur, hauteur = hauteur, blocs = blocs)
        } finally {
            ocr.close()
            image.recycle()
        }
    }

    /** Une petite image redressée de la photo, pour reconnaître la page d'un coup d'œil ; `null` si illisible. */
    suspend fun vignette(fichier: File, cote: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val (largeur, hauteur) = dimensions(fichier)
            val image = BitmapFactory.decodeFile(
                fichier.path,
                BitmapFactory.Options().apply { inSampleSize = reductionPour(maxOf(largeur, hauteur), cote * 2) },
            ) ?: return@runCatching null
            val rotation = rotationExif(fichier)
            if (rotation == 0) image else Bitmap.createBitmap(image, 0, 0, image.width, image.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }.getOrNull()
    }

    private fun boite(r: Rect, echelle: Float) = Boite(
        gauche = (r.left * echelle).roundToInt(),
        haut = (r.top * echelle).roundToInt(),
        droite = (r.right * echelle).roundToInt(),
        bas = (r.bottom * echelle).roundToInt(),
    )

    private fun dimensions(fichier: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fichier.path, options)
        return options.outWidth to options.outHeight
    }

    /** La plus petite réduction par puissance de deux qui ramène [cote] sous [maximum]. */
    private fun reductionPour(cote: Int, maximum: Int): Int {
        var reduction = 1
        while (cote / reduction > maximum) reduction *= 2
        return reduction
    }

    /** Le quart de tour horaire qui redresse la photo, d'après son EXIF (les miroirs n'arrivent pas d'une caméra arrière). */
    private fun rotationExif(fichier: File): Int =
        when (runCatching { ExifInterface(fichier.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
