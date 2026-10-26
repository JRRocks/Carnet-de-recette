---
name: Carnet de recette
description: Une cuisine à deux qui se lit comme un schéma de ligne.
colors:
  primary: "#1E3A70"
  on-primary: "#FFFFFF"
  bleu-nuit-emaille: "#14284B"
  sur-bleu-nuit: "#FFFFFF"
  faience: "#F4F6F7"
  encre: "#0F1620"
  encre-douce: "#323B48"
  contour: "#687282"
  contour-doux: "#C2C9D2"
  surface-container-lowest: "#FFFFFF"
  surface-container-low: "#EEF1F3"
  surface-container: "#E8ECEF"
  surface-container-high: "#E2E6EA"
  surface-container-highest: "#DBE0E5"
  secondary-container: "#DDE3EC"
  on-secondary-container: "#141D2C"
  ligne-decoupe-cobalt: "#2A52A8"
  ligne-chauffe-ocre: "#9A6A00"
  erreur-carmin: "#A8123E"
  error-container: "#FBDDE2"
  on-error-container: "#3E0013"
  sombre-surface: "#0E131A"
  sombre-encre: "#E8ECF1"
  sombre-encre-douce: "#C6CEDA"
  sombre-barre: "#1C3462"
  sombre-plaque: "#B2C6F2"
  sombre-sur-plaque: "#0C2350"
  sombre-ligne-decoupe: "#7A9DE8"
  sombre-ligne-chauffe: "#E8BE5C"
  sombre-erreur: "#FFB3C1"
typography:
  display:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: "36sp"
    fontWeight: 700
    lineHeight: "44sp"
    fontFeature: "tnum"
  headline:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "36sp"
  title:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "28sp"
  body:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: "18sp"
    fontWeight: 500
    lineHeight: "26sp"
  label:
    fontFamily: "Atkinson Hyperlegible Next"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "20sp"
    letterSpacing: "0.006em"
rounded:
  extra-small: "4dp"
  small: "6dp"
  medium: "8dp"
  large: "12dp"
  extra-large: "20dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  xxl: "32dp"
  axe-ligne: "36dp"
  debut-texte: "72dp"
components:
  barre-application:
    backgroundColor: "{colors.bleu-nuit-emaille}"
    textColor: "{colors.sur-bleu-nuit}"
    typography: "{typography.title}"
  bouton-plein:
    backgroundColor: "{colors.bleu-nuit-emaille}"
    textColor: "{colors.sur-bleu-nuit}"
    typography: "{typography.label}"
    rounded: "{rounded.medium}"
    height: "48dp"
  bouton-flottant:
    backgroundColor: "{colors.bleu-nuit-emaille}"
    textColor: "{colors.sur-bleu-nuit}"
    rounded: "{rounded.large}"
    height: "64dp"
  plaque:
    backgroundColor: "{colors.bleu-nuit-emaille}"
    textColor: "{colors.sur-bleu-nuit}"
    typography: "{typography.headline}"
    rounded: "{rounded.extra-small}"
    padding: "4dp 10dp"
  ligne-etat-info:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    rounded: "{rounded.medium}"
  ligne-etat-erreur:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    rounded: "{rounded.medium}"
  barre-navigation:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.encre}"
  indicateur-onglet:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
---

# Design System: Carnet de recette

## Overview

**Creative North Star : « Le plan de ligne au-dessus des portes »**

Une recette à deux se lit comme un schéma de ligne : chaque cuisinier suit ses tâches,
et l'un passe la main à l'autre là où les deux lignes se rejoignent. Le monde de la
signalétique des transports ne prête que la typographie, la palette, la densité d'un
panneau lu de loin et un geste signature ; les contrôles, la navigation et les états
restent ceux de Material 3. Le vocabulaire n'est pas prêté : l'interface parle d'étapes et
de tâches, jamais de stations.

La scène est une cuisine le soir : téléphone posé à distance de bras, mains occupées,
lumière chaude, buée. D'où une densité faible, des textes à 18 sp, des contrastes de texte
au-dessus de 7:1 dans les deux thèmes et des cibles de 48 dp au moins. Refusés : l'écran
type des apps de box-repas (photo en tête, cartes identiques empilées, vert frais, pilules),
le livre de grand-mère et le cockpit. Le pourquoi et les écrans cibles sont dans
[`docs/design/direction.md`](docs/design/direction.md).

**Key Characteristics:**
- Fond de faïence froid, coque bleu nuit émaillé, deux couleurs de ligne sur l'axe bleu-jaune.
- Une seule famille, Atkinson Hyperlegible Next, jamais sous la graisse 500.
- Le schéma de ligne : trait de 6 dp, marqueurs plein, creux, cerclé et pointillé, plaque.
- Des listes et des lignes d'état pleine largeur, pas de cartes empilées.
- Rien ne s'interrompt en pleine cuisine : pas de boîte de dialogue au lancement.

## Colors

Une palette froide et franche : de l'encre sur de la faïence, du bleu nuit pour la coque, et
deux couleurs réservées aux deux cuisiniers.

### Primary
- **Bleu nuit émaillé** (#14284B) : barre d'application, bouton flottant, boutons pleins,
  plaque de l'étape en cours. En sombre, la barre passe à #1C3462 et la plaque s'éclaircit
  (#B2C6F2, texte #0C2350) pour rester un repère.
- **Bleu émail** (#1E3A70, primary) : le trait de la ligne de la semaine, les en-têtes de
  section, les boutons texte.

### Secondary
- **Cobalt** (#2A52A8, sombre #7A9DE8) : la ligne de la découpe, marqueurs ronds.
- **Ocre** (#9A6A00, sombre #E8BE5C) : la ligne de la chauffe, marqueurs carrés.

### Neutral
- **Faïence** (#F4F6F7) : le fond, jamais crème.
- **Encre** (#0F1620) : le texte (16,8:1 sur faïence).
- **Encre douce** (#323B48) : textes d'appui (10,4:1), jamais de gris moyen.
- **Contour** (#687282) : pointillés de ce qui n'est pas encore dans l'app (4,5:1).
- **Carmin** (#A8123E, sombre #FFB3C1) : l'erreur, toujours avec icône, fond errorContainer
  et phrase.

### Named Rules
**La règle des deux cuisiniers.** Le cobalt et l'ocre ne servent qu'aux traits, marqueurs et
contours de pastille, jamais au texte ni aux alertes. Chacun est toujours doublé d'une forme
(rond, carré) et d'un mot.

**La règle de l'état écrit.** Aucun état ne se lit par la couleur seule : une icône et une
phrase l'accompagnent toujours.

**Pas de Material You.** La couleur d'un cuisinier ne change pas avec le fond d'écran.

## Typography

**Display Font :** Atkinson Hyperlegible Next (police variable embarquée, OFL 1.1)
**Body Font :** la même

**Character :** une sans humaniste de signalétique, dessinée pour la basse vision (I, l et 1
distincts, zéro barré permanent), lisible d'un coup d'œil à 70 cm.

### Hierarchy
- **Display** (700, 36 à 52 sp, chiffres tabulaires) : minuteur et numéro de box scannée.
- **Headline** (600, 24 à 32 sp) : tâche en cours (32), étape en cours sur sa plaque (28),
  titres des états vides (24).
- **Title** (600, 16 à 22 sp) : titre de la barre et noms des étapes (22), titres de rangée
  (18), en-têtes de section (16).
- **Body** (500, 16 à 18 sp) : texte courant à 18 sp, textes d'appui et lignes d'état à 16 sp.
- **Label** (600, 12 à 16 sp, interlettrage positif) : boutons (16), libellés de la barre de
  navigation (14), badges (12).

### Named Rules
**La règle du cran sombre.** En thème sombre, corps et libellés montent d'une graisse (500
vers 600, 600 vers 700) : le texte clair sur fond foncé paraît plus maigre.

**La règle des chiffres.** Durées, quantités, dates et numéros en chiffres tabulaires
(`TextStyle.chiffres()`). Espace insécable U+00A0 devant « : ; ! ? % » et dans les
guillemets : la police n'a pas d'espace fine.

**Pas de sur-titre.** Aucun petit texte gris au-dessus d'un titre.

## Layout

Grille de 4 dp ; pas de 4, 8, 12, 16, 24, 32 et 48. Gouttière latérale de 16 dp (24 dp à
partir de 600 dp). Le schéma de ligne pose son trait à 36 dp du bord et son texte à 72 dp ;
rangée d'étape de 72 dp au moins, extensible avec le texte. Listes en rangées de 72 dp avec
séparateurs, pas de cartes. Le contenu laisse 96 dp sous lui quand le bouton flottant est
affiché. Aucune hauteur fixe sur un conteneur de texte : tout suit la taille de police
jusqu'à 200 % (les libellés de la barre de navigation s'arrêtent à 120 %).

## Elevation & Depth

À plat. La profondeur vient des tons (faïence, surfaceContainer, bleu nuit), pas des
ombres : la barre d'application et la barre de navigation sont des aplats, les lignes d'état
des fonds teintés. Seuls le bouton flottant et le snackbar gardent l'élévation Material par
défaut.

## Shapes

Une échelle resserrée de plaque émaillée : 4 dp (plaque, snackbar), 6 dp (pastille carrée de
la chauffe, chips), 8 dp (tous les boutons et les lignes d'état), 12 dp (bouton flottant),
20 dp (boîtes de dialogue). Pas de pilule pour une action. Le cercle est réservé aux
marqueurs ronds de la découpe et de la semaine, à l'indicateur de la barre de navigation et
aux indicateurs de progression.

## Components

### Buttons
- **Shape :** coins de 8 dp (`BoutonCarnet`, car `ButtonDefaults.shape` ne lit pas le thème).
- **Plein :** bleu nuit, texte blanc, 48 dp de haut au moins.
- **Contour, texte :** Material 3, mêmes coins.
- **Bouton flottant étendu :** bleu nuit, 64 dp, coins de 12 dp ; il porte l'action du moment
  et disparaît (sans être grisé) quand aucune action n'est possible.

### Navigation
- Barre de navigation Material 3, quatre destinations (Semaine, Carnet, Cuisine, Réglages),
  fond surfaceContainer. Onglet actif : icône pleine, libellé gras en encre, indicateur
  secondaryContainer. Onglets inactifs : icône au contour, libellé en encre douce.
- Un point sur Réglages quand le Drive ou une mise à jour attend un geste.

### Lignes d'état
- Pleine largeur, coins de 8 dp, icône de 24 dp, phrase en body 16 sp qui dit la cause et
  quoi faire, actions alignées à droite. Info : secondaryContainer. Erreur : errorContainer.
  Neutre (hors ligne) : surfaceContainerHigh. Au plus deux, dans l'ordre erreur, mise à jour,
  hors ligne.

### Listes
- `ListItem` Material 3 de 72 dp au moins, icône au contour en encre douce, titre en title 18 sp,
  détail en body 16 sp. En-têtes de section en title 16 sp bleu émail.

### Le schéma de ligne (signature)
- Trait de 6 dp ; marqueurs de 24 dp : plein (fait), creux à anneau d'encre de 3 dp (à faire),
  cerclé de 32 dp à cœur plein (en cours), pointillé couleur contour (pas encore dans l'app).
- L'étape en cours porte son nom sur une plaque. Découpe en ronds, chauffe en carrés ; un tube
  de 12 dp entre les deux traits quand l'un passe la main à l'autre.
- Pastille d'un cuisinier : initiale à l'encre dans un contour de 3 dp de sa couleur.

## Do's and Don'ts

### Do:
- **Do** garder le trait à 6 dp, la plaque et le tube : ce sont eux qui font la direction.
- **Do** écrire chaque état en toutes lettres, avec son icône.
- **Do** écrire U+00A0 devant « : ; ! ? % » et dans les guillemets.
- **Do** vérifier chaque écran en capture, clair et sombre, à 100 %, 130 % et 200 % de police.

### Don't:
- **Don't** employer le vocabulaire du métro (station, terminus, correspondance, « tu es ici »).
- **Don't** mettre un numéro de recette dans un rond, ni reprendre la couleur exacte d'une
  ligne réelle.
- **Don't** empiler des cartes identiques, ni poser un sur-titre au-dessus d'un titre.
- **Don't** utiliser l'ocre pour une alerte, ni le cobalt ou l'ocre pour du texte.
- **Don't** peindre l'indicateur de la barre de navigation en bleu nuit de plaque.
- **Don't** afficher un élément graphique HelloFresh.
