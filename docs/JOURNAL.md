# Journal du Carnet de recette

> Entrée la plus récente **en haut**. On ne réécrit pas une entrée passée : une erreur se corrige
> par une nouvelle entrée. Avant le 2026-11-09, l'histoire du projet est dans les messages de
> commit et dans les lots du [cadrage](cadrage.md) § 13.

## 2026-11-09 · Revue avec les skills de la Library, mémoire du projet, aperçu des visuels

- **Branche et commit** : `claude/recipe-app-planner-92zsww`, à la suite de `89275f2` (version 0.8).
- **Objectif** : trois demandes de l'utilisateur. « Note le réglage à faire pour plus tard, tu
  me le rappelleras à chaque session », « Utilise les skills en repo Library et passe
  l'application en revue », « Ajoute des visuels graphiques pour le rendre visuellement
  agréable ».
- **Réalisé** :
  - Mémoire du projet selon le skill `memoire-projet` : ce journal, [`RECETTE.md`](RECETTE.md)
    (ce que seul le téléphone montre), [`EN-ATTENTE.md`](EN-ATTENTE.md) (réglages et décisions
    qui attendent l'utilisateur), et la règle du rappel à chaque session dans `CLAUDE.md`.
  - Aperçu des visuels selon `apercu-avant-code` : l'état actuel (captures réelles, semaine
    factice de quatre box) et cinq variantes sur la Semaine et la Cuisine, en clair et en
    sombre : A « Signalétique de cuisine » (recommandée), B « Les plats en vedette »,
    C « La soirée en frise », D « Carnet illustré », E « Tout ensemble ». Page statique :
    [`previews/2026-11-09-visuels-graphiques.html`](previews/2026-11-09-visuels-graphiques.html),
    publiée aussi en page privée, notification envoyée. Appuis : `DESIGN.md`, le dossier
    UI/UX de la Library (chapitres 21 et 41), les guides *impeccable* (`delight`, `android`).
    **Aucun code d'interface** tant que le choix n'est pas fait.
  - CI : la clé de signature décodée est vérifiée tout de suite (`apercu.yml`, `version.yml`),
    avec un message en clair par secret fautif.
- **Ce que la revue a montré** :
  - `depot-public` : le contrôle ne trouve rien de privé dans les fichiers. Deux
    signalements, regardés un à un, tous deux faux : le mot `nextPageToken` dans un test, et
    des suites de lettres au hasard dans la police et les images intégrées à la page d'aperçu
    (le script compare le motif d'une clé Google sans tenir compte de la casse : à corriger
    dans la Library). Les commits de la session portent
    une identité `noreply` ; un commit créé depuis le site GitHub ne la porte pas : le réglage
    qui l'évite est en A4. Réécrire l'historique reste une décision de l'utilisateur.
  - `depannage-ci` : **`main` n'a que le README**. GitHub ne connaît donc que le workflow
    d'aperçu ; `version.yml` (publication), `sonde.yml` (sonde du jeudi) et `tests.yml` ne
    peuvent ni se lancer à la main ni tourner à heure fixe tant que la branche n'est pas
    fusionnée (décision D1).
  - `livraison` : l'APK remis (0.8) est la version **réduite** (R8), signée de la clé release.
    Elle n'a jamais tourné sur un téléphone : c'est le premier point de la recette (R1). Les
    sérialiseurs et les règles des bibliothèques sont bien dans le fichier de réduction ; les
    noms de classes sont brouillés, ce qui rendrait une pile d'appels illisible.
  - `moteurs` : le moteur `rapport-plantage` est maintenant dans la Library (✅ pour les
    exceptions Kotlin). Le cadrage § 11 l'attendait ; il n'est pas encore repris ici.
  - `memoire-projet` : pas de journal ni de liste de recette avant aujourd'hui ; le « Ce soir »
    de la Cuisine est un petit texte gris au-dessus d'un titre, contraire à la règle « pas de
    sur-titre » de `DESIGN.md` (question 2 de l'aperçu).
- **Décisions** (cadrage § 2) : le rappel à chaque session ; la conduite du projet confiée à la
  session ; la clé release créée par la session, secrets à poser plus tard par l'utilisateur.
- **Choix du développeur** (l'utilisateur peut y revenir) : la recommandation A pour les
  visuels ; les noms `JOURNAL.md`, `RECETTE.md`, `EN-ATTENTE.md` ; le contrôle de la clé qui
  refuse deux mots de passe différents (la clé du projet n'en a qu'un).
- **Vérifications** : `controle.sh` de la Library sur tout le contenu du dépôt, puis sur ce
  commit ; l'étape « Restaurer la clé » des deux workflows rejouée hors du dépôt avec la
  vraie clé, puis avec cinq secrets abîmés (quatorze cas sur les deux fichiers, tous au
  résultat attendu) ; la page d'aperçu rendue à 400 px et 1180 px, en clair et en sombre,
  sans défilement horizontal et sans script.
- **Non vérifié sur téléphone** : tout ce que liste [`RECETTE.md`](RECETTE.md) ; en premier,
  le lancement de l'APK réduit (R1).
- **Problèmes et dette** : la CI ne publie aucun lien d'aperçu tant que les secrets ne sont
  pas posés (A1) ; pas de rapport de plantage dans l'app ; noms de classes brouillés dans
  l'APK release alors que le code est public.
- **Prochaine étape** : coder les visuels choisis (D2), puis montrer le rendu réel à côté de
  l'aperçu. Avant le premier vrai vendredi : reprendre `rapport-plantage` de la Library et
  garder les noms de classes lisibles dans l'APK release, pour qu'un plantage se lise sur le
  téléphone.
