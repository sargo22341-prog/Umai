# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Mise à jour des outils de build (Gradle 9.9.1, JDK 25, CMake 4.1.2) et de l'import YouTube (NewPipeExtractor 0.27.1).
- Déclaration de la bibliothèque `libedgetpu_util.so` du pilote TPU, comme le fait le paquet officiel de LiteRT.
