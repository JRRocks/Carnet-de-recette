package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.maj.MiseAJour

/**
 * La proposition de mise à jour, au lancement. [progression] non nulle : téléchargement en
 * cours, les boutons disparaissent. [message] dit ce qui bloque (autorisation d'installer,
 * réseau).
 */
@Composable
fun DialogueMiseAJour(
    publication: MiseAJour.Publication,
    progression: Float?,
    message: String?,
    onMettreAJour: () -> Unit,
    onPlusTard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (progression == null) onPlusTard() },
        title = { Text("Nouvelle version") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("La version ${publication.versionNom} est disponible.")
                publication.notes.takeIf { it.isNotBlank() }?.let { Text(it) }
                progression?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            if (progression == null) TextButton(onClick = onMettreAJour) { Text("Mettre à jour") }
        },
        dismissButton = {
            if (progression == null) TextButton(onClick = onPlusTard) { Text("Plus tard") }
        },
    )
}
