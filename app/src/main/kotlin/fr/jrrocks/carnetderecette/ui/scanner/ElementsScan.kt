package fr.jrrocks.carnetderecette.ui.scanner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.scan.EtatPermission
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.theme.chiffres

/*
 * Ce que le scanner des box et la fiche papier partagent : la zone de la caméra et ce qui
 * l'empêche, le choix du nombre de personnes, une recette du menu, un en-tête de section.
 */

/** Les bornes du nombre de personnes : celles que propose HelloFresh. */
internal const val PERSONNES_MIN = 1
internal const val PERSONNES_MAX = 6

/**
 * La part de la hauteur donnée à la caméra, face au bas de l'écran (poids 1) : un peu moins de
 * la moitié, pour que le dernier résultat et le début de la liste tiennent sous le pouce ; moins
 * encore à grande police, où le texte a besoin de la place (l'étiquette se vise aussi bien).
 */
@Composable
internal fun poidsCamera(): Float = if (LocalDensity.current.fontScale >= 1.5f) 0.6f else 0.85f

/**
 * La zone de la caméra, bord à bord : l'aperçu en direct quand la caméra est accordée et
 * ouverte, sinon ce qui l'empêche, en clair, avec le geste qui débloque. [raison] complète
 * « pour… » (« lire les étiquettes »). [complement] dit ce qui reste possible sans caméra.
 */
@Composable
internal fun ZoneCamera(
    camera: EtatPermission,
    erreur: String?,
    raison: String,
    demander: () -> Unit,
    ouvrirReglages: () -> Unit,
    modifier: Modifier = Modifier,
    complement: String? = null,
    apercu: @Composable () -> Unit,
    dessus: @Composable BoxScope.() -> Unit = {},
) {
    when {
        camera == EtatPermission.ACCORDEE && erreur == null -> Box(modifier.background(Color.Black)) {
            apercu()
            dessus()
        }
        camera == EtatPermission.ACCORDEE -> PanneauCamera(R.drawable.ic_error, erreur.orEmpty(), modifier)
        camera == EtatPermission.EN_ATTENTE -> PanneauCamera(R.drawable.ic_photo_camera, "Autorise la caméra pour $raison.", modifier) {
            BoutonCarnet("Autoriser la caméra", demander, icone = R.drawable.ic_photo_camera)
        }
        else -> PanneauCamera(
            R.drawable.ic_photo_camera,
            "Caméra refusée\u00A0: sans elle, l'app ne peut pas $raison. Autorise-la dans les réglages de l'app, rubrique Autorisations." +
                (complement?.let { " $it" } ?: ""),
            modifier,
        ) {
            BoutonCarnet("Ouvrir les réglages", ouvrirReglages, icone = R.drawable.ic_settings)
        }
    }
}

/** À la place de l'aperçu : une icône, la phrase qui dit la cause et quoi faire, le geste. */
@Composable
private fun PanneauCamera(@DrawableRes icone: Int, texte: String, modifier: Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Icon(painterResource(icone), contentDescription = null, modifier = Modifier.padding(top = 1.dp).size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(texte, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (action != null) Box(Modifier.padding(start = 36.dp)) { action() }
    }
}

/**
 * Le nombre de personnes, corrigé d'un geste : − et + de 48 dp, la valeur au milieu, 12 dp
 * d'écart. [aucune] permet de revenir à « non précisé » (fiche papier) ; `null` s'affiche « — ».
 */
@Composable
internal fun SelecteurPersonnes(
    valeur: Int?,
    surChangement: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    aucune: Boolean = false,
    actif: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BoutonPas(
            R.drawable.ic_remove,
            "Une personne de moins",
            actif && valeur != null && (valeur > PERSONNES_MIN || aucune),
        ) { if (valeur != null) surChangement(if (valeur > PERSONNES_MIN) valeur - 1 else null) }
        Text(
            if (valeur == null) "—" else "$valeur\u00A0pers.",
            style = MaterialTheme.typography.titleMedium.chiffres(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 72.dp)
                .clearAndSetSemantics { contentDescription = if (valeur == null) "Nombre de personnes non précisé" else "$valeur personnes" },
        )
        BoutonPas(R.drawable.ic_add, "Une personne de plus", actif && (valeur ?: 0) < PERSONNES_MAX) {
            surChangement((valeur ?: 0) + 1)
        }
    }
}

@Composable
private fun BoutonPas(@DrawableRes icone: Int, description: String, actif: Boolean, onClick: () -> Unit) {
    // Coins de plaque (8 dp) : le cercle est réservé aux marqueurs (DESIGN.md, Shapes).
    OutlinedIconButton(onClick = onClick, enabled = actif, shape = MaterialTheme.shapes.medium, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icone), contentDescription = description)
    }
}

/** Une recette du menu de la semaine : un toucher la choisit. */
@Composable
internal fun RangeeMenu(entree: EntreeMenu, dejaScannee: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entree.numero.toString(),
            style = MaterialTheme.typography.titleMedium.chiffres(),
            modifier = Modifier.widthIn(min = 48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            entree.titre,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (dejaScannee) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_check), contentDescription = "Déjà scannée", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Un en-tête de section : title 16 sp en bleu émail, lu comme un titre par TalkBack. */
@Composable
internal fun EnteteSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

/** Une attente en cours : un petit indicateur et sa phrase. */
@Composable
internal fun LigneAttente(texte: String, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** La lampe, dans la barre du haut (48 dp). */
@Composable
internal fun BoutonLampe(allumee: Boolean, basculer: () -> Unit) {
    IconButton(onClick = basculer, modifier = Modifier.size(48.dp)) {
        Icon(
            painterResource(if (allumee) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off),
            contentDescription = if (allumee) "Éteindre la lampe" else "Allumer la lampe",
        )
    }
}
