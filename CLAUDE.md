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

## Compiler et tester

Un conteneur cloud n'a pas de SDK Android. Une fois par session :

```bash
./outils/sdk-android.sh
./gradlew --max-workers=1 :core:test :app:testDebugUnitTest :app:assembleDebug
```

`--max-workers=1` au premier passage : Maven Central répond « 429 Too Many Requests » aux
téléchargements en parallèle depuis le proxy des sessions cloud. Si un 429 apparaît quand
même, relancer après une courte pause.

**Voir un écran avant de l'installer** : les tests `app/src/test/.../capture/` rendent les
écrans en PNG sous Robolectric, sans émulateur (méthode de Popote). Les images sortent dans
`app/build/captures/` : les regarder avec `Read`, puis les montrer à l'utilisateur.

## Interface

Avant de toucher un écran : [`docs/design/direction.md`](docs/design/direction.md) (le
pourquoi, les écrans cibles des lots suivants) et [`DESIGN.md`](DESIGN.md) (les valeurs).
Le thème et les composants communs sont dans `ui/theme/` et `ui/composants/` ; on s'en sert
plutôt que de recréer un bouton, une ligne d'état ou un schéma. Aucun mot de métro dans les
textes (station, terminus, correspondance) : le monde se voit, il ne se lit pas.

## Après chaque changement de l'APK : aperçu

1. Incrémenter `versionCode` et `versionName` dans `app/build.gradle.kts`.
2. Commit et push sur la branche de travail. `.github/workflows/apercu.yml` lance les tests
   et, **si la clé release est posée**, attache l'APK à la pré-version `apercu-<versionName>`.
3. Vérifier que le run se termine avec succès (`mcp__github__actions_list` /
   `actions_get`) **et** que la pré-version existe (`mcp__github__get_release_by_tag`,
   `apercu-<versionName>`). Si elle existe, donner le lien :
   `https://github.com/JRRocks/Carnet-de-recette/releases/tag/apercu-<versionName>`.
   Sinon (étape « Publier la pré-version d'aperçu » sautée, run vert quand même), dire à
   l'utilisateur que la clé release et ses secrets ne sont pas encore posés
   (`docs/publication.md`, mise en place) au lieu de donner un lien mort.

Une pré-version n'est jamais proposée en mise à jour par l'app : l'aperçu ne touche aucun
téléphone tant que l'utilisateur ne l'installe pas lui-même.

Sans les secrets de la clé release, `apercu.yml` lance les tests mais ne publie rien : la clé
de debug est publique, son empreinte n'est pas déclarée à Google, et un APK de debug ne se
connecterait pas au Drive (docs/google-cloud.md).

## Publication : uniquement sur feu vert explicite

L'APK publié par `.github/workflows/version.yml` est proposé à **tous** les téléphones au
lancement suivant. Ne le lancer que si l'utilisateur dit explicitement qu'on peut publier
**cette** version ; une autorisation ne vaut pas pour les suivantes. En cas de doute : ne pas
publier, demander. Marche à suivre : [`docs/publication.md`](docs/publication.md).
