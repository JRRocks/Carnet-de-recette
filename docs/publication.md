# Essayer et publier une version

Comment une correction écrite ici arrive sur les téléphones, sans câble ni Play Store.
Repris de Popote Compagnie (moteur Library `maj-auto-github`), avec une simplification :
le code de ce dépôt est **déjà public**, donc les versions sont publiées dans ses propres
Releases. Il n'y a ni second dépôt « versions » ni jeton personnel : le jeton fourni d'office
aux Actions (`GITHUB_TOKEN`) suffit.

## Les trois pièces

| Pièce | Rôle |
|---|---|
| `.github/workflows/apercu.yml` | À chaque push qui touche l'app sur une branche de travail : tests, puis APK d'essai signé de la clé release, attaché à la **pré-version** `apercu-<versionName>` |
| `.github/workflows/version.yml` | **Sur feu vert explicite seulement** : APK release signé attaché à la version `v<versionName>` |
| `app/src/main/kotlin/…/maj/MiseAJour.kt` | Au lancement, interroge `/releases/latest` et propose la mise à jour si son `versionCode` est plus grand |

`/releases/latest` ignore les pré-versions : un aperçu n'est **jamais** proposé en mise à
jour. Seul `version.yml` touche aux téléphones déjà installés.

## Mise en place (une seule fois)

### 1. Créer la clé de signature release

C'est elle qui permet à chaque nouvelle version de **remplacer** la précédente. Android
refuse un APK signé d'une autre clé : la perdre, c'est ne plus jamais pouvoir mettre à jour
sans désinstaller. **Garde-la en deux exemplaires** (par exemple dans ton gestionnaire de mots
de passe et sur une clé USB), et **jamais dans le dépôt** (`.gitignore` l'exclut).

Sur un ordinateur où Java est installé :

```bash
keytool -genkeypair -v -keystore carnet-release.jks -alias carnet \
  -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` demande **un seul** mot de passe : avec un Java récent, le fichier et la clé
partagent le même. Il demande aussi un nom, que tu peux laisser générique. Sans Java :
Android Studio, menu **Build → Generate Signed App Bundle or APK → Create new…** produit le
même fichier. Il affiche deux champs de mot de passe : mets **le même** dans les deux, car
le second est ignoré.

### 2. Déposer les secrets sur ce dépôt

`Settings → Secrets and variables → Actions → New repository secret`, sur
`JRRocks/Carnet-de-recette` :

| Secret | Contenu |
|---|---|
| `SIGNATURE_KEYSTORE_BASE64` | Le fichier `.jks` encodé en base64 (voir ci-dessous) |
| `SIGNATURE_STORE_PASSWORD` | Le mot de passe |
| `SIGNATURE_KEY_ALIAS` | `carnet` (l'alias choisi à l'étape 1) |
| `SIGNATURE_KEY_PASSWORD` | Le même mot de passe |

Encodage en base64, sur une seule ligne :

```bash
base64 -w0 carnet-release.jks                                        # Linux
base64 -i carnet-release.jks | tr -d '\n'                            # macOS
[Convert]::ToBase64String([IO.File]::ReadAllBytes("carnet-release.jks"))   # Windows PowerShell
```

Ces secrets sont la seule copie accessible à la CI. Les workflows effacent la clé du disque
du runner à la dernière étape, quoi qu'il arrive.

### 3. Déclarer l'empreinte release à Google

C'est la seule empreinte déclarée au client OAuth : celle de la clé debug ne l'est jamais,
car cette clé est publique ([google-cloud.md](google-cloud.md), « Pourquoi pas l'empreinte de
la clé debug »). Calcul de l'empreinte :

```bash
keytool -list -v -keystore carnet-release.jks -alias carnet | grep SHA1              # Linux, macOS
keytool -list -v -keystore carnet-release.jks -alias carnet | Select-String SHA1     # Windows PowerShell
```

Puis [google-cloud.md](google-cloud.md), étape 4. Cette empreinte ne va **jamais** dans le
dépôt.

## Essayer une version (aperçu)

1. Incrémenter `versionCode` et `versionName` dans `app/build.gradle.kts`.
2. Pousser sur la branche de travail. `apercu.yml` se lance tout seul.
3. Le lien de l'APK est dans le résumé du run, et sur la page
   `https://github.com/JRRocks/Carnet-de-recette/releases/tag/apercu-<versionName>`.

**Tant que la clé release n'est pas posée**, `apercu.yml` lance les tests puis s'arrête sans
rien publier : un APK de debug ne pourrait pas se connecter au Drive. L'aperçu étant signé de
la même clé que les versions publiées, il s'installe par-dessus et garde les données.

## Publier (feu vert explicite seulement)

L'APK publié est proposé à **tous** les téléphones au lancement suivant. On ne publie donc
qu'avec l'accord explicite de l'utilisateur, donné pour cette version-là.

1. Vérifier que la tête de la branche est bien la version essayée en aperçu.
2. Lancer `version.yml` (`workflow_dispatch`, sur la branche voulue). Le workflow pose
   lui-même la balise `v<versionName>` : le push direct d'une balise depuis une session cloud
   est bloqué (HTTP 403).
3. Vérifier que le run se termine avec succès, puis donner le lien
   `https://github.com/JRRocks/Carnet-de-recette/releases/tag/v<versionName>`.

> GitHub ne permet de lancer à la main (`workflow_dispatch`) qu'un workflow présent sur la
> branche par défaut. Tant que `main` n'a pas reçu `version.yml`, la publication attend la
> fusion de la branche de travail.
