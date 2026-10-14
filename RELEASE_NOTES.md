# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Migration vers LiteRT-LM 0.18 avec accélération Tensor G5 vérifiée et dispatch compilé depuis les sources correspondantes.
- Préservation de la cause d’un échec d’initialisation du moteur d’IA locale.
- Mise à jour de Kotlin et des outils Gradle

