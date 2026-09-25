# IA locale

umai peut faire tourner un modèle de langage **sur le téléphone**, sans service distant. Le
modèle se télécharge à part, depuis *Profil › IA locale*, et se remplace sans mettre à jour
l'application. Toutes les fonctions qui s'en servent marchent aussi sans lui, avec un algorithme
classique.

## Où le modèle sert, et où il ne sert pas

| Fonction | Avec le modèle | Sans le modèle |
|---|---|---|
| Import d'une vidéo YouTube | Lit titre, description, chapitres et transcription horodatée ; écrit les ingrédients avec leurs quantités, des étapes rédigées et titrées, le début de chaque étape dans la vidéo. | Ingrédients et étapes lus dans la description, étapes = chapitres (texte tiré de la transcription), placées par les chapitres ou par alignement mots-transcription. |
| Planning automatique | Classe les recettes que rien ne situe (ni catégorie, ni tag, ni nom, ni historique). | Ces recettes sont jugées sur leurs ingrédients (sucré seul = dessert). |

Restent volontairement **algorithmiques**, parce qu'un algorithme y est plus fiable qu'un modèle
de 4 milliards de paramètres :

- le lien étape ↔ ingrédients (`IngredientLinker`, par les noms) : essayé avec le modèle (positions
  dans la liste), il reliait des ingrédients faux lors de l'essai sur le téléphone (le vin rouge à
  l'étape de l'huile d'olive) ;
- le choix des plats du planning et le regroupement des ingrédients (`MealPlanner`) : c'est un
  problème d'optimisation, pas de langage ;
- le placement des étapes quand le modèle n'en donne pas (`Transcript.alignSteps`, programmation
  dynamique sur les mots partagés).

La réponse du modèle est **contrainte par un schéma JSON** : llama.cpp convertit le schéma en
grammaire GBNF et n'autorise que les jetons qui la respectent. La réponse est donc toujours du JSON
valide, puis elle est vérifiée (débuts d'étapes hors de la vidéo ou dans le désordre écartés, champs
vides complétés par les règles).

## Runtime : llama.cpp, sur le CPU

- **llama.cpp** (licence MIT), épinglé à la release `b11179` et vérifié par SHA-256
  (`app/src/main/cpp/CMakeLists.txt`). Le code source n'est pas dans le dépôt : il est téléchargé à
  la compilation, et seuls les dossiers utiles sont extraits (le dossier `tools/ui` dépasse la
  limite de longueur des chemins de Windows).
- Le backend CPU est compilé en 7 variantes (ARMv8.0 à ARMv9.2, `GGML_CPU_ALL_VARIANTS`) : la
  meilleure pour le processeur est chargée à l'exécution (i8mm/SVE2 sur un Tensor G5, dotprod sur un
  Tensor G1). Il faut pour cela que les bibliothèques soient extraites de l'APK
  (`jniLibs.useLegacyPackaging = true`).
- Pont JNI : `app/src/main/cpp/llm_bridge.cpp`, côté Kotlin `llm/data/LlamaNative.kt`.
- Modèle chargé sans `mmap` (les poids sont réorganisés pour les instructions matricielles : avec
  `mmap`, le fichier restait résident à côté de la copie, 6,3 Go au lieu de 4,2 Go), puis libéré
  une minute après la dernière utilisation.
- Contexte de 16 384 jetons, soit la transcription d'une vidéo d'environ 20 minutes.
- Un service de premier plan tient l'application en vie pendant une génération : on peut passer à
  une autre application pendant l'import d'une vidéo.

### Pourquoi pas le TPU, le GPU ou un autre runtime

- **TPU du Tensor G5** : il n'est accessible aux applications tierces qu'à travers le *Google Tensor
  SDK*, en bêta fermée (inscription, compilation Bazel, bibliothèques de dispatch Google). AICore
  (Gemini Nano) exige les services Google Play, absents de GrapheneOS. Pas utilisable ici.
- **GPU** : le Tensor G5 a un GPU PowerVR ; le backend Vulkan de llama.cpp y est signalé comme
  instable (sorties nulles, plantages du compilateur de shaders), et le backend OpenCL vise les
  GPU Adreno. Le CPU reste le chemin fiable.
- **LiteRT-LM / MediaPipe** : format de modèle propre (`.litertlm`), catalogue restreint, et
  accélération NPU liée au même SDK Tensor ; GGUF + llama.cpp permet n'importe quel modèle de
  Hugging Face, y compris un modèle personnalisé saisi par son adresse.

## Modèles mesurés (Pixel 10 Pro XL, Tensor G5, 16 Go, GrapheneOS)

Mesures de l'écran *Tester la vitesse* (invite d'environ 300 jetons, 64 jetons générés, 6 fils) :

| Modèle (GGUF Q4_K_M) | Fichier | Lecture | Écriture | Mémoire | Chargement |
|---|---|---|---|---|---|
| **Qwen3.5 4B** (recommandé) | 2,7 Go | 44 jetons/s | 6,5 jetons/s | 4,2 Go | 5,0 s |
| Gemma 4 E4B | 5,0 Go | 26 jetons/s | 6,2 jetons/s | 6,6 Go | 8,9 s |
| Qwen3.5 9B | 5,7 Go | 26 jetons/s | 3,8 jetons/s | 6,5 Go | 11,0 s |

Import réel de la vidéo *Lasagnes* de 750g (7 min, chapitres et transcription automatique) avec
Qwen3.5 4B : **4 min 13 s** au total (≈ 25 s de lecture, le reste en rédaction), recette de
5 étapes titrées reliées à la vidéo, ingrédients avec quantités, minuteurs détectés dans les étapes.

Choix : **Qwen3.5 4B** par défaut. Ce n'est pas le plus petit modèle disponible (0,8B et 2B
existent), mais le meilleur compromis mesuré : il lit les transcriptions presque deux fois plus
vite que les deux autres, pour une qualité suffisante sur la compréhension des recettes en français
et en anglais. Qwen3.5 9B reste proposé pour qui accepte un import nettement plus long (estimé à environ
7 à 8 minutes pour la même vidéo, d'après ses vitesses mesurées). Les trois
modèles sont sous licence Apache 2.0.

## Téléchargement

- Par le gestionnaire de téléchargement du système (reprise après coupure, notification),
  **en Wi-Fi uniquement**, dans le stockage propre de l'application (supprimé avec elle).
- Fichier vérifié avant usage : SHA-256 pour les modèles du catalogue, en-tête `GGUF` pour un
  modèle saisi par son adresse. Un seul modèle est gardé : le précédent est supprimé une fois le
  nouveau vérifié.

## Faire évoluer

- Nouveau modèle sans mise à jour : *Un autre modèle*, adresse `https://…/fichier.gguf`.
- Nouveau modèle au catalogue : `llm/domain/LocalModels.kt` (adresse, taille, SHA-256 donnés par
  l'API Hugging Face `https://huggingface.co/api/models/<dépôt>/tree/main`).
- Nouvelle version de llama.cpp : `LLAMA_TAG` et `LLAMA_SHA256` dans `app/src/main/cpp/CMakeLists.txt`.

## YouTube

L'extraction suit la méthode de **yt-dlp** (domaine public), portée en Kotlin
(`youtube/data/YouTubeClient.kt`), sans dépendance :

- page de lecture : chapitres (`ytInitialData`) et identifiant de session (`VISITOR_DATA`) ;
- API player de YouTube, en client `ANDROID_VR` : description, durée, pistes de sous-titres
  téléchargeables sans jeton (format `json3`) ;
- au moment de cuisiner, client `VISIONOS` : flux HLS lu par ExoPlayer, sans en-tête particulier
  (repli sur le MP4 360p du client `ANDROID_VR`). Les adresses de flux expirent : elles sont
  demandées à chaque ouverture du mode cuisine.

Écartés : NewPipeExtractor et youtubedl-android (GPLv3, incompatible avec la licence MIT de
l'application sans la relicencier ; le second embarque en plus Python, environ 25 Mo).

**Fragilité assumée** : YouTube change régulièrement ce qu'il accorde à chaque client. Quand
l'import échoue avec « YouTube a répondu d'une façon que l'application ne sait pas lire », il faut
mettre à jour les noms et versions de clients d'après `yt_dlp/extractor/youtube/_base.py`.
