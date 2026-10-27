package fr.jrrocks.carnetderecette.scan

import android.util.Rational
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Résolution d'analyse : assez fine pour une étiquette lue à bout de bras, assez légère pour
 * que ML Kit tienne la cadence sans faire chauffer le téléphone (moteur `scan-multimode`).
 */
val RESOLUTION_ANALYSE: Size = Size(1280, 720)

/**
 * Aperçu de la caméra arrière (moteur Library `scan-multimode`, sans ses modes ni le réticule,
 * dessiné à part par [DessinReticule]).
 *
 * - [analyseur] tourne sur un fil dédié ; `STRATEGY_KEEP_ONLY_LATEST` abandonne les images
 *   arrivées pendant une analyse plutôt que de les mettre en file : rien ne prend de retard.
 *   Avec un analyseur, un `ViewPort` à la taille de l'aperçu est posé : CameraX donne alors à
 *   chaque image d'analyse le recadrage exact de ce qui est affiché (`cropRect`), ce qui place
 *   le réticule au même endroit dans l'image et à l'écran.
 * - [capture] (ajout du Carnet, pour la fiche papier) est liée en plus, sans recadrage :
 *   la photo garde tout le champ du capteur. [imageEntiere] montre alors l'image entière à
 *   l'écran (bandes sur les côtés) : on cadre la page sur ce qui sera photographié. Sa
 *   rotation suit l'orientation physique du téléphone, même écran verrouillé en portrait.
 * - La caméra n'est reliée de nouveau que si ce qui compte change : la taille de l'aperçu
 *   pour l'analyse (ViewPort), jamais pour la seule photo. [torche] est réappliquée après
 *   chaque liaison et chaque retour au premier plan.
 *
 * [surLampe] dit, une fois la caméra ouverte, si elle a une lampe ; [surErreur] donne une
 * phrase en français, jamais le message brut d'une exception.
 */
@Composable
fun CameraPreview(
    torche: Boolean,
    modifier: Modifier = Modifier,
    analyseur: ImageAnalysis.Analyzer? = null,
    capture: ImageCapture? = null,
    imageEntiere: Boolean = false,
    surLampe: (Boolean) -> Unit = {},
    surErreur: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val vue = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = if (imageEntiere) PreviewView.ScaleType.FIT_CENTER else PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    // Gardé pour la libération : appeler getInstance().get() depuis onDispose bloquerait le fil
    // principal si l'écran est quitté avant l'ouverture.
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var taille by remember { mutableStateOf(IntSize.Zero) }
    val torcheVoulue by rememberUpdatedState(torche)
    // La rotation de la photo selon le téléphone lui-même (voir plus bas), gardée pour la
    // liaison suivante ; -1 tant qu'inconnue. Pas un état : rien ne se recompose avec elle.
    val rotationPhysique = remember { intArrayOf(-1) }

    // La lampe s'éteint quand la caméra perd ses cas d'usage (arrière-plan, nouvelle liaison) :
    // rallumée si elle est demandée, sans quoi le bouton la montrerait allumée sur une scène sombre.
    fun rallumer(ouverte: Camera) {
        if (torcheVoulue && ouverte.cameraInfo.hasFlashUnit()) ouverte.cameraControl.enableTorch(true)
    }

    // La taille ne sert qu'au ViewPort de l'analyse. Sans analyseur (fiche papier), seule
    // compte la première mise en page : le clavier qui s'ouvre ne relie pas la caméra à
    // chaque image de son animation.
    val cleTaille: Any = if (analyseur != null) taille else (taille.width > 0 && taille.height > 0)
    LaunchedEffect(analyseur, capture, cleTaille) {
        // Le ViewPort a besoin de la taille de l'aperçu : on attend la première mise en page.
        if (taille.width == 0 || taille.height == 0) return@LaunchedEffect
        val provider = try {
            ProcessCameraProvider.getInstance(context).await()
        } catch (e: Exception) {
            surErreur(CAMERA_INDISPONIBLE)
            return@LaunchedEffect
        }
        cameraProvider = provider
        val rotation = vue.display?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder().setTargetRotation(rotation).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val groupe = UseCaseGroup.Builder().addUseCase(preview)

        if (analyseur != null) {
            val resolution = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(RESOLUTION_ANALYSE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analyse = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolution)
                .setTargetRotation(rotation)
                .build()
            analyse.setAnalyzer(executor, analyseur)
            groupe.addUseCase(analyse)
            groupe.setViewPort(
                ViewPort.Builder(Rational(taille.width, taille.height), rotation)
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build(),
            )
        }
        if (capture != null) {
            capture.targetRotation = rotationPhysique[0].takeIf { it >= 0 } ?: rotation
            groupe.addUseCase(capture)
        }

        try {
            provider.unbindAll()
            val ouverte = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, groupe.build())
            camera = ouverte
            surLampe(ouverte.cameraInfo.hasFlashUnit())
            rallumer(ouverte)
        } catch (e: Exception) {
            surErreur(CAMERA_INDISPONIBLE)
        }
    }

    LaunchedEffect(camera, torche) {
        val courante = camera ?: return@LaunchedEffect
        if (courante.cameraInfo.hasFlashUnit()) courante.cameraControl.enableTorch(torche)
    }

    // ON_RESUME arrive après la réattache d'ON_START (même exécuteur de CameraX) : la caméra
    // est de nouveau active quand la consigne de lampe repart.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { camera?.let(::rallumer) }

    // Photo : la rotation suit le téléphone, pas l'écran (doc CameraX). Rotation automatique
    // coupée, l'écran reste en portrait quand on tourne le téléphone pour cadrer une page
    // large : sans cela, la photo, sa vignette et le texte lu partiraient couchés. L'analyse
    // du scanner garde la rotation de l'écran, celle de l'aperçu et du réticule.
    DisposableEffect(capture) {
        val prise = capture ?: return@DisposableEffect onDispose {}
        val ecoute = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val cible = GeometrieImage.rotationCible(orientation) ?: return
                rotationPhysique[0] = cible
                if (prise.targetRotation != cible) prise.targetRotation = cible
            }
        }
        ecoute.enable()
        onDispose { ecoute.disable() }
    }

    // Clé Unit : l'effet ne joue qu'à la sortie de l'écran ; cameraProvider est lu à ce moment-là.
    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            executor.shutdown()
        }
    }

    Box(modifier.onSizeChanged { taille = it }) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}

/** La seule phrase montrée quand la caméra ne s'ouvre pas : la cause la plus courante et quoi faire. */
const val CAMERA_INDISPONIBLE = "La caméra ne s'ouvre pas\u00A0: une autre app l'utilise peut-être. Ferme-la, puis reviens ici."

/** Attente d'un ListenableFuture sans dépendance supplémentaire. */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        Executor { it.run() },
    )
}
