#!/usr/bin/env bash
# Prépare la compilation Android dans un conteneur qui n'a pas de SDK (session cloud de
# Claude Code). Sur un ordinateur où Android Studio est installé, inutile.
#
#   ./outils/sdk-android.sh
#
# Deux téléchargements, idempotents :
#  1. le SDK (plateforme 35, outils de build 35.0.0) dans /opt/android-sdk, puis
#     `local.properties` qui le désigne ;
#  2. le pot Android de Robolectric pour les tests d'écran. Robolectric le télécharge
#     lui-même au premier test, mais Maven Central répond souvent « 429 Too Many Requests »
#     depuis le proxy des sessions cloud : on le prend ici, avec des pauses entre les essais.
set -euo pipefail

SDK="${ANDROID_HOME:-/opt/android-sdk}"
racine="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -d "$SDK/platforms/android-35" ] || [ ! -d "$SDK/build-tools/35.0.0" ]; then
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/outils.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$tmp/outils.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  # `yes` ne s'arrête jamais de lui-même : il meurt de SIGPIPE (141) quand sdkmanager a fini,
  # ce qui, sous `pipefail`, arrêterait le script juste après l'installation.
  { yes || true; } | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
    "platforms;android-35" "build-tools;35.0.0" >/dev/null
  rm -rf "$tmp"
  if [ ! -d "$SDK/platforms/android-35" ] || [ ! -d "$SDK/build-tools/35.0.0" ]; then
    echo "Installation du SDK incomplète dans $SDK" >&2
    exit 1
  fi
fi
echo "sdk.dir=$SDK" > "$racine/local.properties"

# Version liée à Robolectric 4.13 et à `@Config(sdk = [34])` des tests de capture.
version=14-robolectric-10818077-i6
dossier="$HOME/.m2/repository/org/robolectric/android-all-instrumented/$version"
base="https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented/$version"
mkdir -p "$dossier"
for ext in pom jar; do
  fichier="$dossier/android-all-instrumented-$version.$ext"
  [ -s "$fichier" ] && continue
  for essai in 1 2 3 4; do
    if curl -fsSL -o "$fichier.part" "$base/android-all-instrumented-$version.$ext"; then
      mv "$fichier.part" "$fichier"
      break
    fi
    sleep $((essai * 10))
  done
  rm -f "$fichier.part"
done
echo "SDK Android prêt : $SDK"
