package fr.jrrocks.carnetderecette.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
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
        get() = drive is EtatDrive.NonConnecte || drive is EtatDrive.Erreur || maj.attendUnGeste
}

/** Les gestes de l'utilisateur, remontés au ViewModel. */
@Immutable
data class ActionsCarnet(
    val brancher: () -> Unit = {},
    val mettreAJour: () -> Unit = {},
    val majPlusTard: () -> Unit = {},
    /** Pousse un écran plein écran par-dessus les onglets (scanner, recette, séance…). */
    val ouvrir: (Destination) -> Unit = {},
)

/** À partir de cette largeur (tablette, téléphone en paysage), un rail remplace la barre du bas. */
private const val LARGEUR_RAIL = 600

/** Largeur de lecture maximale du contenu, centré, sur un écran large. */
private val LARGEUR_CONTENU = 640.dp

/**
 * La coque de l'app : barre d'application bleu nuit, contenu de l'onglet, navigation Material 3
 * à quatre destinations (barre en bas, rail sur un écran large). Le retour système ramène
 * d'abord à la Semaine, puis quitte l'app.
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
    val large = LocalConfiguration.current.screenWidthDp >= LARGEUR_RAIL
    // L'encoche de la caméra compte aussi (safeDrawing) : en paysage, rien ne passe dessous.
    val bords = WindowInsets.safeDrawing
    Row(modifier.fillMaxSize()) {
        if (large) Navigation(onglet, onOnglet, etat.actionRequise, rail = true)
        Scaffold(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = if (large) bords.only(WindowInsetsSides.Vertical + WindowInsetsSides.End) else bords,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                if (onglet == Onglet.SEMAINE) titreSemaine(etat.aujourdHui) else onglet.libelle,
                                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                        },
                        windowInsets = bords.only(WindowInsetsSides.Top + if (large) WindowInsetsSides.End else WindowInsetsSides.Horizontal),
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Carnet.couleurs.barre,
                            titleContentColor = Carnet.couleurs.surBarre,
                        ),
                    )
                    if (onglet == Onglet.SEMAINE && etat.drive is EtatDrive.EnCours) {
                        // Dans la barre, en clair sur bleu nuit : collé dessous en bleu, il se confondait avec son bord.
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().height(4.dp),
                            color = Carnet.couleurs.surBarre,
                            trackColor = Carnet.couleurs.barre,
                        )
                    }
                }
            },
            bottomBar = { if (!large) Navigation(onglet, onOnglet, etat.actionRequise, rail = false) },
            floatingActionButton = { if (bouton != null) BoutonFlottant(bouton) },
            snackbarHost = { SnackbarHost(snackbars) },
        ) { marges ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Crossfade(onglet, Modifier.widthIn(max = LARGEUR_CONTENU).fillMaxSize(), animationSpec = tween(200), label = "onglet") { courant ->
                    when (courant) {
                        Onglet.SEMAINE -> EcranSemaine(etat, actions, marges, avecBouton = bouton != null)
                        Onglet.CARNET -> EcranCarnet(marges)
                        Onglet.CUISINE -> EcranCuisine(marges) {
                            actions.ouvrir(Destination.Seance(SemaineIso.de(vendrediDeReference(etat.aujourdHui)), numero = null))
                        }
                        Onglet.REGLAGES -> EcranReglages(etat, actions, marges)
                    }
                }
            }
        }
    }
}

/**
 * Le bouton d'action de la Semaine. Au-delà de 150 % de taille de police, il se replie sur son
 * icône (comportement Material) : déplié, il couvrait la ligne de la semaine. TalkBack lit
 * toujours la phrase entière.
 */
@Composable
private fun BoutonFlottant(bouton: BoutonAction) {
    val deplie = LocalDensity.current.fontScale < 1.5f
    ExtendedFloatingActionButton(
        text = { Text(bouton.texte, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(painterResource(bouton.icone), contentDescription = null) },
        onClick = bouton.action,
        expanded = deplie,
        modifier = Modifier
            .heightIn(min = 64.dp)
            .clearAndSetSemantics {
                contentDescription = bouton.description
                role = Role.Button
                onClick { bouton.action(); true }
            },
        shape = MaterialTheme.shapes.large,
        containerColor = Carnet.couleurs.action,
        contentColor = Carnet.couleurs.surAction,
    )
}

/** Barre de navigation (téléphone) ou rail (écran large) : les mêmes quatre destinations. */
@Composable
private fun Navigation(onglet: Onglet, onOnglet: (Onglet) -> Unit, actionRequise: Boolean, rail: Boolean) {
    val couleurs = MaterialTheme.colorScheme
    val bords = WindowInsets.safeDrawing
    if (rail) {
        NavigationRail(
            containerColor = couleurs.surfaceContainer,
            windowInsets = bords.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
        ) {
            Onglet.entries.forEach { destination ->
                val actif = destination == onglet
                val pastille = destination == Onglet.REGLAGES && actionRequise
                NavigationRailItem(
                    selected = actif,
                    onClick = { onOnglet(destination) },
                    icon = { IconeOnglet(destination, actif, pastille) },
                    label = { LibelleOnglet(destination, actif) },
                    modifier = Modifier.descriptionOnglet(pastille),
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = couleurs.onSecondaryContainer,
                        selectedTextColor = couleurs.onSurface,
                        indicatorColor = couleurs.secondaryContainer,
                        unselectedIconColor = couleurs.onSurfaceVariant,
                        unselectedTextColor = couleurs.onSurfaceVariant,
                    ),
                )
            }
        }
    } else {
        NavigationBar(
            containerColor = couleurs.surfaceContainer,
            windowInsets = bords.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
        ) {
            Onglet.entries.forEach { destination ->
                val actif = destination == onglet
                val pastille = destination == Onglet.REGLAGES && actionRequise
                NavigationBarItem(
                    selected = actif,
                    onClick = { onOnglet(destination) },
                    icon = { IconeOnglet(destination, actif, pastille) },
                    label = { LibelleOnglet(destination, actif) },
                    modifier = Modifier.descriptionOnglet(pastille),
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
}

/** Icône pleine pour l'onglet actif, au contour sinon ; un point quand Réglages attend un geste. */
@Composable
private fun IconeOnglet(destination: Onglet, actif: Boolean, pastille: Boolean) {
    // Le point est bleu, pas carmin : le carmin est réservé aux erreurs, toujours écrites.
    BadgedBox(badge = { if (pastille) Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
        Icon(painterResource(if (actif) destination.iconeActive else destination.icone), contentDescription = null)
    }
}

/**
 * Les libellés grandissent avec la police jusqu'à 120 %, puis s'arrêtent : au-delà, quatre
 * libellés ne tiennent plus côte à côte sur 360 dp (80 dp chacun), et « Réglages » se coupait.
 * Le titre de chaque écran, lui, suit la taille de police jusqu'au bout ; TalkBack lit le
 * libellé entier.
 */
@Composable
private fun LibelleOnglet(destination: Onglet, actif: Boolean) {
    val densite = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(densite.density, densite.fontScale.coerceAtMost(LIBELLES_MAX))) {
        Text(
            destination.libelle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (actif) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun Modifier.descriptionOnglet(pastille: Boolean): Modifier =
    if (pastille) semantics { contentDescription = "Réglages, action requise" } else this

private const val LIBELLES_MAX = 1.2f

private data class BoutonAction(
    val texte: String,
    @DrawableRes val icone: Int,
    /** Ce que TalkBack lit : la phrase entière, compréhensible hors de son contexte. */
    val description: String,
    val action: () -> Unit,
)

/**
 * Le bouton d'action de la Semaine suit l'étape en cours. Au lot 1, il n'y en a que pour
 * brancher le Drive ; une fois branché, aucun bouton : pas de contrôle qui ne mène nulle part.
 */
private fun boutonDeLaSemaine(drive: EtatDrive, actions: ActionsCarnet): BoutonAction? = when (drive) {
    EtatDrive.NonConnecte -> BoutonAction("Brancher le Drive", R.drawable.ic_add_to_drive, "Brancher le Drive du foyer", actions.brancher)
    is EtatDrive.Erreur -> BoutonAction("Réessayer", R.drawable.ic_refresh, "Réessayer de brancher le Drive", actions.brancher)
    EtatDrive.EnCours, is EtatDrive.Verification, is EtatDrive.Pret -> null
}
