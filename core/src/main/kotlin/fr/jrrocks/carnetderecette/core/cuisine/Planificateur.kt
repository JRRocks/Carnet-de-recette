package fr.jrrocks.carnetderecette.core.cuisine

/** Qui cuisine ce soir. */
enum class ModeCuisine {
    /** L'un découpe, l'autre chauffe : chacun suit ses tâches sur son téléphone. */
    A_DEUX,

    /** Une seule personne fait tout : les attentes laissent le temps d'avancer le reste. */
    SEUL,
}

/** Une tâche à sa place : de la minute [debut] à la minute [fin], comptées depuis le début. */
data class Place(val tache: Tache, val debut: Int, val fin: Int) {
    val role: RoleCuisine get() = tache.qui
}

/** Le plan d'une recette : chaque tâche à sa place, dans l'ordre du temps. */
data class Plan(val mode: ModeCuisine, val places: List<Place>) {

    /** Environ combien de minutes du début à la fin. */
    val duree: Int get() = places.maxOfOrNull { it.fin } ?: 0

    fun duRole(role: RoleCuisine): List<Place> = places.filter { it.role == role }

    /** Le temps où [role] a les mains prises : ses tâches, sans les attentes. */
    fun occupation(role: RoleCuisine): Int = duRole(role).filterNot { it.tache.passive }.sumOf { it.tache.minutes }

    fun place(id: String?): Place? = id?.let { cle -> places.firstOrNull { it.tache.id == cle } }

    /**
     * Le plan à deux à lire de haut en bas : les tâches dans l'ordre du temps, et, juste avant
     * la première tâche qui en a besoin, chaque passage de main de ce que l'un a préparé pour
     * l'autre (« passe à la chauffe : émincer les oignons »). Seuls les ingrédients préparés
     * passent de main en main : une dépendance entre deux tâches de cuisson n'est qu'un ordre.
     */
    fun lignes(): List<LignePlan> {
        val parId = places.associateBy { it.tache.id }
        val passes = HashSet<String>()
        return buildList {
            for (place in places) {
                if (mode == ModeCuisine.A_DEUX) {
                    place.tache.apres.mapNotNull(parId::get)
                        .filter { it.tache.sorte == TypeTache.DECOUPE && it.role != place.role && passes.add(it.tache.id + ">" + place.role) }
                        .forEach { add(LignePlan.Passage(it, vers = place.role)) }
                }
                add(LignePlan.Etape(place))
            }
        }
    }
}

/** Une rangée du plan à deux. */
sealed interface LignePlan {
    data class Etape(val place: Place) : LignePlan

    /** Ce que [source] a préparé part chez [vers]. */
    data class Passage(val source: Place, val vers: RoleCuisine) : LignePlan
}

/**
 * L'ordonnancement **déterministe** des tâches d'une recette (cadrage § 7) : Claude a réfléchi
 * à l'import, l'app place les tâches en cuisine. Les deux téléphones calculent le même plan à
 * partir des mêmes données.
 *
 * Les règles :
 * - une tâche commence quand toutes celles dont elle dépend ([Tache.apres]) sont finies ;
 * - une **attente** (four, mijotage) démarre dès qu'elle est prête et ne retient personne ;
 * - **à deux**, chacun ne fait que les tâches de son rôle : les segments de Claude sont écrits
 *   pour un rôle (« passe les oignons à la chauffe »). Prendre la tâche prête de l'autre quand
 *   on est libre demande que les deux téléphones se parlent : ce sera la synchro (lot 6) ;
 * - **seul**, une seule personne fait tout, et avance le reste pendant les attentes ;
 * - un cuisinier libre prend **la première tâche prête de son rôle, dans l'ordre de Claude**,
 *   qui a ordonné la recette en connaissant la cuisine. Sur les six box de l'aperçu de la
 *   semaine 44, cet ordre finit plus tôt que la priorité au plus long chemin, et colle mieux à
 *   l'ordre des segments (cadrage § 7) ;
 * - une dépendance circulaire ne bloque rien : quand plus rien n'avance, la première tâche
 *   restante part sans attendre ce qui la retient.
 */
object Planificateur {

    fun planifier(recette: RecettePreparee, mode: ModeCuisine): Plan = planifier(recette.taches, mode)

    fun planifier(toutes: List<Tache>, mode: ModeCuisine): Plan {
        // Un identifiant par tâche : le lecteur l'assure ; un appel direct aussi, sans boucler.
        val taches = toutes.distinctBy { it.id }
        val ids = taches.map { it.id }.toSet()
        // Des dépendances vers des tâches connues seulement, pour la même raison.
        val dependances = taches.associate { t -> t.id to t.apres.filter { it in ids && it != t.id } }
        val debut = HashMap<String, Int>()
        val fin = HashMap<String, Int>()
        val cuisiniers = if (mode == ModeCuisine.SEUL) listOf<RoleCuisine?>(null) else RoleCuisine.entries
        val libre = HashMap<RoleCuisine?, Int>().apply { cuisiniers.forEach { put(it, 0) } }

        fun prete(tache: Tache, a: Int) = dependances.getValue(tache.id).all { (fin[it] ?: Int.MAX_VALUE) <= a }
        fun cuisinierDe(tache: Tache): RoleCuisine? = if (mode == ModeCuisine.SEUL) null else tache.qui
        fun placer(tache: Tache, a: Int) {
            debut[tache.id] = a
            fin[tache.id] = a + tache.minutes
            if (!tache.passive) libre[cuisinierDe(tache)] = a + tache.minutes
        }

        var maintenant = 0
        while (debut.size < taches.size) {
            taches.filter { it.id !in debut && it.passive && prete(it, maintenant) }.forEach { placer(it, maintenant) }
            for (cuisinier in cuisiniers) {
                if (libre.getValue(cuisinier) > maintenant) continue
                taches.firstOrNull { it.id !in debut && !it.passive && cuisinierDe(it) == cuisinier && prete(it, maintenant) }
                    ?.let { placer(it, maintenant) }
            }
            val prochain = (libre.values.asSequence() + fin.values.asSequence()).filter { it > maintenant }.minOrNull()
            if (prochain != null) {
                maintenant = prochain
                continue
            }
            // Plus rien ne tourne et rien n'est prêt : une dépendance circulaire. La première
            // tâche restante part sans l'attendre, et le plan reprend son cours.
            val bloquee = taches.first { it.id !in debut }
            placer(bloquee, maxOf(maintenant, if (bloquee.passive) 0 else libre.getValue(cuisinierDe(bloquee))))
        }
        val rang = taches.withIndex().associate { (i, t) -> t.id to i }
        val places = taches.map { Place(it, debut.getValue(it.id), fin.getValue(it.id)) }
            .sortedWith(compareBy<Place>({ it.debut }, { it.role.ordinal }, { rang.getValue(it.tache.id) }))
        return Plan(mode, places)
    }
}
