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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.scan.EtatPermission
import fr.jrrocks.carnetderecette.ui.scanner.ActionsScanner
import fr.jrrocks.carnetderecette.ui.scanner.BoxVue
import fr.jrrocks.carnetderecette.ui.scanner.EtatMenu
import fr.jrrocks.carnetderecette.ui.scanner.EtatPage
import fr.jrrocks.carnetderecette.ui.scanner.EtatScanner
import fr.jrrocks.carnetderecette.ui.scanner.ScannerContenu
import fr.jrrocks.carnetderecette.ui.scanner.ScannerViewModel
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Le scanner des box rendu en PNG sans téléphone (méthode des captures des onglets) : l'aperçu
 * de la caméra est un simple fond sombre, le réticule est le vrai. Menu et recettes
 * **factices** (CLAUDE.md, règle 1). Les images sortent dans `app/build/captures/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesScannerTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val menu = listOf(
        EntreeMenu(12, "Soupe factice du jour", url = "https://exemple.invalid/12"),
        EntreeMenu(45, "Gratin témoin aux poireaux", url = "https://exemple.invalid/45"),
        EntreeMenu(207, "Poulet fictif croustillant", url = "https://exemple.invalid/207"),
        EntreeMenu(208, "Curry imaginaire aux légumes du marché et riz parfumé", url = "https://exemple.invalid/208"),
        EntreeMenu(315, "Pâtes d'essai à la tomate", url = "https://exemple.invalid/315"),
    )

    private val poulet = BoxVue(207, "Poulet fictif croustillant", 4, aConfirmer = false, page = EtatPage.LUE)
    private val pates = BoxVue(315, "Pâtes d'essai à la tomate", 2, aConfirmer = false, page = EtatPage.LUE)
    private val curry = BoxVue(208, "Curry imaginaire aux légumes du marché et riz parfumé", 2, aConfirmer = true, page = EtatPage.EN_LECTURE)

    private var etat by mutableStateOf(EtatScanner(camera = EtatPermission.ACCORDEE, lampeDisponible = true))
    private var sombre by mutableStateOf(false)
    private var police by mutableFloatStateOf(1f)
    private val gestes = mutableListOf<String>()

    /** Une zone que TalkBack annonce quand son texte change. */
    private val annoncee = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

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
        val actions = ActionsScanner(
            ouvrirMenu = { gestes += "menu $it" },
            choisir = { gestes += "choisir $it" },
            retirer = { gestes += "retirer $it" },
            personnes = { n, p -> gestes += "personnes $n $p" },
            photographierFiche = { gestes += "fiche $it" },
        )
        compose.setContent {
            val densite = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densite.density, police)) {
                ThemeCarnet(sombre = sombre) {
                    ScannerContenu(etat, actions, Modifier.testTag("capture")) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF20262E)))
                    }
                }
            }
        }
    }

    @Test
    fun `le scanner dans chacun de ses etats`() {
        afficher()

        compose.onNodeWithText("Lecture du menu de la semaine…").assertExists()
        capturer("71-scanner-lecture-menu")

        etat = etat.copy(menu = EtatMenu.Lu, entreesMenu = menu)
        // La zone annoncée est déjà là avant la première box : seul son texte changera.
        compose.onNode(hasText("Vise l'étiquette", substring = true) and annoncee).assertExists()
        compose.onNodeWithContentDescription("Allumer la lampe").assertExists()
        capturer("72-scanner-pret")

        etat = etat.copy(courante = poulet, boxes = listOf(pates, poulet), progression = 0.4f)
        // Le titre est dans le dernier résultat et dans sa rangée.
        compose.onAllNodesWithText("Poulet fictif croustillant").assertCountEquals(2)
        compose.onNodeWithText("Déjà scannées\u00A0: 2").assertExists()
        compose.onNodeWithText("Recette lue").assertExists()
        compose.onNode(hasText("207") and hasText("Poulet fictif croustillant") and annoncee).assertExists()
        // Le nombre de personnes est annoncé quand − ou + le change.
        compose.onNodeWithContentDescription("4 personnes").assert(annoncee)
        capturer("73-scanner-box-validee")

        compose.onNodeWithContentDescription("Une personne de plus").performClick()
        compose.onNodeWithContentDescription("Retirer la box 315").performScrollTo().performClick()
        // La fiche papier des recours ne reprend pas la dernière box lue : la box en main est illisible.
        compose.onNodeWithText("Photographier la fiche").performScrollTo().performClick()
        assertEquals(listOf("personnes 207 5", "retirer 315", "fiche null"), gestes)

        etat = etat.copy(courante = curry, boxes = listOf(pates, poulet, curry), progression = 0f)
        compose.onAllNodesWithText("à confirmer").assertCountEquals(2)
        compose.onNodeWithText("Lecture de la recette…").assertExists()
        capturer("74-scanner-a-confirmer")

        // Téléphone plein : l'écriture échoue, l'écran le dit au lieu de planter.
        etat = etat.copy(erreur = ScannerViewModel.ERREUR_ECRITURE)
        compose.onNodeWithText(ScannerViewModel.ERREUR_ECRITURE).assertExists()
        capturer("74b-scanner-erreur-ecriture")

        etat = EtatScanner(camera = EtatPermission.ACCORDEE, lampeDisponible = true, menu = EtatMenu.HorsLigne)
        compose.onNodeWithText("Pas de réseau\u00A0: le menu de la semaine n'est pas lu.").assertExists()
        compose.onNodeWithText("Réessayer").assertExists()
        capturer("75-scanner-pas-de-reseau")

        etat = etat.copy(menu = EtatMenu.AccesRompu)
        compose.onNodeWithText("Accès HelloFresh rompu", substring = true).assertExists()
        capturer("76-scanner-acces-rompu")

        etat = etat.copy(menu = EtatMenu.NonEnregistre)
        compose.onNodeWithText("Le menu de la semaine n'a pas pu être enregistré", substring = true).assertExists()
        compose.onNodeWithText("Réessayer").assertExists()
        capturer("76b-scanner-menu-non-enregistre")

        etat = EtatScanner(camera = EtatPermission.REFUSEE, menu = EtatMenu.Lu, entreesMenu = menu, boxes = listOf(pates))
        compose.onNodeWithText("Ouvrir les réglages").assertExists()
        compose.onNodeWithContentDescription("Allumer la lampe").assertDoesNotExist()
        capturer("77-scanner-permission-refusee")

        etat = etat.copy(camera = EtatPermission.ACCORDEE, lampeDisponible = true, menuOuvert = true, boxes = listOf(pates, poulet))
        compose.onNodeWithText("Choisis la recette de la box").assertExists()
        compose.onNodeWithText("Gratin témoin aux poireaux").performClick()
        assertEquals("choisir 45", gestes.last())
        capturer("78-scanner-liste-menu")
    }

    @Test
    fun `le scanner en sombre et a 200 pour cent`() {
        afficher()
        etat = etat.copy(
            menu = EtatMenu.Lu,
            entreesMenu = menu,
            courante = poulet,
            doublon = true,
            boxes = listOf(pates, poulet, curry),
            progression = 0.6f,
            lampe = true,
        )

        sombre = true
        compose.onNodeWithText("Déjà scannée cette semaine.").assertExists()
        compose.onNodeWithContentDescription("Éteindre la lampe").assertExists()
        capturer("79-scanner-sombre")

        sombre = false
        police = 2f
        etat = etat.copy(courante = curry.copy(page = EtatPage.HORS_LIGNE), doublon = false, lampe = false)
        compose.onNodeWithText("Pas de réseau\u00A0: recette pas encore lue.").assertExists()
        capturer("79b-scanner-police-200")

        police = 1.3f
        capturer("79c-scanner-police-130")
    }
}
