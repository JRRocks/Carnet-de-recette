package fr.jrrocks.carnetderecette.ui.scanner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.jrrocks.carnetderecette.scan.CameraPreview
import fr.jrrocks.carnetderecette.scan.OcrElements
import fr.jrrocks.carnetderecette.scan.rememberPermissionCamera
import fr.jrrocks.carnetderecette.ui.Destination

/**
 * Le scanner des box, relié à son ViewModel et à la caméra. Un seul analyseur pour toute la
 * durée de l'écran (le modèle ML Kit ne se recharge pas à chaque recomposition), fermé à la
 * sortie ; la caméra se libère avec l'aperçu.
 */
@Composable
fun EcranScanner(destination: Destination.Scanner, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    val vm: ScannerViewModel = viewModel(key = "scanner-${destination.semaine}", factory = ScannerViewModel.fabrique(destination.semaine))
    LaunchedEffect(vm) { vm.demarrer() }

    val permission = rememberPermissionCamera()
    var lampe by rememberSaveable { mutableStateOf(false) }
    var lampeDisponible by remember { mutableStateOf(false) }
    var erreurCamera by remember { mutableStateOf<String?>(null) }

    val analyseur = remember(vm) { OcrElements(recepteur = vm::surImage) }
    DisposableEffect(analyseur) { onDispose { analyseur.close() } }

    val etat by vm.etat.collectAsStateWithLifecycle()
    ScannerContenu(
        etat = etat.copy(
            camera = permission.etat,
            erreurCamera = erreurCamera,
            lampe = lampe,
            lampeDisponible = lampeDisponible,
        ),
        actions = ActionsScanner(
            retour = onRetour,
            lampe = { lampe = !lampe },
            demanderCamera = permission.demander,
            ouvrirReglages = permission.ouvrirReglages,
            relireMenu = vm::relireMenu,
            ouvrirMenu = vm::ouvrirMenu,
            choisir = vm::choisir,
            selectionner = vm::selectionner,
            personnes = vm::corrigerPersonnes,
            retirer = vm::retirer,
            relirePage = vm::relirePage,
            photographierFiche = { numero -> ouvrir(Destination.Fiche(destination.semaine, numero)) },
        ),
    ) {
        CameraPreview(
            torche = lampe,
            modifier = Modifier.fillMaxSize(),
            analyseur = analyseur,
            surLampe = { lampeDisponible = it },
            surErreur = { erreurCamera = it },
        )
    }
}
