package fr.jrrocks.carnetderecette.ui

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

/** La mise à jour en ligne de l'app. Jamais une boîte de dialogue : une ligne d'état. */
sealed interface EtatMiseAJour {
    data object Aucune : EtatMiseAJour

    data class Disponible(val versionNom: String) : EtatMiseAJour

    data class Telechargement(val versionNom: String, val fraction: Float) : EtatMiseAJour

    /** Android demande d'autoriser l'installation pour cette app (« sources inconnues »). */
    data class AAutoriser(val versionNom: String) : EtatMiseAJour

    data class Echec(val versionNom: String, val motif: String) : EtatMiseAJour
}
