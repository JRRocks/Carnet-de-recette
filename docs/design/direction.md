# Direction visuelle « Correspondance »

> Choisie par l'utilisateur le 2026-10-26, parmi trois directions esquissées à égalité de
> finition (méthode [impeccable](https://github.com/pbakaus/impeccable), mode Operate), avec
> **quatre onglets dont Réglages**. Les corrections des deux juges sont intégrées ci-dessous.
> Les valeurs (couleurs, tailles, formes) font foi dans [`DESIGN.md`](../../DESIGN.md) et dans
> le code (`app/src/main/kotlin/…/ui/theme/`) ; ce document dit **pourquoi** et **où**.

## L'idée

Une recette à deux se lit comme un schéma de ligne : chaque cuisinier suit sa suite de
tâches, et les dépendances sont des points où l'un passe la main à l'autre. L'écran répond
d'un coup d'œil à trois questions : où j'en suis (le marqueur cerclé), ce qui vient (le
marqueur creux suivant), où l'on se croise (le tube entre les deux lignes).

Refusé : l'écran type des apps de box-repas (photo en tête, cartes identiques empilées, vert
frais, boutons pilule, « Étape 3/7 ») et sa version sans photo du lot 0 (vert « herbes »,
deux cartes empilées). Refusés aussi le livre de grand-mère (crème, serif, terracotta) et le
cockpit (noir, néon, jauges).

## Ce que le monde prête, et rien d'autre

Source : la signalétique des transports en commun (plan de ligne au-dessus des portes,
plaques émaillées, faïence des quais). Il prête quatre choses :

1. **La typo** : une sans humaniste de signalétique, ici Atkinson Hyperlegible Next
   (Braille Institute, OFL), dessinée pour la basse vision. Jamais sous la graisse 500.
2. **La palette** : fond de faïence froid (jamais crème), bleu nuit émaillé pour la coque
   (barre, bouton flottant, plaques), deux couleurs de ligne sur l'axe bleu-jaune : cobalt
   pour la découpe, ocre pour la chauffe.
3. **La densité** : celle d'un panneau lu de loin. Peu d'éléments, grands, un message par zone.
4. **Le geste signature** : le schéma de ligne (voir plus bas).

Tout le reste est Material 3 : barre de navigation, boutons, snackbars, listes.

**Le vocabulaire n'est pas prêté.** Aucun texte de l'app ne dit station, terminus,
correspondance, « tu es ici », « ma ligne », « chacun sa ligne », « prochaine station ». On
dit étape, tâche, passer la main, attendre. Le monde se voit, il ne se lit pas.

Exclus aussi : logo ou police de transporteur, couleurs exactes de lignes réelles, numéro de
recette dans un rond (le « 207 » reste du texte).

## Le geste signature : le schéma de ligne

Composants : `ui/composants/LigneDeTemps.kt` (une ligne), `PlanADeux.kt` (deux lignes).

- Trait vertical de **6 dp** : bleu émail (primary) pour la semaine, cobalt ou ocre pour
  chaque cuisinier. Devant ce qui n'est pas encore dans l'app, il garde ses 6 dp et sa
  couleur, **en tirets** : jamais mince ni gris.
- Marqueurs de 24 dp : **plein** = fait, **creux** (anneau d'encre de 3 dp) = à faire,
  **cerclé** (32 dp, cœur plein) = en cours, **pointillé** (outline) = pas encore dans l'app.
- L'étape en cours porte son nom sur une **plaque** (fond plaque, coins de 4 dp, headlineMedium).
- Découpe : marqueurs **ronds**. Chauffe : marqueurs **carrés** (coins de 6 dp). La forme ne
  change jamais d'un écran à l'autre ; la place peut changer (en séance, ta ligne est à gauche).
- Plan à deux : deux traits proches du bord (28 et 72 dp), **une seule colonne de texte**
  (100 dp), les tâches dans l'ordre du temps, chacune sur le trait de son rôle. Chaque libellé
  garde presque toute la largeur, même à 200 %.
- Passer la main : un **tube** de 12 dp entre les deux traits (fond clair, contour d'encre ;
  rempli de la couleur de qui reçoit une fois la main passée), et la phrase en clair à côté.
- Pastille d'un cuisinier : l'initiale **à l'encre** sur fond clair, dans un contour de 3 dp
  de sa couleur (`Pastille.kt`). Le remplissage de couleur est réservé aux étapes faites.
  Jamais vide : vide, elle ressemblerait à un bouton radio ou à une case à cocher. Sans profil
  (lot 1), on montre la **tête de ligne** du rôle (marqueur plein sur le départ de son trait)
  et le rôle en toutes lettres (`LegendeRoles`), sans prénom inventé.
- Chaque rangée dessine son propre tronçon (`drawBehind`), jamais un dessin unique derrière la
  liste : il suit la hauteur réelle du texte jusqu'à 200 %.
- TalkBack : chaque rangée fusionne sa sémantique et dit son état (« Fait », « En cours »…).
  Un schéma d'exemple est lu comme une seule phrase, jamais comme de vraies tâches.

Ce qui ferait tomber la direction dans le banal, à ne pas adoucir : un trait de 2 dp, des
marqueurs plus petits, la disparition de la plaque ou du tube.

## Navigation

Barre de navigation Material 3, quatre destinations, icône et libellé (Material Symbols
Outlined graisse 600, importées en vecteurs par `outils/icones.py`, pleines quand l'onglet
est actif) :

| Onglet | Rôle | Aujourd'hui (lot 1) |
|---|---|---|
| **Semaine** | Accueil : où en est la semaine, ce qui vient, l'action du moment (bouton flottant) | Étape « Drive du foyer » + les quatre temps en pointillé |
| **Carnet** | Les recettes importées, groupées par semaine ; « Importer la semaine » le samedi | État vide qui explique le samedi |
| **Cuisine** | Le plan à deux de ce soir, puis la séance plein écran | État vide avec un plan d'exemple |
| **Réglages** | Drive, version et mise à jour, cuisiniers, matériel, licences | Drive, version, à propos |

- Le Scanner n'a pas d'onglet : c'est un geste du vendredi, lancé par le bouton flottant de la
  Semaine (« Scanner les box »), en plein écran. Il reste accessible les autres jours (une
  livraison peut être décalée).
- La séance de cuisine est en plein écran, sans barre de navigation, écran allumé.
- Indicateur de l'onglet actif en secondaryContainer, **jamais** couleur plaque ; l'état se lit
  aussi par l'icône pleine et le libellé gras.
- Un point (Badge) bleu, pas carmin, sur Réglages quand le Drive ou une mise à jour attend un
  geste (pas pendant un téléchargement) ; Réglages dit la même chose en toutes lettres.
- Les libellés suivent la taille de police jusqu'à 120 %, puis s'arrêtent : au-delà, quatre
  libellés ne tiennent plus sur 360 dp. Les titres et le contenu suivent jusqu'au bout.
- Retour système : d'un autre onglet, il ramène à la Semaine ; de la Semaine, il quitte l'app
  (retour prédictif activé).
- Bord à bord : la barre bleu nuit passe sous la barre d'état (icônes claires) ; l'encoche de
  la caméra est respectée (`safeDrawing`).
- À partir de 600 dp de large (tablette, téléphone en paysage), un **rail** remplace la barre
  du bas, et le contenu se limite à 640 dp, centré.
- Le bouton flottant se replie sur son icône au-delà de 150 % de police ; TalkBack lit la
  phrase entière (« Réessayer de brancher le Drive »).

## Les écrans cibles (lots 2 à 6)

À garder en tête en codant chaque lot ; données d'exemple factices.

- **Quelle semaine** : une semaine du Carnet va du vendredi de livraison au jeudi suivant. Du
  samedi au jeudi, l'accueil, le Carnet, le scanner, la fiche et la séance restent sur la
  semaine du dernier vendredi (celle des box qu'on cuisine) ; on passe à la suivante le
  vendredi de la livraison d'après (décision du 2026-10-27, quand les données sont arrivées).
- **Semaine** (lot 5 en place) : la ligne Vendredi, Vendredi soir, Samedi, En cuisine, avec
  dates en chiffres tabulaires au bout de la ligne ; l'étape courante en plaque ; sous
  « En cuisine », la recette du soir et qui fait quoi (pastilles, nombre de tâches, durée).
  Puis la liste des recettes de la semaine (rangées de 72 dp, pas de cartes). Le bouton
  flottant suit l'étape : « Scanner les box », « Importer la semaine », « Cuisiner la 207 ».
  L'étape courante se déduit des données (box scannées, semaine importée), pas du seul jour.
- **Scanner** (lot 2, en place) : aperçu caméra en haut, tout ce qui se touche en bas,
  dernier résultat en grand (« 207 » en displaySmall tabulaire), sélecteur de personnes,
  « Terminer » pleine largeur. L'aperçu pèse 0,85 contre 1 pour le bas (0,6 au-delà de
  150 % de police) : le dernier résultat et « Déjà scannées » restent sous le pouce. Le
  réticule est dessiné à part (coins blancs sur liseré sombre) et grandit avec le consensus.
  « Pas reconnue ? Choisis dans le menu » et « Photographier la fiche » en boutons texte.
- **Fiche papier** (lot 2, en place) : même aperçu que le Scanner, puis la box (facultative,
  numéro ou choix dans le menu), les pages (recto, verso facultatif) avec vignette et
  nombre de blocs lus, et l'action du moment en bas pleine largeur (« Photographier le
  recto », « Enregistrer la fiche »). Aucun texte interprété : la fiche dit seulement ce qui
  a été lu.
- **Carnet** (lot 4 ; liste de la semaine en place au lot 2) : liste groupée par semaine
  (en-têtes « Semaine 44 »), numéro en gras tabulaire, vignette de 56 dp en bout de ligne,
  jamais d'image en tête ; fiche recette avec onglets Plan, Ingrédients, Fiche d'origine.
  Au lot 2, la fiche recette est une seule page : photo du plat 16:10, personnes −/+,
  ingrédients (quantités en colonne), ustensiles, étapes sur la ligne, allergènes, mention
  « pour ton usage privé » ; bouton flottant « Lire à voix haute ».
- **Cuisine** (lots 5 et 6) : « À deux | Seul », le plan à deux de ce soir, puis « Je suis… »
  (premier usage) ou « Commencer mes tâches ».
- **Séance** : la tâche en cours en headlineLarge (32 sp), les deux suivantes, l'attente de
  l'autre ou le passage de main comme une rangée à part entière, dans l'ordre où la voix les
  dira ; dock bas avec minuteur et « Relire » (72 dp) et « C'est fait » (72 dp, pleine largeur).
  Pas d'« Étape 3/7 » ni de sur-titre. Au lot 2 (en place), la séance lit les étapes de la
  recette : étape en cours en 32 sp, ramenée à 24 sp au-delà de 90 caractères ; voix et micro
  dans la barre du haut, témoin « Micro ouvert » au-dessus du dock ; sous chaque étape à
  venir, son déclencheur (« Lue après l'étape d'avant », « à la fin du minuteur ») et son
  interrupteur de lecture. L'écran de fin dit « Micro coupé » quand la fin a fermé le micro,
  et montre le témoin « Micro ouvert » si on le rouvre à la main.

## États

Les états du Drive et de la mise à jour s'affichent en **lignes d'état** pleine largeur
(`LigneEtat.kt` : icône, phrase qui dit la cause et quoi faire, actions), jamais en boîte de
dialogue : rien n'interrompt quelqu'un en pleine cuisine.

- Sur la Semaine, en tête : l'accès HelloFresh rompu (avec « Photographier la fiche ») et le
  téléphone plein quand ils arrivent, puis la mise à jour et « hors ligne ». Les deux premiers
  sont rares et urgents ; les deux derniers, **au plus deux** lignes. L'erreur du Drive s'écrit
  sous l'étape « Drive du foyer », près de son bouton « Réessayer » ; le bouton flottant reste
  « Scanner les box » (le scan marche sans Drive).
- Au lancement d'un téléphone déjà branché, l'app revérifie le Drive **en silence** : l'étape
  reste faite, le compte reste affiché, rien ne clignote.
- La mise à jour : « La version X est prête » avec « Plus tard » et « Mettre à jour » ; pendant
  le téléchargement, une barre de progression et le pourcentage (annoncés une fois à TalkBack,
  pas à chaque pour cent). « Plus tard » efface la ligne de la Semaine jusqu'au prochain
  lancement ; Réglages propose toujours la version. Réglages écrit « 0.2 installée », jamais
  « à jour » : sans réponse de GitHub, l'app ne peut pas le savoir. Jamais pendant une séance,
  jamais dans le Scanner.
- Les causes s'écrivent en français : jamais le message brut d'une exception.
- Le premier branchement d'un téléphone s'annonce une fois, par un snackbar qui attend
  « Compris » (« Dossier prêt sur le Drive. S'il n'est pas encore dans « Maison »,
  déplace-le. »).
- La couleur ne porte jamais seule un état : icône et phrase l'accompagnent toujours. L'ocre ne
  sert jamais aux alertes.

## Mouvement

Un seul moment orchestré, « le trait avance », quand on touche « C'est fait » (lot 5) :
l'anneau se resserre (150 ms), le tronçon suivant se remplit de la couleur de la ligne
(250 ms), la liste remonte l'étape suivante (350 ms), le texte change en fondu (200 ms), le
tout sous 600 ms ; l'audio suivant démarre sans attendre. Ailleurs : fondu de 200 ms entre
onglets, rien qui pulse en continu. Animations système coupées : coupe franche.

## Risques à surveiller sur les captures

1. **Le costume** : une pastille ronde colorée avec une lettre évoque un pictogramme de ligne.
   Parades en place : initiale à l'encre, carré pour la chauffe, aucun numéro dans un rond.
2. **Le banal** : voir « Ce qui ferait tomber la direction dans le banal ».
3. **L'ocre** (#9A6A00) tire vers le bronze sous une lumière chaude : sûr pour les daltoniens,
   moins solaire. Ne pas l'éclaircir (il passerait sous 3:1).
4. **En sombre**, la barre ne se détache du fond que par la teinte (1,5:1) ; l'identité repose
   alors sur les plaques, claires.
5. **Le zéro barré** d'Atkinson (« 3Ø ») est permanent : la police n'a pas de variante.
6. Trois onglets sur quatre sont des états vides au lot 1 : ils expliquent ce qui viendra.

## Vérifier avant chaque aperçu

Les tests `app/src/test/…/capture/` rendent chaque onglet en PNG (Robolectric) : 360 dp de
large, clair et sombre, police à 100 %, 130 % et 200 %, 412 dp, et en paysage (rail). On
regarde les images (`app/build/captures/`), puis on les montre à l'utilisateur.

Ce que les captures ne montrent pas : la barre d'état, la barre de navigation du système et
l'encoche (Robolectric ne les dessine pas). Un passage sur téléphone ou émulateur, en portrait
et en paysage, reste à faire avec le premier aperçu.

## Écarts assumés par rapport à la fiche d'origine

- Quatre onglets avec Réglages (choix de l'utilisateur) au lieu de Scanner + Foyer poussé
  depuis une icône d'état ; pas d'icône Drive dans la barre du haut.
- Une fois branché, l'étape « Drive du foyer » reste affichée comme faite, avec le compte :
  c'est le retour visible du branchement.
- Ligne de mise à jour : bouton plein (bleu nuit en clair, bleu plaque en sombre), le bouton
  tonal disparaîtrait sur le fond de la ligne.
- Plan à deux : traits à 28 et 72 dp et texte à 100 dp (fiche : 28, 60 et 88 dp), pour que le
  tube reste visible entre les marqueurs.
- La clé « En pointillé : pas encore dans l'app » est posée au-dessus de la ligne, visible dès
  l'ouverture, avec un anneau plus petit que ceux des étapes.
