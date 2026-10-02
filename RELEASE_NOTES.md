# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Planning : le nombre de portions d’un repas ou d’un snack se modifie (« 2 portions », « un café de plus ») et les calories suivent.

- Recette : un appui sur la grande photo ouvre le mode cuisine, et des pastilles y signalent une vidéo ou des étapes illustrées.
- Minuteurs : la notification devient une Live Update, avec une barre de progression et le décompte dans la barre d’état.
- Animations : la recherche et la création de recette se déplient depuis leur bouton, les cartes s’enfoncent sous le doigt, les listes apparaissent en cascade et le chargement montre le contour de l’écran.
- Animations : article de courses barré au fil du trait, portions et calories qui défilent, cœur et étoiles animés, étapes du mode cuisine en parallaxe et minuteur qui bat ses dix dernières secondes, avec retours haptiques.
