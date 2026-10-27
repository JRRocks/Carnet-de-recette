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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.core.audio.CommandeVocale
import fr.jrrocks.carnetderecette.core.audio.LecteurSegments
import fr.jrrocks.carnetderecette.core.audio.SegmentsDeRecette
import fr.jrrocks.carnetderecette.core.recette.RecetteExemple
import fr.jrrocks.carnetderecette.ui.seance.ActionsSeance
import fr.jrrocks.carnetderecette.ui.seance.Entendu
import fr.jrrocks.carnetderecette.ui.seance.EtatSeance
import fr.jrrocks.carnetderecette.ui.seance.SeanceContenu
import fr.jrrocks.carnetderecette.ui.seance.etatSeance
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La séance rendue en PNG sans téléphone (même méthode que [CapturesOngletsTest]), sur la
 * recette d'exemple factice. Les états viennent du vrai [LecteurSegments] et de la vraie
 * construction de l'écran ([etatSeance]) : la capture montre ce que l'app affichera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesSeanceTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val recette = RecetteExemple.recette
    private val lecteur = LecteurSegments(SegmentsDeRecette.de(recette)).apply { demarrer() }
    private val textes = recette.etapes.associate { SegmentsDeRecette.idDe(it) to it.texte }

    private var etat by mutableStateOf(construire())
    private var sombre by mutableStateOf(false)
    private var police by mutableFloatStateOf(1f)

    private fun construire(
        restants: Map<String, Int> = emptyMap(),
        voix: EtatVoix = EtatVoix.PRETE,
        micro: EtatEcoute = EtatEcoute.Arretee,
        entendu: Entendu? = null,
        voixCoupee: Boolean = false,
    ): EtatSeance = etatSeance(recette.titre, lecteur.etat, textes, restants, voix, voixCoupee, micro, entendu)

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
                    SeanceContenu(etat, ActionsSeance(), Modifier.testTag("capture"))
                }
            }
        }
    }

    /** Aucun mot de métro dans les textes (direction, « le monde se voit, il ne se lit pas »). */
    private fun sansVocabulaireDeMetro() {
        for (mot in listOf("station", "terminus", "correspondance", "tu es ici", "ma ligne")) {
            compose.onAllNodes(hasText(mot, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    @Test
    fun `la seance, du debut a la fin`() {
        afficher()

        // Début : l'étape 1 attend qu'on la demande, « Lire » plutôt que « Relire ».
        compose.onNodeWithText("Lire").assertExists()
        compose.onNodeWithText("C'est fait").assertExists()
        compose.onAllNodesWithText("Relire").assertCountEquals(0)
        compose.onNodeWithText(recette.etapes[0].texte).assertExists()
        sansVocabulaireDeMetro()
        capturer("61-seance-debut")

        // L'étape 2, son minuteur de 5 minutes lancé il y a 53 secondes.
        lecteur.relire()
        lecteur.finDeLecture("e1")
        lecteur.valider()
        lecteur.finDeLecture("e2")
        lecteur.lancerMinuteur()
        etat = construire(restants = mapOf("e2" to 247))
        compose.onNodeWithText("Relire").assertExists()
        compose.onNodeWithContentDescription("Minuteur de l'étape 2", substring = true).assertExists()
        compose.onNodeWithText("Lue à la fin du minuteur").assertExists()
        capturer("62-seance-minuteur")

        // Le minuteur fini, l'étape 3 part seule ; le micro ouvert vient de comprendre « suivant ».
        lecteur.minuteurFini("e2")
        lecteur.finDeLecture("e3")
        lecteur.basculer("e5")
        etat = construire(micro = EtatEcoute.Ecoute, entendu = Entendu.Compris(CommandeVocale.SUIVANT))
        compose.onNodeWithText("Micro ouvert", substring = true).assertExists()
        compose.onNodeWithText("Compris", substring = true).assertExists()
        compose.onNodeWithText("Coupée", substring = true).assertExists()
        compose.onNodeWithContentDescription("Arrêter d'écouter").assertExists()
        sansVocabulaireDeMetro()
        capturer("63-seance-micro")

        sombre = true
        capturer("66-seance-sombre")
        sombre = false

        // Une phrase qui n'est pas une commande, et la pause.
        lecteur.pause()
        etat = construire(micro = EtatEcoute.Ecoute, entendu = Entendu.PasCompris("il fait beau"))
        compose.onNodeWithText("Lecture en pause", substring = true).assertExists()
        compose.onNodeWithText("Pas une commande", substring = true).assertExists()
        police = 1.3f
        capturer("68-seance-pause-police-130")
        police = 1f
        lecteur.reprendre()

        // La dernière étape, courte : en 32 sp. L'étape 5, coupée, a été sautée.
        repeat(2) { lecteur.valider() }
        etat = construire()
        compose.onNodeWithText(recette.etapes[5].texte).assertExists()
        capturer("71-seance-derniere-etape")

        // La fin.
        lecteur.valider()
        etat = construire()
        compose.onNodeWithText("Recette terminée").assertExists()
        compose.onNodeWithText("Revenir").assertExists()
        sansVocabulaireDeMetro()
        capturer("65-seance-terminee")
    }

    @Test
    fun `sans voix francaise ni micro`() {
        afficher()
        etat = construire(voix = EtatVoix.LANGUE_ABSENTE, micro = EtatEcoute.PermissionRequise)
        compose.onNodeWithText("Installe une voix française pour la lecture à voix haute.").assertExists()
        compose.onNodeWithText("Installer une voix").assertExists()
        compose.onNodeWithText("Ouvrir les réglages").assertExists()
        capturer("64-seance-voix-absente")

        sombre = true
        etat = construire(voix = EtatVoix.INDISPONIBLE, micro = EtatEcoute.Erreur("Le micro ne répond pas. Une autre app l'utilise peut-être\u00A0: ferme-la, puis réessaie."))
        compose.onNodeWithText("Réessayer").assertExists()
        capturer("69-seance-erreurs-sombre")
    }

    @Test
    fun `la seance a 200 pour cent de police`() {
        lecteur.valider()
        lecteur.finDeLecture("e2")
        lecteur.lancerMinuteur()
        etat = construire(restants = mapOf("e2" to 247))
        police = 2f
        afficher()
        compose.onNodeWithText("C'est fait").assertExists()
        capturer("67-seance-police-200")

        // La voix coupée, écrite en toutes lettres.
        etat = construire(restants = mapOf("e2" to 247), voixCoupee = true)
        compose.onNodeWithText("Voix coupée", substring = true).assertExists()
        compose.onNodeWithContentDescription("Rétablir la voix").assertExists()
        police = 1f
        capturer("70-seance-voix-coupee")
    }
}
