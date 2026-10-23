# Cadrage du Carnet de recettes

> État : **cadrage validé le 2026-10-23**, aucune ligne de code encore écrite.
> Ce document fait foi pour les décisions. Une décision qui change se corrige ici, avec sa date.

## 1. En une phrase

Le vendredi, je scanne l'étiquette de chaque box HelloFresh. Le vendredi soir, Claude Cowork
retrouve les recettes, les découpe en tâches pour deux cuisiniers et prépare la lecture
audio. Le samedi, l'app récupère le résultat sur Drive. En cuisine, chacun suit ses tâches
sur son téléphone, à l'écran et à l'oreille.

## 2. Décisions validées (2026-10-23)

| Sujet | Décision |
|---|---|
| Plateforme | **App Android native (APK)**, distribuée hors Play Store. Pas d'AppSheet (trop restreint) |
| Téléphones | **Un compte Google partagé**, **chacun son téléphone** |
| Stockage | Google Drive, dossier `Maison/Carnet de recettes/` |
| Source des recettes | Le site hellofresh.fr (données structurées, toutes portions, images). PDF et photo de fiche = **mode dégradé** seulement |
| Saisie | Scan de l'**étiquette de la box** (n° de recette, nombre de personnes), **sans regex** |
| Intelligence | **Claude Cowork le vendredi soir**, pas de LLM dans l'app |
| Audio | Lecture **par segments** courts que l'on valide, active ou planifie, jamais toute la recette d'un trait |
| Dépôts | `Library` : moteurs réutilisables · `Carnet-de-recette` : l'app et le skill Cowork |
| Légal | Code public sur GitHub, contenu HelloFresh en usage privé seulement, app jamais vendue |
| Mode dégradé | Place réservée dans le code, **moteurs non codés** tant que l'accès HelloFresh tient |
| Alerte | Une sonde prévient si l'accès HelloFresh casse, pour passer en mode dégradé |

## 3. La semaine type

```
VENDREDI, réception         VENDREDI SOIR, Cowork             SAMEDI               EN CUISINE
─────────────────────       ─────────────────────────         ──────────────       ─────────────────────
App : scan des              Tâche planifiée (skill) :         App : « Importer     Chaque téléphone :
étiquettes (207 · 4p…)      1. sonde HelloFresh               la semaine »         son rôle, ses tâches,
        │                   2. lit les scans + profil         (sélecteur Drive)    ses segments audio,
        ▼                   3. retrouve les recettes                 │             ses minuteurs
Drive : scans-AAAA-Sww.json 4. découpe en tâches, hachoir,           ▼                     ▲
        (écrit par l'app)      segments audio                 Base locale + images         │
                            5. écrit semaine-AAAA-Sww.json    en cache ────────────────────┘
                               (nouveau fichier sur Drive)
```

## 4. Les échanges avec Google Drive (point dur, tranché)

### Ce qui coince

- Le moteur Drive de la Library (`google-drive-appdata`, éprouvé sur Popote) écrit dans le
  **dossier caché** `appDataFolder`. **Claude Cowork ne peut pas le voir.** Il faut un
  dossier visible.
- Portée retenue : **`drive.file`**. Elle est non sensible, donc l'app peut être publiée sans
  validation Google : pas de reconnexion forcée tous les 7 jours, des amis peuvent l'utiliser.
  Mais avec `drive.file`, **l'app ne voit que les fichiers qu'elle a créés elle-même.**
- Le connecteur Drive de Claude peut **créer** un fichier, le renommer ou le déplacer, mais
  **pas réécrire le contenu d'un fichier existant**. Cowork ne peut donc pas remplir un
  fichier préparé par l'app : ce qu'il produit est un fichier neuf, invisible pour l'API de
  l'app.

### Ce qu'on fait

| Sens | Moyen | Pourquoi |
|---|---|---|
| App → Drive (scans, profil cuisine, session de cuisine) | **API Drive, portée `drive.file`**, moteur `google-drive-appdata` adapté (dossier visible au lieu d'`appDataFolder`) | Automatique, sans geste. Cowork lit tout le Drive, donc il voit ces fichiers |
| Cowork → App (résultat de la semaine) | **Sélecteur de fichiers Android** (fournisseur Google Drive), bouton « Importer la semaine » | Colle au « samedi je télécharge ». Aucune portée restreinte, aucun projet Cloud nécessaire pour ce sens |

**Option pour plus tard**, si le geste du samedi pèse : ajouter `drive.readonly` pour un
import automatique. C'est une portée **restreinte** : l'app reste en mode test, il faut
redonner l'accord à peu près chaque semaine, l'écran « application non validée » s'affiche,
et chaque ami doit être déclaré testeur (100 au maximum).

### Le dossier

L'app crée `Carnet de recettes` au premier branchement ; on le déplace une fois dans
`Maison/`. Le déplacement ne retire pas l'accès de l'app, et un second téléphone sur le
même compte le retrouve par une recherche.

```
Maison/Carnet de recettes/
├── profil-cuisine.json        (app)    matériel, cuisiniers, rôles, préférences
├── scans/
│   ├── scans-2026-S43.json    (app)    texte brut des étiquettes
│   └── etiquette-…jpg         (app)    photo de l'étiquette, en secours
├── semaines/
│   └── semaine-2026-S43.json  (Cowork) recettes enrichies de la semaine
└── sessions/                  (app)    synchro des deux téléphones pendant la cuisson
```

Les images des recettes ne passent pas par Drive. Le fichier de la semaine porte leurs
adresses, et l'app les télécharge puis les garde en cache sur le téléphone.

## 5. Scan de l'étiquette de la box, sans regex

L'étiquette porte aujourd'hui un grand numéro (`207`) et un nombre de personnes (`4p`).
On ne fige pas ce format.

- **Moteur** : `scan-multimode` de la Library (extrait de ScanList, OCR ✅ éprouvé), mode OCR :
  `OcrAnalyzer` → `ScanConsensus` (5 lectures identiques) → `ScanDebouncer` → `ScanFeedback`.
  On n'utilise **pas** `RegexRuleEngine`.
- **Adaptation nécessaire** : `OcrAnalyzer` ne remonte aujourd'hui que le texte brut. Il faut
  remonter aussi les **éléments** reconnus avec leur **hauteur et leur position** (taille de
  police relative), et ne garder que ceux qui sont dans le réticule. À consigner dans la fiche
  de la Library (AGENTS.md, règle 6).
- **Consensus** : il porte sur la « signature » de l'étiquette, c'est-à-dire l'ensemble des
  éléments normalisés (`ScanValue`) vus dans le réticule.
- **Ce qui part sur Drive** : les éléments bruts (texte, hauteur, position), l'heure du scan
  et une petite photo de l'étiquette en secours.
- **Interprétation** : **Claude**, le vendredi soir. Il trouve le n° de recette et le nombre
  de personnes, puis il **vérifie contre le menu de la semaine** : le numéro doit exister, et
  le nombre de personnes doit faire partie des portions proposées. Un doute marque la
  recette « à confirmer » au lieu de deviner. Si l'étiquette change de format, rien n'est à
  recoder.
- **À l'écran** : la liste des étiquettes capturées, telle que lue, avec suppression et
  nouveau scan possibles.

## 6. Le travail de Cowork le vendredi soir

Un **skill Cowork** (versionné dans ce dépôt ; les données sont sur Drive, le skill les lit
et les écrit), lancé par une **tâche planifiée** le vendredi soir, ou à la main.

1. **Sonde** HelloFresh (§ 9). En cas d'échec : fichier de semaine au statut `degrade`,
   notification, arrêt.
2. Lit `scans-…json` et `profil-cuisine.json`.
3. Interprète les étiquettes (§ 5) et retrouve les recettes dans le menu de la semaine. Le
   numéro seul ne suffit pas : d'une semaine à l'autre, le même numéro désigne une autre
   recette.
4. Récupère chaque recette **pour le nombre de personnes lu**. Les quantités de HelloFresh
   par portion sont les vraies quantités de la box, pas une multiplication.
5. **Enrichit** : tâches atomiques (§ 7), conseils hachoir, segments audio (§ 8).
6. Écrit `semaine-AAAA-Sww.json` dans `semaines/`.

Format du fichier de la semaine (esquisse, à figer au lot 3) :

```json
{
  "type": "semaine", "version": 1, "semaine": "2026-W43",
  "statut": "ok | partiel | degrade", "message": "…",
  "recettes": [{
    "numero": 207, "personnes": 4, "a_confirmer": false,
    "source": { "type": "hellofresh", "id": "…", "url": "…" },
    "titre": "…", "sous_titre": "…", "duree_min": 30,
    "ingredients": [{ "nom": "…", "quantite": 2, "unite": "pièce(s)", "image": "https://…" }],
    "etapes": [{ "numero": 1, "texte": "…", "image": "https://…" }],
    "taches": [{
      "id": "t3", "type": "decoupe | chauffe | attente | dressage",
      "duree_min": 5, "apres": ["t1"], "ingredients": ["…"], "ustensile": "…",
      "texte": "…", "hachoir": { "avec": ["t4"], "mode": "ensemble | a_la_suite", "ordre": "…" }
    }],
    "segments_audio": [{
      "id": "a3", "tache": "t3", "texte": "…",
      "declencheur": "manuel | fin_tache_precedente | fin_minuteur"
    }]
  }]
}
```

## 7. Le planificateur

**Principe** : Claude réfléchit à l'import ; l'app ordonnance en cuisine.

- **À l'import** : chaque étape est découpée en **tâches atomiques** avec un type (découpe,
  chauffe, attente passive, dressage), une durée, des dépendances (« poireaux émincés » avant
  « poireaux à la poêle »), les ingrédients et l'ustensile. HelloFresh ne fournit pas ce
  découpage : ses étapes sont du texte libre, sans minuteur ni lien vers les ingrédients.
- **En cuisine** : un ordonnancement **déterministe** répartit les tâches selon les cuisiniers
  présents. Seul, tout s'enchaîne. À deux, l'un découpe et l'autre chauffe ; les rôles sont des
  préférences, pas des murs (celui qui est libre prend la tâche prête). Les deux téléphones
  calculent **le même plan** à partir des mêmes données.
- **Hachoir** (selon `profil-cuisine.json`) :
  - même moment d'utilisation et même destination → **ensemble** dans le hachoir ;
  - moments différents → **à la suite sans rincer**, en commençant par le plus sec et le plus
    neutre ;
  - un ingrédient qui finit dans une sauce mixée → **la sauce se fait dans le hachoir** (un
    bol de moins) ;
  - quantité trop faible pour la cuve → couteau.
- **Synchro des deux téléphones** : chaque « tâche terminée » est écrite dans un fichier de
  session sur Drive (créé par l'app, donc lisible par les deux téléphones), relu toutes les
  quelques secondes. Si la latence gêne, on passera à une liaison directe entre téléphones
  (Nearby Connections, sans Internet). À trancher au lot 6.

## 8. La lecture audio par segments

- Un **segment** = une action courte (une tâche, ou une partie de tâche). Jamais la recette
  de A à Z.
- Pour chaque segment, l'utilisateur peut :
  - **valider** : c'est fait, on passe au suivant ;
  - **permettre** : activer ou couper sa lecture (par exemple, couper « L'astuce du chef ») ;
  - **planifier** : choisir le déclencheur (manuel, fin de la tâche précédente, fin d'un
    minuteur).
- Chaque téléphone ne lit que les segments **de son cuisinier**.
- Texte écrit **pour l'oral** à l'import : « ½ cc » devient « une demi-cuillère à café ».
- Minuteurs déduits des durées (« 12-14 min »).
- Synthèse vocale **Android `TextToSpeech`** (voix françaises hors ligne). Commandes vocales
  (« suivant », « répète ») plus tard.

## 9. Mode dégradé et alerte

**Sonde** : la page répond, le jeton anonyme est présent, le menu de la semaine répond, et
une recette contient bien les champs attendus (nom, ingrédients, étapes, portions).

| Où | Quand | Effet si ça casse |
|---|---|---|
| Début du travail Cowork | Chaque vendredi soir | Fichier de semaine au statut `degrade` + notification. Au samedi, l'app affiche un bandeau « Accès HelloFresh rompu : passe en mode dégradé (PDF ou photo de la fiche) » |
| GitHub Actions (optionnel) | Chaque jeudi | Ouvre une issue sur ce dépôt, donc un e-mail, **avant** le vendredi |

**Mode dégradé, version 1 : sans code dans l'app.** On donne le PDF ou la photo de la fiche
à Cowork, et Claude la lit directement. Il produit le même fichier de semaine.

**Place réservée dans le code** (interfaces et champs seulement, **aucun moteur**) :

```
SourceRecette
├── HelloFreshSource   actif
├── PdfSource          réservé (PDF imprimé du site)
├── PhotoFicheSource   réservé (OCR ou vision sur la fiche papier)
└── JsonLdSource       réservé (autres sites de recettes : format schema.org Recipe)
```

## 10. Légal

- CGU HelloFresh, article 12 : reproduction et rediffusion interdites « en dehors de toute
  utilisation privée ». On reste donc dans l'usage privé : chaque utilisateur récupère ses
  propres recettes, et le contenu reste sur son Drive et son téléphone.
- **Le dépôt public ne contient que du code.** Aucun texte, aucune photo, aucun JSON de
  recette réelle, y compris dans les tests.
- Requêtes vers HelloFresh : quelques-unes par semaine et par utilisateur, jamais de
  moissonnage du catalogue.
- Partage à des amis : chacun son compte Google et son Drive, aucun contenu ne transite par
  nous.

## 11. Moteurs de la Library réutilisés

| Moteur | Statut Library | Usage ici | Adaptation |
|---|---|---|---|
| `scan-multimode` (mode OCR) | ✅ OCR | Scan des étiquettes | Remonter les éléments avec taille et position ; pas de `RegexRuleEngine` |
| `google-drive-appdata` | ✅ | Connexion Google, envoi des scans, profil, sessions | Portée `drive.file` et dossier visible au lieu d'`appDataFolder` |
| `maj-auto-github` | ✅ | Mise à jour de l'APK | Dépôt de code déjà public : les Releases peuvent être publiées directement ici |
| Capteur de plantage (Popote, 🔬 candidat) | — | Rapport de plantage sans câble | À extraire d'abord dans la Library |

> Le catalogue de la Library (`INDEX.md`, fiches) est sur la branche
> `claude/library-docs-catalog-xkjytv`, **pas encore fusionnée dans `main`**.

## 12. Points à vérifier

- [ ] **Cowork joint-il hellofresh.fr ?** Mes tests ont été faits depuis un conteneur cloud.
      Si Cowork n'y arrive pas, le tester sur le mode dégradé (photo ou PDF).
- [ ] **Semaine du menu** : vérifier que le numéro de l'étiquette se lit dans le menu de la
      semaine de livraison (premier vendredi réel).
- [ ] La console Google accepte-t-elle deux SHA-1 (debug et release) sur un même client
      Android, ou faut-il deux clients ? (La fiche Library dit « un client, deux
      empreintes ».)
- [ ] Le sélecteur de fichiers Android voit-il bien Google Drive sur les deux téléphones ?
- [ ] La compilation Android dans les sessions cloud demande que l'environnement installe le
      SDK Android (comme pour Popote).

## 13. Lots de réalisation

| Lot | Contenu | Validation |
|---|---|---|
| 0 | Ce cadrage | ✅ 2026-10-23 |
| 1 | Squelette Android (Kotlin, Compose, minSdk 26), clé debug versionnée, CI de publication (`maj-auto-github`), connexion Drive `drive.file` et création du dossier | Configuration Google Cloud faite ensemble ([guide](google-cloud.md)) |
| 2 | Scan des étiquettes (`scan-multimode` adapté) et envoi des scans sur Drive | Un vrai vendredi |
| 3 | Skill Cowork du vendredi soir : sonde, interprétation, récupération, enrichissement, fichier de semaine | Test d'accès Cowork, puis un vrai vendredi soir |
| 4 | Import du samedi (sélecteur), catalogue local, images en cache, bandeau mode dégradé | — |
| 5 | Mode cuisine : planificateur seul ou à deux, hachoir, segments audio, minuteurs | Un vrai repas à deux |
| 6 | Synchro des deux téléphones | — |
| Réservé | Moteurs du mode dégradé dans l'app, autres sites de recettes | Seulement si l'accès HelloFresh tombe |
