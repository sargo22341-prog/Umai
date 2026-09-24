# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Fiche recette : les catégories, tags et ustensiles passent entre la nutrition et le lien d'origine, sur 3 lignes au plus avec « Afficher plus », et un appui lance une recherche sur le tag.
- Fiche recette : la présentation s'affiche sous les durées.
- Les tags techniques `calorie-*` ne sont plus affichés sur les fiches recettes ni proposés dans les filtres de recherche.
- Planning : semaine fixe, qui commence le jour choisi dans les réglages Mealie (« Premier jour de la semaine »), toujours ouverte sur aujourd'hui.
- Planning : la recherche de recette s'ouvre en plein écran avec une animation, comme la page Recherche (tri et filtres compris).
- Planning : tirage d'une recette au hasard, parmi toutes les recettes ou dans une catégorie.
- Planning : le champ de note reste visible au-dessus du clavier.
- Fiche recette : l'ajout au planning propose cette semaine et la suivante, à partir du premier jour de la semaine choisi dans Mealie.
- Réglages Mealie : les préférences sans effet dans l'app sont signalées comme propres au serveur Mealie.
- L'application est verrouillée en mode portrait.
- Import : prise en charge de 750g et Marmiton, avec récupération automatique des photos d'étapes associées à leurs étapes (réglable par fournisseur, activée par défaut).
- Mode cuisine : minuteurs détectés dans les étapes (« cuire 15 min » → Démarrer 15 min), plusieurs à la fois, avec son et vibration réglables.
- Listes de courses : nouveau mode courses, avec de grandes lignes cochées d'un seul appui, regroupées par rayon.
- Listes de courses : cocher plusieurs articles à la suite ne les fait plus clignoter.
- Réglages : chaque ligne à interrupteur est lue comme un seul interrupteur, état désactivé compris.
- Planning : le choix de la catégorie et le bouton de tirage au sort sont sur la même ligne.
- Mode cuisine : les minuteurs continuent de tourner après avoir quitté le mode cuisine ou l'application, s'affichent dans les notifications (pause, reprise, arrêt) et sonnent à l'heure même téléphone en veille.
- Hors du mode cuisine, une pastille montre chaque minuteur en cours ; un appui rouvre la recette à l'étape du minuteur.
- Listes de courses : le rappel des portions d'origine s'accorde au singulier.
