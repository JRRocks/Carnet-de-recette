package fr.jrrocks.carnetderecette.ui

/** Où en est le branchement au Google Drive du foyer. */
sealed interface EtatDrive {
    /** Jamais branché sur ce téléphone, ou autorisation retirée. */
    data object NonConnecte : EtatDrive

    /** Branchement demandé par l'utilisateur : autorisation ou recherche du dossier en cours. */
    data object EnCours : EtatDrive

    /**
     * Déjà branché sur ce téléphone : au lancement, l'app revérifie en silence. Le compte
     * mémorisé reste affiché ; rien ne clignote.
     */
    data class Verification(val compte: String) : EtatDrive

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
    /** Rien à proposer, ou GitHub n'a pas répondu : l'app ne sait pas dire « à jour ». */
    data object Aucune : EtatMiseAJour

    data class Disponible(val versionNom: String) : EtatMiseAJour

    /** « Plus tard » : plus rien sur la Semaine ni de point sur Réglages, mais Réglages la propose encore. */
    data class Ecartee(val versionNom: String) : EtatMiseAJour

    data class Telechargement(val versionNom: String, val fraction: Float) : EtatMiseAJour

    /** Android demande d'autoriser l'installation pour cette app (« sources inconnues »). */
    data class AAutoriser(val versionNom: String) : EtatMiseAJour

    data class Echec(val versionNom: String, val motif: String) : EtatMiseAJour
}

/** Une mise à jour attend un geste (télécharger, autoriser, réessayer) ; pas pendant le téléchargement. */
val EtatMiseAJour.attendUnGeste: Boolean
    get() = this is EtatMiseAJour.Disponible || this is EtatMiseAJour.AAutoriser || this is EtatMiseAJour.Echec
