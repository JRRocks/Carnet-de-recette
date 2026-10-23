# CLAUDE.md

Carnet de recettes : une app Android (APK hors Play Store), un skill Claude Cowork, et les
données sur le Google Drive du foyer.

**Avant toute action, lisez [`docs/cadrage.md`](docs/cadrage.md) en entier** : décisions
validées, architecture, déroulé de la semaine, ce qui est volontairement reporté.

## Règles absolues

1. **Dépôt PUBLIC.** Jamais de secret (jeton, keystore release, empreinte SHA-1 release,
   identifiant de dossier Drive), jamais de donnée personnelle (e-mail, prénoms), jamais de
   **contenu HelloFresh** (textes, photos, JSON de recettes réelles). Les fixtures de test
   sont des recettes **factices**.
2. **Contenu HelloFresh : usage privé uniquement** (CGU HelloFresh, article 12). Il reste
   sur le Drive et le téléphone de chaque utilisateur. L'app ne sera jamais vendue ; elle
   peut être partagée à des amis, chacun avec son propre compte Google.
   **Pages publiques seulement**, lues comme un navigateur : jamais de jeton extrait d'une
   page, jamais d'interface interne du site (`/gw/…`). Ce schéma est traité comme un
   contournement d'accès (cadrage § 2).
3. **La Library d'abord.** Avant d'écrire une brique, cherchez dans
   [JRRocks/Library](https://github.com/JRRocks/Library) (`INDEX.md`) et suivez sa procédure
   AGENTS.md § 5.A : copie, renommage des packages, tests repris, ligne « Utilisé dans ».
   Toute adaptation d'un moteur se consigne aussi dans sa fiche de la Library.
4. **Mode dégradé : place réservée, pas de code.** Les moteurs de secours (OCR de la fiche
   recette, lecture de PDF, extraction par regex, autres sites de recettes) ne se codent pas
   tant que l'accès HelloFresh fonctionne. Seuls l'interface `SourceRecette` et le champ
   `source` du modèle les prévoient (cadrage § 9).
5. **Pas de regex pour lire l'étiquette des box** : le format peut changer. L'app reconnaît
   la recette en comparant le texte lu aux numéros du menu de la semaine, garde le texte
   brut, et Claude confirme (cadrage § 5).
6. **L'app récupère, Cowork réfléchit, personne ne recopie.** L'app lit les pages publiques
   le vendredi. Cowork travaille à partir de ce qu'elle a déposé sur Drive, sans accès au
   site, et écrit avec ses mots ; on ne lui demande jamais de recopier un texte d'origine.
   **Pas d'IA pendant la cuisson** : en cuisine, l'app est déterministe et marche hors
   ligne.
7. **Français** pour la documentation, l'interface et les messages de commit.
