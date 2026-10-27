package fr.jrrocks.carnetderecette.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.scan.DessinReticule
import fr.jrrocks.carnetderecette.scan.EtatPermission
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat
import fr.jrrocks.carnetderecette.ui.theme.chiffres

/** La lecture du menu de la semaine : sans lui, aucune étiquette ne se reconnaît. */
sealed interface EtatMenu {
    data object Lecture : EtatMenu
    data object Lu : EtatMenu

    /** Pas de réseau : rien n'est cassé, il faudra réessayer. */
    data object HorsLigne : EtatMenu

    /** La sonde ne trouve plus ce qu'il faut sur le site : mode dégradé (cadrage § 9). */
    data object AccesRompu : EtatMenu

    /** Lu sur le site, mais le téléphone n'a pas pu l'écrire (plus de place) : il faudra réessayer. */
    data object NonEnregistre : EtatMenu
}

/** Où en est la page de la recette d'une box. */
enum class EtatPage { EN_LECTURE, LUE, HORS_LIGNE, A_LIRE }

/** Une box telle que l'écran la montre. */
@Immutable
data class BoxVue(
    val numero: Int,
    /** Le titre annoncé par le menu ; `null` si le menu n'est pas lu. */
    val titre: String?,
    val personnes: Int,
    val aConfirmer: Boolean,
    val page: EtatPage,
)

/** Ce que l'écran du scanner affiche, sans lien vers Android : de quoi le rendre en test. */
@Immutable
data class EtatScanner(
    val camera: EtatPermission = EtatPermission.EN_ATTENTE,
    /** La caméra est accordée mais ne s'ouvre pas : la phrase à montrer. */
    val erreurCamera: String? = null,
    val lampe: Boolean = false,
    val lampeDisponible: Boolean = false,
    /** L'avancement du consensus, de 0 à 1 : le contour du réticule se remplit d'autant. */
    val progression: Float = 0f,
    val menu: EtatMenu = EtatMenu.Lecture,
    /** Le dernier résultat (ou la box touchée dans la liste). */
    val courante: BoxVue? = null,
    /** La dernière box lue l'était déjà cette semaine : rien n'a été réécrit. */
    val doublon: Boolean = false,
    val boxes: List<BoxVue> = emptyList(),
    val entreesMenu: List<EntreeMenu> = emptyList(),
    /** La liste du menu remplace le bas de l'écran (étiquette illisible). */
    val menuOuvert: Boolean = false,
    /** Une box, une correction ou un retrait n'a pas pu être écrit sur le téléphone : la phrase à montrer. */
    val erreur: String? = null,
)

/** Les gestes de l'écran du scanner. */
@Immutable
data class ActionsScanner(
    val retour: () -> Unit = {},
    val lampe: () -> Unit = {},
    val demanderCamera: () -> Unit = {},
    val ouvrirReglages: () -> Unit = {},
    val relireMenu: () -> Unit = {},
    val ouvrirMenu: (Boolean) -> Unit = {},
    /** Une recette touchée dans le menu : la box est ajoutée (origine « menu »). */
    val choisir: (Int) -> Unit = {},
    val selectionner: (Int) -> Unit = {},
    val personnes: (numero: Int, personnes: Int) -> Unit = { _, _ -> },
    val retirer: (Int) -> Unit = {},
    val relirePage: (Int) -> Unit = {},
    /**
     * Ouvre la fiche papier, pour la box donnée si on la connaît. Les recours l'ouvrent sans
     * numéro : la box qu'on tient n'est pas forcément la dernière lue.
     */
    val photographierFiche: (Int?) -> Unit = {},
)

/**
 * Le scanner des box du vendredi (direction, écran « Scanner ») : en moitié haute, l'aperçu de
 * la caméra bord à bord avec son réticule ; en moitié basse, à portée du pouce, le dernier
 * résultat en grand, les box déjà scannées, les recours (menu, fiche papier) et « Terminer ».
 *
 * Sans état : [apercu] est la caméra dans l'app, un simple fond dans les tests de capture.
 */
@Composable
fun ScannerContenu(
    etat: EtatScanner,
    actions: ActionsScanner,
    modifier: Modifier = Modifier,
    apercu: @Composable () -> Unit,
) {
    EcranPousse(
        "Scanner les box",
        actions.retour,
        modifier,
        actions = {
            if (etat.camera == EtatPermission.ACCORDEE && etat.lampeDisponible) BoutonLampe(etat.lampe, actions.lampe)
        },
    ) { marges ->
        Column(Modifier.fillMaxSize().padding(marges)) {
            ZoneCamera(
                camera = etat.camera,
                erreur = etat.erreurCamera,
                raison = "lire les étiquettes",
                demander = actions.demanderCamera,
                ouvrirReglages = actions.ouvrirReglages,
                complement = "Tu peux encore choisir les box dans le menu, plus bas.",
                modifier = Modifier.fillMaxWidth().weight(poidsCamera()),
                apercu = apercu,
            ) {
                DessinReticule(etat.progression, Modifier.fillMaxSize())
            }
            Column(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    if (etat.menuOuvert) ListeDuMenu(etat, actions) else Resultats(etat, actions)
                }
                BoutonCarnet(
                    "Terminer",
                    actions.retour,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 64.dp),
                    icone = R.drawable.ic_check,
                )
            }
        }
    }
}

/** Le bas de l'écran : l'état du menu, le dernier résultat, les box déjà scannées, les recours. */
@Composable
private fun Resultats(etat: EtatScanner, actions: ActionsScanner) {
    val marges = Modifier.padding(horizontal = 16.dp)
    Spacer(Modifier.height(12.dp))
    EtatDuMenu(etat.menu, actions, marges.padding(bottom = 12.dp))
    etat.erreur?.let { LigneEtat(R.drawable.ic_error, it, TonEtat.ERREUR, marges.padding(bottom = 12.dp)) }
    val courante = etat.courante
    // Annoncée à TalkBack à chaque nouvelle box : on ne regarde pas forcément l'écran. La zone
    // reste en place et seul son contenu change : une zone qui apparaît ne s'annonce pas toujours.
    Box(marges.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        if (courante != null) {
            AnnonceResultat(courante, etat.doublon, actions)
        } else if (etat.menu == EtatMenu.Lu && etat.camera == EtatPermission.ACCORDEE && etat.erreurCamera == null) {
            Text(
                "Vise l'étiquette d'une box\u00A0: le numéro et le nombre de personnes se lisent tout seuls.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (courante != null) PersonnesResultat(courante, actions, marges.padding(top = 8.dp))
    Recours(etat, actions, Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    if (etat.boxes.isNotEmpty()) {
        EnteteSection("Déjà scannées\u00A0: ${etat.boxes.size}", marges.padding(top = 8.dp, bottom = 4.dp))
        etat.boxes.forEachIndexed { i, box ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            RangeeBox(box, box.numero == etat.courante?.numero, actions)
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Le menu de la semaine : en cours de lecture, hors ligne ou accès rompu ; rien quand il est lu. */
@Composable
private fun EtatDuMenu(menu: EtatMenu, actions: ActionsScanner, modifier: Modifier) {
    when (menu) {
        EtatMenu.Lu -> Unit
        EtatMenu.Lecture -> LigneAttente("Lecture du menu de la semaine…", modifier.padding(vertical = 4.dp))
        EtatMenu.HorsLigne -> LigneEtat(
            R.drawable.ic_wifi_off,
            "Pas de réseau\u00A0: le menu de la semaine n'est pas lu.",
            TonEtat.NEUTRE,
            modifier,
        ) {
            BoutonCarnet("Réessayer", actions.relireMenu, sorte = SorteBouton.CONTOUR, icone = R.drawable.ic_refresh)
        }
        EtatMenu.NonEnregistre -> LigneEtat(
            R.drawable.ic_error,
            "Le menu de la semaine n'a pas pu être enregistré\u00A0: le téléphone manque peut-être de place. Libère-en, puis réessaie.",
            TonEtat.ERREUR,
            modifier,
        ) {
            BoutonCarnet("Réessayer", actions.relireMenu, sorte = SorteBouton.CONTOUR, icone = R.drawable.ic_refresh)
        }
        EtatMenu.AccesRompu -> LigneEtat(
            R.drawable.ic_error,
            "Accès HelloFresh rompu\u00A0: passe en mode dégradé (photo de la fiche).",
            TonEtat.ERREUR,
            modifier,
        ) {
            BoutonCarnet("Réessayer", actions.relireMenu, sorte = SorteBouton.TEXTE)
            BoutonCarnet("Photographier la fiche", { actions.photographierFiche(null) }, icone = R.drawable.ic_photo_camera)
        }
    }
}

/**
 * Le dernier résultat, en grand : « 207 », le titre de la recette, ce qui se passe pour sa
 * page. Posé dans la zone annoncée de [Resultats].
 */
@Composable
private fun AnnonceResultat(box: BoxVue, doublon: Boolean, actions: ActionsScanner) {
    // Sous le titre, ce qui se passe pour cette box : une rangée de moins dans le bas de l'écran.
    val details = @Composable { mod: Modifier ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                box.titre ?: "Recette du menu de la semaine",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (doublon) Indication(R.drawable.ic_check_circle, "Déjà scannée cette semaine.")
            EtatDeLaPage(box, actions)
        }
    }
    val numero = @Composable { Text(box.numero.toString(), style = MaterialTheme.typography.displaySmall.chiffres()) }
    if (LocalDensity.current.fontScale >= 1.5f) {
        // À grande police, le numéro passe au-dessus : le titre et l'état gardent toute la largeur.
        Column {
            numero()
            details(Modifier.fillMaxWidth())
        }
    } else {
        Row(verticalAlignment = Alignment.Top) {
            numero()
            Spacer(Modifier.width(16.dp))
            details(Modifier.weight(1f).padding(top = 4.dp))
        }
    }
}

/** Le nombre de personnes du dernier résultat, corrigé d'un geste, et son doute éventuel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonnesResultat(box: BoxVue, actions: ActionsScanner, modifier: Modifier) {
    // À grande police, « à confirmer » passe sous le sélecteur plutôt que de le serrer.
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SelecteurPersonnes(
            box.personnes,
            { it?.let { n -> actions.personnes(box.numero, n) } },
            Modifier.align(Alignment.CenterVertically),
        )
        if (box.aConfirmer) Indication(R.drawable.ic_help, "à confirmer", Modifier.align(Alignment.CenterVertically))
    }
}

/** La page de la recette de la box : en cours de lecture, lue, ou à relire. */
@Composable
private fun EtatDeLaPage(box: BoxVue, actions: ActionsScanner) {
    when (box.page) {
        EtatPage.EN_LECTURE -> LigneAttente("Lecture de la recette…")
        EtatPage.LUE -> Indication(R.drawable.ic_check_circle, "Recette lue")
        EtatPage.HORS_LIGNE, EtatPage.A_LIRE -> Column {
            Indication(
                if (box.page == EtatPage.HORS_LIGNE) R.drawable.ic_wifi_off else R.drawable.ic_info,
                if (box.page == EtatPage.HORS_LIGNE) "Pas de réseau\u00A0: recette pas encore lue." else "Recette pas encore lue.",
            )
            BoutonCarnet("Réessayer", { actions.relirePage(box.numero) }, sorte = SorteBouton.TEXTE, icone = R.drawable.ic_refresh)
        }
    }
}

/** Une icône et une courte phrase en encre douce : un état, jamais porté par la couleur seule. */
@Composable
private fun Indication(icone: Int, texte: String, modifier: Modifier = Modifier) {
    // L'icône reste en face de la première ligne quand la phrase en prend plusieurs.
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(icone),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Les recours quand l'étiquette ne se lit pas : choisir dans le menu, photographier la fiche. */
@Composable
private fun Recours(etat: EtatScanner, actions: ActionsScanner, modifier: Modifier) {
    Column(modifier) {
        if (etat.entreesMenu.isNotEmpty()) {
            BoutonCarnet(
                "Pas reconnue\u00A0? Choisis dans le menu",
                { actions.ouvrirMenu(true) },
                sorte = SorteBouton.TEXTE,
                icone = R.drawable.ic_list_alt,
            )
        }
        // Accès rompu : la ligne d'erreur porte déjà ce geste, en bouton plein. Sans numéro :
        // la box qu'on tient, illisible, n'est pas forcément la dernière lue ; la fiche
        // propose le menu, box déjà scannées en tête.
        if (etat.menu != EtatMenu.AccesRompu) {
            BoutonCarnet(
                "Photographier la fiche",
                { actions.photographierFiche(null) },
                sorte = SorteBouton.TEXTE,
                icone = R.drawable.ic_photo_camera,
            )
        }
    }
}

/** Une box déjà scannée, en rangée compacte : numéro, titre, personnes, retirer. Un toucher la reprend en grand. */
@Composable
private fun RangeeBox(box: BoxVue, courante: Boolean, actions: ActionsScanner) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (courante) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
            .clickable { actions.selectionner(box.numero) }
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(box.numero.toString(), style = MaterialTheme.typography.titleMedium.chiffres(), modifier = Modifier.widthIn(min = 48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(box.titre ?: "Recette du menu", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (box.aConfirmer) {
                Text("à confirmer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("${box.personnes}\u00A0pers.", style = MaterialTheme.typography.bodyMedium.chiffres())
        IconButton(onClick = { actions.retirer(box.numero) }, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = "Retirer la box ${box.numero}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Le menu de la semaine, à la place des résultats : un toucher ajoute la box. Les box déjà scannées sont marquées. */
@Composable
private fun ListeDuMenu(etat: EtatScanner, actions: ActionsScanner) {
    val scannees = etat.boxes.map { it.numero }.toSet()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        EnteteSection("Choisis la recette de la box", Modifier.weight(1f))
        IconButton(onClick = { actions.ouvrirMenu(false) }, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Fermer le menu")
        }
    }
    etat.entreesMenu.forEachIndexed { i, entree ->
        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        RangeeMenu(entree, entree.numero in scannees) { actions.choisir(entree.numero) }
    }
}
