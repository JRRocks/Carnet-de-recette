package fr.jrrocks.carnetderecette.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutionException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Le dossier `Carnet de recettes` sur le Google Drive du foyer.
 *
 * **Provenance.** Moteur `google-drive-appdata` de la Library (JRRocks/Library), issu de
 * Popote Compagnie, commit `08c211a`. On garde son noyau générique (autorisation, liste
 * paginée, envoi multipart, téléchargement, suppression, transport) et on retire tout ce qui
 * était propre à Popote (espaces, socles, points, trousseaux, photos, rotation).
 *
 * **Adaptation, et pourquoi** (cadrage § 4) :
 *  - portée `drive.file` au lieu de `drive.appdata`. Le dossier caché `appDataFolder` est
 *    invisible pour Claude Cowork, qui doit lire ce que l'app dépose. `drive.file` reste
 *    **non sensible** : l'app se publie sans validation Google ;
 *  - un **dossier visible**, retrouvé par son nom ou créé, au lieu d'`appDataFolder` ;
 *  - l'app ne voit que les fichiers **qu'elle a créés**. Ce que Cowork écrit lui revient
 *    donc par le sélecteur de fichiers d'Android, pas par ce moteur.
 *
 *  - les erreurs de Drive sont **lues** : seul un refus d'autorisation (401, ou 403
 *    `insufficientPermissions`/`authError`) devient [AutorisationRevoquee]. La version
 *    d'origine traitait tout 403 ainsi, y compris « API Drive non activée » ou une limite de
 *    débit : l'utilisateur était renvoyé reconnecter son compte sans fin. Patch reporté dans
 *    la Library le 2026-10-26 ;
 *  - un refus de Drive ([RefusDrive]) se distingue d'une panne de réseau ([estPanneReseau]) ;
 *  - un jeton refusé est retiré du cache de Play Services ([avecJeton]).
 *
 * **Non vérifiable ici.** Ni compte Google ni Play Services dans un conteneur : seules les
 * parts sans I/O se testent (`DriveCarnetTest`). Le reste se constate sur le téléphone.
 */
object DriveCarnet {

    private const val PORTEE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val URL_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private const val URL_FICHIERS = "https://www.googleapis.com/drive/v3/files"
    private const val URL_USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo"
    private const val TYPE_DOSSIER = "application/vnd.google-apps.folder"

    /** Le dossier de l'app, créé au premier branchement, puis déplacé à la main dans `Maison/`. */
    const val NOM_DOSSIER = "Carnet de recettes"

    /** Le maximum que Drive v3 accepte par page. */
    private const val TAILLE_PAGE = 1000

    /** Garde-fou : de quoi lister vingt mille fichiers, et ne jamais boucler sans fin. */
    private const val PAGES_MAX = 20

    private const val DELAI_CONNEXION_MS = 15_000
    private const val DELAI_LECTURE_MS = 15_000

    private val json = Json { ignoreUnknownKeys = true }

    /** Un fichier ou un dossier, tel que Drive le liste. */
    data class FichierDrive(
        val id: String,
        val nom: String,
        val modifie: Instant? = null,
        val taille: Long? = null,
    )

    /**
     * Drive a répondu, mais par un refus (HTTP hors 2xx) : par opposition à une panne de
     * réseau, où rien n'a répondu. Son message est déjà lisible tel quel ([exceptionPour]).
     */
    open class RefusDrive(message: String) : IOException(message)

    /**
     * Drive refuse le jeton (401, ou 403 de raison [RAISONS_AUTORISATION]) : réessayer ne
     * changera rien tant que quelqu'un ne rouvre pas l'écran d'autorisation.
     */
    class AutorisationRevoquee(message: String) : RefusDrive(message)

    /** Les raisons d'un 403 qui disent vraiment « ce jeton n'a pas les droits ». */
    private val RAISONS_AUTORISATION = setOf("insufficientPermissions", "authError")

    // ── Autorisation ────────────────────────────────────────────────────────────

    /**
     * La demande d'autorisation : les fichiers créés par l'app, et l'adresse du compte.
     *
     * Jamais le Drive entier : `drive` ou `drive.readonly` sont des portées restreintes, qui
     * obligeraient l'app à rester en mode test (accord à redonner chaque semaine, amis à
     * déclarer un par un). L'adresse sert seulement à **afficher** le compte branché.
     *
     * [compte], quand il est posé, épingle la demande sur ce compte précis : sans lui,
     * `authorize()` réutilise en silence le compte déjà autorisé.
     */
    fun requeteAutorisation(compte: Account? = null): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(PORTEE_DRIVE_FILE), Scope(Scopes.EMAIL)))
            .apply { compte?.let { setAccount(it) } }
            .build()

    /** L'issue d'une demande d'autorisation, telle qu'un écran doit la traiter. */
    sealed interface Autorisation {
        /** Le compte est connecté ; [compte] est son adresse, à afficher. */
        data class Accordee(val compte: String) : Autorisation

        /** Il faut présenter [intention], puis relire [autorisationDepuisIntention]. Normal au premier branchement. */
        data class AEcran(val intention: PendingIntent) : Autorisation

        /** Rien n'a abouti. [reseau] : faute de réseau seulement, le compte reste valable. */
        data class Echec(val motif: String, val reseau: Boolean = false) : Autorisation
    }

    /**
     * Demande l'accès. Ne lève pas quand l'autorisation manque : rend l'écran à présenter,
     * parce que quelqu'un est justement devant pour l'accepter.
     */
    suspend fun demanderAutorisation(contexte: Context, compte: Account? = null): Autorisation =
        withContext(Dispatchers.IO) {
            val resultat = try {
                Tasks.await(Identity.getAuthorizationClient(contexte).authorize(requeteAutorisation(compte)))
            } catch (indisponible: Exception) {
                // `Tasks.await` enveloppe l'`ApiException` de Play Services dans une
                // `ExecutionException` dont le message brut ressemble à « ApiException: 7: ».
                val cause = (indisponible as? ExecutionException)?.cause ?: indisponible
                return@withContext echecGoogle((cause as? ApiException)?.statusCode, cause.message)
            }
            lireResultat(contexte, resultat)
        }

    /** Relit ce que l'écran de consentement a rendu. `null` : refusé ou abandonné. */
    suspend fun autorisationDepuisIntention(contexte: Context, donnees: Intent?): Autorisation =
        withContext(Dispatchers.IO) {
            val intention = donnees ?: return@withContext Autorisation.Echec("Autorisation refusée.")
            try {
                lireResultat(contexte, Identity.getAuthorizationClient(contexte).getAuthorizationResultFromIntent(intention))
            } catch (refus: ApiException) {
                echecGoogle(refus.statusCode, refus.message)
            } catch (refus: Exception) {
                Autorisation.Echec(refus.message ?: "Autorisation refusée.")
            }
        }

    /**
     * L'accord ne porte pas l'identité du compte (`authorize()` autorise, il ne connecte
     * pas) : on la redemande au point `userinfo` avec le jeton obtenu.
     */
    private fun lireResultat(contexte: Context, resultat: AuthorizationResult): Autorisation {
        resultat.pendingIntent?.takeIf { resultat.hasResolution() }?.let { return Autorisation.AEcran(it) }
        val jeton = resultat.accessToken ?: return Autorisation.Echec("Google n'a pas rendu de jeton d'accès.")
        val compte = try {
            lireEmail(String(appel(URL_USERINFO, "GET", jeton), StandardCharsets.UTF_8))
        } catch (refuse: AutorisationRevoquee) {
            // Accès retiré depuis le compte Google, jeton encore en cache : sans l'oublier,
            // `authorize()` le rendrait à nouveau et l'écran de consentement ne reviendrait pas.
            oublierJeton(contexte, jeton)
            return Autorisation.Echec(MOTIF_ACCES_RETIRE)
        } catch (indisponible: IOException) {
            return if (estPanneReseau(indisponible)) {
                Autorisation.Echec(MOTIF_RESEAU, reseau = true)
            } else {
                Autorisation.Echec("Google n'a pas rendu l'adresse du compte\u00A0: ${indisponible.message}")
            }
        } catch (corrompu: SerializationException) {
            null
        } ?: return Autorisation.Echec("Google n'a pas rendu l'adresse du compte.")
        return Autorisation.Accordee(compte)
    }

    /**
     * Le jeton d'accès du compte déjà autorisé. [IllegalStateException] si l'autorisation n'a
     * jamais été donnée (il faut quelqu'un devant l'écran), [IOException] pour le transitoire.
     */
    suspend fun jeton(contexte: Context): String = withContext(Dispatchers.IO) {
        val resultat: AuthorizationResult = try {
            Tasks.await(Identity.getAuthorizationClient(contexte).authorize(requeteAutorisation()))
        } catch (indisponible: Exception) {
            throw IOException("Autorisation Google indisponible.", indisponible)
        }
        if (resultat.hasResolution()) {
            throw IllegalStateException("Le compte Google n'a pas encore autorisé l'accès au Drive.")
        }
        resultat.accessToken ?: throw IOException("Google n'a pas rendu de jeton d'accès.")
    }

    /**
     * Exécute [bloc] avec le jeton du compte. Si Drive le refuse ([AutorisationRevoquee]), le
     * jeton est d'abord retiré du cache de Play Services : sinon `authorize()` rendrait le
     * même jeton mort jusqu'à son expiration (jusqu'à une heure), sans jamais reproposer
     * l'écran de consentement.
     */
    private suspend fun <T> avecJeton(contexte: Context, bloc: (String) -> T): T {
        val jeton = jeton(contexte)
        return try {
            bloc(jeton)
        } catch (refuse: AutorisationRevoquee) {
            oublierJeton(contexte, jeton)
            throw refuse
        }
    }

    /** Retire [jeton] du cache de Play Services (appel bloquant : thread d'E/S seulement). */
    private fun oublierJeton(contexte: Context, jeton: String) {
        runCatching { GoogleAuthUtil.clearToken(contexte, jeton) }
    }

    // ── Dossiers ────────────────────────────────────────────────────────────────

    /**
     * Le dossier [nom] (dans [parent], ou n'importe où si `null`), créé s'il n'existe pas.
     *
     * Sans [parent], la recherche porte sur **tout** ce que l'app voit : c'est ce qui permet
     * de retrouver `Carnet de recettes` après qu'on l'a déplacé dans `Maison/`, et de le
     * retrouver depuis le second téléphone du même compte. Avec `drive.file`, un dossier du
     * même nom créé par quelqu'un d'autre n'apparaît pas : pas de confusion possible.
     *
     * S'il y en a plusieurs (deux téléphones branchés au même instant), le plus ancien gagne,
     * pour que les deux convergent vers le même.
     */
    suspend fun dossier(contexte: Context, nom: String = NOM_DOSSIER, parent: String? = null): FichierDrive =
        withContext(Dispatchers.IO) {
            avecJeton(contexte) { jeton -> chercherOuCreerDossier(jeton, nom, parent) }
        }

    private fun chercherOuCreerDossier(jeton: String, nom: String, parent: String?): FichierDrive {
        val champs = URLEncoder.encode("files(id,name,createdTime)", "UTF-8")
        val requete = URLEncoder.encode(requeteDossier(nom, parent), "UTF-8")
        val reponse = String(
            appel("$URL_FICHIERS?q=$requete&fields=$champs&orderBy=createdTime&pageSize=10", "GET", jeton),
            StandardCharsets.UTF_8,
        )
        return lireListe(reponse).firstOrNull() ?: run {
            val cree = appel(
                url = "$URL_FICHIERS?fields=id,name",
                methode = "POST",
                jeton = jeton,
                typeContenu = "application/json; charset=UTF-8",
                corps = metadonnees(nom, parent, TYPE_DOSSIER).toByteArray(StandardCharsets.UTF_8),
            )
            lireFichier(String(cree, StandardCharsets.UTF_8))
                ?: throw IOException("Drive n'a pas rendu le dossier créé.")
        }
    }

    // ── Fichiers ────────────────────────────────────────────────────────────────

    /**
     * Le contenu de [dossierId], **en entier** : Drive pagine, et la version d'origine qui
     * ignorait `nextPageToken` perdait silencieusement tout ce qui dépassait la première page.
     */
    suspend fun lister(contexte: Context, dossierId: String): List<FichierDrive> = withContext(Dispatchers.IO) {
        avecJeton(contexte) { jeton -> listerAvec(jeton, dossierId) }
    }

    private fun listerAvec(jeton: String, dossierId: String): List<FichierDrive> {
        val champs = URLEncoder.encode("nextPageToken,files(id,name,modifiedTime,size)", "UTF-8")
        val requete = URLEncoder.encode("'${echapper(dossierId)}' in parents and trashed = false", "UTF-8")
        val tous = mutableListOf<FichierDrive>()
        var jetonPage: String? = null
        var pages = 0
        do {
            val url = buildString {
                append(URL_FICHIERS)
                append("?q=").append(requete)
                append("&pageSize=").append(TAILLE_PAGE)
                append("&fields=").append(champs)
                jetonPage?.let { append("&pageToken=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val reponse = String(appel(url, "GET", jeton), StandardCharsets.UTF_8)
            tous += lireListe(reponse)
            jetonPage = lireJetonPage(reponse)
            pages++
        } while (jetonPage != null && pages < PAGES_MAX)
        return tous
    }

    /**
     * Dépose [octets] sous [nom] dans [dossierId], en **remplaçant** le fichier de même nom
     * s'il existe : un scan renvoyé deux fois ne doit pas laisser deux fichiers.
     */
    suspend fun envoyer(
        contexte: Context,
        dossierId: String,
        nom: String,
        octets: ByteArray,
        typeMime: String = "application/json",
        existants: List<FichierDrive>? = null,
    ): FichierDrive = withContext(Dispatchers.IO) {
        val deja = (existants ?: lister(contexte, dossierId)).firstOrNull { it.nom == nom }
        val (frontiere, corps) = construireCorpsMultipart(
            nomFichier = nom,
            contenu = octets,
            typeMime = typeMime,
            parent = dossierId.takeIf { deja == null },
        )
        val url = if (deja == null) {
            "$URL_UPLOAD?uploadType=multipart&fields=id,name"
        } else {
            "$URL_UPLOAD/${deja.id}?uploadType=multipart&fields=id,name"
        }
        val reponse = avecJeton(contexte) { jeton ->
            appel(
                url = url,
                methode = if (deja == null) "POST" else "PATCH",
                jeton = jeton,
                typeContenu = "multipart/related; boundary=$frontiere",
                corps = corps,
            )
        }
        lireFichier(String(reponse, StandardCharsets.UTF_8)) ?: FichierDrive(deja?.id.orEmpty(), nom)
    }

    /** Rapatrie le contenu d'un fichier ; [progression] reçoit les octets reçus et le total (`0` si inconnu). */
    suspend fun telecharger(
        contexte: Context,
        id: String,
        progression: (recus: Long, total: Long) -> Unit = { _, _ -> },
    ): ByteArray = withContext(Dispatchers.IO) {
        avecJeton(contexte) { jeton ->
            val connexion = (URL("$URL_FICHIERS/$id?alt=media").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DELAI_CONNEXION_MS
                readTimeout = DELAI_LECTURE_MS
                setRequestProperty("Authorization", "Bearer $jeton")
            }
            try {
                val code = connexion.responseCode
                if (code !in 200..299) throw exceptionPour(code, "GET", lireCorpsErreur(connexion))
                lireAvecProgression(connexion.inputStream, connexion.contentLengthLong.coerceAtLeast(0L), progression)
            } finally {
                connexion.disconnect()
            }
        }
    }

    /** Efface définitivement un fichier. */
    suspend fun supprimer(contexte: Context, id: String): Unit = withContext(Dispatchers.IO) {
        avecJeton(contexte) { jeton -> appel("$URL_FICHIERS/$id", "DELETE", jeton) }
    }

    // ── Parts pures, testables sans réseau ni compte ────────────────────────────

    /** Hors ligne pendant un branchement : rien n'est mémorisé, il faudra réessayer. */
    const val MOTIF_RESEAU = "Pas de réseau\u00A0: réessaie une fois connecté."

    /** Hors ligne au lancement, téléphone déjà branché : le branchement mémorisé tient. */
    const val MOTIF_HORS_LIGNE = "Hors ligne\u00A0: le Drive sera vérifié au prochain lancement."

    const val MOTIF_ACCES_RETIRE = "Google a retiré l'accès au Drive. Appuie sur «\u00A0Réessayer\u00A0» pour le rebrancher."

    /**
     * Une panne de réseau (rien n'a répondu), par opposition à un refus ([RefusDrive]). On
     * remonte la chaîne des causes : `jeton()` enveloppe l'échec de Play Services, et une
     * coupure en cours de requête arrive en `SocketException` (« connection reset »,
     * « Software caused connection abort », `NoRouteToHostException`) ou en `SSLException`.
     */
    internal fun estPanneReseau(erreur: Throwable): Boolean =
        erreur !is RefusDrive && generateSequence(erreur) { it.cause }.take(10).any {
            it is UnknownHostException ||
                it is SocketTimeoutException ||
                it is SocketException ||
                it is SSLException ||
                (it as? ApiException)?.statusCode == CommonStatusCodes.NETWORK_ERROR
        }

    /**
     * L'échec d'une demande d'autorisation, dit en français. Les codes de Play Services
     * qu'on rencontre réellement au premier branchement ont un message qui dit quoi faire ;
     * les autres gardent leur code, pour pouvoir le chercher.
     */
    internal fun echecGoogle(code: Int?, message: String?): Autorisation.Echec {
        val texte = message?.trim()?.takeIf { it.isNotBlank() }
        return when {
            code == CommonStatusCodes.NETWORK_ERROR -> Autorisation.Echec(MOTIF_RESEAU, reseau = true)
            // Google rend `UNREGISTERED_ON_API_CONSOLE` avec le code 10, mais aussi avec le 8
            // (« [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE] ») : le texte décide.
            code == CommonStatusCodes.DEVELOPER_ERROR || texte?.contains("UNREGISTERED_ON_API_CONSOLE") == true ->
                Autorisation.Echec(
                    "Google ne reconnaît pas l'app\u00A0: le nom de package ou l'empreinte SHA-1 du client " +
                        "OAuth Android ne correspond pas (docs/google-cloud.md, étape 4).",
                )
            code == CommonStatusCodes.CANCELED -> Autorisation.Echec("Autorisation annulée.")
            code == null -> Autorisation.Echec(texte ?: "Google n'a pas répondu à la demande d'autorisation.")
            // Le texte de Google est gardé : c'est lui qu'on cherche quand le code ne suffit pas.
            else -> Autorisation.Echec("Google a refusé la demande d'autorisation (code $code" + (texte?.let { "\u00A0: $it" } ?: "") + ").")
        }
    }

    /** Ce que Drive dit de son refus : `error.errors[0].reason` et `error.message`. */
    internal data class ErreurDrive(val raison: String?, val message: String?)

    /** Lit le corps d'erreur JSON de Drive ; `null` s'il est absent ou illisible. */
    internal fun lireErreurDrive(corps: String?): ErreurDrive? {
        val erreur = corps
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject["error"]?.jsonObject }.getOrNull() }
            ?: return null
        val raison = runCatching {
            erreur["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.content
        }.getOrNull()
        val message = runCatching { erreur["message"]?.jsonPrimitive?.content }.getOrNull()
        return ErreurDrive(raison, message)
    }

    /**
     * L'exception qui correspond à une réponse hors 2xx. Seul un vrai refus du jeton devient
     * [AutorisationRevoquee] (définitif, il faut reconnecter) ; le reste est un [RefusDrive]
     * qui porte le message de Google, pour que l'écran dise la vraie cause.
     */
    internal fun exceptionPour(code: Int, methode: String, corps: String?): RefusDrive {
        val erreur = lireErreurDrive(corps)
        val raison = erreur?.raison
        if (code == 401 || (code == 403 && raison in RAISONS_AUTORISATION)) {
            return AutorisationRevoquee("Drive a refusé l'appel $methode\u00A0: HTTP $code (autorisation retirée).")
        }
        if (raison == "accessNotConfigured" || raison == "SERVICE_DISABLED") {
            return RefusDrive(
                "L'API Google Drive n'est pas activée dans le projet Google Cloud (docs/google-cloud.md, étape 2).",
            )
        }
        val detail = listOfNotNull(erreur?.message, raison?.let { "($it)" }).joinToString(" ")
        return RefusDrive("Drive a refusé l'appel $methode\u00A0: HTTP $code" + if (detail.isBlank()) "." else " — $detail")
    }

    /**
     * La requête Drive qui retrouve un dossier par son nom. Les guillemets simples et les
     * barres obliques inverses du nom sont échappés : sans ça, un nom comme « L'atelier »
     * casserait la requête.
     */
    internal fun requeteDossier(nom: String, parent: String?): String = buildString {
        append("name = '").append(echapper(nom)).append("'")
        append(" and mimeType = '").append(TYPE_DOSSIER).append("'")
        append(" and trashed = false")
        parent?.let { append(" and '").append(echapper(it)).append("' in parents") }
    }

    /** Échappement d'une valeur littérale dans une requête Drive (`q`). */
    internal fun echapper(valeur: String): String = valeur.replace("\\", "\\\\").replace("'", "\\'")

    /**
     * Les métadonnées JSON d'une création. Construites par la bibliothèque JSON plutôt qu'à
     * la main (la version d'origine concaténait le nom) : un nom avec des guillemets reste
     * un JSON valide.
     */
    internal fun metadonnees(nom: String, parent: String?, typeMime: String? = null): String =
        JsonObject(
            buildMap {
                put("name", JsonPrimitive(nom))
                typeMime?.let { put("mimeType", JsonPrimitive(it)) }
                parent?.let { put("parents", JsonArray(listOf(JsonPrimitive(it)))) }
            },
        ).toString()

    /**
     * Le corps `multipart/related` d'un envoi Drive v3 : les métadonnées JSON, puis le
     * contenu. [parent] n'est posé qu'à la **création** (`null` pour un remplacement) : Drive
     * refuse qu'un PATCH rejoue `parents`.
     */
    internal fun construireCorpsMultipart(
        nomFichier: String,
        contenu: ByteArray,
        typeMime: String,
        parent: String?,
    ): Pair<String, ByteArray> {
        val frontiere = "carnet-${UUID.randomUUID()}"
        val avant = buildString {
            append("--").append(frontiere).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadonnees(nomFichier, parent)).append("\r\n")
            append("--").append(frontiere).append("\r\n")
            append("Content-Type: ").append(typeMime).append("\r\n\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        val apres = "\r\n--$frontiere--\r\n".toByteArray(StandardCharsets.UTF_8)
        return frontiere to (avant + contenu + apres)
    }

    /** Lit [entree] jusqu'au bout en rendant compte de chaque bloc lu à [progression]. */
    internal fun lireAvecProgression(
        entree: java.io.InputStream,
        total: Long,
        progression: (recus: Long, total: Long) -> Unit,
    ): ByteArray {
        val sortie = java.io.ByteArrayOutputStream(if (total in 1..Int.MAX_VALUE.toLong()) total.toInt() else 64 * 1024)
        val tampon = ByteArray(64 * 1024)
        var recus = 0L
        progression(0L, total)
        entree.use { flux ->
            while (true) {
                val lus = flux.read(tampon)
                if (lus < 0) break
                sortie.write(tampon, 0, lus)
                recus += lus
                progression(recus, total)
            }
        }
        return sortie.toByteArray()
    }

    /** Extrait `files[]` de la réponse de liste de Drive. */
    internal fun lireListe(reponse: String): List<FichierDrive> =
        json.parseToJsonElement(reponse).jsonObject["files"]
            ?.jsonArray
            ?.mapNotNull { lireFichier(it.jsonObject) }
            .orEmpty()

    /** Un fichier seul, tel que Drive le rend après une création ; `null` sans `id`. */
    internal fun lireFichier(reponse: String): FichierDrive? =
        runCatching { json.parseToJsonElement(reponse).jsonObject }.getOrNull()?.let { lireFichier(it) }

    private fun lireFichier(objet: JsonObject): FichierDrive? {
        val id = objet["id"]?.jsonPrimitive?.content ?: return null
        val nom = objet["name"]?.jsonPrimitive?.content ?: return null
        return FichierDrive(
            id = id,
            nom = nom,
            modifie = objet["modifiedTime"]?.jsonPrimitive?.content
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            // Drive rend la taille en chaîne (`"size": "1234"`), pas en nombre.
            taille = objet["size"]?.jsonPrimitive?.content?.toLongOrNull(),
        )
    }

    /** Le jeton de la page suivante, `null` quand la liste est complète (clé absente ou vide). */
    internal fun lireJetonPage(reponse: String): String? =
        json.parseToJsonElement(reponse).jsonObject["nextPageToken"]
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }

    /** Extrait `email` de la réponse du point `userinfo` de Google. */
    internal fun lireEmail(reponse: String): String? =
        json.parseToJsonElement(reponse).jsonObject["email"]?.jsonPrimitive?.content

    // ── Transport ───────────────────────────────────────────────────────────────

    /** Un appel à l'API Drive. Hors 2xx : [exceptionPour]. */
    private fun appel(
        url: String,
        methode: String,
        jeton: String,
        typeContenu: String? = null,
        corps: ByteArray? = null,
    ): ByteArray {
        val connexion = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = methode
            connectTimeout = DELAI_CONNEXION_MS
            readTimeout = DELAI_LECTURE_MS
            setRequestProperty("Authorization", "Bearer $jeton")
            typeContenu?.let { setRequestProperty("Content-Type", it) }
            if (corps != null) doOutput = true
        }
        try {
            corps?.let { charge -> connexion.outputStream.use { it.write(charge) } }
            val code = connexion.responseCode
            if (code !in 200..299) throw exceptionPour(code, methode, lireCorpsErreur(connexion))
            return if (methode == "DELETE") ByteArray(0) else connexion.inputStream.use { it.readBytes() }
        } finally {
            connexion.disconnect()
        }
    }

    /** Le corps d'une réponse d'erreur, s'il y en a un : c'est lui qui dit la vraie cause. */
    private fun lireCorpsErreur(connexion: HttpURLConnection): String? =
        runCatching { connexion.errorStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } }.getOrNull()
}
