# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- Nouvelle transition entre les pages : glissement horizontal, geste retour prédictif compris.
- Page recette : favori et notation 5 étoiles entre le titre et la description.
- Page recette : le champ commentaire passe sous les commentaires et reste visible au-dessus du clavier.
- Page recette : bouton crayon pour modifier la recette, avec des sections navigables par onglets.
- Réglages : choix des sections affichées sur la page recette (durées, nutrition, lien d'origine, commentaires).
- Photo de profil : galerie, appareil photo ou fichiers, puis recadrage (zoom et déplacement).
- Création de recette : nouvelle étape photo, avec recadrage.
- Création de recette : une pastille confirme l'enregistrement du brouillon en quittant le formulaire.
- Recherche : tri directement sur l'écran, avec sens croissant ou décroissant.
- Filtres : note minimale en étoiles, catégories et tags par saisie, portions retirées, suggestions visibles au-dessus du clavier.
