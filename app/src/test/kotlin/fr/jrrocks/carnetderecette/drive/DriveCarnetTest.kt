package fr.jrrocks.carnetderecette.drive

import com.google.android.gms.common.api.CommonStatusCodes
import fr.jrrocks.carnetderecette.drive.DriveCarnet.FichierDrive
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Le transport Drive a besoin d'un compte Google et du réseau : ce fichier couvre les parts
 * **sans I/O**. Les tests génériques viennent du moteur Library `google-drive-appdata`
 * (corps multipart, liste, pagination, adresse, progression) ; ceux du dossier visible et de
 * l'échappement sont propres à cette adaptation.
 */
class DriveCarnetTest {

    @Test
    fun `la lecture d un telechargement rend compte des octets recus`() {
        val contenu = ByteArray(150_000) { (it % 251).toByte() }
        val releves = mutableListOf<Pair<Long, Long>>()

        val lu = DriveCarnet.lireAvecProgression(contenu.inputStream(), contenu.size.toLong()) { recus, total ->
            releves += recus to total
        }

        assertTrue(lu.contentEquals(contenu))
        assertEquals(0L to 150_000L, releves.first())
        assertEquals(150_000L to 150_000L, releves.last())
        assertTrue("les octets ne reculent jamais", releves.zipWithNext().all { (a, b) -> b.first >= a.first })
    }

    @Test
    fun `une creation porte le nom, le dossier parent et le type du contenu`() {
        val (frontiere, corps) = DriveCarnet.construireCorpsMultipart(
            nomFichier = "scans-2026-S43.json",
            contenu = "{}".toByteArray(),
            typeMime = "application/json",
            parent = "dossier-42",
        )
        val texte = String(corps, Charsets.UTF_8)

        assertTrue(texte.contains("--$frontiere"))
        assertTrue(texte.contains(""""name":"scans-2026-S43.json""""))
        assertTrue(texte.contains(""""parents":["dossier-42"]"""))
        assertTrue(texte.contains("Content-Type: application/json\r\n"))
        assertTrue("le corps doit se refermer sur la frontière", texte.trimEnd().endsWith("--$frontiere--"))
    }

    /** Drive refuse qu'un remplacement rejoue `parents`. */
    @Test
    fun `un remplacement ne rejoue pas le dossier parent`() {
        val (_, corps) = DriveCarnet.construireCorpsMultipart("profil-cuisine.json", byteArrayOf(1), "application/json", parent = null)

        val texte = String(corps, Charsets.UTF_8)
        assertTrue(texte.contains(""""name":"profil-cuisine.json""""))
        assertFalse("un PATCH ne doit pas porter parents", texte.contains("parents"))
    }

    @Test
    fun `le contenu binaire n est ni tronque ni altere dans le corps`() {
        val contenu = ByteArray(64) { it.toByte() }

        val (_, corps) = DriveCarnet.construireCorpsMultipart("etiquette.jpg", contenu, "image/jpeg", "d")

        assertTrue("le contenu doit apparaître intact dans le corps", indexOfSousTableau(corps, contenu) >= 0)
    }

    /** La version d'origine concaténait le nom dans le JSON : un guillemet le cassait. */
    @Test
    fun `un nom avec guillemets reste un JSON valide`() {
        val metadonnees = Json.parseToJsonElement(DriveCarnet.metadonnees("Le \"bon\" carnet", "p1")).jsonObject

        assertEquals("Le \"bon\" carnet", metadonnees["name"]?.jsonPrimitive?.content)
        assertEquals("p1", metadonnees["parents"]?.jsonArray?.single()?.jsonPrimitive?.content)
    }

    @Test
    fun `un dossier se cree avec le type dossier de Drive`() {
        val metadonnees = Json.parseToJsonElement(
            DriveCarnet.metadonnees(DriveCarnet.NOM_DOSSIER, parent = null, typeMime = "application/vnd.google-apps.folder"),
        ).jsonObject

        assertEquals("Carnet de recettes", metadonnees["name"]?.jsonPrimitive?.content)
        assertEquals("application/vnd.google-apps.folder", metadonnees["mimeType"]?.jsonPrimitive?.content)
        assertNull("sans parent, le dossier naît à la racine du Drive", metadonnees["parents"])
    }

    /** Sans parent, la recherche porte sur tout ce que l'app voit : le dossier déplacé dans `Maison/` se retrouve. */
    @Test
    fun `la recherche du dossier vise le nom, le type et ignore la corbeille`() {
        assertEquals(
            "name = 'Carnet de recettes' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            DriveCarnet.requeteDossier("Carnet de recettes", parent = null),
        )
        assertEquals(
            "name = 'scans' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and 'abc' in parents",
            DriveCarnet.requeteDossier("scans", parent = "abc"),
        )
    }

    @Test
    fun `l apostrophe et la barre oblique inverse sont echappees dans une requete`() {
        assertEquals("L\\'atelier", DriveCarnet.echapper("L'atelier"))
        assertEquals("a\\\\b", DriveCarnet.echapper("a\\b"))
        assertTrue(DriveCarnet.requeteDossier("L'atelier", null).startsWith("name = 'L\\'atelier'"))
    }

    @Test
    fun `la reponse de liste se lit en fichiers`() {
        val reponse = """
            {"files":[
              {"id":"1a","name":"scans-2026-S43.json"},
              {"id":"2b","name":"profil-cuisine.json"}
            ]}
        """.trimIndent()

        assertEquals(
            listOf(FichierDrive("1a", "scans-2026-S43.json"), FichierDrive("2b", "profil-cuisine.json")),
            DriveCarnet.lireListe(reponse),
        )
    }

    @Test
    fun `un dossier vide se lit comme une liste vide`() {
        assertEquals(emptyList<FichierDrive>(), DriveCarnet.lireListe("""{"files":[]}"""))
        assertEquals(emptyList<FichierDrive>(), DriveCarnet.lireListe("{}"))
    }

    @Test
    fun `la liste lit aussi la date et la taille de chaque fichier`() {
        val reponse = """{"files":[{"id":"1a","name":"x.json","modifiedTime":"2026-10-23T18:05:00.000Z","size":"1234"}]}"""

        assertEquals(
            listOf(FichierDrive("1a", "x.json", Instant.parse("2026-10-23T18:05:00Z"), 1234L)),
            DriveCarnet.lireListe(reponse),
        )
    }

    @Test
    fun `le fichier rendu par une creation se lit, et rien sans identifiant`() {
        assertEquals(FichierDrive("d1", "Carnet de recettes"), DriveCarnet.lireFichier("""{"id":"d1","name":"Carnet de recettes"}"""))
        assertNull(DriveCarnet.lireFichier("""{"name":"sans id"}"""))
        assertNull(DriveCarnet.lireFichier("<html>erreur</html>"))
    }

    @Test
    fun `une reponse paginee annonce sa page suivante`() {
        val reponse = """{"nextPageToken":"suite~42","files":[{"id":"1a","name":"a.json"}]}"""

        assertEquals("suite~42", DriveCarnet.lireJetonPage(reponse))
        assertEquals(listOf(FichierDrive("1a", "a.json")), DriveCarnet.lireListe(reponse))
    }

    @Test
    fun `la derniere page n annonce aucune suite`() {
        assertNull(DriveCarnet.lireJetonPage("""{"files":[{"id":"1a","name":"x.json"}]}"""))
        assertNull(DriveCarnet.lireJetonPage("{}"))
        assertNull(DriveCarnet.lireJetonPage("""{"nextPageToken":"","files":[]}"""))
    }

    @Test
    fun `la reponse userinfo se lit en adresse`() {
        assertEquals("foyer@example.org", DriveCarnet.lireEmail("""{"sub":"1","email":"foyer@example.org"}"""))
        assertNull(DriveCarnet.lireEmail("""{"sub":"1"}"""))
    }

    /** Corps d'erreur tel que Drive v3 le rend. */
    private fun erreur(code: Int, raison: String, message: String) =
        """{"error":{"code":$code,"message":"$message","errors":[{"domain":"usageLimits","reason":"$raison","message":"$message"}]}}"""

    @Test
    fun `le corps d erreur de Drive se lit en raison et message`() {
        assertEquals(
            DriveCarnet.ErreurDrive("rateLimitExceeded", "Rate Limit Exceeded"),
            DriveCarnet.lireErreurDrive(erreur(403, "rateLimitExceeded", "Rate Limit Exceeded")),
        )
        assertNull(DriveCarnet.lireErreurDrive(null))
        assertNull(DriveCarnet.lireErreurDrive("<html>Erreur</html>"))
    }

    /**
     * Relecture du lot 1 : tout 403 passait pour une autorisation retirée. « API non activée »
     * renvoyait alors l'utilisateur reconnecter son compte, sans fin et sans la vraie cause.
     */
    @Test
    fun `un 403 API non activee dit d activer l API, il ne demande pas de reconnecter`() {
        val exception = DriveCarnet.exceptionPour(403, "GET", erreur(403, "accessNotConfigured", "Google Drive API has not been used"))

        assertFalse(exception is DriveCarnet.AutorisationRevoquee)
        assertTrue(exception.message!!.contains("n'est pas activée"))
        assertTrue(exception.message!!.contains("étape 2"))
    }

    @Test
    fun `une limite de debit reste une erreur ordinaire qui porte le message de Google`() {
        val exception = DriveCarnet.exceptionPour(403, "POST", erreur(403, "rateLimitExceeded", "Rate Limit Exceeded"))

        assertFalse(exception is DriveCarnet.AutorisationRevoquee)
        assertTrue(exception.message!!.contains("Rate Limit Exceeded"))
        assertTrue(exception.message!!.contains("rateLimitExceeded"))
    }

    @Test
    fun `seul un vrai refus du jeton demande de reconnecter le compte`() {
        assertTrue(DriveCarnet.exceptionPour(401, "GET", null) is DriveCarnet.AutorisationRevoquee)
        assertTrue(
            DriveCarnet.exceptionPour(403, "GET", erreur(403, "insufficientPermissions", "Insufficient Permission"))
                is DriveCarnet.AutorisationRevoquee,
        )
        assertFalse(DriveCarnet.exceptionPour(403, "GET", null) is DriveCarnet.AutorisationRevoquee)
        assertFalse(DriveCarnet.exceptionPour(500, "GET", null) is DriveCarnet.AutorisationRevoquee)
    }

    @Test
    fun `une panne de reseau se distingue d un refus`() {
        assertTrue(DriveCarnet.estPanneReseau(UnknownHostException("www.googleapis.com")))
        assertTrue(DriveCarnet.estPanneReseau(SocketTimeoutException()))
        assertFalse(DriveCarnet.estPanneReseau(IOException("Drive a refusé l'appel GET : HTTP 500.")))
    }

    /** Relecture du lot 1 : hors ligne, l'écran affichait « ApiException: 7: » ou accusait Google. */
    @Test
    fun `les echecs de Play Services se disent en francais, le reseau a part`() {
        val reseau = DriveCarnet.echecGoogle(CommonStatusCodes.NETWORK_ERROR, "7: ")
        assertTrue(reseau.reseau)
        assertEquals(DriveCarnet.MOTIF_RESEAU, reseau.motif)

        val inconnue = DriveCarnet.echecGoogle(CommonStatusCodes.DEVELOPER_ERROR, "10: ")
        assertFalse(inconnue.reseau)
        assertTrue(inconnue.motif.contains("SHA-1"))

        assertNotEquals("4: ", DriveCarnet.echecGoogle(4, "4: ").motif)
        assertEquals("Délai dépassé", DriveCarnet.echecGoogle(null, "Délai dépassé").motif)
    }

    private fun indexOfSousTableau(tableau: ByteArray, motif: ByteArray): Int {
        outer@ for (i in 0..tableau.size - motif.size) {
            for (j in motif.indices) if (tableau[i + j] != motif[j]) continue@outer
            return i
        }
        return -1
    }
}
