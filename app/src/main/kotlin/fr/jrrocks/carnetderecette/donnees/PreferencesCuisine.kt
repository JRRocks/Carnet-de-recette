package fr.jrrocks.carnetderecette.donnees

import android.content.Context
import fr.jrrocks.carnetderecette.core.cuisine.ModeCuisine
import fr.jrrocks.carnetderecette.core.cuisine.RoleCuisine

/**
 * Ce que ce téléphone retient de la cuisine : qui l'utilise (la découpe ou la chauffe) et si
 * l'on cuisine à deux ou seul. Propre à chaque téléphone : sur celui de l'un, « découpe » ; sur
 * celui de l'autre, « chauffe ».
 */
class PreferencesCuisine(contexte: Context) {

    private val preferences = contexte.getSharedPreferences("cuisine", Context.MODE_PRIVATE)

    /** `null` tant qu'on ne l'a pas choisi : la Cuisine le demande avant de commencer. */
    var role: RoleCuisine?
        get() = preferences.getString(CLE_ROLE, null)?.let { nom -> RoleCuisine.entries.firstOrNull { it.name == nom } }
        set(valeur) {
            preferences.edit().putString(CLE_ROLE, valeur?.name).apply()
        }

    var mode: ModeCuisine
        get() = preferences.getString(CLE_MODE, null)?.let { nom -> ModeCuisine.entries.firstOrNull { it.name == nom } } ?: ModeCuisine.A_DEUX
        set(valeur) {
            preferences.edit().putString(CLE_MODE, valeur.name).apply()
        }

    private companion object {
        const val CLE_ROLE = "role"
        const val CLE_MODE = "mode"
    }
}
