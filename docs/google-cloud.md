# Configuration Google Cloud, pas à pas

> À faire **ensemble au lot 1**, une fois le squelette Android en place : il faut connaître le
> nom de package et l'empreinte SHA-1 de la clé debug avant de créer le client.
> Les intitulés de la console Google changent souvent. Si un écran ne ressemble pas à ce qui
> est décrit, on s'arrête et on regarde ensemble.
>
> Base : moteur `google-drive-appdata` de la Library (éprouvé sur Popote), avec une
> différence : portée **`drive.file`** au lieu de `drive.appdata` (voir cadrage § 4).

## Ce qu'il faut avoir sous la main

| Élément | D'où il vient | Va dans le dépôt ? |
|---|---|---|
| Nom de package (`applicationId`) | Choisi au lot 1 | Oui |
| SHA-1 de la clé **debug** | `app/keystore/debug.keystore`, versionnée exprès (sinon chaque conteneur cloud fabrique une clé neuve et la connexion casse) | La clé oui, l'empreinte se recalcule |
| SHA-1 de la clé **release** | Ta clé release, sur ton ordinateur et dans les secrets GitHub | **Jamais** |

Calcul d'une empreinte :

```bash
keytool -list -v -keystore app/keystore/debug.keystore -alias androiddebugkey -storepass android | grep SHA1
```

## Étape 1 : un projet dédié

1. Ouvrir <https://console.cloud.google.com>, sélecteur de projet en haut, **Nouveau projet**.
2. Nom : `Carnet de recettes`, puis **Créer**, et vérifier qu'il est bien sélectionné.

Pourquoi ne pas réutiliser le projet de Popote : l'écran de consentement affiche le nom de
l'app, et des projets séparés se révoquent séparément.

## Étape 2 : activer l'API Google Drive

**API et services** → **Bibliothèque** → chercher « Google Drive API » → **Activer**.

## Étape 3 : l'écran de consentement (Google Auth Platform)

1. **Google Auth Platform** (anciennement « Écran de consentement OAuth ») → **Commencer**.
2. **Branding** : nom de l'app `Carnet de recettes`, e-mail d'assistance = le compte partagé.
   Pas de logo : un logo déclenche une validation de marque.
3. **Audience** : **Externe**.
4. **Accès aux données** → **Ajouter ou supprimer des champs d'application** → cocher
   **uniquement** `.../auth/drive.file` (« Voir, modifier, créer et supprimer uniquement les
   fichiers Google Drive spécifiques que vous utilisez avec cette application »).
   **Ne pas** ajouter `drive` ni `drive.readonly`.
5. **Audience** → **Publier l'application** (passage « En production »). Avec une seule
   portée non sensible, Google ne demande pas de validation. Sans cette publication, l'accord
   expire au bout de 7 jours et seuls les comptes testeurs peuvent se connecter. Si Google
   réclame malgré tout une validation, on reste en test et on ajoute le compte partagé (et
   ceux des amis) dans **Utilisateurs tests**.

## Étape 4 : le client OAuth Android

1. **Clients** → **Créer un client** → type **Android**.
2. Nom : `Carnet Android`. Nom de package : celui du lot 1. Empreinte : le SHA-1 **debug**.
3. **Créer.** Un client Android n'a **pas de secret** : il n'y a rien à télécharger, et rien
   à mettre dans l'app. Google reconnaît l'app par son package et sa signature.
4. Ajouter le SHA-1 **release** au même client. Si la console n'accepte qu'une empreinte,
   créer un second client Android, même package, avec le SHA-1 release. Dans les deux cas,
   le faire **avant** la première release signée.

## Étape 5 : premier essai (fin du lot 1)

1. Installer l'APK debug, puis appuyer sur « Connecter Google Drive ».
2. Choisir le compte partagé et accepter l'écran de consentement.
3. Vérifier que le dossier `Carnet de recettes` apparaît dans le Drive, puis le **déplacer
   dans `Maison/`**.
4. Sur le second téléphone (même compte) : se connecter et vérifier qu'il retrouve le dossier.

## Erreurs déjà rencontrées sur Popote

| Message | Cause | Remède |
|---|---|---|
| `UNREGISTERED_ON_API_CONSOLE` en release alors que le debug marche | SHA-1 release absent du client | Étape 4.4 |
| La connexion casse « sans qu'aucun code n'ait changé » | Nouvelle clé debug générée par le conteneur | Clé debug versionnée |
| `DEVELOPER_ERROR` | Package ou SHA-1 différents de ceux du client | Recalculer l'empreinte et comparer |
| `access_denied` | App en test et compte non déclaré testeur | Étape 3.5 |
