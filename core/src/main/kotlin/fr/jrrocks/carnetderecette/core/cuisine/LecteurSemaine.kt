package fr.jrrocks.carnetderecette.core.cuisine

import fr.jrrocks.carnetderecette.core.semaine.SemaineIso
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Ce que donne la lecture d'un fichier de semaine. */
sealed interface LectureSemaine {
    /** Lu ; [remarques] dit en clair ce qui a été laissé de côté (une recette illisible…). */
    data class Lue(val fichier: FichierSemaine, val remarques: List<String> = emptyList()) : LectureSemaine

    /** Refusé : [motif] dit pourquoi, et quoi faire. */
    data class Refusee(val motif: String) : LectureSemaine
}

/**
 * Lit le fichier de la semaine écrit par Cowork, pour l'import du samedi (cadrage § 3).
 *
 * Exigeant sur ce qui rendrait la semaine fausse : un autre fichier (celui des recettes, une
 * photo), une autre semaine que celle qu'on cuisine, une version plus récente que l'app. Indulgent
 * sur le reste : une recette, une tâche ou un segment illisible est laissé de côté et dit dans
 * les remarques ; les champs inconnus sont ignorés, une valeur inconnue reprend sa valeur par
 * défaut. Puis le fichier est remis d'aplomb pour le planificateur et la séance : identifiants
 * uniques, dépendances connues, segments rattachés à un rôle.
 */
object LecteurSemaine {

    /** Au-delà, ce n'est pas une semaine (quelques dizaines de Ko d'habitude) : on ne lit pas. */
    const val TAILLE_MAX = 2 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Le fichier en JSON, tel que Cowork l'écrit : pour les tests et les exemples. */
    fun ecrire(fichier: FichierSemaine): String = json.encodeToString(FichierSemaine.serializer(), fichier)

    /** Lit [texte] ; [attendue] : la semaine en cours, à laquelle le fichier doit correspondre. */
    fun lire(texte: String, attendue: SemaineIso? = null): LectureSemaine {
        if (texte.length > TAILLE_MAX) return LectureSemaine.Refusee(MOTIF_PAS_UNE_SEMAINE)
        // Certains éditeurs ouvrent le fichier par une marque d'ordre des octets : elle n'est pas du JSON.
        val racine = runCatching { json.parseToJsonElement(texte.removePrefix("\uFEFF")) }.getOrNull() as? JsonObject
            ?: return LectureSemaine.Refusee(MOTIF_PAS_UNE_SEMAINE)

        when (racine.texte("type")) {
            null, FichierSemaine.TYPE -> Unit
            "recettes" -> return LectureSemaine.Refusee(
                "C'est le fichier des recettes déposé par l'app, pas celui de Claude\u00A0: choisis le fichier «\u00A0semaine-…\u00A0».",
            )
            else -> return LectureSemaine.Refusee(MOTIF_PAS_UNE_SEMAINE)
        }
        val version = racine.entier("version") ?: FichierSemaine.VERSION
        if (version > FichierSemaine.VERSION) {
            return LectureSemaine.Refusee("Ce fichier vient d'une version plus récente du skill de Claude\u00A0: mets l'app à jour, puis réessaie.")
        }
        if (version < 1) return LectureSemaine.Refusee(MOTIF_PAS_UNE_SEMAINE)

        val semaine = racine.texte("semaine")?.let(::semaineDe)
            ?: return LectureSemaine.Refusee("Ce fichier ne dit pas de quelle semaine il s'agit\u00A0: demande à Claude de le refaire.")
        if (attendue != null && semaine != attendue) {
            return LectureSemaine.Refusee(
                "Ce fichier prépare ${libelle(semaine, attendue)}, et la semaine en cours est ${libelle(attendue, semaine)}\u00A0: " +
                    "choisis «\u00A0${attendue.nomDeFichier("semaine")}\u00A0».",
            )
        }
        val statut = racine["statut"]?.let { runCatching { json.decodeFromJsonElement(StatutPreparation.serializer(), it) }.getOrNull() }
            ?: StatutPreparation.OK

        val remarques = mutableListOf<String>()
        val recettes = mutableListOf<RecettePreparee>()
        val elements = (racine["recettes"] as? JsonArray).orEmpty()
        if (elements.size > RECETTES_MAX) remarques += "Le fichier compte ${elements.size} recettes\u00A0: seules les $RECETTES_MAX premières sont gardées."
        elements.take(RECETTES_MAX).forEach { element ->
            val recette = lireRecette(element, remarques) ?: return@forEach
            if (recettes.any { it.numero == recette.numero }) {
                remarques += "La recette ${recette.numero} apparaît deux fois\u00A0: seule la première est gardée."
            } else {
                recettes += recette
            }
        }
        if (recettes.isEmpty() && statut != StatutPreparation.DEGRADE) {
            return LectureSemaine.Refusee(
                if (remarques.isEmpty()) "Ce fichier ne contient aucune recette préparée\u00A0: demande à Claude de refaire la semaine."
                else "Aucune recette de ce fichier ne se lit\u00A0: demande à Claude de refaire la semaine.",
            )
        }
        val fichier = FichierSemaine(
            version = version,
            semaine = semaine,
            statut = statut,
            message = racine.texte("message")?.trim()?.takeIf { it.isNotEmpty() },
            recettes = recettes,
        )
        return LectureSemaine.Lue(fichier, remarques)
    }

    // ── Une recette ─────────────────────────────────────────────────────────────

    /**
     * Une recette, ses tâches et ses segments lus un par un : un élément abîmé ne fait pas
     * perdre les autres. L'en-tête se lit champ par champ, chacun à sa valeur par défaut s'il
     * est illisible. `null` sans numéro : on ne saurait pas de quelle box il s'agit.
     */
    private fun lireRecette(element: JsonElement, remarques: MutableList<String>): RecettePreparee? {
        val objet = element as? JsonObject
        val numero = objet?.entier("numero")
        if (objet == null || numero == null) {
            remarques += "Une recette sans numéro est laissée de côté."
            return null
        }
        val source = objet["source"] as? JsonObject
        val taches = lireTous(objet["taches"], Tache.serializer(), TACHES_MAX)
        val segments = lireTous(objet["segments_audio"], SegmentPrepare.serializer(), SEGMENTS_MAX)
        val illisibles = taches.second + segments.second
        if (illisibles > 0) {
            remarques += "Recette $numero\u00A0: ${if (illisibles == 1) "un élément illisible laissé" else "$illisibles éléments illisibles laissés"} de côté."
        }
        if (taches.third || segments.third) {
            remarques += "Recette $numero\u00A0: trop longue pour une recette du soir, elle est coupée après $TACHES_MAX tâches et $SEGMENTS_MAX segments."
        }
        return remettreDAplomb(
            RecettePreparee(
                numero = numero,
                personnes = objet.entier("personnes"),
                aConfirmer = (objet["a_confirmer"] as? JsonPrimitive)?.booleanOrNull ?: false,
                confirmation = objet.texte("confirmation"),
                source = source?.let { SourcePreparee(it.texte("type"), it.texte("id"), it.texte("url")) },
                taches = taches.first,
                segments = segments.first,
            ),
        )
    }

    /**
     * Les [max] premiers éléments de [tableau] qui se lisent, le nombre de ceux qui ne se lisent
     * pas, et s'il en restait au-delà de [max].
     */
    private fun <T> lireTous(tableau: JsonElement?, serialiseur: KSerializer<T>, max: Int): Triple<List<T>, Int, Boolean> {
        val elements = (tableau as? JsonArray).orEmpty()
        val gardes = elements.take(max)
        val lus = gardes.mapNotNull { runCatching { json.decodeFromJsonElement(serialiseur, it) }.getOrNull() }
        return Triple(lus, gardes.size - lus.size, elements.size > max)
    }

    /**
     * Ce dont le planificateur et la séance ont besoin, garanti : des identifiants uniques (la
     * séance suit ses segments par identifiant), des dépendances vers des tâches connues, un rôle
     * pour chaque segment, un libellé pour chaque tâche, un nombre de personnes plausible.
     */
    internal fun remettreDAplomb(recette: RecettePreparee): RecettePreparee {
        val idsTaches = Identifiants("t")
        val taches = recette.taches.mapIndexed { i, tache ->
            tache.copy(
                id = idsTaches.unique(tache.id),
                texte = tache.texte.trim().ifEmpty { "Tâche ${i + 1}" },
            )
        }
        // Une dépendance renvoie à la première tâche de ce nom : celle que Claude a écrite d'abord.
        val connues = taches.map { it.id }.toSet()
        val propres = taches.map { tache ->
            tache.copy(apres = tache.apres.map { it.trim() }.filter { it in connues && it != tache.id }.distinct())
        }
        val parId = propres.associateBy { it.id }
        val idsSegments = Identifiants("a")
        val segments = recette.segments.filter { it.texte.isNotBlank() }.map { segment ->
            val tache = segment.tache?.trim()?.let(parId::get)
            segment.copy(
                id = idsSegments.unique(segment.id),
                texte = segment.texte.trim(),
                tache = tache?.id,
                role = segment.role ?: tache?.qui ?: RoleCuisine.CHAUFFE,
                texteSeul = segment.texteSeul?.trim(),
            )
        }
        return recette.copy(
            personnes = recette.personnes?.takeIf { it in 1..PERSONNES_MAX },
            confirmation = recette.confirmation?.trim()?.takeIf { it.isNotEmpty() },
            taches = propres,
            segments = segments,
        )
    }

    /** Des identifiants uniques : un vide reçoit un numéro, un doublon un suffixe. */
    private class Identifiants(private val prefixe: String) {
        private val pris = HashSet<String>()

        fun unique(voulu: String): String {
            val base = voulu.trim().ifEmpty { "$prefixe-${pris.size + 1}" }
            var id = base
            var n = 2
            while (!pris.add(id)) id = "$base~${n++}"
            return id
        }
    }

    // ── Outils ──────────────────────────────────────────────────────────────────

    private fun JsonObject.texte(cle: String): String? = (get(cle) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** Un entier, écrit 4, 4.0 ou "4". */
    private fun JsonObject.entier(cle: String): Int? {
        val valeur = get(cle) as? JsonPrimitive ?: return null
        return valeur.intOrNull ?: valeur.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && it in -1e6..1e6 }?.toInt()
    }

    /** « 2026-W44 », et la forme française des noms de fichiers, « 2026-S44 ». */
    private fun semaineDe(texte: String): SemaineIso? =
        SemaineIso.lire(texte) ?: SemaineIso.lire(texte.trim().uppercase().replace("-S", "-W"))

    /** « la semaine 43 », avec l'année si elle diffère de celle de [autre]. */
    private fun libelle(semaine: SemaineIso, autre: SemaineIso): String =
        if (semaine.annee == autre.annee) "la semaine ${semaine.numero}" else "la semaine ${semaine.numero} de ${semaine.annee}"

    private const val PERSONNES_MAX = 20

    /*
     * Une semaine compte une dizaine de box, une recette une quarantaine de tâches au plus. Ces
     * plafonds gardent l'app réactive devant un fichier aberrant : le plan se calcule à chaque
     * affichage, et le fichier importé est relu à chaque lancement.
     */
    const val RECETTES_MAX = 40
    const val TACHES_MAX = 200
    const val SEGMENTS_MAX = 400

    const val MOTIF_PAS_UNE_SEMAINE =
        "Ce fichier n'est pas une semaine préparée par Claude\u00A0: choisis le fichier «\u00A0semaine-…\u00A0» du dossier «\u00A0Carnet de recettes\u00A0»."
}
