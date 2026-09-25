# Notes de la prochaine version

Écrire sous la ligne `<!-- notes -->
` les changements de la prochaine version, en Markdown (une
ligne `- …` par changement). À chaque push sur `main`, la CI publie ce texte comme description de
la GitHub Release, puis vide la liste dans le commit `Version X.Y.Z`. Sans notes, GitHub génère la
liste des commits. Détails : [docs/release.md](docs/release.md).

<!-- notes -->

- IA locale : un modèle de langage téléchargé à part (Gemma 4 E2B recommandé) tourne sur le téléphone, sans service distant, pour l'import vidéo et la reconnaissance des plats.

- IA locale : le modèle tourne avec LiteRT-LM sur le TPU des Pixel à puce Tensor G5 ou G6 (y compris sous GrapheneOS, sans services Google), sinon sur le GPU, et sur le processeur en dernier recours ; l'écran indique où il tourne réellement. Les anciens modèles GGUF sont supprimés.