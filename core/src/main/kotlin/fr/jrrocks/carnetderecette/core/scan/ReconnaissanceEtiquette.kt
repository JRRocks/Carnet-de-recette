package fr.jrrocks.carnetderecette.core.scan

/**
 * Ce qu'une image de l'étiquette a donné : le numéro de la recette et le nombre de personnes,
 * chacun `null` quand rien de sûr n'a été lu, et les éléments bruts qui l'ont permis.
 */
data class LectureEtiquette(
    val numero: Int?,
    val personnes: Int?,
    val elements: List<ElementLu>,
)

/**
 * Reconnaît une box sur son étiquette **sans regex ni format figé** (CLAUDE.md, règle 5 ;
 * cadrage § 5) : on compare ce qui est lu à des **valeurs connues**, jamais à un motif. Si
 * l'étiquette change de mise en page, la reconnaissance tient tant que le numéro et le nombre
 * de personnes y sont écrits.
 *
 * - **Numéro** : un mot lu qui est exactement l'écriture décimale d'un numéro du menu de la
 *   semaine désigne la recette. « 2O7 » (lettre O) ne passe pas : on ne corrige rien, le
 *   consensus de plusieurs images fera le tri.
 * - **Nombre de personnes** : un texte lu comparé à l'ensemble des formes connues (« 4P »,
 *   « 4 PERS. », « X4 »…) construites pour chaque nombre possible.
 *
 * Dans le doute (deux numéros de même taille, un autre texte aussi grand que le numéro, deux
 * nombres de personnes différents), la réponse est `null` : mieux vaut ne rien proposer que
 * deviner.
 */
object ReconnaissanceEtiquette {

    /** Les nombres de personnes que propose HelloFresh. */
    val PERSONNES_POSSIBLES: Set<Int> = (1..6).toSet()

    /**
     * Deux numéros dont les hauteurs diffèrent de moins de 10 % sont jugés de même taille :
     * aucun n'est le grand numéro de l'étiquette.
     */
    const val ECART_HAUTEUR = 0.10f

    /** Ce qui suit le nombre de personnes sur une étiquette (« 4p », « 4 pers. »…). */
    private val APRES = listOf("P", "P.", "PERS", "PERS.", "PERSONNE", "PERSONNES", "PORTION", "PORTIONS", "PORT.")

    /** Ce qui le précède (« x4 », « × 4 »). */
    private val AVANT = listOf("X", "×")

    fun reconnaitre(
        elements: List<ElementLu>,
        numerosDuMenu: Set<Int>,
        personnesPossibles: Set<Int> = PERSONNES_POSSIBLES,
    ): LectureEtiquette = LectureEtiquette(
        numero = numero(elements, numerosDuMenu),
        personnes = personnes(elements, personnesPossibles),
        elements = elements,
    )

    /**
     * Les formes connues du nombre de personnes, en clés de [ScanValue] (majuscules, espaces
     * réduits à un seul), avec et sans espace : « 4P » et « 4 P », « X4 » et « X 4 »…
     */
    fun formesPersonnes(personnesPossibles: Set<Int>): Map<String, Int> = buildMap {
        personnesPossibles.forEach { n ->
            APRES.forEach { mot ->
                put("$n$mot", n)
                put("$n $mot", n)
            }
            AVANT.forEach { mot ->
                put("$mot$n", n)
                put("$mot $n", n)
            }
        }
    }

    /**
     * Le numéro doit être **nettement le plus grand texte** du réticule : un autre élément lu
     * (numéro du menu ou non) à moins de [ECART_HAUTEUR] de sa hauteur laisse un doute. Sans
     * cette règle, quand le grand numéro est mal lu (« I7 ») ou hors du réticule, un petit
     * nombre imprimé ailleurs sur l'étiquette (« LOT 12/10 45 ») qui serait aussi au menu
     * deviendrait le numéro de la box.
     */
    private fun numero(elements: List<ElementLu>, numerosDuMenu: Set<Int>): Int? {
        val parEcriture = numerosDuMenu.associateBy { it.toString() }
        // Pour chaque numéro reconnu, la plus grande hauteur à laquelle il a été lu.
        val hauteurs = HashMap<Int, Float>()
        elements.forEach { element ->
            mots(element.texte).forEach { mot ->
                parEcriture[mot]?.let { numero -> hauteurs[numero] = maxOf(hauteurs[numero] ?: 0f, element.hauteur) }
            }
        }
        val classes = hauteurs.entries.sortedByDescending { it.value }
        val premier = classes.firstOrNull() ?: return null
        val seuil = premier.value * (1 - ECART_HAUTEUR)
        val second = classes.getOrNull(1)
        if (second != null && second.value >= seuil) return null
        val ecriture = premier.key.toString()
        // Tout autre élément lu, qui ne porte pas ce même numéro, doit rester nettement plus petit.
        val rival = elements.any { element ->
            val mots = mots(element.texte)
            mots.isNotEmpty() && ecriture !in mots && element.hauteur >= seuil
        }
        return if (rival) null else premier.key
    }

    private fun personnes(elements: List<ElementLu>, personnesPossibles: Set<Int>): Int? {
        val formes = formesPersonnes(personnesPossibles)
        val trouves = HashSet<Int>()
        elements.forEachIndexed { i, element ->
            val textes = mots(element.texte) + ScanValue.key(element.texte)
            textes.forEach { texte -> formes[texte]?.let(trouves::add) }
            // « 4 pers. » se lit souvent en deux éléments voisins sur la même ligne : « 4 » puis « pers. ».
            val suivant = elements.getOrNull(i + 1)
            if (suivant != null && memeLigne(element, suivant)) {
                formes[ScanValue.key(element.texte + " " + suivant.texte)]?.let(trouves::add)
            }
        }
        return trouves.singleOrNull()
    }

    /** Les mots d'un texte lu, en clés de [ScanValue] : découpés aux espaces, sans motif. */
    private fun mots(texte: String): List<String> =
        ScanValue.key(texte).split(' ').filter { it.isNotEmpty() }

    /** Deux éléments qui se suivent, l'un à droite de l'autre, à la même hauteur dans le réticule. */
    private fun memeLigne(a: ElementLu, b: ElementLu): Boolean =
        b.x > a.x && kotlin.math.abs(a.y - b.y) <= maxOf(a.hauteur, b.hauteur) / 2
}
