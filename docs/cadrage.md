# Cadrage du Carnet de recettes

> État : **cadrage validé le 2026-10-23**, révisé le même jour après le test d'accès Cowork,
> révision validée le 2026-10-25. **Lot 1 codé** (squelette, connexion Drive, publication),
> en attente de la configuration Google Cloud et d'un essai sur téléphone.
> **2026-10-27** : priorité aux outils (scans, lecture du site, images, audio) ; la
> configuration OAuth (clé release, Google Cloud) passe **à la toute fin**. Les outils
> marchent donc **sans Drive** : tout reste sur le téléphone, et part sur Drive une fois
> branché.
> Ce document fait foi pour les décisions. Une décision qui change se corrige ici, avec sa date.

## 1. En une phrase

Le vendredi, je scanne l'étiquette de chaque box HelloFresh et l'app récupère les recettes
sur le site. Le vendredi soir, Claude Cowork les découpe en tâches pour deux cuisiniers et
prépare la lecture audio. Le samedi, l'app récupère ce travail sur Drive. En cuisine, chacun
suit ses tâches sur son téléphone, à l'écran et à l'oreille.

## 2. Décisions

| Sujet | Décision | Date |
|---|---|---|
| Plateforme | **App Android native (APK)**, distribuée hors Play Store. Pas d'AppSheet (trop restreint) | 2026-10-23 |
| Téléphones | **Un compte Google partagé**, **chacun son téléphone** | 2026-10-23 |
| Stockage | Google Drive, dossier `Maison/Carnet de recettes/` | 2026-10-23 |
| Source des recettes | **Pages publiques** de hellofresh.fr, lues comme le ferait un navigateur : le menu de la semaine et la page de chaque recette (§ 5). **Jamais de jeton extrait d'une page, jamais d'interface interne du site.** PDF et photo de fiche = **mode dégradé** seulement | 2026-10-23, révisé |
| Qui récupère | **L'app, au moment du scan** (vendredi). Cowork n'accède plus au site | 2026-10-23, validé le 2026-10-25 |
| Nom | App « **Carnet de recette** », identifiant Android `fr.jrrocks.carnetderecette` (définitif dès la première version distribuée) | 2026-10-25 |
| Saisie | Scan de l'**étiquette de la box** (n° de recette, nombre de personnes), **sans regex** | 2026-10-23 |
| Fiche papier | Scan de la **fiche recette papier** codé dès maintenant, en secours : photos et texte brut lu par OCR (blocs et positions), **sans regex ni interprétation dans l'app** ; Cowork structure la recette le vendredi soir (§ 9) | 2026-10-27, demande de l'utilisateur |
| Intelligence | **Claude Cowork le vendredi soir**, à partir des données déposées sur Drive. Pas de LLM dans l'app | 2026-10-23 |
| Audio | Lecture **par segments** courts que l'on valide, active ou planifie, jamais toute la recette d'un trait | 2026-10-23 |
| Commandes vocales | « suivant », « répète », « pause »… codées dès maintenant : reconnaissance vocale d'Android **sur l'appareil** quand il le permet, phrases comparées à une liste connue, sans IA (§ 8) | 2026-10-27, demande de l'utilisateur |
| Dépôts | `Library` : moteurs réutilisables · `Carnet-de-recette` : l'app et le skill Cowork | 2026-10-23 |
| Légal | Code public sur GitHub, contenu HelloFresh en usage privé seulement, app jamais vendue | 2026-10-23 |
| Mode dégradé | Place réservée dans le code, **moteurs non codés** tant que l'accès HelloFresh tient, **sauf la fiche papier** (ligne « Fiche papier ») | 2026-10-23, révisé le 2026-10-27 |
| Alerte | Une sonde prévient si l'accès HelloFresh casse, pour passer en mode dégradé | 2026-10-23 |
| Visuel | Direction « **Correspondance** » (schéma de ligne, bleu nuit, Atkinson Hyperlegible Next), **4 onglets** : Semaine, Carnet, Cuisine, Réglages. La mise à jour se propose en ligne d'état, plus en boîte de dialogue ([direction](design/direction.md), [DESIGN.md](../DESIGN.md)) | 2026-10-26 |

### Pourquoi la récupération est passée de Cowork à l'app (test du 2026-10-23)

| Test dans Cowork | Résultat |
|---|---|
| Lire une page recette publique avec l'outil web | ✅ Ingrédients obtenus (portion affichée seulement). Étapes **résumées** : Cowork ne recopie pas un texte d'origine mot pour mot |
| Script : jeton extrait de la page, puis appel de l'interface interne `/gw/…` | ❌ **Bloqué** par le garde-fou de Cowork (« contournement d'accès à un site ») |

On en tire trois conséquences :

1. **Abandon du jeton et de l'interface interne, partout.** Ce schéma est vu comme un
   contournement d'accès. Ça ne colle pas avec une démarche légale, même s'il fonctionnait
   depuis un conteneur cloud.
2. **Les pages publiques suffisent** (vérifié) :
   - `https://www.hellofresh.fr/menus/AAAA-Www` donne le menu d'une semaine, numéros
     compris, pour les semaines passées aussi ;
   - la page de chaque recette contient ses données complètes : portions de 1 à 6,
     images du plat, des étapes et des ingrédients.
3. **L'app récupère et Cowork réfléchit.**
   - L'app lit ces pages comme un navigateur et garde le contenu d'origine sur le téléphone
     et le Drive de l'utilisateur.
   - Cowork travaille à partir de ce contenu et écrit **avec ses mots** les tâches et les
     segments audio. On ne lui demande jamais de recopier un texte d'origine.

## 3. La semaine type

```
VENDREDI, réception              VENDREDI SOIR, Cowork            SAMEDI               EN CUISINE
──────────────────────────       ────────────────────────         ──────────────       ────────────────────
App :                            Tâche planifiée (skill) :        App : « Importer     Chaque téléphone :
1. sonde du site                 1. lit recettes, scans et        la semaine »         son rôle, ses tâches,
2. menu de la semaine               profil sur Drive              (sélecteur Drive)    ses segments audio,
3. scan des étiquettes           2. confirme personnes et               │              ses minuteurs
   → « 207 = <titre> »              recettes                            ▼                     ▲
4. pages des recettes            3. tâches, hachoir,              Fusion avec les             │
        │                           segments audio                recettes déjà en ───────────┘
        ▼                        4. écrit semaine-AAAA-Sww.json   base locale
Drive : recettes-AAAA-Sww.json      (nouveau fichier sur Drive)
        scans-AAAA-Sww.json
```

## 4. Les échanges avec Google Drive

### Ce qui coince

- Le moteur Drive de la Library (`google-drive-appdata`, éprouvé sur Popote) écrit dans le
  **dossier caché** `appDataFolder`. **Claude Cowork ne peut pas le voir.** Il faut un
  dossier visible.
- **Portée retenue : `drive.file`.**
  - Elle est non sensible : l'app peut être publiée sans validation Google. Il n'y a pas de
    reconnexion forcée tous les 7 jours, et des amis peuvent l'utiliser.
  - Mais avec `drive.file`, **l'app ne voit que les fichiers qu'elle a créés elle-même.**
- **Le connecteur Drive de Claude ne réécrit pas un fichier existant.** Il peut **créer** un
  fichier, le renommer ou le déplacer, mais pas en changer le contenu.
  - Cowork ne peut donc pas remplir un fichier préparé par l'app.
  - Ce qu'il produit est un fichier neuf, invisible pour l'API de l'app.

### Ce qu'on fait

| Sens | Moyen | Pourquoi |
|---|---|---|
| App → Drive (recettes, scans, profil cuisine, session de cuisine) | **API Drive, portée `drive.file`**, moteur `google-drive-appdata` adapté (dossier visible au lieu d'`appDataFolder`) | Automatique, sans geste. Cowork lit tout le Drive, donc il voit ces fichiers. Le second téléphone aussi (même compte, même app) |
| Cowork → App (travail de la semaine) | **Sélecteur de fichiers Android** (fournisseur Google Drive), bouton « Importer la semaine » | Colle au « samedi je télécharge ». Aucune portée restreinte, aucun projet Cloud nécessaire pour ce sens |

**Option pour plus tard**, si le geste du samedi pèse : ajouter `drive.readonly` pour un
import automatique. C'est une portée **restreinte**, avec plusieurs contraintes :
- l'app reste en mode test ;
- il faut redonner l'accord à peu près chaque semaine ;
- l'écran « application non validée » s'affiche ;
- chaque ami doit être déclaré testeur (100 au maximum).

### Le dossier

L'app crée `Carnet de recettes` au premier branchement ; on le déplace une fois dans
`Maison/`. Le déplacement ne retire pas l'accès de l'app, et un second téléphone sur le
même compte le retrouve par une recherche.

```
Maison/Carnet de recettes/
├── profil-cuisine.json         (app)    matériel, cuisiniers, rôles, préférences
├── recettes/
│   └── recettes-2026-S43.json  (app)    recettes de la semaine, telles que lues sur le site
├── scans/
│   ├── scans-2026-S43.json     (app)    texte brut des étiquettes, recette reconnue
│   └── etiquette-…jpg          (app)    photo de l'étiquette, en secours
├── semaines/
│   └── semaine-2026-S43.json   (Cowork) tâches, hachoir, segments audio
└── sessions/                   (app)    synchro des deux téléphones pendant la cuisson
```

Les images ne passent pas par Drive. Les données portent leurs adresses ; l'app les
télécharge et les garde en cache sur le téléphone.

## 5. Le vendredi dans l'app : scan et récupération

### Le site, pages publiques seulement

| Page | Ce qu'on y lit |
|---|---|
| `https://www.hellofresh.fr/menus/AAAA-Www` | Le menu de la semaine : numéro → titre, sous-titre, adresse de la recette |
| Adresse de la recette | La recette complète, portions de 1 à 6, images |

Les données sont dans la page telle que le site l'envoie à tout visiteur (bloc de données
de la page, et bloc `schema.org/Recipe` prévu pour les machines). Il n'y a ni connexion ni
jeton, et une poignée de pages par semaine. Ce lecteur est **la** pièce fragile : si le site
change, c'est lui qui casse, et la sonde (§ 9) le dit tout de suite.

### Le scan de l'étiquette, sans regex

L'étiquette porte aujourd'hui un grand numéro (`207`) et un nombre de personnes (`4p`).
On ne fige pas ce format.

- **Moteur** : `scan-multimode` de la Library (extrait de ScanList, OCR ✅ éprouvé), mode OCR.
  - La chaîne : `OcrElements` (adaptation d'`OcrAnalyzer`) → `ScanConsensus` (5 lectures
    identiques) → anti-rebond **glissant** de `SuiviEtiquettes` (à la place du verrou à durée
    fixe de `ScanDebouncer` : une étiquette restée en vue n'est pas revalidée) → `ScanFeedback`.
  - On n'utilise **pas** `RegexRuleEngine`.
- **Adaptation** (faite le 2026-10-27, consignée dans la fiche de la Library) : `OcrAnalyzer`
  devient `OcrElements`, qui remonte les **éléments** reconnus dont le centre est dans le
  réticule, avec leur **hauteur et leur position** rapportées au réticule.
- **Consensus** : il porte sur le **numéro reconnu** (5 images d'accord). Une « signature »
  de l'étiquette (tous les éléments lus) changerait au moindre bruit d'OCR. Pendant ce temps,
  chaque image vote pour le nombre de personnes qu'elle a lu.
- **Reconnaissance de la recette, sans format.**
  - Le menu de la semaine est chargé avant le scan.
  - Un élément lu qui est **exactement l'un des numéros de ce menu** désigne la recette.
  - On compare à une liste de valeurs connues, pas à un motif : si l'étiquette change de
    mise en page, ça marche encore.
  - Si rien ne correspond, l'app affiche le menu de la semaine et on touche la recette.
- **Nombre de personnes** : l'app le **propose** et on le corrige d'un geste. Les éléments
  bruts partent avec le scan, et Claude le confirme le vendredi soir. Un doute marque la
  recette « à confirmer » au lieu de deviner.
- **Retour immédiat** : « 207 · <titre de la recette> · 4 pers. ✔ », puis la page de la recette est
  lue et ajoutée à `recettes-AAAA-Sww.json`.
- **Images** : photo du plat, des étapes et des ingrédients, téléchargées en arrière-plan
  après la lecture de la page ; celles qui manquent (coupure, app fermée trop tôt) sont
  redemandées au chargement de la semaine et à l'ouverture d'une fiche recette.
- **Ce qui part sur Drive** :
  - les éléments bruts (texte, hauteur, position) et l'heure du scan ;
  - la recette reconnue ;
  - une petite photo de l'étiquette, en secours.

## 6. Le travail de Cowork le vendredi soir

Un **skill Cowork** (versionné dans ce dépôt ; les données sont sur Drive, le skill les lit
et les écrit), lancé par une **tâche planifiée** le vendredi soir, ou à la main.
**Il n'accède pas au site HelloFresh** : tout ce qu'il lui faut est sur Drive.

1. Lit `recettes-…json`, `scans-…json` et `profil-cuisine.json`. Si l'app a signalé le mode
   dégradé (§ 9), il le reprend dans son fichier et s'arrête.
2. Confirme, pour chaque box, la recette et le nombre de personnes à partir des éléments
   bruts. En cas de doute, il marque la recette « à confirmer ».
3. **Enrichit**, avec ses propres mots :
   - les tâches atomiques (§ 7) ;
   - les conseils hachoir ;
   - les segments audio (§ 8).

   Les quantités viennent des données de la recette **pour le nombre de personnes lu** :
   HelloFresh donne les vraies quantités de la box par portion, ce n'est pas une
   multiplication.
4. Écrit `semaine-AAAA-Sww.json` dans `semaines/`. Ce fichier ne contient **que** son
   travail et des références aux recettes, jamais une copie du contenu d'origine.

Format du fichier de la semaine (esquisse, à figer au lot 3) :

```json
{
  "type": "semaine", "version": 1, "semaine": "2026-W43",
  "statut": "ok | partiel | degrade", "message": "…",
  "recettes": [{
    "numero": 207, "personnes": 4, "a_confirmer": false,
    "source": { "type": "hellofresh", "id": "…", "url": "…" },
    "taches": [{
      "id": "t3", "type": "decoupe | chauffe | attente | dressage",
      "duree_min": 5, "apres": ["t1"], "ingredients": ["…"], "ustensile": "…",
      "texte": "…", "etape_origine": 2,
      "hachoir": { "avec": ["t4"], "mode": "ensemble | a_la_suite", "ordre": "…" }
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

- **À l'import** : chaque étape est découpée en **tâches atomiques**. Une tâche porte :
  - un type : découpe, chauffe, attente passive, dressage ;
  - une durée ;
  - des dépendances (« poireaux émincés » avant « poireaux à la poêle ») ;
  - les ingrédients et l'ustensile.

  HelloFresh ne fournit pas ce découpage : ses étapes sont du texte libre, sans minuteur ni
  lien vers les ingrédients.
- **En cuisine** : un ordonnancement **déterministe** répartit les tâches selon les
  cuisiniers présents.
  - Seul, tout s'enchaîne.
  - À deux, l'un découpe et l'autre chauffe. Les rôles sont des préférences, pas des murs :
    celui qui est libre prend la tâche prête.
  - Les deux téléphones calculent **le même plan** à partir des mêmes données.
- **Hachoir** (selon `profil-cuisine.json`) :
  - même moment d'utilisation et même destination → **ensemble** dans le hachoir ;
  - moments différents → **à la suite sans rincer**, en commençant par le plus sec et le plus
    neutre ;
  - un ingrédient qui finit dans une sauce mixée → **la sauce se fait dans le hachoir** (un
    bol de moins) ;
  - quantité trop faible pour la cuve → couteau.
- **Synchro des deux téléphones** :
  - Chaque « tâche terminée » est écrite dans un fichier de session sur Drive. L'app l'a
    créé, donc les deux téléphones peuvent le lire.
  - Chaque téléphone relit ce fichier toutes les quelques secondes.
  - Si la latence gêne, on passera à une liaison directe entre téléphones (Nearby
    Connections, sans Internet). À trancher au lot 6.

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
- Synthèse vocale **Android `TextToSpeech`** (voix françaises hors ligne).
- **Commandes vocales** (décision du 2026-10-27) : « suivant » / « c'est fait », « répète »,
  « précédent », « pause », « reprends », « minuteur ».
  - Reconnaissance vocale d'Android, **sur l'appareil** quand il le permet (Android 12 et
    plus), sinon le service du téléphone en préférant le hors-ligne.
  - Ce qui est entendu est comparé à une **liste de phrases connues**, après normalisation
    (casse, accents). Aucune IA : une phrase inconnue ne fait rien.
  - Le micro se coupe pendant que la voix parle, pour qu'elle ne s'entende pas elle-même.
  - Le micro n'écoute que pendant une séance, sur demande, avec un témoin visible.
- **En attendant les segments de Cowork** (lot 3), la séance lit les **étapes de la recette**,
  une par segment : de quoi essayer la voix et les commandes dès maintenant.
- Choix faits en codant la séance (2026-10-27) :
  - **Minuteurs lancés à la main** (bouton ou « minuteur »), jamais tout seuls : la voix lit
    l'étape avant que les poireaux soient dans la poêle.
  - **Une sonnerie** marque la fin d'un minuteur, même en pause ou voix coupée : un minuteur
    muet, c'est un plat qui brûle.
  - **Garde-fou contre la conversation** : une commande n'est prise que si la phrase compte
    peu de mots autour ; une négation (« ne passe pas ») annule tout.
  - Un geste qui demande la voix (valider, relire, précédent) lève la pause.
  - Étape en cours en 32 sp, ramenée à 24 sp au-delà de 90 caractères : une étape entière du
    site remplirait l'écran et cacherait la suite. Les segments courts de Cowork restent en
    32 sp.
  - **Durées lues** : minutes, heures (« 1 h 30 » = 90 min) et « 4 min 30 ». Une fraction ou
    un nombre douteux ne donne aucun minuteur plutôt qu'un faux.
  - **Sonnerie** : seule, environ 9 s de salves ; une seule salve quand la voix enchaîne sur
    l'étape suivante. Un geste la fait taire.
  - **Garde-fou, suite** : une négation juste après la commande (« continue pas ») annule
    aussi ; deux commandes dans la même phrase : « pause » ou « arrête d'écouter » l'emporte.
  - **Fin de recette** : le micro se ferme et l'écran de fin le dit. L'écran n'est plus gardé
    allumé, sauf si un minuteur tourne encore.
  - Limites connues : les minuteurs s'arrêtent si l'on quitte la séance ou si Android tue
    l'app (ni service ni notification). Écran éteint ou app en arrière-plan, ils sonnent à
    l'heure : un verrou d'éveil borné (fin du dernier minuteur plus une marge) garde le
    processeur éveillé tant qu'un minuteur tourne, puis le temps de la sonnerie. En veille
    profonde (Doze : téléphone immobile, écran éteint, sur batterie depuis longtemps, surtout
    pour un minuteur de plus d'une heure), Android ignore ce verrou et la sonnerie peut partir
    en retard ; seule une alarme exacte ou un service au premier plan l'éviterait. Beaucoup de
    téléphones bipent à chaque relance de l'écoute. « ok » ou « fait » entendus d'une télé
    avancent d'une étape (« précédent » répare).

## 9. Mode dégradé et alerte

**Sonde**, dans l'app, au début du vendredi (avant le premier scan). Elle vérifie que :
- la page du menu de la semaine répond et contient des numéros ;
- une page recette contient bien les champs attendus (nom, ingrédients, étapes, portions).

| Où | Quand | Effet si ça casse |
|---|---|---|
| App | Chaque lecture du menu ou d'une page recette | Bandeau « Accès HelloFresh rompu » avec « Photographier la fiche », et statut `degrade` dans `recettes-…json` pour que Cowork le reprenne |
| GitHub Actions (`sonde.yml`) | Chaque jeudi, 6 h 17 UTC (branche par défaut seulement) | Même lecteur sur le menu du vendredi suivant et une recette ; ouvre ou complète une issue sur ce dépôt, donc un e-mail, **avant** le vendredi |

Précisions (relecture du 2026-10-27) :
- **Dans l'app**, un refus passager du site (408, 429, 502, 503, 504) compte comme « hors
  ligne », pas comme un accès rompu. 403, 404, 410, une redirection hors des pages publiques,
  une page démesurée ou une page sans les champs attendus rompent l'accès.
- L'accès rompu suit **les pages en échec** : il se lève quand la dernière se relit, quand sa
  box est retirée, ou par « Réessayer », qui relit le menu puis ces pages. Le statut `degrade`
  survit au redémarrage, et les pages en échec se retrouvent d'après le fichier.
- **La sonde de GitHub** sort avec 0 (OK), 1 (accès rompu : issue) ou 2 (réseau ou refus
  passager : simple avertissement, pas d'issue) ; un échec de compilation fait échouer le job
  sans issue. Elle n'écrit que des numéros, des codes HTTP et nos propres mots, **jamais** un
  titre, un chemin de page ou un texte du site (dépôt public). Une recette lue par le bloc de
  secours `schema.org` (plus de quantités par portion) compte comme accès rompu pour elle, pas
  pour l'app, qui s'en sert.

**Mode dégradé.** Deux chemins vers le même fichier de semaine :

- **Fiche papier, dans l'app** (décision du 2026-10-27) : on photographie le recto et le verso
  de la fiche. L'app garde les photos et le **texte brut** lu par OCR (blocs, lignes, positions),
  sans regex ni interprétation, et dépose le tout avec le numéro de la box. Claude structure la
  recette le vendredi soir, à partir des photos et du texte.
- **PDF, sans code dans l'app** : on donne le PDF à Cowork, et Claude le lit directement.

**Place réservée dans le code** (interfaces et champs seulement, **aucun moteur**) :

```
SourceRecette
├── HelloFreshSource   actif (pages publiques)
├── PdfSource          réservé (PDF imprimé du site)
├── PhotoFicheSource   actif depuis le 2026-10-27 (photos et texte brut OCR ; Cowork structure)
└── JsonLdSource       réservé (autres sites de recettes : format schema.org Recipe)
```

## 10. Légal

- **CGU HelloFresh, article 12** : reproduction et rediffusion interdites « en dehors de
  toute utilisation privée ». On reste donc dans l'usage privé : chaque utilisateur récupère
  ses propres recettes, et le contenu reste sur son Drive et son téléphone.
- **Pages publiques seulement**, lues comme un navigateur.
  - Pas de jeton récupéré dans une page, pas d'interface interne du site.
  - Le test Cowork a montré que ce schéma est traité comme un contournement d'accès.
- **Le dépôt public ne contient que du code.** Aucun texte, aucune photo, aucun JSON de
  recette réelle, y compris dans les tests.
- **Peu de requêtes vers HelloFresh** : quelques pages par semaine et par utilisateur,
  jamais de moissonnage du catalogue.
- **Cowork ne recopie pas les textes d'origine.** Ce qu'il écrit sur Drive est son propre
  travail.
- **Partage à des amis** : chacun son compte Google et son Drive, aucun contenu ne transite
  par nous.

## 11. Moteurs de la Library réutilisés

| Moteur | Statut Library | Usage ici | Adaptation |
|---|---|---|---|
| `scan-multimode` (mode OCR) | ✅ OCR | Scan des étiquettes | Remonter les éléments avec taille et position ; pas de `RegexRuleEngine` |
| `google-drive-appdata` | ✅ | Connexion Google, dépôt des recettes et des scans, profil, sessions | Portée `drive.file` et dossier visible au lieu d'`appDataFolder` |
| `maj-auto-github` | ✅ | Mise à jour de l'APK | Dépôt de code déjà public : les Releases peuvent être publiées directement ici |
| Capteur de plantage (Popote, 🔬 candidat) | — | Rapport de plantage sans câble | À extraire d'abord dans la Library |

> Le catalogue de la Library (`INDEX.md`, fiches) n'est **pas encore fusionné dans `main`**.
> Il est sur `claude/library-docs-catalog-xkjytv`, et les fiches mises à jour pour le Carnet
> (variantes, deux patchs, « Utilisé dans ») sur `claude/recipe-app-planner-92zsww`, qui la
> contient. C'est cette dernière qui fait foi pour les moteurs du Carnet.

## 12. Points à vérifier

- [x] **Cowork joint-il hellofresh.fr ?** Pages publiques : oui. Jeton et interface interne :
      bloqués, et abandonnés (§ 2).
- [x] **Semaine du menu** : le numéro de l'étiquette se lit dans le menu de la semaine de
      livraison (box 207 reçue en semaine 43, confirmée le 2026-10-25).
- [x] ~~Deux SHA-1 (debug et release) sur un même client ?~~ Sans objet (2026-10-26) : seule
      l'empreinte release est déclarée, la clé debug étant publique (google-cloud.md).
- [ ] Le sélecteur de fichiers Android voit-il bien Google Drive sur les deux téléphones ?
- [ ] Cowork accepte-t-il de produire les tâches à partir de `recettes-…json` déposé par
      l'app ? (Test du lot 3.)
- [x] Compilation Android dans les sessions cloud : `./outils/sdk-android.sh` installe le
      SDK et le pot de Robolectric (CLAUDE.md, « Compiler et tester »).

## 13. Lots de réalisation

| Lot | Contenu | Validation |
|---|---|---|
| 0 | Ce cadrage | ✅ 2026-10-23 |
| 1 | Squelette Android (Kotlin, Compose, minSdk 26), clé debug versionnée, CI d'aperçu et de publication (`maj-auto-github`), connexion Drive `drive.file` et création du dossier | 🟡 Codé le 2026-10-25, relu (relecture adversariale, 9 défauts corrigés) le 2026-10-26, visuel refait (« Correspondance », version 0.2) le 2026-10-26. Reste, **à la toute fin** (décision du 2026-10-27), dans l'ordre : clé release et secrets ([publication](publication.md)), configuration Google Cloud ([guide](google-cloud.md)), essai sur téléphone |
| 2 | **Les outils** (priorité du 2026-10-27), sans Drive : sonde, menu de la semaine et pages recettes, stockage sur le téléphone, images en cache, scan des étiquettes (`scan-multimode` adapté), scan de la fiche papier, fiche recette, lecture à voix haute par étapes, commandes vocales, minuteurs. Dépôt sur Drive une fois branché | 🟡 Codé le 2026-10-27 (version 0.3), relu (relecture adversariale). Reste : dépôt sur Drive, puis un vrai vendredi et un essai en cuisine sur téléphone |
| 3 | Skill Cowork du vendredi soir (sans accès web) : confirmation, tâches, hachoir, segments audio, fichier de semaine | Un vrai vendredi soir |
| 4 | Import du samedi (sélecteur), catalogue de toutes les semaines, bandeau mode dégradé | — |
| 5 | Mode cuisine : planificateur seul ou à deux, hachoir, segments audio de Cowork | Un vrai repas à deux |
| 6 | Synchro des deux téléphones | — |
| Réservé | Moteurs du mode dégradé dans l'app (sauf la fiche papier, codée au lot 2), autres sites de recettes | Seulement si l'accès HelloFresh tombe |
