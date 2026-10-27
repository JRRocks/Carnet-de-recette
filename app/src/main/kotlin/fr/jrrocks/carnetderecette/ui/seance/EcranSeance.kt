package fr.jrrocks.carnetderecette.ui.seance

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.audio.EtatEcoute
import fr.jrrocks.carnetderecette.audio.EtatVoix
import fr.jrrocks.carnetderecette.audio.Voix
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.recette.RecetteExemple
import fr.jrrocks.carnetderecette.donnees.DepotLocal
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat

/** La séance lue à voix haute (cadrage § 8), en plein écran, écran allumé. */
@Composable
fun EcranSeance(destination: Destination.Seance, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    val numero = destination.numero
    if (numero == null) {
        // « Essayer la lecture à voix haute » depuis la Cuisine : la recette d'exemple.
        SeanceDeRecette(RecetteExemple.recette, onRetour)
        return
    }
    val depot = DepotLocal.de(LocalContext.current)
    val etat by depot.etat(destination.semaine).collectAsState()
    val recette = etat.recette(numero)
    if (recette != null) {
        SeanceDeRecette(recette, onRetour)
    } else {
        // Données effacées entre-temps : on renvoie vers la fiche, qui sait relire la page.
        EcranPousse("Box $numero", onRetour) { marges ->
            Column(Modifier.padding(marges).padding(16.dp)) {
                LigneEtat(R.drawable.ic_help, "La recette de la box $numero n'est pas lue sur ce téléphone.", TonEtat.INFO) {
                    BoutonCarnet("Ouvrir la fiche", { ouvrir(Destination.Recette(destination.semaine, numero)) }, sorte = SorteBouton.CONTOUR)
                }
            }
        }
    }
}

/**
 * La séance d'une [recette] donnée : c'est l'appelant qui la trouve (la recette d'exemple, ou
 * celle d'une box, lue dans le dépôt de la semaine).
 */
@Composable
fun SeanceDeRecette(recette: Recette, onRetour: () -> Unit) {
    val contexte = LocalContext.current
    val activite = remember(contexte) { contexte.activite() }
    val vm = seanceViewModel(recette, activite)

    // Garder l'écran allumé le temps de la séance seulement : les mains sont occupées. La recette
    // finie, le téléphone posé pendant le repas s'éteint comme d'habitude, sauf si un minuteur
    // tourne encore : l'écran de fin l'affiche.
    val etatVm = vm.etat
    val garderAllume = !etatVm.terminee || etatVm.minuteur?.enMarche == true || etatVm.autresMinuteurs.isNotEmpty()
    DisposableEffect(activite, garderAllume) {
        val fenetre = activite?.window?.takeIf { garderAllume }
        fenetre?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { fenetre?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    // Au retour d'une installation de voix, la voix réessaie ; en arrière-plan, le micro se coupe.
    LifecycleResumeEffect(vm) {
        vm.auRetour()
        onPauseOrDispose { vm.enArrierePlan() }
    }

    val demandeMicro = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
        if (accordee) vm.ouvrirMicro() else vm.permissionRefusee()
    }

    val etat = vm.etat
    SeanceContenu(
        etat = etat,
        actions = ActionsSeance(
            valider = vm::valider,
            relire = vm::relire,
            precedent = vm::precedent,
            reprendre = vm::reprendre,
            minuteur = vm::minuteur,
            basculer = vm::basculer,
            changerDeclencheur = vm::changerDeclencheur,
            basculerVoix = vm::basculerVoix,
            basculerMicro = {
                when {
                    etat.micro == EtatEcoute.Ecoute -> vm.fermerMicro()
                    micAutorise(contexte) -> vm.ouvrirMicro()
                    else -> demandeMicro.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            reglerVoix = {
                if (etat.voix == EtatVoix.LANGUE_ABSENTE) {
                    ouvrirLePremier(contexte, Voix.installation(), Voix.reglages())
                } else {
                    ouvrirLePremier(contexte, Voix.reglages(), Intent(Settings.ACTION_SETTINGS))
                }
            },
            reglerMicro = {
                ouvrirLePremier(
                    contexte,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexte.packageName, null)),
                    Intent(Settings.ACTION_SETTINGS),
                )
            },
            revenir = onRetour,
        ),
    )
}

/**
 * Le ViewModel de la séance, dans le [MagasinSeance] de l'Activity : une rotation le garde,
 * quitter l'écran le libère (voix, micro, minuteurs).
 */
@Composable
private fun seanceViewModel(recette: Recette, activite: Activity?): SeanceViewModel {
    val application = LocalContext.current.applicationContext as Application
    val hote = viewModel<MagasinSeance>()
    val proprietaire = remember(hote) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = hote.magasin
        }
    }
    DisposableEffect(hote) {
        onDispose { if (activite?.isChangingConfigurations != true) hote.magasin.clear() }
    }
    return viewModel(
        viewModelStoreOwner = proprietaire,
        key = "seance-${recette.id}",
        factory = viewModelFactory { initializer { SeanceViewModel(application, recette) } },
    )
}

private fun micAutorise(contexte: Context) =
    ContextCompat.checkSelfPermission(contexte, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Ouvre le premier écran que le téléphone sait ouvrir : chaque fabricant a ses réglages. */
private fun ouvrirLePremier(contexte: Context, vararg intentions: Intent) {
    for (intention in intentions) {
        try {
            contexte.startActivity(intention)
            return
        } catch (_: ActivityNotFoundException) {
            // Écran absent sur ce téléphone : le suivant.
        }
    }
}

private tailrec fun Context.activite(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activite()
    else -> null
}
