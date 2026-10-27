package fr.jrrocks.carnetderecette.sonde

import fr.jrrocks.carnetderecette.core.hellofresh.AdressesHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.LecteurHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.PageIllisible
import fr.jrrocks.carnetderecette.core.hellofresh.Sonde
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.system.exitProcess

/**
 * La sonde du jeudi : lit le menu de la semaine du vendredi qui vient, puis la page d'une de ses
 * recettes, avec le même lecteur que l'app. Deux pages publiques par semaine, rien d'autre.
 * Code de sortie 0 si tout va bien, 1 si l'accès est rompu (le workflow ouvre alors une issue),
 * 2 si le réseau manque ou si le site refuse en passant (429, 503… : on ne conclut rien).
 *
 * Sa sortie part dans le journal et dans une issue du dépôt **public** : elle n'écrit que des
 * numéros, des codes HTTP et nos propres mots, jamais un titre, une adresse ou un chemin de page
 * (CLAUDE.md, règle 1).
 */
fun main() {
    val vendredi = LocalDate.now(ZoneId.of("Europe/Paris")).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
    val semaine = SemaineIso.de(vendredi)
    println("Sonde HelloFresh, semaine $semaine (livraison du $vendredi)")
    val menu = try {
        LecteurHelloFresh.lireMenu(lire(AdressesHelloFresh.menu(semaine)).first, semaine, Instant.now().toString())
    } catch (e: Exception) {
        echec("menu de la semaine ${semaine.numero}", e)
    }
    Sonde.verifierMenu(menu)?.let { rompu(it) }
    println("Menu : ${menu.entrees.size} recettes, numéros ${menu.entrees.first().numero} à ${menu.entrees.last().numero}.")

    val entree = menu.entrees.first()
    val recette = try {
        val (html, adresse) = lire(entree.url)
        LecteurHelloFresh.lireRecette(html, adresse)
    } catch (e: Exception) {
        echec("recette ${entree.numero}", e)
    }
    // Les champs manquants, pas le texte de verifierRecette : il cite le titre de la recette.
    Sonde.champsManquants(recette).takeIf { it.isNotEmpty() }?.let { manque ->
        rompu("recette ${entree.numero} : la page ne donne plus ${manque.joinToString(", ")}")
    }
    // Lue par le bloc schema.org de secours : l'app marche encore, mais sans quantités par
    // portion (cadrage § 6). Le bloc de données de la page a changé : autant le savoir jeudi.
    if (recette.portions.none { it.quantites.isNotEmpty() }) {
        rompu("recette ${entree.numero} : lue par le bloc de secours, plus de quantités par portion")
    }
    println("Recette ${entree.numero} : ${recette.ingredients.size} ingrédients, ${recette.etapes.size} étapes, portions ${recette.personnesPossibles}.")
    println("Accès HelloFresh : OK.")
}

/** Un refus du site, décrit sans rien de la page : un code HTTP, ou un motif écrit ici. */
private class Refus(val motif: String, val passager: Boolean = false) : Exception(motif)

private fun echec(quoi: String, e: Exception): Nothing = when (e) {
    is Refus -> if (e.passager) rienAConclure("$quoi : ${e.motif}") else rompu("$quoi : ${e.motif}")
    // Le nom de l'erreur seulement : son message peut citer une adresse.
    is IOException -> rienAConclure("$quoi : réseau indisponible (${e.javaClass.simpleName})")
    // Messages écrits dans le lecteur, sans contenu de la page (voir PageIllisible).
    is PageIllisible -> rompu("$quoi : ${e.message}")
    else -> rompu("$quoi : erreur de lecture (${e.javaClass.simpleName})")
}

private fun rompu(motif: String): Nothing {
    println("ACCÈS ROMPU : $motif")
    println("Passer en mode dégradé : photographier les fiches papier dans l'app (Scanner → Photographier la fiche).")
    exitProcess(1)
}

private fun rienAConclure(motif: String): Nothing {
    println("Rien à conclure : $motif.")
    exitProcess(2)
}

/** Une page publique, en suivant les redirections du site (adresses publiques seulement). */
private fun lire(adresse: String): Pair<String, String> {
    var courante = adresse
    repeat(5) {
        if (!AdressesHelloFresh.estPagePublique(courante)) throw Refus("le site renvoie hors des pages publiques")
        val c = (URL(courante).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", AdressesHelloFresh.AGENT)
            setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
        }
        try {
            when (val code = c.responseCode) {
                in 200..299 -> return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } to courante
                in 300..399 -> courante = c.getHeaderField("Location")
                    ?.let { runCatching { URL(URL(courante), it).toString() }.getOrNull() }
                    ?: throw Refus("le site redirige sans adresse lisible (code $code)")
                // Le code seulement : jamais le chemin de la page, qui reprend le titre de la recette.
                else -> throw Refus("le site a répondu « $code »", passager = Sonde.refusPassager(code))
            }
        } finally {
            c.disconnect()
        }
    }
    throw Refus("trop de redirections")
}
