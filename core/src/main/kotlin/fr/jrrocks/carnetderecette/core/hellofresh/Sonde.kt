package fr.jrrocks.carnetderecette.core.hellofresh

import fr.jrrocks.carnetderecette.core.recette.MenuSemaine
import fr.jrrocks.carnetderecette.core.recette.Recette

/**
 * La sonde du vendredi (cadrage § 9) : elle vérifie que ce qu'on a lu ressemble encore à un
 * menu et à une recette. Elle rend la cause en clair, ou `null` si tout va bien. Un défaut ici
 * veut dire « le site a changé » : on passe en mode dégradé (photo de la fiche papier).
 */
object Sonde {

    fun verifierMenu(menu: MenuSemaine): String? = when {
        menu.entrees.isEmpty() -> "Le menu de la semaine ${menu.semaine.numero} ne contient aucune recette numérotée."
        menu.entrees.size < 5 -> "Le menu de la semaine ${menu.semaine.numero} ne contient que ${menu.entrees.size} recettes : la page a sans doute changé."
        else -> null
    }

    fun verifierRecette(recette: Recette): String? {
        val manque = champsManquants(recette)
        return if (manque.isEmpty()) null else "La page de « ${recette.titre} » ne donne plus ${manque.joinToString(", ")}."
    }

    /**
     * Les champs attendus qui manquent à [recette], dans nos mots (« les étapes ») : rien de la
     * page. La sonde de GitHub ne publie que ceux-là, jamais le titre (dépôt public).
     */
    fun champsManquants(recette: Recette): List<String> = buildList {
        if (recette.titre.isBlank()) add("le nom")
        if (recette.ingredients.isEmpty()) add("les ingrédients")
        if (recette.etapes.isEmpty()) add("les étapes")
        if (recette.portions.isEmpty()) add("les portions")
    }

    /**
     * Un refus passager du site : délai dépassé (408), trop de demandes (429), passerelle ou
     * serveur momentanément indisponible (502, 503, 504). Il ne dit rien de l'accès : on
     * réessaie plus tard. Les autres refus (403, 404, 410…) disent que l'accès est rompu.
     */
    fun refusPassager(code: Int): Boolean = code == 408 || code == 429 || code in 502..504
}
