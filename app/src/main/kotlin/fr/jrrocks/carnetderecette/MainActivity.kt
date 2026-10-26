package fr.jrrocks.carnetderecette

import android.graphics.Color
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.jrrocks.carnetderecette.drive.DriveCarnet
import fr.jrrocks.carnetderecette.ui.AccueilViewModel
import fr.jrrocks.carnetderecette.ui.ActionsCarnet
import fr.jrrocks.carnetderecette.ui.CarnetApp
import fr.jrrocks.carnetderecette.ui.EtatCarnet
import fr.jrrocks.carnetderecette.ui.Onglet
import fr.jrrocks.carnetderecette.ui.theme.ThemeCarnet
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La barre bleu nuit passe sous la barre d'état : icônes claires, en clair comme en sombre.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            ThemeCarnet { Application(viewModel()) }
        }
    }
}

/** Relie la coque à son ViewModel ; seuls les lanceurs d'activité vivent ici. */
@Composable
private fun Application(vm: AccueilViewModel) {
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
    // Une seule fois, au premier branchement réussi. Interrompue par une rotation, l'annonce revient.
    LaunchedEffect(vm.annonceDossier) {
        if (vm.annonceDossier) {
            snackbars.showSnackbar(
                "Dossier « ${DriveCarnet.NOM_DOSSIER} » prêt. Déplace-le dans « Maison » sur le Drive.",
                actionLabel = "Compris",
                duration = SnackbarDuration.Long,
            )
            vm.annonceVue()
        }
    }

    var onglet by rememberSaveable { mutableStateOf(Onglet.SEMAINE) }
    CarnetApp(
        etat = EtatCarnet(vm.etat, vm.maj, BuildConfig.VERSION_NAME, LocalDate.now()),
        onglet = onglet,
        onOnglet = { onglet = it },
        actions = ActionsCarnet(
            brancher = vm::connecter,
            mettreAJour = { vm.mettreAJour(ouvrirReglages = contexte::startActivity) },
            majPlusTard = vm::ecarterMiseAJour,
        ),
        snackbars = snackbars,
    )
}
