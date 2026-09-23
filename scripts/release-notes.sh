#!/usr/bin/env bash
# Notes de la prochaine release, ecrites a la main dans RELEASE_NOTES.md sous la ligne marqueur.
# Usage : scripts/release-notes.sh extract <fichier>   ecrit les notes (fichier vide s'il n'y en a pas)
#         scripts/release-notes.sh reset               vide les notes en gardant l'en-tete
set -euo pipefail

file="${RELEASE_NOTES_FILE:-$(dirname "$0")/../RELEASE_NOTES.md}"
marker='<!-- notes -->'

# \r : le fichier peut avoir des fins de ligne Windows sur un poste de developpement.
if ! tr -d '\r' < "$file" | grep -qxF "$marker"; then
  echo "Ligne marqueur $marker absente de $file" >&2
  exit 1
fi

case "${1:-}" in
  extract)
    out="${2:?Fichier de sortie manquant}"
    # Tout ce qui suit le marqueur, sans lignes vides au debut ni a la fin.
    tr -d '\r' < "$file" | awk -v marker="$marker" '
      seen && NF { while (blank > 0) { print ""; blank-- } print; started = 1; next }
      seen && !NF { if (started) blank++; next }
      $0 == marker { seen = 1 }
    ' > "$out"
    ;;
  reset)
    tmp="$(mktemp)"
    tr -d '\r' < "$file" | awk -v marker="$marker" '{ print } $0 == marker { exit }' > "$tmp"
    printf '\n' >> "$tmp"
    mv "$tmp" "$file"
    ;;
  *)
    echo "Usage : $0 extract <fichier> | reset" >&2
    exit 1
    ;;
esac
