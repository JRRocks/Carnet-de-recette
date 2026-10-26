package fr.jrrocks.carnetderecette.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.jrrocks.carnetderecette.R
import fr.jrrocks.carnetderecette.drive.DriveCarnet
import fr.jrrocks.carnetderecette.ui.composants.BoutonCarnet

/**
 * Les réglages du foyer : le Drive, la version de l'app, puis ce qui viendra (cuisiniers,
 * matériel). Une liste, pas des cartes ; chaque rangée dit son état en toutes lettres.
 */
@Composable
fun EcranReglages(etat: EtatCarnet, actions: ActionsCarnet, marges: PaddingValues) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(marges).padding(bottom = 24.dp)) {
        SectionDrive(etat.drive, actions)
        Separateur()
        SectionApplication(etat, actions)
        Separateur()
        EnTete("Cuisine")
        Rangee(R.drawable.ic_group, "Cuisiniers", "Qui découpe, qui chauffe : pas encore dans cette version.")
        Rangee(R.drawable.ic_blender, "Matériel", "Le hachoir et les autres appareils : pas encore dans cette version.")
        Separateur()
        EnTete("À propos")
        Rangee(
            R.drawable.ic_description,
            "Polices et icônes",
            "Atkinson Hyperlegible Next (SIL Open Font License 1.1) · Material Symbols (Apache 2.0)",
        )
    }
}

@Composable
private fun SectionDrive(drive: EtatDrive, actions: ActionsCarnet) {
    EnTete("Google Drive")
    Rangee(R.drawable.ic_account_circle, "Compte", (drive as? EtatDrive.Pret)?.compte ?: "Aucun compte branché")
    Rangee(
        R.drawable.ic_folder,
        "Dossier",
        if (drive is EtatDrive.Pret) {
            "« ${DriveCarnet.NOM_DOSSIER} », à ranger dans « Maison » sur le Drive"
        } else {
            "Créé au branchement : « ${DriveCarnet.NOM_DOSSIER} »"
        },
    )
    when (drive) {
        EtatDrive.NonConnecte -> Rangee(R.drawable.ic_add_to_drive, "État", "Pas encore branché")
        EtatDrive.EnCours -> Rangee(R.drawable.ic_schedule, "État", "Connexion au compte Google…")
        is EtatDrive.Erreur -> Rangee(R.drawable.ic_sync_problem, "État", drive.motif)
        is EtatDrive.Pret -> if (drive.horsLigne) {
            Rangee(R.drawable.ic_cloud_off, "État", DriveCarnet.MOTIF_HORS_LIGNE)
        } else {
            Rangee(R.drawable.ic_cloud_done, "État", "Branché")
        }
    }
    val bouton = when (drive) {
        EtatDrive.NonConnecte -> "Brancher le Drive"
        is EtatDrive.Erreur -> "Réessayer"
        EtatDrive.EnCours, is EtatDrive.Pret -> null
    }
    if (bouton != null) {
        BoutonCarnet(
            bouton,
            actions.brancher,
            // Aligné sur le texte des rangées (16 + 24 + 16 dp).
            modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            icone = if (drive is EtatDrive.Erreur) R.drawable.ic_refresh else R.drawable.ic_add_to_drive,
        )
    }
}

@Composable
private fun SectionApplication(etat: EtatCarnet, actions: ActionsCarnet) {
    EnTete("Application")
    // La suite (version prête, téléchargement, échec) est dite une seule fois, par la ligne d'état.
    val version = if (etat.maj is EtatMiseAJour.Aucune) "${etat.versionNom} · à jour" else "${etat.versionNom} installée"
    Rangee(R.drawable.ic_system_update, "Version", version)
    LigneMiseAJour(etat.maj, actions, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
}

@Composable
private fun EnTete(titre: String) {
    Text(
        titre,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Rangee(@DrawableRes icone: Int, titre: String, detail: String) {
    ListItem(
        headlineContent = { Text(titre, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(detail, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { Icon(painterResource(icone), contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.heightIn(min = 72.dp),
    )
}

@Composable
private fun Separateur() {
    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
