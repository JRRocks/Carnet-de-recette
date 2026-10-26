package fr.jrrocks.carnetderecette.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import fr.jrrocks.carnetderecette.ui.EcranAccueil
import fr.jrrocks.carnetderecette.ui.EtatDrive
import fr.jrrocks.carnetderecette.ui.ThemeCarnet
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L'accueil rendu en PNG sans émulateur ni téléphone, à la largeur d'un téléphone de 360 dp
 * (méthode de Popote, `docs/decisions.md` § « Rendu visuel » de JRRocks/Suivie-Popote-Cie).
 * Les images sortent dans `app/build/captures/`.
 *
 * Sert aussi de test de fumée : chaque état du branchement Drive se compose sans planter.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesAccueilTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var etat by mutableStateOf<EtatDrive>(EtatDrive.NonConnecte)

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
    fun `l accueil dans chaque etat du branchement Drive`() {
        compose.setContent {
            ThemeCarnet(sombre = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("capture")) {
                    EcranAccueil(etat = etat, versionNom = "0.1-squelette", onConnecter = {})
                }
            }
        }

        compose.onNodeWithText("Connecter Google Drive").assertExists()
        capturer("01-accueil-non-connecte")

        etat = EtatDrive.Pret("foyer@example.org")
        compose.onNodeWithText("Connecté : foyer@example.org").assertExists()
        capturer("02-accueil-connecte")

        etat = EtatDrive.Pret("foyer@example.org", horsLigne = true)
        compose.onNodeWithText("Pas de réseau", substring = true).assertExists()
        capturer("03-accueil-hors-ligne")

        etat = EtatDrive.Erreur("Le Drive a refusé : l'API Google Drive n'est pas activée dans le projet Google Cloud (docs/google-cloud.md, étape 2).")
        compose.onNodeWithText("Réessayer").assertExists()
        capturer("04-accueil-erreur")
    }
}
