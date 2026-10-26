package fr.jrrocks.carnetderecette.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Un téléphone en paysage (800 dp de large) : à partir de 600 dp, un rail de navigation remplace
 * la barre du bas, et le contenu se limite à une largeur de lecture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xxhdpi")
class CapturesPaysageTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var onglet by mutableStateOf(Onglet.SEMAINE)

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

    @Test
    fun `rail de navigation en paysage`() {
        val etat = EtatCarnet(
            EtatDrive.Pret("foyer@example.org"),
            EtatMiseAJour.Disponible("0.3"),
            "0.2",
            LocalDate.of(2026, 10, 26),
        )
        compose.setContent {
            ThemeCarnet(sombre = false) {
                CarnetApp(etat, onglet, { onglet = it }, ActionsCarnet(), Modifier.testTag("capture"))
            }
        }
        capturer("51-semaine-paysage")
        onglet = Onglet.CUISINE
        capturer("52-cuisine-paysage")
    }
}
