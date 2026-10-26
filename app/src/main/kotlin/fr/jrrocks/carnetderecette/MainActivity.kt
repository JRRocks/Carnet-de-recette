package fr.jrrocks.carnetderecette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.jrrocks.carnetderecette.ui.AccueilViewModel
import fr.jrrocks.carnetderecette.ui.DialogueMiseAJour
import fr.jrrocks.carnetderecette.ui.EcranAccueil
import fr.jrrocks.carnetderecette.ui.ThemeCarnet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThemeCarnet {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Application(viewModel(), Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

/** Relie l'accueil à son ViewModel ; seuls les lanceurs d'activité vivent ici. */
@Composable
private fun Application(vm: AccueilViewModel, modifier: Modifier = Modifier) {
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

    EcranAccueil(
        etat = vm.etat,
        versionNom = BuildConfig.VERSION_NAME,
        onConnecter = vm::connecter,
        modifier = modifier,
    )

    vm.publication?.let { publication ->
        DialogueMiseAJour(
            publication = publication,
            progression = vm.progression,
            message = vm.messageMaj,
            onMettreAJour = { vm.mettreAJour(ouvrirReglages = contexte::startActivity) },
            onPlusTard = vm::ecarterMiseAJour,
        )
    }
}
