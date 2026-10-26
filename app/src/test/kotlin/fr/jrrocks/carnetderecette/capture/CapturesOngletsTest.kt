package fr.jrrocks.carnetderecette.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import fr.jrrocks.carnetderecette.drive.DriveCarnet
import fr.jrrocks.carnetderecette.ui.ActionsCarnet
import fr.jrrocks.carnetderecette.ui.CarnetApp
import fr.jrrocks.carnetderecette.ui.EtatCarnet
import fr.jrrocks.carnetderecette.ui.EtatDrive
import fr.jrrocks.carnetderecette.ui.EtatMiseAJour
import fr.jrrocks.carnetderecette.ui.Onglet
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Les quatre onglets rendus en PNG sans émulateur ni téléphone, à la largeur d'un téléphone
 * étroit de 360 dp, en clair, en sombre et à 200 % de taille de police (méthode de Popote,
 * `docs/decisions.md` § « Rendu visuel » de JRRocks/Suivie-Popote-Cie). Les images sortent
 * dans `app/build/captures/`.
 *
 * Sert aussi de test de fumée : chaque état du Drive et de la mise à jour se compose sans
 * planter. Compte fictif, recette d'exemple factice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesOngletsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var etat by mutableStateOf(
        EtatCarnet(EtatDrive.NonConnecte, EtatMiseAJour.Aucune, "0.2-correspondance", LocalDate.of(2026, 10, 26)),
    )
    private var onglet by mutableStateOf(Onglet.SEMAINE)
    private var sombre by mutableStateOf(false)
    private var police by mutableFloatStateOf(1f)

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
                    CarnetApp(etat, onglet, { onglet = it }, ActionsCarnet(), Modifier.testTag("capture"))
                }
            }
        }
    }

    private fun drive(nouveau: EtatDrive, maj: EtatMiseAJour = EtatMiseAJour.Aucune) {
        etat = etat.copy(drive = nouveau, maj = maj)
    }

    @Test
    fun `la semaine dans chaque etat du Drive et de la mise a jour`() {
        afficher()

        compose.onNodeWithText("Semaine 44").assertExists()
        compose.onNodeWithText("Brancher le Drive", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Réglages, action requise").assertExists()
        capturer("01-semaine-non-branche")

        drive(EtatDrive.EnCours)
        compose.onNodeWithText("Brancher le Drive", useUnmergedTree = true).assertDoesNotExist()
        capturer("02-semaine-connexion")

        // Le message réel du code, pas une copie : la capture montre ce que l'app affichera.
        val apiNonActivee = """{"error":{"code":403,"errors":[{"reason":"accessNotConfigured"}]}}"""
        drive(EtatDrive.Erreur(DriveCarnet.exceptionPour(403, "GET", apiNonActivee).message!!))
        compose.onNodeWithText("Réessayer", useUnmergedTree = true).assertExists()
        capturer("03-semaine-erreur")

        drive(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Disponible("0.3"))
        compose.onNodeWithText("La version 0.3 est prête.").assertExists()
        compose.onNodeWithText("Brancher le Drive", useUnmergedTree = true).assertDoesNotExist()
        capturer("04-semaine-branche-maj")

        drive(EtatDrive.Pret("foyer@example.org"))
        compose.onNodeWithContentDescription("Réglages, action requise").assertDoesNotExist()
        capturer("05-semaine-branche")

        sombre = true
        drive(EtatDrive.Pret("foyer@example.org", horsLigne = true), EtatMiseAJour.Telechargement("0.3", 0.42f))
        compose.onNodeWithText(DriveCarnet.MOTIF_HORS_LIGNE).assertExists()
        capturer("06-semaine-hors-ligne-telechargement-sombre")

        drive(EtatDrive.NonConnecte)
        capturer("07-semaine-non-branche-sombre")

        sombre = false
        police = 2f
        capturer("08-semaine-non-branche-police-200")

        police = 1.3f
        capturer("09-semaine-non-branche-police-130")
    }

    @Test
    fun `le carnet la cuisine et les reglages`() {
        afficher()

        onglet = Onglet.CARNET
        compose.onNodeWithText("Le carnet se remplit le samedi").assertExists()
        capturer("11-carnet")

        onglet = Onglet.CUISINE
        compose.onNodeWithText("Ce soir, chacun ses tâches").assertExists()
        capturer("21-cuisine")

        onglet = Onglet.REGLAGES
        compose.onNodeWithText("Google Drive").assertExists()
        capturer("31-reglages-non-branche")

        drive(EtatDrive.Pret("foyer@example.org"), EtatMiseAJour.Echec("0.3", "réseau indisponible"))
        capturer("32-reglages-branche-echec-maj")

        sombre = true
        onglet = Onglet.CARNET
        capturer("12-carnet-sombre")
        onglet = Onglet.CUISINE
        capturer("22-cuisine-sombre")
        onglet = Onglet.REGLAGES
        drive(EtatDrive.Pret("foyer@example.org"))
        capturer("33-reglages-branche-sombre")

        sombre = false
        police = 2f
        onglet = Onglet.CUISINE
        capturer("23-cuisine-police-200")
        onglet = Onglet.REGLAGES
        capturer("34-reglages-police-200")

        police = 1.3f
        onglet = Onglet.CUISINE
        capturer("24-cuisine-police-130")
    }
}
