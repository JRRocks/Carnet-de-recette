package fr.jrrocks.carnetderecette.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import fr.jrrocks.carnetderecette.core.recette.Portion
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.ui.composants.LocalImagesDuSite
import fr.jrrocks.carnetderecette.ui.composants.LocalVersionImages
import fr.jrrocks.carnetderecette.ui.recette.RecetteContenu
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La fiche d'une recette **factice**, rendue en PNG (images absentes : leur aplat tient la place). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesRecetteTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var recette by mutableStateOf<Recette?>(SemaineFactice.recette)
    private var enLecture by mutableStateOf(false)
    private var sombre by mutableStateOf(false)
    private var police by mutableFloatStateOf(1f)

    private fun capturer(nom: String) {
        compose.waitForIdle()
        val zone = compose.onNodeWithTag("capture").fetchSemanticsNode().boundsInWindow
        val vue = compose.activity.window.decorView
        val plein = Bitmap.createBitmap(vue.width, vue.height, Bitmap.Config.ARGB_8888)
        vue.draw(Canvas(plein))
        val rogne = Bitmap.createBitmap(plein, zone.left.toInt(), zone.top.toInt(), zone.width.toInt(), zone.height.toInt().coerceAtMost(plein.height - zone.top.toInt()))
        File("build/captures").mkdirs()
        File("build/captures/$nom.png").outputStream().use { rogne.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `la fiche d une recette de la semaine`() {
        compose.setContent {
            val densite = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densite.density, police)) {
                ThemeCarnet(sombre = sombre) {
                    Box(Modifier.fillMaxSize().testTag("capture")) {
                        RecetteContenu(
                            numero = 207,
                            box = SemaineFactice.boxes.first(),
                            recette = recette,
                            entree = SemaineFactice.menu.entree(207),
                            enLecture = enLecture,
                            echec = null,
                            onRetour = {},
                            relire = {},
                            lireAVoixHaute = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("207 · Poulet fictif croustillant").assertExists()
        compose.onNodeWithText("2\u00A0pièces").assertExists()
        capturer("91-recette")

        compose.onNodeWithText("Étape 2", substring = true).performScrollTo()
        capturer("92-recette-etapes")

        sombre = true
        compose.onNodeWithText("Ingrédients").performScrollTo()
        capturer("93-recette-sombre")

        sombre = false
        police = 2f
        capturer("94-recette-police-200")

        police = 1f
        recette = null
        compose.onNodeWithText("La page de la recette n'est pas encore lue.").assertExists()
        capturer("95-recette-page-absente")

        enLecture = true
        capturer("96-recette-lecture")
    }

    @Test
    fun `une image arrivee s affiche sans remettre la fiche a zero`() {
        // Une « photo du plat » factice : un aplat de couleur.
        val photo = File.createTempFile("plat-factice", ".png").apply { deleteOnExit() }
        Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(196, 120, 48)) }
            .let { image -> photo.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        // Pas un état Compose, et une seule instance de la fonction : seule la version des
        // images doit redessiner la fiche.
        var telechargee = false
        val images: (String) -> File? = { chemin -> photo.takeIf { telechargee && chemin == "/image/plat-factice.jpg" } }
        var version by mutableIntStateOf(0)
        val uneAQuatre = SemaineFactice.recette.copy(
            image = "/image/plat-factice.jpg",
            portions = listOf(Portion(1, emptyList())) + SemaineFactice.recette.portions,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalImagesDuSite provides images,
                LocalVersionImages provides version,
            ) {
                ThemeCarnet {
                    Box(Modifier.fillMaxSize().testTag("capture")) {
                        RecetteContenu(207, SemaineFactice.boxes.first(), uneAQuatre, SemaineFactice.menu.entree(207), false, null, {}, {}, {})
                    }
                }
            }
        }

        // 2 → 1 : au singulier, et TalkBack annonce la nouvelle valeur.
        compose.onNodeWithContentDescription("Une personne de moins").performClick()
        compose.onNodeWithText("1\u00A0personne")
            .assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Photo du plat", substring = true).assertDoesNotExist()

        // L'image arrive pendant que la fiche est ouverte.
        telechargee = true
        version++
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Photo du plat", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Le nombre de personnes choisi reste.
        compose.onNodeWithText("1\u00A0personne").assertExists()
        capturer("97-recette-image-arrivee")
    }
}
