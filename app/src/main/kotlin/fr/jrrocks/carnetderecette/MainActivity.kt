package fr.jrrocks.carnetderecette

import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import fr.jrrocks.carnetderecette.donnees.DepotLocal
import fr.jrrocks.carnetderecette.drive.DriveCarnet
import fr.jrrocks.carnetderecette.ui.AccueilViewModel
import fr.jrrocks.carnetderecette.ui.ActionsCarnet
import fr.jrrocks.carnetderecette.ui.CarnetApp
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.EtatCarnet
import fr.jrrocks.carnetderecette.ui.Onglet
import fr.jrrocks.carnetderecette.ui.fiche.EcranFiche
import fr.jrrocks.carnetderecette.ui.recette.EcranRecette
import fr.jrrocks.carnetderecette.ui.scanner.EcranScanner
import fr.jrrocks.carnetderecette.ui.seance.EcranSeance
import fr.jrrocks.carnetderecette.ui.composants.LocalImagesDuSite
import fr.jrrocks.carnetderecette.ui.vendrediDeReference
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /**
     * La date du jour, relue à chaque retour au premier plan : l'app laissée ouverte jeudi et
     * reprise samedi ne doit pas garder la semaine de jeudi.
     */
    private var aujourdHui by mutableStateOf(LocalDate.now())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La barre bleu nuit passe sous la barre d'état : icônes claires, en clair comme en sombre.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val depot = DepotLocal.de(this)
        setContent {
            // Les images du site déjà téléchargées, pour tous les écrans.
            CompositionLocalProvider(LocalImagesDuSite provides depot::imageLocale) {
                ThemeCarnet { Application(viewModel(), aujourdHui) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        aujourdHui = LocalDate.now()
    }
}

/** Relie la coque à son ViewModel ; seuls les lanceurs d'activité vivent ici. */
@Composable
private fun Application(vm: AccueilViewModel, aujourdHui: LocalDate) {
    val contexte = LocalContext.current

    val lanceurConsentement = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.retourConsentement(it.data)
    }
    // Le ViewModel demande l'écran de consentement ; l'Activity, seule à pouvoir le lancer, le présente.
    LaunchedEffect(vm.consentement) {
        vm.consentement?.let {
            lanceurConsentement.launch(IntentSenderRequest.Builder(it).build())
            vm.consentementPresente()
        }
    }

    val snackbars = remember { SnackbarHostState() }
    // Une seule fois, au premier branchement de ce téléphone. Elle reste jusqu'à « Compris » :
    // en temps limité, elle pouvait s'effacer sans avoir été lue. Interrompue par une rotation,
    // elle revient.
    LaunchedEffect(vm.annonceDossier) {
        if (vm.annonceDossier) {
            snackbars.showSnackbar(
                "Dossier «\u00A0${DriveCarnet.NOM_DOSSIER}\u00A0» prêt sur le Drive. S'il n'est pas encore dans «\u00A0Maison\u00A0», déplace-le.",
                actionLabel = "Compris",
                duration = SnackbarDuration.Indefinite,
            )
            vm.annonceVue()
        }
    }

    var onglet by rememberSaveable { mutableStateOf(Onglet.SEMAINE) }
    // Les écrans poussés par-dessus les onglets (scanner, fiche, recette, séance).
    var pile by rememberSaveable(stateSaver = Destination.SauvegardePile) { mutableStateOf(emptyList<Destination>()) }
    // Seul le haut de la pile est composé : chaque écran du dessous garde ici son état
    // (défilement, nombre de personnes…) et le retrouve au retour. Un écran dépilé l'oublie.
    val etatsDesEcrans = rememberSaveableStateHolder()
    val ouvrir: (Destination) -> Unit = { pile = pile + it }
    val retour: () -> Unit = {
        pile.lastOrNull()?.let { etatsDesEcrans.removeState(cleEcran(pile.lastIndex, it)) }
        pile = pile.dropLast(1)
    }
    BackHandler(enabled = pile.isNotEmpty(), onBack = retour)

    // La semaine affichée (vendredi de livraison de référence) et ce que le téléphone en sait.
    val depot = remember { DepotLocal.de(contexte) }
    val semaine = remember(aujourdHui) { SemaineIso.de(vendrediDeReference(aujourdHui)) }
    val etatSemaine by depot.etat(semaine).collectAsState()

    val haut = pile.lastOrNull()
    etatsDesEcrans.SaveableStateProvider(cleEcran(pile.lastIndex, haut)) {
        when (haut) {
            null -> CarnetApp(
                etat = EtatCarnet(vm.etat, vm.maj, BuildConfig.VERSION_NAME, aujourdHui, etatSemaine),
                onglet = onglet,
                onOnglet = { onglet = it },
                actions = ActionsCarnet(
                    brancher = vm::connecter,
                    mettreAJour = { vm.mettreAJour(ouvrirReglages = contexte::startActivity) },
                    majPlusTard = vm::ecarterMiseAJour,
                    ouvrir = ouvrir,
                ),
                snackbars = snackbars,
            )
            is Destination.Scanner -> EcranScanner(haut, retour, ouvrir)
            is Destination.Fiche -> EcranFiche(haut, retour, ouvrir)
            is Destination.Recette -> EcranRecette(haut, retour, ouvrir)
            is Destination.Seance -> EcranSeance(haut, retour, ouvrir)
        }
    }
}

/** La clé de l'état d'un écran : son rang dans la pile (la même recette peut y être deux fois), ou les onglets. */
private fun cleEcran(rang: Int, destination: Destination?): String =
    if (destination == null) "onglets" else "$rang|${Destination.ecrire(destination)}"
