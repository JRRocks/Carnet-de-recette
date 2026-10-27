package fr.jrrocks.carnetderecette.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.scan.EtatPermission
import fr.jrrocks.carnetderecette.ui.fiche.ActionsFiche
import fr.jrrocks.carnetderecette.ui.fiche.EtatFiche
import fr.jrrocks.carnetderecette.ui.fiche.Face
import fr.jrrocks.carnetderecette.ui.fiche.FicheContenu
import fr.jrrocks.carnetderecette.ui.fiche.PageVue
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La fiche papier rendue en PNG sans téléphone : l'aperçu est un fond sombre, la vignette une
 * page dessinée (aucune photo réelle, CLAUDE.md, règle 1). Images dans `app/build/captures/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesFicheTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val menu = listOf(
        EntreeMenu(45, "Gratin témoin aux poireaux", url = "https://exemple.invalid/45"),
        EntreeMenu(207, "Poulet fictif croustillant", url = "https://exemple.invalid/207"),
        EntreeMenu(315, "Pâtes d'essai à la tomate", url = "https://exemple.invalid/315"),
    )

    private var etat by mutableStateOf(EtatFiche(camera = EtatPermission.ACCORDEE, lampeDisponible = true, entreesMenu = menu))
    private var sombre by mutableStateOf(false)
    private var police by mutableFloatStateOf(1f)

    /** Une page factice : un rectangle clair et quelques traits en guise de lignes. */
    private fun pageDessinee(): ImageBitmap {
        val image = ImageBitmap(112, 112)
        val toile = androidx.compose.ui.graphics.Canvas(image)
        toile.drawRect(Rect(0f, 0f, 112f, 112f), Paint().apply { color = Color(0xFFB9A98C) })
        toile.drawRect(Rect(16f, 8f, 96f, 104f), Paint().apply { color = Color(0xFFF7F4EC) })
        val encre = Paint().apply { color = Color(0xFF6B6B6B); strokeWidth = 3f }
        listOf(20f, 30f, 46f, 54f, 62f, 70f, 86f).forEach { y -> toile.drawLine(Offset(24f, y), Offset(if (y < 40f) 70f else 88f, y), encre) }
        return image
    }

    private fun capturer(nom: String) {
        compose.waitForIdle()
        val zone = compose.onNodeWithTag("capture").fetchSemanticsNode().boundsInWindow
        val vue = compose.activity.window.decorView
        val plein = Bitmap.createBitmap(vue.width, vue.height, Bitmap.Config.ARGB_8888)
        vue.draw(Canvas(plein))
        val rogne = Bitmap.createBitmap(
            plein,
            zone.left.toInt(),
            zone.top.toInt(),
            zone.width.toInt(),
            zone.height.toInt().coerceAtMost(plein.height - zone.top.toInt()),
        )
        File("build/captures").mkdirs()
        File("build/captures/$nom.png").outputStream().use { rogne.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun afficher() {
        compose.setContent {
            val densite = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densite.density, police)) {
                ThemeCarnet(sombre = sombre) {
                    FicheContenu(etat, ActionsFiche(), Modifier.testTag("capture")) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF20262E)))
                    }
                }
            }
        }
    }

    @Test
    fun `la fiche papier dans chacun de ses etats`() {
        afficher()

        compose.onNodeWithText("Photographier le recto").assertExists()
        compose.onNodeWithText("Claude lira la fiche le vendredi soir.").assertExists()
        compose.onNodeWithText("Enregistrer la fiche").assertDoesNotExist()
        capturer("81-fiche-sans-page")

        etat = etat.copy(
            numeroDonne = true,
            numero = 207,
            titre = "Poulet fictif croustillant",
            recto = PageVue(14, pageDessinee()),
            personnes = 4,
        )
        compose.onNodeWithText("Photo prise, 14 blocs de texte lus").assertExists()
        // Le verso, facultatif, se photographie depuis sa rangée ; en bas, une seule action.
        compose.onNodeWithText("Photographier le verso").assertExists()
        compose.onNodeWithText("Reprendre la photo").assertExists()
        compose.onNodeWithText("Photographier le recto").assertDoesNotExist()
        compose.onNodeWithText("Enregistrer la fiche").assertExists()
        capturer("82-fiche-recto-pris")

        etat = etat.copy(enCours = Face.VERSO)
        compose.onNodeWithText("Verso\u00A0: lecture du texte…").assertExists()
        capturer("83-fiche-lecture-verso")

        etat = etat.copy(enCours = null, verso = PageVue(0, pageDessinee()), erreur = "La fiche n'a pas été enregistrée\u00A0: réessaie.")
        compose.onNodeWithText("Aucun texte lu", substring = true).assertExists()
        compose.onNodeWithText("Photographier le verso").assertDoesNotExist()
        capturer("84-fiche-deux-pages-erreur")

        etat = EtatFiche(camera = EtatPermission.ACCORDEE, entreesMenu = menu, scannees = setOf(315), menuOuvert = true)
        compose.onNodeWithText("Choisis la recette de la fiche").assertExists()
        capturer("85-fiche-menu-ouvert")

        etat = EtatFiche(camera = EtatPermission.REFUSEE, saisie = "315", numero = 315, titre = "Pâtes d'essai à la tomate", entreesMenu = menu)
        compose.onNodeWithText("Ouvrir les réglages").assertExists()
        capturer("86-fiche-permission-refusee")
    }

    @Test
    fun `la fiche papier en sombre et a 200 pour cent`() {
        afficher()
        etat = etat.copy(
            numeroDonne = true,
            numero = 207,
            titre = "Poulet fictif croustillant",
            recto = PageVue(14, pageDessinee()),
            personnes = 4,
            lampe = true,
        )

        sombre = true
        capturer("87-fiche-sombre")

        sombre = false
        police = 2f
        capturer("88-fiche-police-200")

        etat = EtatFiche(camera = EtatPermission.ACCORDEE, entreesMenu = menu)
        capturer("89-fiche-sans-page-police-200")
    }
}
