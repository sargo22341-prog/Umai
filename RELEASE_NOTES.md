# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Planning : le tirage d'une recette au hasard (choix de catégorie et bouton « Tirer ») est retiré de l'ajout d'un repas.
- Planning : planification automatique d'un jour ou de la semaine, un plat à midi et un le soir (jamais de dessert ni de boisson), choisis pour partager leurs ingrédients et respecter les règles de planning de Mealie.
- Planning : écran « Types de plats reconnus » pour corriger le type vu dans une catégorie ou un tag.
- Import : une vidéo YouTube est reconstruite en recette complète (ingrédients, étapes titrées, passage de la vidéo de chaque étape) à partir de sa description, de ses chapitres et de sa transcription.
- Mode cuisine : la vidéo YouTube d'une recette se place sur l'étape en cours et la joue en boucle.
- IA locale : un modèle de langage téléchargé à part (Qwen3.5 4B recommandé) tourne sur le téléphone, sans service distant, pour l'import vidéo et la reconnaissance des plats.
- Recherche : la page s'ouvre directement sur la liste des recettes, et le tri « date d'ajout » décroissant n'affiche plus une page vide ; les recettes sans note ni date de réalisation passent en fin de tri.
- Import : une vidéo YouTube déjà importée est reconnue comme doublon, quelle que soit la forme de son adresse.
