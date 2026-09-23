#!/usr/bin/env bash
# Monte la version de l'application dans app/version.properties.
# Usage : scripts/bump-version.sh [patch|minor|major]   (patch par defaut)
# versionCode augmente toujours de 1 : Android refuse d'installer une mise a jour dont le
# versionCode est inferieur a celui de la version installee.
set -euo pipefail

part="${1:-patch}"
file="$(dirname "$0")/../app/version.properties"

# \r? : le fichier peut avoir des fins de ligne Windows sur un poste de developpement.
code=$(sed -n 's/^versionCode=\([0-9]\+\)\r\?$/\1/p' "$file")
name=$(sed -n 's/^versionName=\([0-9]\+\.[0-9]\+\.[0-9]\+\)\r\?$/\1/p' "$file")
if [[ -z "$code" || -z "$name" ]]; then
  echo "versionCode ou versionName illisible dans $file" >&2
  exit 1
fi

IFS=. read -r major minor patch <<< "$name"
case "$part" in
  patch) patch=$((patch + 1)) ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  major) major=$((major + 1)); minor=0; patch=0 ;;
  *) echo "Partie de version inconnue : $part (patch, minor ou major)" >&2; exit 1 ;;
esac

new_code=$((code + 1))
new_name="$major.$minor.$patch"
sed -i -e "s/^versionCode=.*/versionCode=$new_code/" -e "s/^versionName=.*/versionName=$new_name/" "$file"

echo "Version $name ($code) -> $new_name ($new_code)"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  { echo "code=$new_code"; echo "name=$new_name"; } >> "$GITHUB_OUTPUT"
fi
