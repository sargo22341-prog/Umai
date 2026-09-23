# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Mode cuisine : la vidéo de la recette (Jow) se lit sur le passage de chaque étape, en boucle et sans son par défaut.
- Mode cuisine : à la fin, la recette peut être marquée comme cuisinée dans Mealie.
- Réglages → Fournisseurs : page Jow pour récupérer vidéo et photos d'étapes à l'import.
- Filtre calories (300, 500, 700 kcal ou sans calories) grâce aux tags « calorie-… », posés automatiquement.
- Édition et création : bouton pour lier automatiquement les ingrédients aux étapes, liens supprimables un par un.
- Photos d'étapes (step-1, step-2…) : affichées sur la recette et en mode cuisine, et ajoutables depuis l'éditeur.
- Partager → Umai depuis un navigateur ou une app de recettes ouvre directement l'import.
- Import : une recette déjà importée depuis la même page est signalée au lieu d'être dupliquée.
- Planning : ajout des recettes de la semaine à une liste de courses (recettes, portions, ingrédients).
- Sécurité : le jeton Mealie n'est plus envoyé qu'à l'instance, jamais aux sites tiers.
- Nouveau logo : un bol de ramen sur fond crème, aussi affiché au démarrage.
