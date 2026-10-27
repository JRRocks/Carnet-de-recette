package fr.jrrocks.carnetderecette.ui.recette

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.core.recette.EntreeMenu
import fr.jrrocks.carnetderecette.core.recette.Ingredient
import fr.jrrocks.carnetderecette.core.recette.Quantites
import fr.jrrocks.carnetderecette.core.recette.Recette
import fr.jrrocks.carnetderecette.core.semaine.BoxScannee
import fr.jrrocks.carnetderecette.donnees.DepotLocal
import fr.jrrocks.carnetderecette.donnees.ResultatRecette
import fr.jrrocks.carnetderecette.ui.Destination
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet
import fr.jrrocks.carnetderecette.ui.composants.EcranPousse
import fr.jrrocks.carnetderecette.ui.composants.Etape
import fr.jrrocks.carnetderecette.ui.composants.EtatEtape
import fr.jrrocks.carnetderecette.ui.composants.ImageLocale
import fr.jrrocks.carnetderecette.ui.composants.LigneDeTemps
import fr.jrrocks.carnetderecette.ui.composants.LigneEtat
import fr.jrrocks.carnetderecette.ui.composants.SorteBouton
import fr.jrrocks.carnetderecette.ui.composants.TonEtat
import fr.jrrocks.carnetderecette.ui.composants.imageDuSite
import fr.jrrocks.carnetderecette.ui.theme.Carnet
import fr.jrrocks.carnetderecette.ui.theme.chiffres
import kotlinx.coroutines.launch

/** La fiche d'une recette de la semaine, telle que lue sur la page publique du site. */
@Composable
fun EcranRecette(destination: Destination.Recette, onRetour: () -> Unit, ouvrir: (Destination) -> Unit) {
    val depot = DepotLocal.de(LocalContext.current)
    val etat by depot.etat(destination.semaine).collectAsState()
    val portee = rememberCoroutineScope()
    var echec by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    RecetteContenu(
        numero = destination.numero,
        box = etat.fichier.box(destination.numero),
        recette = etat.recette(destination.numero),
        entree = etat.menu?.entree(destination.numero),
        enLecture = destination.numero in etat.enLecture,
        echec = echec,
        onRetour = onRetour,
        relire = {
            portee.launch {
                echec = when (val r = depot.relireRecette(destination.semaine, destination.numero)) {
                    is ResultatRecette.Lue -> null
                    ResultatRecette.HorsMenu -> "La recette ${destination.numero} n'est pas dans le menu de la semaine."
                    is ResultatRecette.HorsLigne -> r.motif
                    is ResultatRecette.AccesRompu -> r.motif
                }
            }
        },
        lireAVoixHaute = { ouvrir(Destination.Seance(destination.semaine, destination.numero)) },
    )
}

/** La fiche, sans état : de quoi la rendre en capture. */
@Composable
fun RecetteContenu(
    numero: Int,
    box: BoxScannee?,
    recette: Recette?,
    entree: EntreeMenu?,
    enLecture: Boolean,
    echec: String?,
    onRetour: () -> Unit,
    relire: () -> Unit,
    lireAVoixHaute: () -> Unit,
) {
    val titre = recette?.titre ?: entree?.titre ?: "Recette $numero"
    EcranPousse(
        titre = "$numero · $titre",
        onRetour = onRetour,
        boutonFlottant = {
            if (recette != null && recette.etapes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Lire à voix haute", style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null) },
                    onClick = lireAVoixHaute,
                    modifier = Modifier.heightIn(min = 64.dp),
                    shape = MaterialTheme.shapes.large,
                    containerColor = Carnet.couleurs.action,
                    contentColor = Carnet.couleurs.surAction,
                )
            }
        },
    ) { marges ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges).padding(bottom = 96.dp)) {
            if (recette == null) {
                PageAbsente(entree, enLecture, echec, relire)
            } else {
                FicheRecette(recette, box)
            }
        }
    }
}

@Composable
private fun PageAbsente(entree: EntreeMenu?, enLecture: Boolean, echec: String?, relire: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entree?.let {
            Text(it.titre, style = MaterialTheme.typography.headlineSmall)
            it.sousTitre?.let { s -> Text(s, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            enLecture -> {
                Text("Lecture de la page de la recette…", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            entree == null -> LigneEtat(R.drawable.ic_help, "Ce numéro n'est pas dans le menu de la semaine.", TonEtat.INFO)
            else -> LigneEtat(R.drawable.ic_wifi_off, echec ?: "La page de la recette n'est pas encore lue.", TonEtat.NEUTRE) {
                BoutonCarnet("Réessayer", relire, sorte = SorteBouton.CONTOUR, icone = R.drawable.ic_refresh)
            }
        }
    }
}

@Composable
private fun FicheRecette(recette: Recette, box: BoxScannee?) {
    val couleurs = MaterialTheme.colorScheme
    val typo = MaterialTheme.typography
    val possibles = recette.personnesPossibles
    val depart = box?.personnes?.takeIf { it in possibles } ?: possibles.firstOrNull { it >= 2 } ?: possibles.firstOrNull() ?: 2
    var personnes by rememberSaveable(recette.id) { mutableIntStateOf(depart) }

    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(recette.titre, style = typo.headlineSmall, modifier = Modifier.semantics { heading() })
        recette.sousTitre?.let { Text(it, style = typo.bodyLarge, color = couleurs.onSurfaceVariant) }
        val infos = listOfNotNull(
            recette.dureeTotaleMin?.let { "$it\u00A0min" },
            when (recette.difficulte) { 1 -> "facile"; 2 -> "moyenne"; 3 -> "difficile"; else -> null },
            "à confirmer".takeIf { box?.aConfirmer == true },
        )
        if (infos.isNotEmpty()) Text(infos.joinToString(" · "), style = typo.bodyMedium.copy(fontFeatureSettings = "tnum"), color = couleurs.onSurfaceVariant)
    }
    ImageLocale(
        fichier = imageDuSite(recette.image),
        description = "Photo du plat\u00A0: ${recette.titre}",
        modifier = Modifier.padding(16.dp).fillMaxWidth().aspectRatio(16f / 10f),
    )

    if (possibles.size > 1) {
        ChoixPersonnes(personnes, possibles) { personnes = it }
    }

    val portion = recette.portion(personnes)
    val (livres, chezToi) = recette.ingredients.partition { it.livre }
    Section("Ingrédients")
    livres.forEach { RangeeIngredient(it, portion?.quantites?.firstOrNull { q -> q.ingredient == it.id }?.let { q -> Quantites.formater(q.montant, q.unite) }) }
    if (chezToi.isNotEmpty()) {
        Section("À avoir chez toi")
        chezToi.forEach { RangeeIngredient(it, portion?.quantites?.firstOrNull { q -> q.ingredient == it.id }?.let { q -> Quantites.formater(q.montant, q.unite) }) }
    }
    if (recette.ustensiles.isNotEmpty()) {
        Section("Ustensiles")
        Text(recette.ustensiles.joinToString(", "), style = typo.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
    }

    Section("Étapes")
    LigneDeTemps(recette.etapes.map { Etape("Étape ${it.numero}", EtatEtape.A_FAIRE) }) { etape ->
        val source = recette.etapes.firstOrNull { "Étape ${it.numero}" == etape.titre } ?: return@LigneDeTemps
        Text(source.texte, style = typo.bodyLarge, modifier = Modifier.padding(top = 4.dp))
        source.images.firstOrNull()?.let { image ->
            ImageLocale(
                fichier = imageDuSite(image.chemin),
                description = image.legende,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(16f / 10f),
                taillePx = 800,
            )
        }
    }

    if (recette.allergenes.isNotEmpty()) {
        Text(
            "Allergènes\u00A0: ${recette.allergenes.joinToString(", ")}.",
            style = typo.bodyMedium,
            color = couleurs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    Text(
        "Recette lue sur hellofresh.fr, pour ton usage privé.",
        style = typo.bodyMedium,
        color = couleurs.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Section(titre: String) {
    Text(
        titre,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Pour combien de personnes : − et + de 48 dp, bornés aux portions que la page donne. */
@Composable
private fun ChoixPersonnes(personnes: Int, possibles: List<Int>, choisir: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val rang = possibles.indexOf(personnes)
        FilledTonalIconButton(onClick = { possibles.getOrNull(rang - 1)?.let(choisir) }, enabled = rang > 0, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = "Une personne de moins")
        }
        Text(
            "$personnes\u00A0personnes",
            style = MaterialTheme.typography.titleMedium.chiffres(),
            modifier = Modifier.padding(horizontal = 12.dp).widthIn(min = 120.dp),
        )
        FilledTonalIconButton(onClick = { possibles.getOrNull(rang + 1)?.let(choisir) }, enabled = rang < possibles.lastIndex, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = "Une personne de plus")
        }
    }
}

/** Une ligne d'ingrédient : la quantité dans une colonne fixe (lisible sans lire la phrase), puis le nom. */
@Composable
private fun RangeeIngredient(ingredient: Ingredient, quantite: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ImageLocale(
            fichier = imageDuSite(ingredient.image),
            description = null,
            modifier = Modifier.size(40.dp),
            forme = MaterialTheme.shapes.medium,
            taillePx = 200,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            quantite?.takeIf { it.isNotEmpty() } ?: "—",
            style = MaterialTheme.typography.bodyLarge.chiffres(),
            // Colonne bornée : une quantité longue (« selon ton goût ») passe à la ligne au lieu de toucher le nom.
            modifier = Modifier.widthIn(min = 96.dp, max = 128.dp).padding(end = 12.dp),
        )
        Text(ingredient.nom, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
