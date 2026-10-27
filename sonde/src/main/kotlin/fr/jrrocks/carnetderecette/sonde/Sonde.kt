package fr.jrrocks.carnetderecette.sonde

import fr.jrrocks.carnetderecette.core.hellofresh.AdressesHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.LecteurHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.Sonde
import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
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
 * 2 si le réseau manque (on ne conclut rien).
 */
fun main() {
    val vendredi = LocalDate.now(ZoneId.of("Europe/Paris")).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
    val semaine = SemaineIso.de(vendredi)
    println("Sonde HelloFresh, semaine $semaine (livraison du $vendredi)")
    val menu = try {
        LecteurHelloFresh.lireMenu(lire(AdressesHelloFresh.menu(semaine)).first, semaine, Instant.now().toString())
    } catch (e: java.io.IOException) {
        println("Réseau indisponible : ${e.message}")
        exitProcess(2)
    } catch (e: Exception) {
        rompu("menu de la semaine ${semaine.numero} : ${e.message}")
    }
    Sonde.verifierMenu(menu)?.let { rompu(it) }
    println("Menu : ${menu.entrees.size} recettes, numéros ${menu.entrees.first().numero} à ${menu.entrees.last().numero}.")

    val entree = menu.entrees.first()
    val recette = try {
        val (html, adresse) = lire(entree.url)
        LecteurHelloFresh.lireRecette(html, adresse)
    } catch (e: java.io.IOException) {
        println("Réseau indisponible : ${e.message}")
        exitProcess(2)
    } catch (e: Exception) {
        rompu("recette ${entree.numero} : ${e.message}")
    }
    Sonde.verifierRecette(recette)?.let { rompu(it) }
    println("Recette ${entree.numero} : ${recette.ingredients.size} ingrédients, ${recette.etapes.size} étapes, portions ${recette.personnesPossibles}.")
    println("Accès HelloFresh : OK.")
}

private fun rompu(motif: String): Nothing {
    println("ACCÈS ROMPU : $motif")
    println("Passer en mode dégradé : photographier les fiches papier dans l'app (Scanner → Photographier la fiche).")
    exitProcess(1)
}

/** Une page publique, en suivant les redirections du site (adresses publiques seulement). */
private fun lire(adresse: String): Pair<String, String> {
    var courante = adresse
    repeat(5) {
        require(AdressesHelloFresh.estPagePublique(courante)) { "Adresse hors des pages publiques : $courante" }
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
                in 300..399 -> courante = URL(URL(courante), c.getHeaderField("Location")).toString()
                else -> throw IllegalStateException("le site a répondu « $code » pour ${c.url.path}")
            }
        } finally {
            c.disconnect()
        }
    }
    throw IllegalStateException("trop de redirections pour $adresse")
}
