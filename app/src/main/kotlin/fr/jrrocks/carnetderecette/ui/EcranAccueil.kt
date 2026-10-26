package fr.jrrocks.carnetderecette.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.drive.DriveCarnet

/** Où en est le branchement au Google Drive du foyer. */
sealed interface EtatDrive {
    /** Jamais branché sur ce téléphone, ou autorisation retirée. */
    data object NonConnecte : EtatDrive

    /** Autorisation ou recherche du dossier en cours. */
    data object EnCours : EtatDrive

    /**
     * Le compte [compte] est branché et le dossier de l'app existe. [horsLigne] : rien n'a pu
     * être vérifié faute de réseau, mais le branchement mémorisé tient toujours.
     */
    data class Pret(val compte: String, val horsLigne: Boolean = false) : EtatDrive

    /** [motif] dit pourquoi ça n'a pas abouti ; on peut réessayer. */
    data class Erreur(val motif: String) : EtatDrive
}

/**
 * L'accueil du lot 1 : le branchement au Drive, et la semaine telle qu'elle se déroulera.
 * Sans état propre : tout arrive en paramètres, ce qui permet de le rendre en test
 * (`CapturesAccueilTest`) sans compte Google.
 */
@Composable
fun EcranAccueil(
    etat: EtatDrive,
    versionNom: String,
    onConnecter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Carnet de recette",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CarteDrive(etat, onConnecter)
        CarteSemaine()
        Spacer(Modifier.height(8.dp))
        Text(
            "Version $versionNom",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun CarteDrive(etat: EtatDrive, onConnecter: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Drive", style = MaterialTheme.typography.titleMedium)
            when (etat) {
                EtatDrive.NonConnecte -> {
                    Text(
                        "Branche le compte Google du foyer. L'app y crée le dossier " +
                            "« ${DriveCarnet.NOM_DOSSIER} », à déplacer ensuite dans « Maison ».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onConnecter) { Text("Connecter Google Drive") }
                }
                EtatDrive.EnCours -> {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Connexion en cours…", style = MaterialTheme.typography.bodyMedium)
                }
                is EtatDrive.Pret -> {
                    Text("Connecté : ${etat.compte}", style = MaterialTheme.typography.bodyMedium)
                    if (etat.horsLigne) {
                        Text(
                            DriveCarnet.MOTIF_RESEAU,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Dossier « ${DriveCarnet.NOM_DOSSIER} » prêt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                is EtatDrive.Erreur -> {
                    Text(
                        etat.motif,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onConnecter) { Text("Réessayer") }
                }
            }
        }
    }
}

/** Ce qui arrive aux lots suivants, pour que l'écran dise où l'on va (cadrage § 3). */
@Composable
private fun CarteSemaine() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La semaine", style = MaterialTheme.typography.titleMedium)
            Etape("Vendredi", "Scanner les box à leur arrivée", "bientôt")
            Etape("Vendredi soir", "Claude prépare les tâches et l'audio", "bientôt")
            Etape("Samedi", "Importer la semaine depuis Drive", "bientôt")
            Etape("En cuisine", "Chacun ses tâches, à l'écran et à l'oreille", "bientôt")
        }
    }
}

@Composable
private fun Etape(quand: String, quoi: String, etat: String) {
    Column {
        Text(quand, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("$quoi · $etat", style = MaterialTheme.typography.bodyMedium)
    }
}
