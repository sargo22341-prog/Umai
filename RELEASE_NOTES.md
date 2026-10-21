# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Les dernières recettes de l’accueil ne restent plus invisibles, laissant un grand vide sous leur titre, quand l’app s’ouvre.
- Dépendances mises à jour (Gradle 9.8.2, DataStore 1.3.0, kotlinx.serialization 1.12.0, LiteRT-LM 0.18.1).
