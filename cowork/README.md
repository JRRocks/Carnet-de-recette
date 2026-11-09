# Les skills Cowork du Carnet de recettes

| Skill | Quand | Lit | Écrit |
|---|---|---|---|
| [`carnet-semaine`](carnet-semaine/SKILL.md) | Le vendredi soir, en tâche planifiée ou à la main | `recettes-AAAA-Sww.json`, `profil-cuisine.json` | `semaine-AAAA-Sww.json` |

**Installer** : ajouter le dossier `carnet-semaine` (zippé) aux skills de Claude, puis planifier
une tâche le vendredi soir, avec Opus (cadrage § 6) : « Prépare la semaine du Carnet de
recettes ». Le skill n'a besoin que du connecteur Google Drive, jamais d'Internet.

**Changer la forme du fichier de la semaine** : des deux côtés, dans le même commit. Le skill
l'écrit ; l'app le lit avec `core/.../cuisine/LecteurSemaine.kt`, testé sur une semaine factice
(`core/src/test/.../cuisine/SemainePrepareeFactice.kt`), et un test relit l'exemple du skill :
après une modification du seul skill, lancer `./gradlew :core:test` (la CI d'aperçu ne part
que sur un changement de l'app). Un fichier d'une version plus récente que l'app est refusé
avec « mets l'app à jour ».

Aucun contenu de recette réelle ici : les exemples sont inventés (CLAUDE.md, règle 1).
