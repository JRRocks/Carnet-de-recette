---
name: carnet-semaine
description: Prépare la semaine de cuisine du Carnet de recettes à partir du fichier « recettes-AAAA-Sww.json » déposé sur le Drive par l'app — confirme chaque box, découpe chaque recette en tâches pour deux cuisiniers (découpe et chauffe), dit quand servent le hachoir et la râpe électrique, écrit les segments que la voix lira en cuisine, puis crée « semaine-AAAA-Sww.json » sur le Drive. À utiliser le vendredi soir (tâche planifiée ou à la main) dès qu'on parle de préparer la semaine du Carnet de recettes, des box de la semaine ou du fichier de la semaine.
---

# Le vendredi soir du Carnet de recettes

Un foyer a reçu ses box de repas. Son application (le Carnet de recettes) a lu les étiquettes
et les pages publiques des recettes, et a déposé ce qu'elle a trouvé sur le Drive. Tu prépares
la semaine de cuisine **à partir de ces fichiers seulement** : demain, l'app l'importe, et en
cuisine chaque téléphone lit à son cuisinier ses propres tâches, à voix haute, sans aucune IA.

Modèle conseillé : Opus. Compte une quinzaine de minutes par box ; ce n'est pas un problème un
vendredi soir.

## Ce que tu ne fais jamais

- **Aucun accès au site de recettes ni à Internet.** N'utilise aucun outil web : tout ce qu'il
  te faut est dans les fichiers du Drive.
- **Ne recopie jamais une phrase du texte d'origine.** Tu reformules, tu raccourcis, tu
  ordonnes, avec tes propres mots. Le fichier que tu écris contient ton travail et des
  références aux recettes (numéro, identifiant, adresse), jamais une copie de leur contenu. Le
  contenu des recettes est à usage privé et reste sur le Drive du foyer.
- Ne réécris aucun fichier existant : tu crées des fichiers neufs.

## 1. Trouver les fichiers

Dans le dossier « Carnet de recettes » du Drive (souvent rangé dans « Maison ») :

- `recettes-AAAA-Sww.json` (dans `recettes/`, ou ailleurs dans le dossier ; tant que l'app ne
  le dépose pas elle-même, le foyer l'y enregistre depuis l'app) : prends celui de la semaine
  qu'on te demande, sinon le plus récent. Son champ `semaine` (« 2026-W44 ») fait foi.
- `profil-cuisine.json` : les deux cuisiniers, leurs rôles et le matériel de la cuisine.

S'il manque le profil : en conversation, demande quel matériel il y a (hachoir, râpe
électrique, robot…) et qui fait quoi, puis crée `profil-cuisine.json` (forme plus bas) pour les
fois suivantes. En tâche planifiée, ne demande rien : suppose deux cuisiniers, l'un à la
découpe, l'autre à la chauffe, avec une planche et un couteau, et dis-le dans ton compte rendu.

Ce que contient `recettes-…json` :

- `statut` : `degrade` si l'app n'a pas pu lire le site. Alors **arrête-toi** : écris le fichier
  de la semaine avec `"statut": "degrade"`, son `message`, aucune recette, et dis au foyer de
  photographier les fiches papier depuis l'app.
- `boxes[]`, une par étiquette scannée : `numero` (la recette au menu), `personnes` (proposé
  par l'app), `aConfirmer` (doute de l'app), `elements` (le texte lu sur l'étiquette, avec la
  hauteur des lettres et leur position dans le cadre de visée, de 0 à 1), `recetteId`.
- `recettes[]`, lues sur les pages publiques : `id`, `titre`, `ingredients[]` (`id`, `nom`,
  `livre` : vrai si dans la box, faux si à avoir chez soi), `portions[]` (pour chaque nombre de
  personnes : `quantites[]` avec `ingredient`, `montant`, `unite`), `etapes[]` (`numero`,
  `texte` d'origine), `ustensiles`, `dureeTotaleMin`, `dureePreparationMin`.
- `fiches[]` : des fiches papier photographiées, en secours (texte brut lu par l'app).

## 2. Ce que tu fais, box par box

1. **Confirmer la box** : la recette et le nombre de personnes, à partir des éléments lus sur
   l'étiquette (le gros nombre est le numéro ; le nombre de personnes est souvent écrit
   « 2P », « 4 pers. »…). En cas de doute, `a_confirmer: true` et dis pourquoi dans
   `confirmation`, en une phrase.
2. **Découper la recette en tâches atomiques** pour deux cuisiniers : l'un découpe, l'autre
   tient les feux.
   - Une tâche = une action courte qu'une seule personne fait d'un trait.
   - `type` : `decoupe` (éplucher, couper, hacher, râper, presser), `chauffe` (four, feu,
     assaisonner pendant la cuisson), `attente` (cuisson passive : four, mijotage, repos),
     `dressage`.
   - `role` : qui la fait, `decoupe` ou `chauffe`. Il peut différer du type : une tâche de
     découpe peut revenir à la chauffe quand elle est libre (« celui qui est libre prend la
     tâche prête »). Une attente porte le rôle de qui la surveille.
   - `duree_min`, `apres` (les tâches qui doivent être finies avant), `ingredients` **avec la
     quantité pour le nombre de personnes confirmé**, `ustensile`, `etape_origine`.
   - **L'ordre des tâches compte** : à deux, chaque téléphone donne à son cuisinier la
     première tâche prête de son rôle, dans ton ordre. Range-les pour que les deux travaillent
     en même temps et que la cuisson n'attende pas la découpe : ce dont la chauffe a besoin en
     premier se découpe en premier.
3. **Le matériel du foyer** (`materiel` de chaque tâche) : dis quand utiliser le hachoir ou la
   râpe électrique, et quand ils ne servent à rien.
   - Hachoir : même moment et même destination → ensemble dans la cuve ; moments différents →
     à la suite sans rincer, du plus sec et neutre au plus humide et parfumé ; un ingrédient
     qui finit dans une sauce mixée → la sauce se fait dans le hachoir ; quantité trop faible
     pour la cuve → couteau.
   - Râpe électrique : seulement s'il y a quelque chose à râper. Ne force pas son usage.
4. **Les segments audio** : ce que la voix lit à chaque cuisinier, sur son propre téléphone
   (chacun n'entend que les siens), **dans l'ordre où il les entendra**.
   - Un segment = une action courte, en une ou deux phrases, jamais la recette entière.
   - Écrit pour l'oreille : nombres et unités en toutes lettres quand c'est plus clair
     (« une demi-cuillère à café », « deux cents degrés »), pas d'abréviation, pas de
     parenthèses, pas de symbole.
   - Quand un cuisinier finit ce dont l'autre a besoin, le segment dit de le lui passer. Ce
     segment porte aussi `texte_seul` : la même consigne pour qui cuisine seul (« Roule les
     dés dans la farine. » au lieu de « …, puis passe-les à la chauffe. »). Un segment qui n'a
     pas de sens seul (« Attends que la découpe t'apporte le poulet. ») porte
     `"texte_seul": ""` : il est sauté quand on cuisine seul.
   - `declencheur` : `manuel` (on demande le segment), `fin_tache_precedente` (dès que le
     segment d'avant du même cuisinier est fait), `fin_minuteur` (à la fin du minuteur du
     segment d'avant du même cuisinier). Le premier segment de chaque cuisinier est `manuel`.
   - Un segment qui lance une cuisson minutée porte `minuteur_s` (en secondes ; la borne haute
     d'un intervalle). Pas de minuteur inventé : une durée de cuisson vient de la recette.
   - Les conseils utiles se disent au bon moment ; les phrases sans action (« respecte les
     quantités indiquées ») disparaissent.
5. **Ce que la recette ne dit pas toujours, et que tu dis** :
   - la **cuisson à cœur** de la volaille, du porc et de la viande hachée : comment vérifier
     (plus de rose, jus clair) avant de servir ;
   - le **lavage des mains** (et de la planche, du couteau) après avoir touché de la viande ou
     du poisson cru, avant de toucher autre chose.
6. **Les données douteuses** : signale-les au lieu de les suivre ou de les corriger en
   silence. Une unité étrange (« 2 pièces » pour une épice en poudre), une durée totale plus courte que la
   préparation, un seul sachet prévu pour deux fois plus de personnes… Dis ce que tu as compris
   et ce qu'il faut vérifier, dans `confirmation` (et `a_confirmer: true` si cela change ce
   qu'on achète ou ce qu'on sert), et dans ton compte rendu.

Quantités : celles de `portions` pour le nombre de personnes confirmé. Le texte d'origine parle
« par personne » ou de « quantité indiquée » : convertis avec les portions, ne laisse jamais
« par personne ». Ne multiplie pas à la main ce que les portions donnent déjà.

Le français partout ; tutoiement, ton calme et bref.

## 3. L'ordre de la semaine

Range les recettes **dans l'ordre où les cuisiner** : d'abord ce qui se garde le moins
(poisson, viande hachée, herbes fraîches, salade), puis le reste. L'app propose la première
recette pas encore cuisinée comme « ce soir ».

## 4. Écrire le fichier de la semaine

Crée `semaine-AAAA-Sww.json` (« semaine-2026-S44.json ») dans `semaines/` du dossier
« Carnet de recettes ». Si ce nom est déjà pris (tu as déjà préparé cette semaine), ajoute
« -v2 », « -v3 »… : l'app lit la semaine dans le fichier, pas dans son nom.

Puis dis au foyer, en quinze lignes au plus : ce que tu as confirmé (et ce qui reste à vérifier),
l'organisation à deux, le matériel, les points d'attention. En tâche planifiée, écris ce compte
rendu dans `semaines/compte-rendu-AAAA-Sww.md`.

Forme du fichier (version 1 ; l'app refuse un fichier d'une autre semaine que celle qu'elle
cuisine) :

```json
{
  "type": "semaine",
  "version": 1,
  "semaine": "2026-W44",
  "statut": "ok",
  "message": null,
  "recettes": [{
    "numero": 207,
    "personnes": 2,
    "a_confirmer": false,
    "confirmation": "Le numéro et le 2 se lisent nettement sur l'étiquette.",
    "source": { "type": "hellofresh", "id": "<recetteId>", "url": "<adresse de la recette>" },
    "taches": [{
      "id": "t1",
      "type": "decoupe",
      "role": "decoupe",
      "duree_min": 4,
      "apres": [],
      "ingredients": ["2 filets de poulet"],
      "ustensile": "planche et couteau",
      "texte": "Couper le poulet en dés",
      "etape_origine": 1,
      "materiel": null
    }, {
      "id": "t2",
      "type": "decoupe",
      "role": "decoupe",
      "duree_min": 2,
      "apres": [],
      "ingredients": ["1 oignon nouveau"],
      "ustensile": "hachoir",
      "texte": "Hacher l'oignon nouveau",
      "etape_origine": 3,
      "materiel": { "outil": "hachoir", "conseil": "Quelques impulsions suffisent.", "avec": [], "mode": null }
    }],
    "segments_audio": [{
      "id": "a1",
      "tache": "t1",
      "role": "decoupe",
      "texte": "Coupe les deux filets de poulet en gros dés, puis passe-les à la chauffe.",
      "texte_seul": "Coupe les deux filets de poulet en gros dés.",
      "declencheur": "manuel",
      "minuteur_s": null
    }]
  }]
}
```

- `statut` : `ok`, `partiel` (une box n'a pas pu être préparée : dis laquelle dans `message`),
  `degrade` (voir plus haut).
- `texte` d'une tâche : le libellé court affiché sur le plan, six mots environ, à l'infinitif.
- `materiel` vaut `null` quand aucun outil ne sert ; `outil` : `hachoir`, `rape_electrique` ou
  `aucun` ; `mode` : `ensemble` ou `a_la_suite` (avec les tâches de `avec`), sinon `null`.
- `texte_seul` est facultatif : absent, le segment se dit pareil quand on cuisine seul.
- Les identifiants sont uniques dans une recette ; `apres` et `tache` ne citent que des
  identifiants de la même recette.
- Rends un JSON valide, en UTF-8.

## Forme de `profil-cuisine.json`

```json
{
  "type": "profil",
  "version": 1,
  "cuisiniers": [
    { "role": "decoupe", "prenom": "…" },
    { "role": "chauffe", "prenom": "…" }
  ],
  "materiel": ["hachoir", "rape_electrique"],
  "remarques": "…"
}
```

Les prénoms sont facultatifs ; ce fichier reste sur le Drive du foyer.
