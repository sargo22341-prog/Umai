# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Fiche recette : un message s'affiche quand aucune application ne peut ouvrir la page d'origine
- Import : un envoi refusé d'une photo d'étape est désormais signalé au lieu d'être ignoré
- Écoute d'une vidéo : un décodeur audio bloqué s'arrête au bout de 30 s au lieu de tourner sans fin
