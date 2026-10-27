package fr.jrrocks.carnetderecette.net

import fr.jrrocks.carnetderecette.core.hellofresh.AdressesHelloFresh
import fr.jrrocks.carnetderecette.core.hellofresh.PageIllisible
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le site a répondu par un refus (404, 503…) ou renvoie hors des pages publiques : ce n'est
 * pas une panne de réseau. Les refus passagers (429, 503…) se reconnaissent à leur [code]
 * ([fr.jrrocks.carnetderecette.core.hellofresh.Sonde.refusPassager]).
 */
class RefusSite(val code: Int, message: String) : IOException(message)

/** Une page reçue : son texte et l'adresse finale, après les redirections. */
class PageRecue(val html: String, val adresse: String)

/**
 * Lit les pages **publiques** de hellofresh.fr comme le ferait un navigateur : une requête
 * simple, sans connexion ni jeton, en s'annonçant pour ce qu'on est ([AdressesHelloFresh.AGENT]).
 * Quelques pages par semaine seulement (cadrage § 10) : le menu, puis une page par box scannée.
 */
object PagesHelloFresh {

    private const val DELAI_CONNEXION_MS = 15_000
    private const val DELAI_LECTURE_MS = 20_000

    /** Une page de menu pèse environ 4 Mo ; au-delà de 16 Mo, quelque chose ne va pas. */
    private const val TAILLE_MAX_PAGE = 16L * 1024 * 1024
    private const val TAILLE_MAX_IMAGE = 8L * 1024 * 1024
    private const val REDIRECTIONS_MAX = 5

    suspend fun lire(adresse: String): PageRecue = withContext(Dispatchers.IO) {
        if (!AdressesHelloFresh.estPagePublique(adresse)) throw RefusSite(0, "Adresse hors des pages publiques\u00A0: $adresse.")
        var courante = adresse
        repeat(REDIRECTIONS_MAX + 1) {
            val connexion = ouvrir(courante, "text/html,application/xhtml+xml")
            try {
                when (val code = connexion.responseCode) {
                    in 200..299 -> {
                        // Une page démesurée : le site a changé, ce n'est pas le réseau.
                        val octets = connexion.inputStream.use { lireBorne(it, TAILLE_MAX_PAGE) }
                            ?: throw PageIllisible("La page dépasse ${TAILLE_MAX_PAGE / (1024 * 1024)} Mo\u00A0: le site a sans doute changé.")
                        return@withContext PageRecue(String(octets, Charsets.UTF_8), courante)
                    }
                    // Le site redirige l'ancienne adresse d'une recette vers la nouvelle.
                    in 300..399 -> {
                        val suivante = connexion.getHeaderField("Location")
                            ?.let { runCatching { URL(URL(courante), it).toString() }.getOrNull() }
                            ?: throw RefusSite(code, "Le site redirige sans dire où (code $code).")
                        // Une redirection hors des pages publiques (autre site, http, /gw…) : on ne la
                        // suit pas, et c'est un changement du site, pas une coupure de réseau.
                        if (!AdressesHelloFresh.estPagePublique(suivante)) {
                            throw RefusSite(code, "Le site renvoie hors des pages publiques (code $code).")
                        }
                        courante = suivante
                    }
                    else -> throw RefusSite(code, "Le site a répondu «\u00A0$code\u00A0» pour ${connexion.url.path}.")
                }
            } finally {
                connexion.disconnect()
            }
        }
        throw RefusSite(310, "Trop de redirections pour $adresse.")
    }

    /** Télécharge une image du site dans [cible], via un fichier partiel : jamais d'image tronquée. */
    suspend fun telechargerImage(adresse: String, cible: File) = withContext(Dispatchers.IO) {
        // Les images viennent du réseau de diffusion du site, qui peut rediriger entre ses serveurs.
        val connexion = ouvrir(adresse, "image/*").apply { instanceFollowRedirects = true }
        try {
            val code = connexion.responseCode
            if (code !in 200..299) throw RefusSite(code, "Image refusée (code $code).")
            val partiel = File(cible.parentFile, cible.name + ".partiel")
            cible.parentFile?.mkdirs()
            val octets = connexion.inputStream.use { lireBorne(it, TAILLE_MAX_IMAGE) }
                ?: throw IOException("Image trop grosse (plus de ${TAILLE_MAX_IMAGE / (1024 * 1024)} Mo).")
            partiel.writeBytes(octets)
            if (!partiel.renameTo(cible)) {
                partiel.delete()
                throw IOException("Image impossible à ranger.")
            }
        } finally {
            connexion.disconnect()
        }
    }

    private fun ouvrir(adresse: String, accepte: String): HttpURLConnection =
        (URL(adresse).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = DELAI_CONNEXION_MS
            readTimeout = DELAI_LECTURE_MS
            // Les redirections sont suivies à la main : chacune est vérifiée (pages publiques seulement).
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", AdressesHelloFresh.AGENT)
            setRequestProperty("Accept", accepte)
            setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
        }

    /** Le corps de la réponse ; `null` s'il dépasse [max] octets. */
    private fun lireBorne(entree: java.io.InputStream, max: Long): ByteArray? {
        val sortie = ByteArrayOutputStream()
        val tampon = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val lus = entree.read(tampon)
            if (lus < 0) break
            total += lus
            if (total > max) return null
            sortie.write(tampon, 0, lus)
        }
        return sortie.toByteArray()
    }
}
