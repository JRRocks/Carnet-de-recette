package fr.jrrocks.carnetderecette.ui.composants

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Le cache des images décodées : une vignette ne sert jamais de photo pleine largeur. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ImageLocaleTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `chaque taille demandee a son image decodee`() {
        // Une photo factice de 1200 px, comme celle du plat (LARGEUR_PLAT).
        val fichier = File.createTempFile("plat-factice", ".png").apply { deleteOnExit() }
        Bitmap.createBitmap(1200, 750, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
            .let { image -> fichier.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        var taille by mutableIntStateOf(240)
        compose.setContent { ImageLocale(fichier, null, Modifier.size(56.dp), taillePx = taille) }

        // La vignette du Carnet : réduite à 300 px.
        compose.waitUntil(5_000) { imageEnMemoire(fichier, 240) != null }
        assertEquals(300, imageEnMemoire(fichier, 240)!!.width)
        assertNull("la vignette ne sert pas à la fiche", imageEnMemoire(fichier, 1200))

        // La fiche de la recette : l'image est redécodée en grand.
        taille = 1200
        compose.waitForIdle()
        compose.waitUntil(5_000) { imageEnMemoire(fichier, 1200) != null }
        assertEquals(1200, imageEnMemoire(fichier, 1200)!!.width)
    }
}
