# Configuration Google Cloud, pas à pas

> À faire **ensemble au lot 1**, **après** avoir créé la clé release
> ([publication.md](publication.md), mise en place, étapes 1 et 2) : le client OAuth a besoin
> de son empreinte.
> Les intitulés de la console Google changent souvent. Si un écran ne ressemble pas à ce qui
> est décrit, on s'arrête et on regarde ensemble.
>
> Base : moteur `google-drive-appdata` de la Library (éprouvé sur Popote), avec une
> différence : portée **`drive.file`** au lieu de `drive.appdata` (voir cadrage § 4).

## Ce qu'il faut avoir sous la main

| Élément | D'où il vient | Va dans le dépôt ? |
|---|---|---|
| Nom de package (`applicationId`) | **`fr.jrrocks.carnetderecette`** | Oui |
| SHA-1 de la clé **release** | Ta clé release ([publication.md](publication.md), étape 1), sur ton ordinateur et dans les secrets GitHub | **Jamais** |

Calcul de l'empreinte, là où se trouve la clé :

```bash
keytool -list -v -keystore carnet-release.jks -alias carnet | grep SHA1              # Linux, macOS
keytool -list -v -keystore carnet-release.jks -alias carnet | Select-String SHA1     # Windows PowerShell
```

### Pourquoi pas l'empreinte de la clé debug

Popote déclarait aussi l'empreinte de sa clé debug. Ici, ce serait une faille : la clé debug
est versionnée dans ce dépôt **public**. N'importe qui pourrait signer avec elle un APK du
même nom de package, et Google le prendrait pour l'app. Avec la portée `drive.file`, cet APK
aurait accès à tout le dossier du Carnet sur le compte de la personne qui l'installe.

On ne déclare donc **que** l'empreinte release. La contrepartie : un APK de debug compilé à
la main ne se connecte pas au Drive. Les essais sur téléphone passent par les aperçus, qui
sont signés de la clé release ([publication.md](publication.md)).

## Étape 1 : un projet dédié

1. Ouvrir <https://console.cloud.google.com>, sélecteur de projet en haut, **Nouveau projet**.
2. Nom : `Carnet de recettes`, puis **Créer**, et vérifier qu'il est bien sélectionné.

Pourquoi ne pas réutiliser le projet de Popote : l'écran de consentement affiche le nom de
l'app, et des projets séparés se révoquent séparément.

## Étape 2 : activer l'API Google Drive

**API et services** → **Bibliothèque** → chercher « Google Drive API » → **Activer**.

Si cette étape manque, l'app le dit : « L'API Google Drive n'est pas activée dans le projet
Google Cloud ».

## Étape 3 : l'écran de consentement (Google Auth Platform)

1. **Google Auth Platform** (anciennement « Écran de consentement OAuth ») → **Commencer**.
2. **Branding** : nom de l'app `Carnet de recette`, e-mail d'assistance = le compte partagé.
   Pas de logo : un logo déclenche une validation de marque.
3. **Audience** : **Externe**.
4. **Accès aux données** → **Ajouter ou supprimer des champs d'application** → cocher
   `.../auth/drive.file` (« Voir, modifier, créer et supprimer uniquement les fichiers Google
   Drive spécifiques que vous utilisez avec cette application »).
   **Ne pas** ajouter `drive` ni `drive.readonly`, qui sont des portées restreintes.
   L'app demande aussi l'adresse du compte (`email`, pour l'afficher). C'est une portée de
   base, non sensible, qu'il n'est pas nécessaire de déclarer ici.
5. **Audience** → **Publier l'application** (passage « En production »). Avec des portées
   non sensibles, Google ne demande pas de validation. Sans cette publication, l'accord
   expire au bout de 7 jours et seuls les comptes testeurs peuvent se connecter. Si Google
   réclame malgré tout une validation, on reste en test et on ajoute le compte partagé (et
   ceux des amis) dans **Utilisateurs tests**.

## Étape 4 : le client OAuth Android

1. **Clients** → **Créer un client** → type **Android**.
2. Nom : `Carnet Android`. Nom de package : `fr.jrrocks.carnetderecette`. Empreinte : le
   SHA-1 de la clé **release**.
3. **Créer.** Un client Android n'a **pas de secret** : il n'y a rien à télécharger, et rien
   à mettre dans l'app. Google reconnaît l'app par son package et sa signature.

## Étape 5 : premier essai (fin du lot 1)

1. Installer l'APK d'aperçu ([publication.md](publication.md)), puis appuyer sur
   « Connecter Google Drive ».
2. Choisir le compte partagé et accepter l'écran de consentement.
3. Vérifier que le dossier `Carnet de recettes` apparaît dans le Drive, puis le **déplacer
   dans `Maison/`**.
4. Sur le second téléphone (même compte) : se connecter et vérifier qu'il retrouve le dossier.

## Erreurs connues

| Ce que l'app affiche | Cause | Remède |
|---|---|---|
| « Google ne reconnaît pas l'app : le nom de package ou l'empreinte SHA-1… » (`DEVELOPER_ERROR`, ou `UNREGISTERED_ON_API_CONSOLE` quel que soit son code) | Le client OAuth ne porte pas ce package ou cette empreinte, ou l'APK n'est pas signé de la clé release | Recalculer l'empreinte release et comparer (étape 4) ; installer un APK d'aperçu, pas un APK de debug |
| « L'API Google Drive n'est pas activée dans le projet Google Cloud… » | Étape 2 oubliée, ou activée depuis moins de quelques minutes | Étape 2, puis réessayer |
| « Google a refusé la demande d'autorisation (code N : …) » | Autre refus de Play Services ; le texte de Google suit le code | Revoir les étapes 2 à 4, puis chercher le code et le texte affichés |
| « Google a retiré l'accès au Drive. Reconnecte le compte. » | L'accès a été retiré depuis le compte Google (Sécurité → applications tierces) | « Connecter Google Drive » : l'écran de consentement revient |
| `access_denied` sur l'écran Google | App restée en test et compte non déclaré testeur | Étape 3.5 |
| « Pas de réseau : réessaie une fois connecté. » | Hors ligne pendant un branchement | « Réessayer » une fois le réseau revenu |
| « Hors ligne : le Drive sera vérifié au prochain lancement. » | Téléphone déjà branché, lancé sans réseau | Rien : le branchement mémorisé tient |
