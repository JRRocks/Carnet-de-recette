package fr.jrrocks.carnetderecette.ui.fiche

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.scan.EtatPermission
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat
import fr.jrrocks.carnetderecette.ui.scanner.BoutonLampe
import fr.jrrocks.carnetderecette.ui.scanner.EnteteSection
import fr.jrrocks.carnetderecette.ui.scanner.LigneAttente
import fr.jrrocks.carnetderecette.ui.scanner.RangeeMenu
import fr.jrrocks.carnetderecette.ui.scanner.SelecteurPersonnes
import fr.jrrocks.carnetderecette.ui.scanner.ZoneCamera
import fr.jrrocks.carnetderecette.ui.scanner.poidsCamera
import fr.jrrocks.carnetderecette.ui.theme.chiffres

/** Les deux faces de la fiche papier. */
enum class Face(val nom: String) { RECTO("Recto"), VERSO("Verso") }

/** Une page déjà photographiée : combien de blocs de texte l'OCR y a vus, et sa vignette. */
@Immutable
data class PageVue(val blocs: Int, val vignette: ImageBitmap?)

/** Ce que l'écran de la fiche papier affiche, sans lien vers Android : de quoi le rendre en test. */
@Immutable
data class EtatFiche(
    val camera: EtatPermission = EtatPermission.EN_ATTENTE,
    val erreurCamera: String? = null,
    val lampe: Boolean = false,
    val lampeDisponible: Boolean = false,
    /** Le numéro vient de l'écran précédent (scanner) : il n'est pas à saisir. */
    val numeroDonne: Boolean = false,
    val numero: Int? = null,
    /** Le titre annoncé par le menu pour [numero], s'il est lu. */
    val titre: String? = null,
    /** Le numéro tapé au clavier, tel quel. */
    val saisie: String = "",
    val entreesMenu: List<EntreeMenu> = emptyList(),
    /** Les box déjà scannées : en tête de la liste du menu. */
    val scannees: Set<Int> = emptySet(),
    val menuOuvert: Boolean = false,
    val recto: PageVue? = null,
    val verso: PageVue? = null,
    /** La face en cours de prise ou de lecture. */
    val enCours: Face? = null,
    val personnes: Int? = null,
    val erreur: String? = null,
    val enregistrement: Boolean = false,
) {
    /** La prochaine face à photographier ; `null` quand les deux sont prises. */
    val prochaine: Face? get() = when {
        recto == null -> Face.RECTO
        verso == null -> Face.VERSO
        else -> null
    }

    /** La caméra est accordée et ouverte : on peut photographier. */
    val cameraPrete: Boolean get() = camera == EtatPermission.ACCORDEE && erreurCamera == null
}

/** Les gestes de l'écran de la fiche papier. */
@Immutable
data class ActionsFiche(
    val retour: () -> Unit = {},
    val lampe: () -> Unit = {},
    val demanderCamera: () -> Unit = {},
    val ouvrirReglages: () -> Unit = {},
    val photographier: () -> Unit = {},
    val reprendre: (Face) -> Unit = {},
    val saisir: (String) -> Unit = {},
    val ouvrirMenu: (Boolean) -> Unit = {},
    val choisir: (Int) -> Unit = {},
    val personnes: (Int?) -> Unit = {},
    val enregistrer: () -> Unit = {},
)

/**
 * La fiche recette papier, en secours (cadrage § 9) : on photographie le recto, puis le verso
 * si on veut, et l'app garde les photos avec le texte brut lu par l'OCR. Elle ne l'interprète
 * pas : Claude structure la recette le vendredi soir.
 *
 * Même partage que le scanner : la caméra en haut (image entière, pour cadrer la page), tout ce
 * qui se touche en bas. Sans état : [apercu] est la caméra dans l'app, un fond dans les tests.
 */
@Composable
fun FicheContenu(
    etat: EtatFiche,
    actions: ActionsFiche,
    modifier: Modifier = Modifier,
    apercu: @Composable () -> Unit,
) {
    EcranPousse(
        "Fiche papier",
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
                raison = "photographier la fiche",
                demander = actions.demanderCamera,
                ouvrirReglages = actions.ouvrirReglages,
                modifier = Modifier.fillMaxWidth().weight(poidsCamera()),
                apercu = apercu,
            ) {
                val face = etat.enCours
                if (face != null) {
                    Surface(
                        Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        LigneAttente("${face.nom}\u00A0: lecture du texte…", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = 8.dp),
                ) {
                    if (etat.menuOuvert) ListeDuMenu(etat, actions) else Renseignements(etat, actions)
                }
                Boutons(etat, actions)
            }
        }
    }
}

/** La box, les deux pages, le nombre de personnes, et ce que l'app en fera. */
@Composable
private fun Renseignements(etat: EtatFiche, actions: ActionsFiche) {
    val marges = Modifier.padding(horizontal = 16.dp)
    etat.erreur?.let { LigneEtat(R.drawable.ic_error, it, TonEtat.ERREUR, marges.padding(bottom = 12.dp)) }
    LaBox(etat, actions, marges)
    Spacer(Modifier.height(16.dp))
    EnteteSection("Pages", marges.padding(bottom = 4.dp))
    RangeePage(Face.RECTO, etat.recto, etat.enCours == Face.RECTO, photographiable = false, actions)
    HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
    RangeePage(
        Face.VERSO,
        etat.verso,
        etat.enCours == Face.VERSO,
        photographiable = etat.prochaine == Face.VERSO && etat.cameraPrete && etat.enCours == null && !etat.enregistrement,
        actions,
    )
    Spacer(Modifier.height(16.dp))
    EnteteSection("Personnes (facultatif)", marges.padding(bottom = 4.dp))
    SelecteurPersonnes(etat.personnes, actions.personnes, marges, aucune = true)
    Spacer(Modifier.height(16.dp))
    Text(
        "Claude lira la fiche le vendredi soir.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = marges,
    )
}

/** Le numéro de la box : donné par le scanner, ou choisi dans le menu, ou tapé ; facultatif. */
@Composable
private fun LaBox(etat: EtatFiche, actions: ActionsFiche, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        EnteteSection(if (etat.numeroDonne) "Box" else "Box (facultatif)", Modifier.padding(bottom = 4.dp))
        if (etat.numeroDonne && etat.numero != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(etat.numero.toString(), style = MaterialTheme.typography.headlineSmall.chiffres())
                Spacer(Modifier.width(12.dp))
                Text(etat.titre ?: "Recette du menu", style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = etat.saisie,
                onValueChange = actions.saisir,
                label = { Text("Numéro") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.chiffres(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(Modifier.width(12.dp))
            etat.titre?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        if (etat.entreesMenu.isNotEmpty()) {
            BoutonCarnet(
                "Choisir dans le menu",
                { actions.ouvrirMenu(true) },
                // Décalé de la marge interne du bouton texte : l'icône s'aligne sur la gouttière.
                Modifier.padding(top = 4.dp).offset(x = (-12).dp),
                sorte = SorteBouton.TEXTE,
                icone = R.drawable.ic_list_alt,
            )
        }
    }
}

/**
 * Une face de la fiche : sa vignette, où elle en est, et son geste sous la phrase (la phrase
 * garde la largeur, jusqu'à 200 %) : « Reprendre la photo » une fois prise ; « Photographier le
 * verso », facultatif, qui n'occupe donc pas le bas de l'écran.
 */
@Composable
private fun RangeePage(face: Face, page: PageVue?, enCours: Boolean, photographiable: Boolean, actions: ActionsFiche) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Vignette(page?.vignette)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(face.nom, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        enCours -> "Lecture du texte…"
                        page == null -> if (face == Face.RECTO) "À photographier" else "Facultatif, pas encore pris"
                        page.blocs == 0 -> "Aucun texte lu\u00A0: reprends la photo, plus près et bien à plat."
                        page.blocs == 1 -> "Photo prise, 1 bloc de texte lu"
                        else -> "Photo prise, ${page.blocs} blocs de texte lus"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Le bouton texte a sa propre marge : décalé, son libellé s'aligne sur la phrase.
            val geste = Modifier.offset(x = (-12).dp)
            when {
                page != null && !enCours ->
                    BoutonCarnet("Reprendre la photo", { actions.reprendre(face) }, geste, sorte = SorteBouton.TEXTE, icone = R.drawable.ic_replay)
                photographiable ->
                    BoutonCarnet("Photographier le verso", actions.photographier, geste, sorte = SorteBouton.TEXTE, icone = R.drawable.ic_photo_camera)
            }
        }
    }
}

/** La vignette d'une page (56 dp), ou sa place vide. */
@Composable
private fun Vignette(image: ImageBitmap?) {
    val forme = MaterialTheme.shapes.small
    Box(
        Modifier.size(56.dp).clip(forme).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_receipt_long), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Une seule action en bas, à portée du pouce, celle du moment : photographier le recto, puis
 * enregistrer la fiche. Pendant une lecture ou un enregistrement, elle dit ce qui se passe et
 * ne fait rien.
 */
@Composable
private fun Boutons(etat: EtatFiche, actions: ActionsFiche) {
    val occupe = etat.enCours != null || etat.enregistrement
    val modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 64.dp)
    when {
        etat.recto != null -> BoutonCarnet(
            when {
                etat.enregistrement -> "Enregistrement…"
                etat.enCours != null -> "Lecture du texte…"
                else -> "Enregistrer la fiche"
            },
            { if (!occupe) actions.enregistrer() },
            modifier,
            icone = R.drawable.ic_check,
        )
        etat.cameraPrete -> BoutonCarnet(
            if (etat.enCours != null) "Lecture du texte…" else "Photographier le recto",
            { if (!occupe) actions.photographier() },
            modifier,
            icone = R.drawable.ic_photo_camera,
        )
    }
}

/** Le menu de la semaine, les box déjà scannées en tête : un toucher donne le numéro. */
@Composable
private fun ListeDuMenu(etat: EtatFiche, actions: ActionsFiche) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        EnteteSection("Choisis la recette de la fiche", Modifier.weight(1f))
        IconButton(onClick = { actions.ouvrirMenu(false) }, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Fermer le menu")
        }
    }
    val (scannees, autres) = etat.entreesMenu.partition { it.numero in etat.scannees }
    (scannees + autres).forEachIndexed { i, entree ->
        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        RangeeMenu(entree, entree.numero in etat.scannees) { actions.choisir(entree.numero) }
    }
}
