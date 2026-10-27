package fr.jrrocks.carnetderecette.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Où en est l'accès à la caméra. */
enum class EtatPermission {
    ACCORDEE,

    /** Demandée, la réponse n'est pas encore venue. */
    EN_ATTENTE,

    /** Refusée : l'écran l'explique et mène aux réglages de l'app. */
    REFUSEE,
}

/** L'accès à la caméra et les deux gestes qui le débloquent. */
class PermissionCamera(
    val etat: EtatPermission,
    val demander: () -> Unit,
    val ouvrirReglages: () -> Unit,
)

/**
 * Demande l'accès à la caméra à l'ouverture de l'écran, une seule fois (une rotation ne
 * redemande pas). Au retour des réglages Android, l'accès est relu : accordé entre-temps, la
 * caméra s'ouvre sans autre geste.
 */
@Composable
fun rememberPermissionCamera(): PermissionCamera {
    val contexte = LocalContext.current
    var etat by rememberSaveable { mutableStateOf(if (accordee(contexte)) EtatPermission.ACCORDEE else EtatPermission.EN_ATTENTE) }
    var demandee by rememberSaveable { mutableStateOf(false) }
    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        etat = if (ok) EtatPermission.ACCORDEE else EtatPermission.REFUSEE
    }
    LaunchedEffect(Unit) {
        if (etat != EtatPermission.ACCORDEE && !demandee) {
            demandee = true
            lanceur.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (accordee(contexte)) etat = EtatPermission.ACCORDEE
    }
    return PermissionCamera(
        etat = etat,
        demander = { lanceur.launch(Manifest.permission.CAMERA) },
        ouvrirReglages = {
            contexte.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexte.packageName, null)),
            )
        },
    )
}

private fun accordee(contexte: Context): Boolean =
    ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
