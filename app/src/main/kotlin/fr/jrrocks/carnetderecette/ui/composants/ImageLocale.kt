package fr.jrrocks.carnetderecette.ui.composants

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Où trouver sur le téléphone une image du site, d'après son chemin (« /image/…jpg ») :
 * fourni par l'Activity ([fr.jrrocks.carnetderecette.donnees.DepotLocal.imageLocale]). Par
 * défaut, aucune : les captures de test montrent l'aplat qui tient la place.
 */
val LocalImagesDuSite = staticCompositionLocalOf<(String) -> File?> { { null } }

/**
 * Augmente à chaque image du site téléchargée
 * ([fr.jrrocks.carnetderecette.donnees.EtatSemaine.versionImages]). [imageDuSite] la lit :
 * quand une image arrive, seuls ses appelants se redessinent, et l'écran garde son état
 * (nombre de personnes, défilement).
 */
val LocalVersionImages = compositionLocalOf { 0 }

/** Le fichier local de l'image [chemin] du site, s'il est déjà téléchargé. */
@Composable
fun imageDuSite(chemin: String?): File? {
    // Lue pour s'abonner : une image arrivée redessine l'appelant, même si rien d'autre n'a changé.
    LocalVersionImages.current
    return chemin?.let { LocalImagesDuSite.current(it) }
}

/** Les images déjà décodées, pour ne pas les relire à chaque défilement (16 Mo au plus). */
private val memoire = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(cle: String, valeur: ImageBitmap) = valeur.width * valeur.height * 4
}

/**
 * Une image se garde décodée à la taille demandée : la vignette de 300 px du Carnet ne doit
 * pas servir de photo pleine largeur sur la fiche de la recette.
 */
private fun cleMemoire(fichier: File, taillePx: Int) = "${fichier.path}@$taillePx"

/** L'image déjà décodée pour [fichier] à [taillePx], s'il y en a une (pour les tests). */
internal fun imageEnMemoire(fichier: File, taillePx: Int): ImageBitmap? = memoire.get(cleMemoire(fichier, taillePx))

/**
 * Une image du site déjà téléchargée sur le téléphone ([fichier]), décodée hors du fil
 * principal et réduite à [taillePx] au plus. Tant qu'elle n'est pas là (téléchargement en
 * cours, hors ligne), un aplat neutre tient sa place : la mise en page ne saute pas.
 * Sans [description], l'image est décorative pour TalkBack.
 */
@Composable
fun ImageLocale(
    fichier: File?,
    description: String?,
    modifier: Modifier = Modifier,
    forme: Shape = MaterialTheme.shapes.large,
    taillePx: Int = 1200,
) {
    val cle = fichier?.let { cleMemoire(it, taillePx) }
    var image by remember(cle) { mutableStateOf(cle?.let { memoire.get(it) }) }
    LaunchedEffect(cle) {
        if (fichier != null && cle != null && image == null) {
            image = withContext(Dispatchers.IO) { decoder(fichier, taillePx) }?.also { memoire.put(cle, it) }
        }
    }
    val semantique = if (description == null) Modifier.clearAndSetSemantics {} else Modifier
    Box(modifier.clip(forme).background(MaterialTheme.colorScheme.surfaceContainerHigh).then(semantique)) {
        image?.let {
            Image(it, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

private fun decoder(fichier: File, taillePx: Int): ImageBitmap? = runCatching {
    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(fichier.path, bornes)
    var echantillon = 1
    while (maxOf(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= taillePx) echantillon *= 2
    BitmapFactory.decodeFile(fichier.path, BitmapFactory.Options().apply { inSampleSize = echantillon })?.asImageBitmap()
}.getOrNull()
