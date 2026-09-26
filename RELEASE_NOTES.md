# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Import vidéo : sans sous-titres (ou quand YouTube les refuse), l'IA locale écoute la vidéo, et regarde ses images quand rien n'y est dit, pour placer chaque étape au bon moment.
- Import vidéo : les chapitres ne sont plus devinés quand la vidéo ne donne aucun repère, et le nombre de portions et les temps lus par l'IA ne sont plus perdus.
- Modifier une recette : nouvel onglet Vidéo pour placer ou corriger le début et la fin de chaque étape dans la vidéo, avec le lecteur à portée de main.
- Import vidéo : la recette est de nouveau écrite sur le TPU des Pixel 10, bien plus vite, au lieu du GPU pour une vidéo courte.
- Import vidéo : les images de la vidéo ne sont plus analysées quand la description ou la page de recette donne déjà les ingrédients.
- Import vidéo : une vidéo sans sous-titres est maintenant écoutée par Whisper, plus juste et plus rapide que l'IA locale ; trois tailles au choix dans Profil › IA locale (Small recommandé).
- Import : il continue quand on quitte l'écran ou l'application, avec son avancement dans une notification ; revenir sur « Importer une recette » (ou toucher la notification) retrouve l'import en cours, et une notification prévient quand il est terminé.
- IA locale : sans TPU, le modèle tourne d'abord sur le processeur, plus rapide que le GPU et chargé en 2 s au lieu d'une minute (mesuré sur Pixel 6 Pro) ; le GPU reste pour les longues transcriptions.
- Planning automatique : la reconnaissance des plats par l'IA locale est plus rapide et plus juste (plus aucune recette oubliée), et le modèle se charge pendant la lecture des recettes.
- Planning : total des calories de chaque jour (une portion par recette), et bouton « Ajouter un snack, une boisson… » : nom, photo facultative, étiquette nutritionnelle photographiée et lue par l'IA locale (la photo est supprimée ensuite), puis quantité consommée.
