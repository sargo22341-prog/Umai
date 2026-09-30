# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->
- Les minuteurs de cuisson survivent à l'arrêt de l'application par le système et sonnent quand même à l'heure.
- Une action refusée par Mealie faute de droits affiche « Action non autorisée » au lieu de « session expirée ».
- Une connexion enregistrée momentanément illisible propose de réessayer au lieu de renvoyer vers la configuration.
- Le jeton n'est plus envoyé qu'à l'instance elle-même (même schéma, hôte et port), jamais en HTTP à une instance HTTPS.
- Les liens de description vidéo et les sites tiers ne peuvent plus viser le réseau local, et les téléchargements tiers sont plafonnés.
- La synchronisation des étiquettes de calories et le rangement des photos ne modifient plus que ce champ de la recette (PATCH).
- Annuler un import n'attend plus la fin de la transcription Whisper : le décodage en cours est interrompu.
- Une recherche d'ingrédients en échec affiche l'erreur au lieu d'une liste vide ; les règles de planning illisibles ne sont plus ignorées en silence.

