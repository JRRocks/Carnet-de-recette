package fr.jrrocks.carnetderecette.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.ui.theme.Carnet
import java.time.LocalDate

/** Les quatre destinations de la barre de navigation. */
enum class Onglet(val libelle: String, @DrawableRes val icone: Int, @DrawableRes val iconeActive: Int) {
    SEMAINE("Semaine", R.drawable.ic_calendar_view_week, R.drawable.ic_calendar_view_week_plein),
    CARNET("Carnet", R.drawable.ic_menu_book, R.drawable.ic_menu_book_plein),
    CUISINE("Cuisine", R.drawable.ic_skillet, R.drawable.ic_skillet_plein),
    REGLAGES("Réglages", R.drawable.ic_settings, R.drawable.ic_settings_plein),
}

/** Ce que l'écran affiche, sans lien vers Android : de quoi le rendre en test. */
@Immutable
data class EtatCarnet(
    val drive: EtatDrive,
    val maj: EtatMiseAJour,
    val versionNom: String,
    val aujourdHui: LocalDate,
) {
    /** Le Drive ou une mise à jour attend un geste : point sur l'onglet Réglages. */
    val actionRequise: Boolean
        get() = drive is EtatDrive.NonConnecte || drive is EtatDrive.Erreur || maj !is EtatMiseAJour.Aucune
}

/** Les gestes de l'utilisateur, remontés au ViewModel. */
@Immutable
data class ActionsCarnet(
    val brancher: () -> Unit = {},
    val mettreAJour: () -> Unit = {},
    val majPlusTard: () -> Unit = {},
)

/**
 * La coque de l'app : barre d'application bleu nuit, contenu de l'onglet, barre de
 * navigation Material 3 à quatre destinations. Le retour système ramène d'abord à la
 * Semaine, puis quitte l'app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarnetApp(
    etat: EtatCarnet,
    onglet: Onglet,
    onOnglet: (Onglet) -> Unit,
    actions: ActionsCarnet,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = onglet != Onglet.SEMAINE) { onOnglet(Onglet.SEMAINE) }
    val bouton = if (onglet == Onglet.SEMAINE) boutonDeLaSemaine(etat.drive, actions) else null
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            if (onglet == Onglet.SEMAINE) "Semaine ${numeroSemaine(etat.aujourdHui)}" else onglet.libelle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Carnet.couleurs.barre,
                        titleContentColor = Carnet.couleurs.surBarre,
                    ),
                )
                if (onglet == Onglet.SEMAINE && etat.drive is EtatDrive.EnCours) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
                }
            }
        },
        bottomBar = { BarreDeNavigation(onglet, onOnglet, etat.actionRequise) },
        floatingActionButton = {
            if (bouton != null) {
                ExtendedFloatingActionButton(
                    text = { Text(bouton.texte, style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(painterResource(bouton.icone), contentDescription = null) },
                    onClick = bouton.action,
                    modifier = Modifier.heightIn(min = 64.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = Carnet.couleurs.barre,
                    contentColor = Carnet.couleurs.surBarre,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { marges ->
        Crossfade(onglet, animationSpec = tween(200), label = "onglet") { courant ->
            when (courant) {
                Onglet.SEMAINE -> EcranSemaine(etat, actions, marges, avecBouton = bouton != null)
                Onglet.CARNET -> EcranCarnet(marges)
                Onglet.CUISINE -> EcranCuisine(marges)
                Onglet.REGLAGES -> EcranReglages(etat, actions, marges)
            }
        }
    }
}

@Composable
private fun BarreDeNavigation(onglet: Onglet, onOnglet: (Onglet) -> Unit, actionRequise: Boolean) {
    val couleurs = MaterialTheme.colorScheme
    // Les libellés grandissent avec la police jusqu'à 120 %, puis s'arrêtent : au-delà, quatre
    // libellés ne tiennent plus côte à côte sur 360 dp (80 dp chacun), et « Réglages » se coupait. Le titre de
    // chaque écran, lui, suit la taille de police jusqu'au bout ; TalkBack lit le libellé entier.
    val densite = LocalDensity.current
    val densiteLibelles = Density(densite.density, densite.fontScale.coerceAtMost(LIBELLES_MAX))
    NavigationBar(containerColor = couleurs.surfaceContainer) {
        Onglet.entries.forEach { destination ->
            val actif = destination == onglet
            val pastille = destination == Onglet.REGLAGES && actionRequise
            NavigationBarItem(
                selected = actif,
                onClick = { onOnglet(destination) },
                icon = {
                    BadgedBox(badge = { if (pastille) Badge() }) {
                        Icon(painterResource(if (actif) destination.iconeActive else destination.icone), contentDescription = null)
                    }
                },
                label = {
                    CompositionLocalProvider(LocalDensity provides densiteLibelles) {
                        Text(
                            destination.libelle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (actif) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                },
                modifier = if (pastille) Modifier.semantics { contentDescription = "Réglages, action requise" } else Modifier,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = couleurs.onSecondaryContainer,
                    selectedTextColor = couleurs.onSurface,
                    indicatorColor = couleurs.secondaryContainer,
                    unselectedIconColor = couleurs.onSurfaceVariant,
                    unselectedTextColor = couleurs.onSurfaceVariant,
                ),
            )
        }
    }
}

private const val LIBELLES_MAX = 1.2f

private data class BoutonAction(val texte: String, @DrawableRes val icone: Int, val action: () -> Unit)

/**
 * Le bouton d'action de la Semaine suit l'étape en cours. Au lot 1, il n'y en a que pour
 * brancher le Drive ; une fois branché, aucun bouton : pas de contrôle qui ne mène nulle part.
 */
private fun boutonDeLaSemaine(drive: EtatDrive, actions: ActionsCarnet): BoutonAction? = when (drive) {
    EtatDrive.NonConnecte -> BoutonAction("Brancher le Drive", R.drawable.ic_add_to_drive, actions.brancher)
    is EtatDrive.Erreur -> BoutonAction("Réessayer", R.drawable.ic_refresh, actions.brancher)
    EtatDrive.EnCours, is EtatDrive.Pret -> null
}
