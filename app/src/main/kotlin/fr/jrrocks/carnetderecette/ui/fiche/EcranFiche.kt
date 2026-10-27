package fr.jrrocks.carnetderecette.ui.fiche

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.jrrocks.carnetderecette.scan.CameraPreview
import fr.jrrocks.carnetderecette.scan.rememberPermissionCamera
import fr.jrrocks.carnetderecette.ui.Destination
import kotlinx.coroutines.flow.first

/**
 * La fiche papier, reliée à son ViewModel et à la caméra : l'aperçu montre l'image entière,
 * celle que la prise de vue (pleine résolution, JPEG) enregistrera. Une fois la fiche
 * enregistrée, l'écran se referme.
 */
@Composable
fun EcranFiche(destination: Destination.Fiche, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    val vm: FicheViewModel = viewModel(
        key = "fiche-${destination.semaine}-${destination.numero ?: "libre"}",
        factory = FicheViewModel.fabrique(destination.semaine, destination.numero),
    )
    val retour by rememberUpdatedState(onRetour)
    LaunchedEffect(vm) {
        // demarrer() efface une fin restée d'une visite précédente (écran quitté pendant
        // l'enregistrement) ; on n'attend qu'ensuite celle de cette visite.
        vm.demarrer()
        vm.fini.first { it }
        vm.retourFait()
        retour()
    }

    val permission = rememberPermissionCamera()
    var lampe by rememberSaveable { mutableStateOf(false) }
    var lampeDisponible by remember { mutableStateOf(false) }
    var erreurCamera by remember { mutableStateOf<String?>(null) }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    val etat by vm.etat.collectAsStateWithLifecycle()
    FicheContenu(
        etat = etat.copy(
            camera = permission.etat,
            erreurCamera = erreurCamera,
            lampe = lampe,
            lampeDisponible = lampeDisponible,
        ),
        actions = ActionsFiche(
            retour = onRetour,
            lampe = { lampe = !lampe },
            demanderCamera = permission.demander,
            ouvrirReglages = permission.ouvrirReglages,
            photographier = { vm.photographier(capture) },
            reprendre = vm::reprendre,
            saisir = vm::saisir,
            ouvrirMenu = vm::ouvrirMenu,
            choisir = vm::choisir,
            personnes = vm::corrigerPersonnes,
            enregistrer = vm::enregistrer,
        ),
    ) {
        CameraPreview(
            torche = lampe,
            modifier = Modifier.fillMaxSize(),
            capture = capture,
            imageEntiere = true,
            surLampe = { lampeDisponible = it },
            surErreur = { erreurCamera = it },
        )
    }
}
