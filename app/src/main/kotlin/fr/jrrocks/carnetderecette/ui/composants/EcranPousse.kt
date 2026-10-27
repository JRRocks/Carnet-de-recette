package fr.jrrocks.carnetderecette.ui.composants

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.ui.theme.Carnet

/**
 * Un écran poussé par-dessus les onglets (scanner, fiche, recette, séance) : la barre bleu nuit
 * avec la flèche de retour, sans barre de navigation. Le retour système dépile aussi l'écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranPousse(
    titre: String,
    onRetour: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    boutonFlottant: @Composable () -> Unit = {},
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    contenu: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        titre,
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Retour")
                    }
                },
                actions = actions,
                windowInsets = WindowInsets.safeDrawing,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Carnet.couleurs.barre,
                    titleContentColor = Carnet.couleurs.surBarre,
                    navigationIconContentColor = Carnet.couleurs.surBarre,
                    actionIconContentColor = Carnet.couleurs.surBarre,
                ),
            )
        },
        floatingActionButton = boutonFlottant,
        snackbarHost = { SnackbarHost(snackbars) },
        content = contenu,
    )
}
