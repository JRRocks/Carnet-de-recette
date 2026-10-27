package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.donnees.EtatSemaine
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.EtatVide
import fr.jrrocks.carnetderecette.ui.composants.ImageLocale
import fr.jrrocks.carnetderecette.ui.composants.imageDuSite
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.theme.chiffres

/**
 * Le carnet : les recettes de la semaine, une rangée par box scannée (pas de cartes), puis les
 * fiches papier photographiées. Le catalogue de toutes les semaines viendra avec l'import du
 * samedi (lot 4).
 */
@Composable
fun EcranCarnet(marges: PaddingValues, semaine: EtatSemaine?, ouvrir: (Destination) -> Unit = {}) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges)) {
        if (semaine == null || (semaine.boxes.isEmpty() && semaine.fichier.fiches.isEmpty())) {
            EtatVide(
                titre = "Les recettes arrivent au scan des box",
                texte = "Le vendredi, scanne chaque box\u00A0: sa recette s'ajoute ici, avec ses images, ses ingrédients " +
                    "et ses étapes. Le samedi, «\u00A0Importer la semaine\u00A0» y ajoutera le travail de Claude.",
                mention = "L'import du samedi\u00A0: pas encore dans l'app.",
            )
            semaine?.let { s ->
                BoutonCarnet(
                    "Scanner les box",
                    { ouvrir(Destination.Scanner(s.semaine)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    sorte = SorteBouton.CONTOUR,
                    icone = R.drawable.ic_document_scanner,
                )
            }
            return@Column
        }
        EnTeteSection("Semaine ${semaine.semaine.numero}")
        semaine.boxes.sortedBy { it.numero }.forEach { box ->
            RangeeRecette(semaine, box) { ouvrir(Destination.Recette(semaine.semaine, box.numero)) }
            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (semaine.fichier.fiches.isNotEmpty()) {
            EnTeteSection("Fiches papier")
            semaine.fichier.fiches.forEach { fiche ->
                val titre = fiche.numero?.let { n -> semaine.menu?.entree(n)?.let { "$n · ${it.titre}" } ?: "Box $n" } ?: "Fiche sans numéro"
                Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(titre, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (fiche.pages.size > 1) "${fiche.pages.size} photos" else "1 photo") + " · Claude la lira le vendredi soir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun EnTeteSection(titre: String) {
    Text(
        titre,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Une recette de la semaine : numéro en chiffres tabulaires, titre, personnes, vignette du plat. */
@Composable
private fun RangeeRecette(semaine: EtatSemaine, box: BoxScannee, ouvrir: () -> Unit) {
    val couleurs = MaterialTheme.colorScheme
    val recette = semaine.recette(box.numero)
    val entree = semaine.menu?.entree(box.numero)
    val titre = recette?.titre ?: entree?.titre ?: "Recette ${box.numero}"
    val etatPage = when {
        recette != null -> null
        box.numero in semaine.enLecture -> "lecture de la page…"
        entree == null -> "hors du menu de la semaine"
        else -> "page pas encore lue"
    }
    val details = listOfNotNull(
        "${box.personnes}\u00A0pers.",
        "à confirmer".takeIf { box.aConfirmer },
        etatPage,
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = "Ouvrir la recette", onClick = ouvrir)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${box.numero}", style = MaterialTheme.typography.titleLarge.chiffres(), modifier = Modifier.widthIn(min = 56.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = couleurs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        val image = recette?.image ?: entree?.image
        ImageLocale(
            fichier = imageDuSite(image),
            description = null,
            modifier = Modifier.size(56.dp),
            forme = MaterialTheme.shapes.medium,
            taillePx = 240,
        )
    }
}
