package fr.jrrocks.carnetderecette.maj

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests d'origine du moteur Library `maj-auto-github`, repris avec le nom d'APK de cette app.
 *
 * La mise à jour en ligne demande GitHub, un `PackageManager` réel et un installateur —
 * rien de tout cela ici. Ce fichier couvre ce qui **décide** : lire la réponse de l'API et
 * choisir si une version mérite d'être proposée.
 *
 * C'est là que se joue le risque. Un APK mal identifié se propose à l'installation ; un
 * `versionCode` mal comparé fait régresser le téléphone ou lui repropose sans fin ce qu'il a
 * déjà.
 */
class MiseAJourTest {

    /** Une réponse de `/releases/latest` telle que GitHub la rend, réduite à l'utile. */
    private fun reponse(actifs: String, notes: String = "\"Correction du bilan.\"") = """
        {
          "tag_name": "v13.21-maj-en-ligne",
          "name": "Version 13.21-maj-en-ligne",
          "body": $notes,
          "assets": [$actifs]
        }
    """.trimIndent()

    private fun actif(nom: String, taille: Long = 12_345_678L) = """
        {
          "name": "$nom",
          "size": $taille,
          "browser_download_url": "https://github.com/JRRocks/Carnet-de-recette/releases/download/v1/$nom"
        }
    """.trimIndent()

    @Test
    fun `une version publiee se lit avec son code, son nom et son lien`() {
        val publication = MiseAJour.lirePublication(
            reponse(actif("carnet-de-recette-13.21-maj-en-ligne-22.apk")),
        )

        assertNotNull(publication)
        assertEquals(22L, publication!!.versionCode)
        assertEquals("13.21-maj-en-ligne", publication.versionNom)
        assertEquals("carnet-de-recette-13.21-maj-en-ligne-22.apk", publication.nomFichier)
        assertEquals(12_345_678L, publication.octets)
        assertEquals("Correction du bilan.", publication.notes)
        assertEquals(
            "https://github.com/JRRocks/Carnet-de-recette/releases/download/v1/" +
                "carnet-de-recette-13.21-maj-en-ligne-22.apk",
            publication.url,
        )
    }

    /**
     * Le `versionCode` n'existe nulle part dans l'API GitHub : il n'est porté que par le nom
     * du fichier. Un APK déposé à la main sans ce suffixe ne se compare à rien — le proposer
     * reviendrait à proposer n'importe quoi.
     */
    @Test
    fun `un APK sans versionCode dans son nom est ignore`() {
        assertNull(MiseAJour.lirePublication(reponse(actif("carnet-de-recette.apk"))))
        assertNull(MiseAJour.lirePublication(reponse(actif("app-terrain-release.apk"))))
        assertNull(MiseAJour.lirePublication(reponse(actif("carnet-de-recette-13.21-maj-en-ligne.apk"))))
    }

    /** Une version peut porter des sources, une signature, un journal — l'APK se retrouve parmi eux. */
    @Test
    fun `l'APK est retrouve parmi les autres fichiers joints`() {
        val publication = MiseAJour.lirePublication(
            reponse(
                listOf(
                    actif("Source code (zip)"),
                    actif("carnet-de-recette-13.21-maj-en-ligne-22.apk"),
                    actif("notes.txt"),
                ).joinToString(","),
            ),
        )

        assertEquals(22L, publication?.versionCode)
    }

    /** GitHub écrit `"body": null` quand la version n'a pas de notes : ce n'est pas une panne. */
    @Test
    fun `une version sans notes se lit quand meme`() {
        val publication = MiseAJour.lirePublication(
            reponse(actif("carnet-de-recette-13.21-maj-en-ligne-22.apk"), notes = "null"),
        )

        assertEquals(22L, publication?.versionCode)
        assertEquals("", publication?.notes)
    }

    /** Aucune version publiée, réponse tronquée, HTML d'une page d'erreur : rien à proposer. */
    @Test
    fun `une reponse illisible ou sans fichier joint ne propose rien`() {
        assertNull(MiseAJour.lirePublication(""))
        assertNull(MiseAJour.lirePublication("<html>404</html>"))
        assertNull(MiseAJour.lirePublication("""{"message":"Not Found"}"""))
        assertNull(MiseAJour.lirePublication(reponse(actifs = "")))
    }

    /**
     * Strictement plus récente, et rien d'autre : réinstaller le même code ne corrige rien,
     * et proposer un code inférieur ferait reculer un téléphone en service.
     */
    @Test
    fun `seule une version strictement plus recente est proposee`() {
        val publication = MiseAJour.lirePublication(
            reponse(actif("carnet-de-recette-13.21-maj-en-ligne-22.apk")),
        )

        assertNotNull(MiseAJour.aProposer(publication, installee = 21L))
        assertNull(MiseAJour.aProposer(publication, installee = 22L))
        assertNull(MiseAJour.aProposer(publication, installee = 23L))
        assertNull("sans version publiée, rien à proposer", MiseAJour.aProposer(null, installee = 1L))
    }

    /**
     * Le nom de version contient lui-même des chiffres et des tirets (`13.21-maj-en-ligne`) :
     * c'est bien le **dernier** groupe qui fait le code, pas le premier venu.
     */
    @Test
    fun `le code se lit apres le dernier tiret, pas dans le nom de version`() {
        assertEquals(22L, MiseAJour.versionCodeDuNom("carnet-de-recette-13.21-maj-en-ligne-22.apk"))
        assertEquals(7L, MiseAJour.versionCodeDuNom("carnet-de-recette-2.0-7.apk"))
        assertEquals(105L, MiseAJour.versionCodeDuNom("carnet-de-recette-14.0-105.apk"))
        assertNull(MiseAJour.versionCodeDuNom("carnet-de-recette-13.21.apk"))
        assertNull(MiseAJour.versionCodeDuNom("autre-appli-13.21-22.apk"))
    }

    /**
     * Patch relevé dans le Carnet, reporté dans la Library le 2026-10-26 : un écran recréé pendant la
     * requête annule l'interrogation. Avant, l'annulation était avalée et la réponse vide
     * retenue pour tout le processus ; maintenant l'appel suivant interroge à nouveau.
     */
    @Test
    fun `une interrogation annulee n empeche pas la suivante de proposer la mise a jour`() = runBlocking {
        MiseAJour.oublier()
        val publication = MiseAJour.lirePublication(reponse(actif("carnet-de-recette-13.21-maj-en-ligne-22.apk")))
        val commencee = CompletableDeferred<Unit>()

        val premiere = launch {
            MiseAJour.publicationDuLancement(
                interrogation = { commencee.complete(Unit); awaitCancellation() },
                installee = { 21L },
            )
        }
        commencee.await()
        premiere.cancel()
        premiere.join()

        val seconde = MiseAJour.publicationDuLancement(interrogation = { publication }, installee = { 21L })

        assertEquals(22L, seconde?.versionCode)
        MiseAJour.oublier()
    }

    /** Une panne (réseau, GitHub) est retenue : on ne réinterroge pas à chaque écran. */
    @Test
    fun `un echec d interrogation est retenu jusqu au prochain processus`() = runBlocking {
        MiseAJour.oublier()
        val publication = MiseAJour.lirePublication(reponse(actif("carnet-de-recette-13.21-maj-en-ligne-22.apk")))

        val premiere = MiseAJour.publicationDuLancement(interrogation = { error("GitHub en panne") }, installee = { 21L })
        val seconde = MiseAJour.publicationDuLancement(interrogation = { publication }, installee = { 21L })

        assertNull(premiere)
        assertNull("la réponse est retenue pour tout le processus", seconde)
        MiseAJour.oublier()
    }
}
