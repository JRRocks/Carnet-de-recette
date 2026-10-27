package fr.jrrocks.carnetderecette.core.hellofresh

import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import java.net.URI

/**
 * Les adresses publiques du site, et rien d'autre : la page du menu de la semaine, la page de
 * chaque recette et les images. Jamais d'interface interne du site (`/gw/…`), jamais de jeton
 * (CLAUDE.md, règle 2).
 */
object AdressesHelloFresh {

    private const val SITE = "https://www.hellofresh.fr"
    private const val IMAGES = "https://img.hellofresh.com"

    /**
     * Le client s'annonce pour ce qu'il est : un lecteur de pages publiques, en usage privé.
     * Le site sert la même page qu'à un navigateur (vérifié le 2026-10-27).
     */
    const val AGENT = "Mozilla/5.0 (Linux; Android) CarnetDeRecette (usage privé)"

    fun menu(semaine: SemaineIso): String = "$SITE/menus/$semaine"

    /**
     * L'adresse d'une image du site à la largeur voulue. [chemin] est celui des pages
     * (« /image/…jpg », « /ingredient/…png », « /<id>/step-…jpg »).
     */
    fun image(chemin: String, largeur: Int): String {
        require(chemin.startsWith("/") && ".." !in chemin) { "Chemin d'image inattendu : $chemin" }
        return "$IMAGES/f_auto,fl_lossy,q_auto,w_$largeur/hellofresh_s3$chemin"
    }

    /** Vrai si [adresse] est une page publique du site (on ne suit rien d'autre). */
    fun estPagePublique(adresse: String): Boolean {
        val uri = runCatching { URI(adresse) }.getOrNull() ?: return false
        val hote = uri.host ?: return false
        return uri.scheme == "https" &&
            (hote == "www.hellofresh.fr" || hote == "hellofresh.fr") &&
            !(uri.path ?: "").startsWith("/gw")
    }
}
