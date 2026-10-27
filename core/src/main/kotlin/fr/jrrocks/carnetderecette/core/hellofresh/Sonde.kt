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
        val manque = buildList {
            if (recette.titre.isBlank()) add("le nom")
            if (recette.ingredients.isEmpty()) add("les ingrédients")
            if (recette.etapes.isEmpty()) add("les étapes")
            if (recette.portions.isEmpty()) add("les portions")
        }
        return if (manque.isEmpty()) null else "La page de « ${recette.titre} » ne donne plus ${manque.joinToString(", ")}."
    }
}
