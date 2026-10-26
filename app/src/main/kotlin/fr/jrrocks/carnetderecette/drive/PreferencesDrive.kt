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

    fun memoriser(compte: String, dossierId: String) {
        preferences.edit().putString(CLE_COMPTE, compte).putString(CLE_DOSSIER, dossierId).apply()
    }

    fun oublier() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val CLE_COMPTE = "compte"
        const val CLE_DOSSIER = "dossier_id"
    }
}
