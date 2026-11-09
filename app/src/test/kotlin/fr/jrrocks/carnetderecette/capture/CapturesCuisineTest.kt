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
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.core.audio.LecteurSegments
import fr.jrrocks.carnetderecette.core.cuisine.ModeCuisine
import fr.jrrocks.carnetderecette.core.cuisine.RoleCuisine
import fr.jrrocks.carnetderecette.core.cuisine.SegmentsDeLaSeance
import fr.jrrocks.carnetderecette.core.cuisine.SegmentsDuCuisinier
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.ui.ActionsCarnet
import fr.jrrocks.carnetderecette.ui.CarnetApp
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.EtatCarnet
import fr.jrrocks.carnetderecette.ui.EtatDrive
import fr.jrrocks.carnetderecette.ui.EtatMiseAJour
import fr.jrrocks.carnetderecette.ui.Onglet
import fr.jrrocks.carnetderecette.ui.Qui
import fr.jrrocks.carnetderecette.ui.seance.ActionsSeance
import fr.jrrocks.carnetderecette.ui.seance.EtatSeance
import fr.jrrocks.carnetderecette.ui.seance.SeanceContenu
import fr.jrrocks.carnetderecette.ui.seance.etatSeance
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Capture la zone marquée « capture » dans `app/build/captures/<nom>.png`. */
private fun capturerVers(compose: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, ComponentActivity>, nom: String) {
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

/**
 * Le samedi et la cuisine (lots 4 et 5) sur la semaine préparée **factice** : l'envoi des
 * recettes à Claude, l'import, la recette de ce soir, le plan à deux, seul, « Je suis… ».
 * Rendus sur un écran très haut pour voir chaque écran en entier.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1800dp-xxhdpi")
class CapturesCuisineTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var etat by mutableStateOf(
        EtatCarnet(EtatDrive.NonConnecte, EtatMiseAJour.Aucune, "0.6", LocalDate.of(2026, 10, 31), SemaineFactice.etat()),
    )
    private var onglet by mutableStateOf(Onglet.SEMAINE)
    private var sombre by mutableStateOf(false)
    private var police by mutableFloatStateOf(1f)
    private val ouvertes = mutableListOf<Destination>()
    private var importer = 0
    private var envois = 0
    private var choisies = mutableListOf<Int>()

    private fun afficher() {
        compose.setContent {
            val densite = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(densite.density, police)) {
                ThemeCarnet(sombre = sombre) {
                    CarnetApp(
                        etat,
                        onglet,
                        { onglet = it },
                        ActionsCarnet(
                            ouvrir = { ouvertes += it },
                            importer = { importer++ },
                            envoyerAClaude = { envois++ },
                            choisirRole = { etat = etat.copy(role = it) },
                            choisirMode = { etat = etat.copy(mode = it) },
                            choisirCeSoir = { choisies += it },
                        ),
                        Modifier.testTag("capture"),
                    )
                }
            }
        }
    }

    private fun capturer(nom: String) = capturerVers(compose, nom)

    private fun sansVocabulaireDeMetro() {
        for (mot in listOf("station", "terminus", "correspondance", "tu es ici", "ma ligne")) {
            compose.onAllNodes(hasText(mot, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    @Test
    fun `du vendredi soir au samedi`() {
        afficher()
        // Box scannées : on envoie les recettes à Claude ; le bouton flottant propose l'import.
        compose.onNodeWithText("Envoyer les recettes").performClick()
        assertEquals(1, envois)
        compose.onNodeWithContentDescription("Importer la semaine préparée par Claude").performClick()
        assertEquals(1, importer)
        compose.onNodeWithText("Scanner d'autres box", useUnmergedTree = true).assertExists()
        sansVocabulaireDeMetro()
        capturer("c01-semaine-envoi-a-claude")

        // Le mauvais fichier : le motif, sous l'étape du samedi, avec de quoi en choisir un autre.
        etat = etat.copy(semaine = SemaineFactice.etat().copy(importRefuse = "C'est le fichier des recettes déposé par l'app, pas celui de Claude\u00A0: choisis le fichier «\u00A0semaine-…\u00A0»."))
        compose.onNodeWithText("Choisir un autre fichier", useUnmergedTree = true).assertExists()
        capturer("c02-semaine-import-refuse")

        // Importée : ce soir, la 207 ; le bouton flottant emmène en cuisine.
        etat = etat.copy(semaine = SemaineFactice.etatImporte())
        compose.onNodeWithText("Ce soir\u00A0: la 207, environ 29\u00A0min à deux.").assertExists()
        compose.onNodeWithContentDescription("Découpe\u00A0: 4\u00A0tâches · 11\u00A0min").assertExists()
        // Une autre version de Claude s'importe encore, et les recettes se renvoient.
        compose.onNodeWithText("Importer une autre version").performClick()
        assertEquals(2, importer)
        compose.onNodeWithText("Renvoyer les recettes").performClick()
        assertEquals(2, envois)
        compose.onNodeWithContentDescription("Cuisiner la recette 207 ce soir").performClick()
        assertEquals(Onglet.CUISINE, onglet)
        onglet = Onglet.SEMAINE
        sansVocabulaireDeMetro()
        capturer("c03-semaine-importee")

        sombre = true
        capturer("c03b-semaine-importee-sombre")
    }

    @Test
    fun `la cuisine a deux et seul`() {
        etat = etat.copy(semaine = SemaineFactice.etatImporte())
        onglet = Onglet.CUISINE
        afficher()
        compose.onNodeWithText("207 · Poulet fictif croustillant").assertExists()
        compose.onNodeWithText("Choisis ton rôle\u00A0: ce téléphone le retient.").assertExists()
        // Le plan se lit tâche par tâche, avec ses passages de main.
        compose.onNodeWithContentDescription("Découpe\u00A0: Couper le poulet en dés").assertExists()
        compose.onNodeWithContentDescription("Passe à la chauffe\u00A0: fariner le poulet").assertExists()
        compose.onNodeWithContentDescription("Chauffe\u00A0: Cuisson au four (14\u00A0min)").assertExists()
        sansVocabulaireDeMetro()
        capturer("c04-cuisine-a-deux")

        compose.onNode(hasText("À deux") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Seul") and hasClickAction()).assertIsNotSelected()
        compose.onNode(hasText("Découpe") and hasClickAction()).performClick()
        assertEquals(RoleCuisine.DECOUPE, etat.role)
        compose.onNode(hasText("Découpe") and hasClickAction()).assertIsSelected()
        // Une autre recette pour ce soir : le choix remonte, pour la Semaine aussi.
        compose.onNodeWithText("Poêlée d'essai au sésame").performClick()
        assertEquals(listOf(203), choisies)
        compose.onNodeWithText("Commencer mes tâches").performClick()
        assertEquals(Destination.Seance(SemaineIso(2026, 44), 207, Qui.DECOUPE), ouvertes.last())
        capturer("c05-cuisine-a-deux-decoupe")

        compose.onNode(hasText("Seul") and hasClickAction()).performClick()
        assertEquals(ModeCuisine.SEUL, etat.mode)
        compose.onNodeWithText("Commencer").performClick()
        assertEquals(Destination.Seance(SemaineIso(2026, 44), 207, Qui.SEUL), ouvertes.last())
        capturer("c06-cuisine-seul")

        // La 207 est faite : ce soir, la 203, dont Claude doute du nombre de personnes.
        etat = etat.copy(mode = ModeCuisine.A_DEUX, role = RoleCuisine.CHAUFFE, semaine = SemaineFactice.etatImporte(cuisinees = setOf(207)))
        compose.onNodeWithText("203 · Poêlée d'essai au sésame").assertExists()
        compose.onNodeWithText("Claude a un doute", substring = true).assertExists()
        compose.onNodeWithText("cuisinée", useUnmergedTree = true).assertExists()
        capturer("c07-cuisine-203-doute")

        sombre = true
        capturer("c07b-cuisine-203-sombre")
        sombre = false
        police = 2f
        capturer("c08-cuisine-police-200")
    }

    @Test
    fun `le telephone qui n'a rien scanne importe aussi`() {
        // Le téléphone de l'autre cuisinier : ni box, ni menu.
        etat = etat.copy(semaine = SemaineFactice.etat(avecBoxes = false).copy(menu = null))
        afficher()
        compose.onNodeWithContentDescription("Scanner les box de la semaine").assertExists()
        compose.onNodeWithText("Importer la semaine").performClick()
        assertEquals(1, importer)
        capturer("c10-semaine-second-telephone")

        // Importée avant que le menu soit lu : chaque recette se nomme par son numéro, sans redite.
        etat = etat.copy(semaine = SemaineFactice.etat(avecBoxes = false).copy(menu = null, travail = SemaineFactice.travail), role = RoleCuisine.CHAUFFE)
        onglet = Onglet.CUISINE
        compose.onNodeWithText("Recette 207").assertExists()
        compose.onNodeWithText("Commencer mes tâches").assertExists()
        capturer("c10b-cuisine-second-telephone-sans-menu")
    }

    @Test
    fun `avant l'import, la cuisine montre le principe`() {
        onglet = Onglet.CUISINE
        afficher()
        compose.onNodeWithText("En cuisine, chacun ses tâches").assertExists()
        compose.onNodeWithText("Importer la semaine").performClick()
        assertEquals(1, importer)
        // Le plan d'exemple reste une seule phrase pour TalkBack.
        compose.onAllNodesWithContentDescription("Exemple", substring = true).assertCountEquals(1)
        capturer("c09-cuisine-avant-import")
    }
}

/** La séance de chacun, sur son téléphone : seulement ses segments, ceux de Claude. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class CapturesSeanceACoupleTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var etat by mutableStateOf<EtatSeance?>(null)

    private fun montrer(nom: String, titre: String, lus: SegmentsDeLaSeance, avance: Int, minuteur: Boolean = false) {
        val lecteur = LecteurSegments(lus.segments).apply { demarrer() }
        repeat(avance) {
            lecteur.valider()
            lecteur.etat.enLecture?.let { lecteur.finDeLecture(it) }
        }
        var restants = emptyMap<String, Int>()
        if (minuteur) {
            lecteur.lancerMinuteur()
            val courant = lecteur.etat.segmentCourant!!
            restants = mapOf(courant.id to courant.minuteurSecondes!! * 3 / 4)
        }
        etat = etatSeance(titre, lecteur.etat, lus.textes, restants, EtatVoix.PRETE, false, EtatEcoute.Arretee, null)
        capturerVers(compose, nom)
    }

    @Test
    fun `la decoupe et la chauffe`() {
        compose.setContent { ThemeCarnet { etat?.let { SeanceContenu(it, ActionsSeance(), Modifier.testTag("capture")) } } }
        val poulet = SemaineFactice.poulet
        montrer("c11-seance-decoupe", "207 · Découpe", SegmentsDuCuisinier.aDeux(poulet, RoleCuisine.DECOUPE), avance = 1)
        compose.onNodeWithText("Roule les dés dans les trente grammes de farine, puis passe-les à la chauffe.").assertExists()
        montrer("c12-seance-chauffe-minuteur", "207 · Chauffe", SegmentsDuCuisinier.aDeux(poulet, RoleCuisine.CHAUFFE), avance = 2, minuteur = true)
        compose.onNodeWithText("Fais dorer le poulet six minutes dans un filet d'huile, en le retournant.").assertExists()
        montrer("c13-seance-seul", "207 · Poulet fictif croustillant", SegmentsDuCuisinier.seul(poulet), avance = 2)
        compose.onNodeWithText("Roule les dés dans les trente grammes de farine.").assertExists()
    }
}
