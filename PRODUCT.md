# Product

<!-- impeccable:product-schema 1 -->

> Écrit le 2026-10-26 à partir des décisions validées dans `docs/cadrage.md` (aucune
> invention : ce qui n'y figure pas est marqué « ouvert »).

## Platform

android

## Users

Un foyer abonné à HelloFresh : deux personnes qui cuisinent **ensemble**, chacune avec son
téléphone, sur un même compte Google. L'une découpe, l'autre s'occupe de la chauffe ; les
rôles sont des préférences, pas des murs. L'app peut être partagée à quelques amis abonnés,
chacun avec son propre compte.

## Product Purpose

Transformer la box HelloFresh de la semaine en une cuisine à deux sans friction : on scanne
les box le vendredi, Claude prépare le travail le vendredi soir, on importe la semaine le
samedi, et en cuisine chacun suit **ses** tâches, à l'écran et à l'oreille. Réussite : un
repas à deux où personne ne relit la fiche papier et où les deux mains restent dans la
cuisine.

## Positioning

La fiche HelloFresh décrit une recette pour une personne qui fait tout. Le Carnet la
découpe en tâches réparties entre **deux cuisiniers**, adaptées au matériel du foyer (par
exemple le hachoir : ensemble, à la suite, ou sauce faite dans la cuve), et lues à voix
haute **par segments**. Le contenu d'origine reste celui du site, en usage privé.

## Operating Context

- **Vendredi, réception** : on range les box et on scanne l'étiquette de chacune (n° de
  recette, nombre de personnes). Geste rapide, à la chaîne, téléphone tenu d'une main.
- **Vendredi soir** : une tâche planifiée de Claude Cowork prépare tâches, conseils hachoir
  et segments audio, sans intervention.
- **Samedi** : un geste, « Importer la semaine » depuis Google Drive.
- **En cuisine** : téléphone posé sur le plan de travail, à distance de bras, mains
  occupées ou mouillées, minuteurs qui tournent, lecture audio. Souvent le soir, sous
  l'éclairage de la cuisine.
- Les deux téléphones calculent le même plan ; chacun ne lit que les segments de son
  cuisinier.

## Capabilities and Constraints

- App Android native (Kotlin, Jetpack Compose, Material 3, minSdk 26), hors Play Store,
  mise à jour automatique depuis les Releases GitHub.
- Données sur le Google Drive du foyer (portée `drive.file`, dossier « Carnet de
  recettes »). Retour du travail de Cowork par le sélecteur de fichiers Android.
- Recettes lues sur les pages **publiques** de hellofresh.fr ; usage privé seulement.
- Pas d'IA dans l'app ; en cuisine, tout est déterministe et marche hors ligne.
- Mode dégradé (PDF, photo de fiche) prévu mais non codé.
- Construit par lots : aujourd'hui seuls le branchement Drive et la mise à jour existent
  (lot 1) ; scan (lot 2), travail Cowork (lot 3), import et catalogue (lot 4), mode cuisine
  (lot 5), synchro des deux téléphones (lot 6) restent à faire.

## Brand Commitments

- Nom : « Carnet de recette ». Langue : français, tutoiement.
- Aucun élément graphique HelloFresh (logo, photos, vert citron) : l'app ne doit pas se
  faire passer pour HelloFresh.

## Evidence on Hand

- Aucune photo ni texte de recette dans le dépôt (public) ; en usage, les images viennent du
  site et restent en cache sur le téléphone. Les captures et tests utilisent des données
  factices.

## Product Principles

1. **Les mains restent dans la cuisine** : lisible à distance de bras, gros gestes, l'audio
   avant l'écran.
2. **Chacun ses tâches** : l'écran d'un cuisinier ne montre que ce qui le concerne.
3. **La semaine a un rythme** : vendredi, vendredi soir, samedi, cuisine ; l'app dit
   toujours où l'on en est et ce qui vient.
4. **Rien ne se perd** : hors ligne, l'app continue ; une erreur dit sa cause et quoi faire.

## Accessibility & Inclusion

Lecture à distance de bras et en mouvement : contraste et taille de texte généreux, cibles
tactiles ≥ 48 dp, respect de la taille de police système et du thème sombre.
