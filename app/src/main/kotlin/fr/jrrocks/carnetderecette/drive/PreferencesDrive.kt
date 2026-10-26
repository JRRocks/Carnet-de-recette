package fr.jrrocks.carnetderecette.drive

import android.content.Context

/**
 * Ce que le téléphone retient du branchement : le compte affiché et l'identifiant du dossier.
 *
 * L'identifiant n'est qu'un raccourci : [DriveCarnet.dossier] retrouve le dossier par son nom
 * de toute façon, y compris après un déplacement dans `Maison/` ou depuis l'autre téléphone.
 */
class PreferencesDrive(contexte: Context) {

    private val preferences = contexte.getSharedPreferences("drive", Context.MODE_PRIVATE)

    val compte: String? get() = preferences.getString(CLE_COMPTE, null)

    val dossierId: String? get() = preferences.getString(CLE_DOSSIER, null)

    /** Le dossier créé a été annoncé une fois (avec le rappel de le ranger dans « Maison »). */
    var dossierAnnonce: Boolean
        get() = preferences.getBoolean(CLE_ANNONCE, false)
        set(valeur) {
            preferences.edit().putBoolean(CLE_ANNONCE, valeur).apply()
        }

    fun memoriser(compte: String, dossierId: String) {
        preferences.edit().putString(CLE_COMPTE, compte).putString(CLE_DOSSIER, dossierId).apply()
    }

    /** Oublie le branchement ; l'annonce du dossier, déjà faite, ne revient pas. */
    fun oublier() {
        preferences.edit().remove(CLE_COMPTE).remove(CLE_DOSSIER).apply()
    }

    private companion object {
        const val CLE_COMPTE = "compte"
        const val CLE_DOSSIER = "dossier_id"
        const val CLE_ANNONCE = "dossier_annonce"
    }
}
