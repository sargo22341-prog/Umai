# IA locale

umai peut faire tourner un modèle de langage **sur le téléphone**, sans service distant. Le
modèle se télécharge à part, depuis *Profil › IA locale*, et se remplace sans mettre à jour
l'application. Toutes les fonctions qui s'en servent marchent aussi sans lui, avec un algorithme
classique.

## Où le modèle sert, et où il ne sert pas

| Fonction | Avec le modèle | Sans le modèle |
|---|---|---|
| Import d'une vidéo YouTube | Lit titre, description, chapitres et transcription horodatée ; sans sous-titres, fait **écouter** la vidéo par Whisper, et en dernier recours **regarde** ses images (voir *Vidéos sans sous-titres*) ; écrit des étapes rédigées et titrées, le début de chaque étape dans la vidéo, et les ingrédients quand ni la page de recette ni la description ne les donnent. | Ingrédients de la page de recette ou de la description, étapes lues dans la description, sinon étapes = chapitres (texte tiré de la transcription), placées par les chapitres ou par alignement mots-transcription. |
| Planning automatique | Classe les recettes que rien ne situe (ni catégorie, ni tag, ni nom, ni historique) : voir *Planning automatique* plus bas. | Ces recettes sont jugées sur leurs ingrédients (sucré seul = dessert). |
| Produit ajouté au planning | **Lit** l'étiquette nutritionnelle photographiée : voir *Étiquettes nutritionnelles* plus bas. | Les valeurs pour 100 g se saisissent à la main. |
| Aliment écrit au planning (« 1 pizza saumon raviole ») | **Estime** un plat que la table Ciqual n'a pas : voir *Aliments écrits* plus bas. | Un aliment absent de la table se décrit à la main. |

Restent volontairement **algorithmiques**, parce qu'un algorithme y est plus fiable qu'un modèle
de téléphone :

- le lien étape ↔ ingrédients (`IngredientLinker`, par les noms) : essayé avec le modèle (positions
  dans la liste), il reliait des ingrédients faux lors de l'essai sur le téléphone (le vin rouge à
  l'étape de l'huile d'olive) ;
- le choix des plats du planning et le regroupement des ingrédients (`MealPlanner`) : c'est un
  problème d'optimisation, pas de langage ;
- le placement des étapes quand le modèle n'en donne pas (`Transcript.alignSteps`, programmation
  dynamique sur les mots partagés) ;
- la liste finale des ingrédients (`IngredientMerge`, `IngredientEvidence`) : une ligne par aliment,
  et une quantité seulement si une source l'écrit ou la dit (voir *Ingrédients* plus bas).

La réponse du modèle est **contrainte par un schéma JSON** : le runtime n'autorise que les jetons qui
le respectent, donc la réponse est toujours du JSON valide, puis elle est vérifiée (débuts d'étapes
hors de la vidéo ou dans le désordre écartés, champs vides complétés par les règles). Avec la version
de LiteRT-LM utilisée (voir plus bas), seul le décodage des **appels d'outils** est contraint : le
schéma devient les paramètres de l'unique outil `answer` par lequel le modèle répond, et l'app lit
les arguments de l'appel (`LiteRtLmEngine`).

Ce que ce décodage contraint respecte, et ne respecte pas, constaté sur le Pixel 10 Pro XL :

- les **propriétés** d'un objet et leur caractère obligatoire sont respectées : c'est pourquoi le
  placement des étapes sur des images demande une clé par étape (`step_1`, `step_2`…), et non une
  liste ;
- `maxItems` et `minItems` ne le sont **pas** : une liste plafonnée à 8 étapes en a reçu 13 ;
- un `enum` n'est tenu que si la propriété a aussi son `"type"` (constaté sur le Pixel 6 Pro, voir
  *Planning automatique*) ; `additionalProperties` avec un schéma fait échouer la contrainte, et
  `$ref` n'est pas suivi ;
- les **nombres** reviennent en `LazilyParsedNumber` de Gson, jamais en `Double`, et le modèle écrit
  `0.0` pour `0` : `ToolArguments` les réécrit en entiers. Avant cette correction, tous les entiers de
  la réponse étaient perdus (portions, temps, **débuts des étapes**) : les chapitres venaient alors
  tous de l'alignement par mots.

## Runtime : LiteRT-LM, sur le TPU, le GPU ou le CPU

- **LiteRT-LM** (Google, Apache 2.0), dépendance Maven `com.google.ai.edge.litertlm:litertlm-android`,
  sans aucun service Google Play. Il est isolé derrière l'interface `AiEngine`
  (`llm/domain/AiEngine.kt`) : seul `llm/data/LiteRtLmEngine.kt` l'importe.
- **Ordre des backends : TPU → CPU → GPU** (`LocalLanguageModel.PREFERENCE`). Le CPU ne prend que
  les prompts qui tiennent dans son contexte de 4 096 jetons ; les plus longs (la transcription d'une
  longue vidéo) vont au GPU. Mesuré sur le Pixel 6 Pro (voir *Téléphones sans TPU* plus bas) et
  cohérent avec les mesures du Pixel 10 Pro XL : le CPU écrit plus vite que le GPU, charge le modèle
  en 2 s au lieu d'une minute, et ne pousse pas Android à fermer d'autres applications pour lui faire
  de la place. Un backend n'est retenu qu'une
  fois le modèle chargé **et prouvé** dessus : après le chargement, `DeviceAccelerators.missingDriver`
  vérifie dans `/proc/self/maps` que le pilote du backend est bien chargé dans le processus
  (`libLiteRtDispatch_GoogleTensor.so` et `libedgetpu_litert.so` pour le TPU, `libOpenCL*.so` pour le
  GPU) ; l'app ne charge jamais ces bibliothèques elle-même, leur présence prouve donc que le runtime
  s'en sert. Un runtime qui accepterait le TPU en retombant en silence sur le CPU ne passerait pas ce
  contrôle : l'écran n'affiche jamais « TPU » pour une inférence qui tourne ailleurs.
- Un backend qui échoue au chargement est écarté jusqu'au redémarrage de l'app ; une réponse qui
  échoue en cours d'écriture est reprise sur le backend suivant. Un prompt trop long pour le contexte
  d'un backend va directement au suivant ; si seul un backend à grand contexte pouvait le prendre et
  qu'il échoue, l'appelant reçoit `TOO_LONG` et raccourcit la transcription.
- LiteRT-LM 0.14.0 ne compte pas les jetons à l'avance et ne plafonne pas la réponse : la taille est
  **estimée** (`LocalLanguageModel.estimatedTokens` : système + message + schéma à 3,5 caractères par
  jeton, plus la réponse attendue). Mesure sur le prompt d'import d'une vidéo française de 4 min 45
  (`theImportPromptEstimateHoldsOnTheTpu`) : 5 497 caractères = **1 403 jetons** (3,9 car./jeton),
  réponse de 451 jetons, 38 s au TPU. L'ancienne estimation (3 car./jeton, schéma oublié, 2 048 jetons
  réservés à la réponse) donnait 4 414 jetons pour un tel import : il partait sur le GPU, trois fois
  plus lent à écrire. Le journal dit quand un backend est écarté pour cette raison
  (`Backend: TPU skipped: about 4414 tokens needed, context 4096`).
- Journal (`adb logcat -s UmaiAi`) : `Backend: TPU | Model: Gemma 4 E2B | SoC: Tensor G5` au
  chargement, puis après chaque réponse les vitesses mesurées par le runtime
  (`prompt 130 tokens at 136,6/s | answer 50 tokens at 13,9/s`).
- Modèle chargé en `mmap`, libéré une minute après la dernière utilisation. Un service de premier
  plan tient l'app en vie pendant une génération ; pendant un import, c'est celui de l'import qui la
  tient, avec sa propre notification d'avancement (`SystemImportHost`), et celui de l'IA ne démarre
  pas. Les deux passent par `ForegroundKeeper` : un service arrêté avant d'avoir atteint le premier
  plan ferait tuer l'app par Android (un import qui échoue aussitôt, par exemple).
- `LanguageModel.prepare()` charge le modèle à l'avance, là où ira une requête courte : le planning
  l'appelle pendant qu'il lit les recettes sur Mealie.

### Le TPU Tensor

- LiteRT-LM passe par une **bibliothèque de dispatch** Google Tensor, qui remet le modèle compilé au
  pilote TPU du téléphone (`/vendor/lib64/libedgetpu_litert.so`, déclaré public par le fabricant,
  `<uses-native-library>` dans le manifeste). Google la publie précompilée avec chaque release de
  LiteRT (`litert_npu_runtime_libraries.zip`, Apache 2.0, construite depuis
  `litert/vendors/google_tensor/dispatch`) ; elle est téléchargée à la compilation, à une release
  épinglée, vérifiée par SHA-256 (`FetchTensorDispatch`, `app/build.gradle.kts`), jamais commitée.
  Box fait de même (en la commitant dans son dépôt) ; umai reprend le mécanisme, pas Box.
- **Box n'a pas de solution plus récente** (vérifié le 26/09/2026 sur ses branches `main`,
  `custom-rom-support` et `experimental`) : il est épinglé sur LiteRT-LM **0.10.0**, plus ancien que
  le 0.14.0 d'umai, avec un dispatch commité. Le code publié s'arrête à sa v1.0.12 (avril 2026) ; les
  versions suivantes, dont l'APK « custom-rom-support » pour GrapheneOS, ne publient que leur README.
  Cet APK retire surtout ce qui dépend des services Google (Gemini Nano via AICore, synthèse vocale
  Google) : umai n'en a jamais dépendu.
- **Le dispatch et le runtime doivent venir du même source LiteRT** : l'API qu'ils partagent change
  sans numéro de version. Essais sur le Pixel 10 Pro XL :

  | LiteRT-LM (LiteRT embarqué) | Dispatch | Résultat |
  |---|---|---|
  | 0.17.1 (`9fe5be4`, 27/08) | v2.2.0 | SIGSEGV dans le dispatch (options Google Tensor) |
  | 0.16.1 (`0ff2811`, 03/08) | v2.1.6 | « Unsupported dispatch runtime version » |
  | 0.15.0 (`3cb830a`, 28/07) | v2.1.6 | SIGSEGV (pointeur de fonction nul) |
  | **0.14.0** (`622f1f3`, 29/06) | **v2.1.6** (`1461b6b`, un commit plus tôt) | **TPU utilisé** |

  Les autres versions n'offrent pas de meilleure paire : 0.16.0 et 0.17.0 embarquent le même LiteRT
  que 0.16.1 et 0.17.1. La plus proche du dispatch v2.2.0 (`145c752`, 06/08) est 0.16.x (`0ff2811`,
  03/08), mais les 40 commits qui les séparent ajoutent un `LiteRtAbiHeader` en tête de
  `LiteRtDispatchApi` (48 → 56 octets) : les deux ne lisent pas la même structure.

  D'où l'épinglage de LiteRT-LM à **0.14.0** (commentaire dans `gradle/libs.versions.toml`,
  avertissement lint ciblé dans `app/lint.xml`). Cette version n'a pas encore `ResponseFormat` : d'où
  le schéma porté par un appel d'outil (plus haut), et une réponse livrée **d'un bloc** une fois
  écrite (l'app ne peut plus compter les jetons pendant l'écriture ; la progression affiche « lecture
  et rédaction »). Monter de version exige un dispatch publié du même source : comparer le
  `LITERT_REF` du `WORKSPACE` de LiteRT-LM au commit de la release LiteRT, puis passer le test
  matériel.
- Un dispatch incompatible plante **en code natif**, hors de portée d'un `try`. `TpuCrashGuard`
  pose un marqueur avant le chargement TPU et le retire après : trouvé au démarrage suivant, il écarte
  le TPU pour cette version de l'app sur cette version du système, et le modèle tourne sur le GPU ou
  le CPU.
- Puces prises en charge : **Tensor G5 et G6** (`TensorChip`, d'après `Build.SOC_MODEL`), les seules
  pour lesquelles Google publie des modèles compilés pour le TPU. Sur les Tensor G1 à G4 : CPU puis
  GPU.
- Fonctionne sous **GrapheneOS**, sans services Google Play ni AICore : vérifié sur un Pixel 10 Pro XL
  (le service `com.google.edgetpu.tachyon` et le pilote `/dev/edgetpu` démarrent dans le journal).

### Mémoire et contexte

Android 17 plafonne la mémoire anonyme + swap d'une app (**4 Gio** sur le Pixel 10 Pro XL,
`MemoryLimiter`) et la tue au-delà. Mesures (`LiteRtLmBackendTest`, mémoire anonyme après une
réponse courte) :

| Backend | Contexte | Mémoire anonyme | Lecture | Écriture |
|---|---|---|---|---|
| TPU (fichier Tensor G5) | 4 096 (fixé à la compilation) | 150 Mo | 120–280 jetons/s | **13,7 jetons/s** |
| GPU (PowerVR, OpenCL) | 16 384 | 570 Mo | 64–77 jetons/s | 3,6–4,1 jetons/s |
| CPU (XNNPACK, 6 fils) | 16 384 | 7,6 Go + 4,4 Go de swap : **tuée** | 9 jetons/s | 2,0 jetons/s |
| CPU | 8 192 | 3,5 Go, plafond atteint | 15 jetons/s | 9,4 jetons/s |
| **CPU** | **4 096** | **1,7 Go** | 21 jetons/s | **16,1 jetons/s** |

D'où un contexte de **16 384** sur GPU et **4 096** sur CPU (`ModelFile.contextSizeOn`). Le premier
chargement GPU prend près d'une minute (le GPU PowerVR fait préparer ses poids par le CPU) ; les
suivants profitent du cache (`cacheDir/litertlm`).

## Modèles

Publiés par la communauté LiteRT sur Hugging Face (`litert-community`), sous licence Apache 2.0 :

| Modèle | Fichiers téléchargés | Taille |
|---|---|---|
| **Gemma 4 E2B** (recommandé) | universel (GPU, CPU) + version Tensor G5 ou G6 (TPU) selon la puce | 2,6 Go, 5,7 Go avec le TPU G5 |
| Gemma 4 E4B | universel (GPU, CPU) | 3,7 Go |

Le « modèle spécial Pixel 10 » de Box est ce même **Gemma 4 E2B compilé pour le Tensor G5**, déjà
téléchargé par umai sur un Pixel 10. Les autres versions Tensor publiées ne conviennent pas :
Gemma 3 1B « Tensor G5/G6 » (`litert-community/Gemma3-1B-IT`) exige un compte Hugging Face et
l'acceptation de la licence Gemma, et n'a que 1 280 jetons de contexte, moins que le seul prompt
d'import ; `Gemma3-1B-IT-Tensor-NPU` n'existe que pour les Tensor G3 et G4 (Pixel 8 et 9).

Sur un Tensor G5 ou G6, les deux fichiers de Gemma 4 E2B sont gardés : le fichier TPU ne tourne que
sur le TPU, avec 4 096 jetons de contexte ; le fichier universel sert au GPU (prompts longs : la
transcription d'une vidéo) et au CPU (dernier recours). Les anciens modèles GGUF de llama.cpp sont
supprimés au démarrage.

## Téléchargement

- Par le gestionnaire de téléchargement du système (reprise après coupure, notification),
  **en Wi-Fi uniquement**, dans le stockage propre de l'application (supprimé avec elle).
- Chaque fichier est vérifié avant usage : en-tête `LITERTLM`, et SHA-256 pour les modèles du
  catalogue. Un seul modèle est gardé : le précédent est supprimé une fois le nouveau vérifié.

## Faire évoluer

- Nouveau modèle sans mise à jour : *Un autre modèle*, adresse `https://…/fichier.litertlm`
  (GPU et CPU seulement : une version TPU ne tourne que sur la puce pour laquelle elle est compilée).
- Nouveau modèle au catalogue : `llm/domain/LocalModels.kt` (adresse, taille, SHA-256 donnés par
  l'API Hugging Face `https://huggingface.co/api/models/<dépôt>/tree/main`).
- Le fichier universel contient aussi la partie **vision** du modèle, chargée à la demande
  (`AiSense`), sur le GPU (deux fois plus rapide qu'au CPU). Un fichier TPU n'en a pas. Une partie
  qui ne se charge pas n'écarte pas le backend pour le texte. Sa partie **audio** n'est plus
  utilisée : la parole est écrite par Whisper (plus bas), plus juste et sans phrases inventées.
- Vérifier le TPU sur un téléphone : mettre les fichiers dans
  `/sdcard/Android/data/org.opensources.umai.debug/files/models/` (téléchargés par l'app, ou
  `adb push`), puis `connectedDebugAndroidTest` (classe `LiteRtLmBackendTest`, ignorée sans eux).

## YouTube

L'extraction passe par **NewPipeExtractor** (GPL-3.0-or-later, version dans
`gradle/libs.versions.toml`, publiée sur JitPack), branché sur le client OkHttp « sites externes »
de l'application (`youtube/data/OkHttpDownloader.kt`) : pas de second client HTTP, jamais
d'identifiants Mealie vers YouTube.

- `YouTubeClient.video` : titre, description (HTML converti en texte, liens en entier), durée,
  miniature la plus grande, **chapitres de l'auteur** (segments), sous-titres horodatés (TTML) ;
- sous-titres : piste écrite par une personne dans la langue parlée, sinon piste automatique. La
  langue parlée est celle de la piste audio d'origine : sur une vidéo doublée automatiquement,
  YouTube propose aussi une piste automatique dans la langue du doublage ;
- `YouTubeClient.stream` (mode cuisine) : flux HLS si YouTube le propose, sinon le meilleur MP4
  progressif image + son ; ExoPlayer les lit sans en-tête particulier. Les adresses expirent :
  elles sont demandées à chaque ouverture du mode cuisine ;
- la langue de l'application est imposée à l'extracteur (`forceLocalization`) : la préférence
  globale de NewPipe n'atteint pas toutes ses requêtes, et YouTube renvoyait alors la description
  traduite dans une autre langue.

Limites constatées :

- NewPipeExtractor n'expose que les chapitres **posés par l'auteur**, pas les chapitres générés
  automatiquement par YouTube (`engagement-panel-macro-markers-auto-chapters`) : sans chapitres
  d'auteur, les étapes sont placées par le modèle ou par alignement sur la transcription ;
- quand YouTube casse l'extraction (« YouTube a répondu d'une façon que l'application ne sait pas
  lire »), la correction est une montée de version de NewPipeExtractor, publiée en général en
  quelques jours ;
- YouTube **limite par adresse IP** la lecture des sous-titres (`/api/timedtext`) : au-delà, il
  répond 429 pendant des heures, à tous les clients (web, iOS), alors que la page de la vidéo et ses
  flux restent lisibles. L'app ne le masque plus (`transcriptRefused`) : la vidéo est alors écoutée,
  et l'avis d'import le dit si les étapes n'ont pas pu être placées.

### Vidéos sans sous-titres

Idée reprise de [yt-transcript](https://github.com/plc/yt-transcript) (Whisper quand il n'y a pas
de sous-titres) et de [pick-a-recipe](https://github.com/pickeld/pick-a-recipe) (Whisper et texte à
l'écran lu par un modèle vision), mais **sur le téléphone** (`VideoWatcher`, `AndroidVideoMedia`) :

1. **Écouter**, avec **Whisper** (voir *Transcription : Whisper*) — quand la vidéo n'a pas de
   sous-titres, ou que YouTube les refuse : la piste audio **d'origine** (pas celle que YouTube double
   automatiquement dans une autre langue) est décodée (`MediaExtractor` + `MediaCodec`), ramenée à
   16 kHz mono en moyennant les échantillons, et donnée à Whisper par morceaux de **2 minutes**.
   Chaque phrase entendue devient une ligne de la transcription, avec ses propres temps. Au-delà de
   20 minutes, la suite n'est pas écoutée. Sans modèle Whisper installé, la vidéo n'est pas écoutée.
2. **Regarder** — en dernier recours : quand rien n'est dit (ni sous-titres, ni parole entendue),
   qu'il n'y a pas de chapitres, et que ni la description ni la page de recette ne listent les
   ingrédients (la page est donc lue avant l'écoute) : une image au milieu de chaque tranche de **20 s** (30 images au plus, plus espacées sur
   une longue vidéo), lue dans le fichier MP4 à l'endroit voulu (`MediaMetadataRetriever`), décrite en
   une phrase. Ces descriptions ne servent **pas** à écrire les étapes à leur place : le modèle écrit
   la recette en voyant ce que montrent les images, **sans leurs temps** (avec la frise horodatée, il
   écrivait une étape par image, fautes comprises : « versez du jus d'orange »), puis un second appel
   court place chaque étape sur la frise (`PicturePlacement`). Si la réponse ne donne pas un temps
   par étape, l'alignement par mots prend le relais.

Sans aucun repère (ni chapitres, ni parole, ni images lisibles), les débuts que le schéma oblige le
modèle à écrire sont des devinettes (il écrit `0` partout) : ils sont écartés, et la recette n'a pas
de chapitres plutôt que des chapitres faux. Les étapes se placent alors à la main, dans *Modifier ›
Vidéo*. De même, l'alignement par mots ne place plus une étape qui ne partage aucun mot avec le
passage où elle tomberait.

Mesures sur le Pixel 10 Pro XL, vidéo de 4 min 45 (« Petits pains farcis à la poêle », Deli Cuisine,
sous-titres refusés par YouTube) :

| Étape | Où | Durée |
|---|---|---|
| Écoute par Whisper Small (voir plus bas) | CPU, 2 fils | ≈ 3 min 15 |
| *Écoute par Gemma, avant Whisper* | *CPU, 10 tranches de 30 s* | *3 min 35 à 3 min 46* |
| Recette depuis la transcription entendue | TPU (1 731 jetons lus, 495 écrits) | 47 s |
| Regard, 14 images (test sans le son) | CPU, vision au GPU (≈ 410 jetons lus par image) | 3 min 47 |
| Recette depuis les images, puis placement | TPU | 42 s |

Avec la transcription entendue, les 7 étapes tombent là où elles sont dites (pâte 0:00, repos 1:00,
garniture 1:30, pâtons 2:00, étalage 2:30, farce 3:00, cuisson 4:00), et les portions (8) et les
temps sont lus. Avec les images seules, 5 étapes cohérentes sont placées à 0:40, 1:20, 2:40, 3:00 et
4:00 : moins précis, à corriger au besoin dans l'éditeur.

### Transcription : Whisper

La parole est écrite par **whisper.cpp** (MIT), un modèle spécialisé bien plus petit que Gemma :
Gemma 4 E2B, généraliste, inventait des phrases et reformulait ; Whisper écrit ce qui est dit, avec
les temps de chaque phrase. Il tourne sur le **CPU** (`speech/data/WhisperTranscriber.kt`).

- **Build** : les sources de whisper.cpp sont téléchargées à la compilation, à une release épinglée,
  vérifiées par SHA-256 (`fetchWhisperSource`, `app/build.gradle.kts`), jamais commitées ; seuls
  `CMakeLists.txt`, `cmake/`, `ggml/`, `include/` et `src/` sont extraits (les *bindings* et les
  exemples contiennent des projets Gradle que l'extension Kotlin de l'IDE ouvre et verrouille).
  `app/src/main/cpp/CMakeLists.txt` les compile avec le pont JNI (`whisper_jni.cpp`), **optimisés
  même en debug** : en `-O0`, 30 s de parole prenaient 2 min 40.
- **Réglages mesurés** (Pixel 10 Pro XL, une minute de la vidéo ci-dessus, Small) :

  | Réglage | Durée |
  |---|---|
  | 6 fils | 71 à 104 s |
  | 4 fils | 62 à 68 s |
  | **2 fils** | **43 s** |

  Les fils de ggml s'attendent à chaque étape : plus il y en a, plus certains tombent sur des cœurs
  lents. `flash_attn` est coupé (un jeton écrit environ un cinquième plus vite au CPU), et une
  tranche à redécoder à plus haute température ne l'est qu'une fois (`best_of = 1`, au lieu de 5).
  Des morceaux de 2 minutes plutôt que de 30 s : Whisper finit chaque morceau par une fenêtre pour
  la dernière phrase, qui doublait le travail sur des morceaux de 30 s.
- **Modèles** (Hugging Face `ggerganov/whisper.cpp`, MIT, quantifiés), un seul gardé à la fois,
  choisi dans *Profil › IA locale › Transcription de la parole* :

  | Modèle | Taille | 2 min de parole (Pixel 10 Pro XL) | Français |
  |---|---|---|---|
  | Base (`q5_1`) | 60 Mo | 24 s | quelques mots faux (« petits peintes farsissons ») |
  | **Small** (`q5_1`, recommandé) | 190 Mo | 81 s | presque sans faute (« 250 millilitres de lait », « 400 g au total ») |
  | Large v3 Turbo (`q5_0`) | 574 Mo | 356 s | le plus juste, ponctuation comprise |

  Small est recommandé à partir de 6 Go de mémoire (`SpeechModelCatalog.recommendedFor`), Base
  en dessous ; chacun peut prendre une autre taille. Sur un Pixel 6 Pro, voir *Téléphones sans TPU*
  plus bas : Small y reste le bon choix, Large v3 Turbo y est trop lent.
- Sur un son sans parole (musique, bruit), Whisper peut inventer une courte phrase (« Sous-titrage
  ST' 501 » avec Turbo sur un son pur). Les segments que Whisper lui-même juge sans parole
  (`no_speech_prob` > 0,6) sont écartés, et les étiquettes comme « [Musique] » retirées.
- Vérifier sur un téléphone : mettre les modèles dans
  `/sdcard/Android/data/org.opensources.umai.debug/files/models/`, puis `WhisperTranscriberTest`
  (temps de chaque taille dans `adb logcat -s UmaiAiTest`). Pendant un import, chaque morceau
  entendu est journalisé (`Whisper: 120,0 s heard in … ms`, `adb logcat -s UmaiAi`).

### Ingrédients

Ordre de priorité des sources, du plus sûr au moins sûr :

1. **la page de recette** vers laquelle pointe la description. Les liens sont repérés par le texte
   qui les entoure, en français et en anglais (« Quantités de la recette : », « Full recipe: »,
   « la recette illustrée … en suivant ce lien »), sans liste de domaines (`DescriptionLinks`) ;
   « matériel », « livre », « boutique » ou un lien YouTube écartent une ligne. Le lien est
   ouvert pour suivre les redirections des raccourcisseurs, puis la page est lue par Mealie
   (`POST /api/recipes/test-scrape-url`, qui renvoie le schema.org `Recipe` trouvé sans rien
   créer ; `create/url` aurait créé une recette à supprimer). Si ce `Recipe` n'a pas
   d'ingrédients — cas de philippe-etchebest.com — la liste placée sous le titre « Ingrédients »
   de la page est lue (`RecipePageParsing.fromHtml`). La liste de la page est complète : les
   autres sources ne lui ajoutent pas d'aliment ;
2. **la liste écrite dans la description** ;
3. **ce que le modèle a lu dans la vidéo**, vérifié (`IngredientEvidence`) : un aliment jamais nommé
   dans le titre, la description, la page ou la transcription est écarté ; une quantité n'est
   gardée que si le même nombre (et la même unité, « g » valant « grammes ») est dit ou écrit à
   quelques mots de l'aliment, et si elle est plausible. Sinon la ligne reste, sans quantité.

Puis la fusion déterministe (`IngredientMerge`, clés de `IngredientKeys`) : une ligne par aliment ;
la quantité de la source la plus sûre l'emporte ; une ligne sans quantité ne reçoit que celle
qu'une autre source écrit. Un aliment écrit deux fois par un auteur (l'ail de la salade et celui de
la sauce) additionne ses quantités de même unité ; répété par le modèle, il n'est gardé qu'une fois.
Quand la page ou la description donne la liste, le modèle est prié de ne pas la réécrire (réponse
plus courte, donc import plus rapide) et d'en reprendre les noms dans les étapes. La vidéo reste la
source des étapes, de leurs passages, et l'URL d'origine de la recette.

## Planning automatique

Le modèle ne sert qu'à classer (plat, dessert, boisson, autre) les recettes que rien ne situe ; le
choix des plats reste un algorithme (`MealPlanner`). Les réponses sont gardées sur le téléphone
(`DishCourseStore`) : une recette n'est demandée qu'une fois.

- **Une propriété obligatoire par recette**, typée (`{"r1": "main", "r2": "dessert", …}`, chaque
  valeur `{"type": "string", "enum": [...]}`), et non une liste d'objets `{"id", "course"}` : le
  décodage contraint respecte les propriétés obligatoires, donc le modèle répond pour chaque recette,
  et écrit moins. Mesuré sur le Pixel 6 Pro, CPU, 25 recettes françaises (`PlanningPromptSample`) :

  | Réponse | Jetons lus | Jetons écrits | Durée | Résultat |
  |---|---|---|---|---|
  | liste d'objets (avant) | 805 | 339 | 53,9 s | une recette oubliée (pad thaï) ; curry, chili, risotto et quiche classés « autre » |
  | **une propriété typée par recette** | 1 586 | **202** | **50 s** | les 25 répondues, toutes justes |

  Le **type** est indispensable : LiteRT-LM 0.14 ne tient une valeur à son `enum` que si son type est
  donné. Sans lui, sur un lot de 3 ou 7 recettes, le modèle recopiait la ligne de chaque recette comme
  valeur, et sur une seule, répondait en texte au lieu d'appeler l'outil (constaté en vrai : le second
  lot d'un planning, 243 jetons perdus). Les formes plus courtes ne marchent pas : un seul
  `additionalProperties` typé fait échouer la création de la contrainte, un `$ref` vers une définition
  commune n'est pas tenu. Chaque `"type"` coûte une vingtaine de jetons lus (le modèle de conversation
  le réécrit en long) : `LocalLanguageModel.estimatedTokens` les compte à part (`TYPE_TOKENS`).
  Les anciennes réponses gardées sont redemandées une fois (`model_courses_v3`).
- Un lot de 25 recettes est estimé à 1 877 jetons (1 788 comptés par le runtime) : il tient dans les
  4 096 du TPU et du CPU, et va donc au **TPU** sur un Tensor G5 ou G6, au CPU ailleurs. Test
  matériel, sur un lot plein et sur un lot de 3 : `thePlanningPromptAnswersEveryRecipeOnTheFirstBackend`
  (`LiteRtLmBackendTest`).
- Le modèle se **charge pendant la lecture des plats** sur Mealie (`DishPoolRepository`), dès que
  l'échantillon compte une recette que rien ne situe : le chargement ne s'ajoute plus à l'attente.

## Étiquettes nutritionnelles

Un produit ajouté au planning (un snack, une boisson) peut avoir son tableau nutritionnel
photographié : le modèle le lit avec sa partie vision, sur le CPU puis le GPU (une version TPU n'a
pas de vision). La photo est lue puis supprimée (`DeviceLabelPictures`), rien ne quitte le téléphone.
Un OCR classique n'a pas été retenu : les étiquettes sont photographiées de biais, imprimées sur des
couleurs, souvent en plusieurs langues, et il faudrait en plus reconnaître les lignes et les colonnes.

- **Le modèle recopie, l'app interprète.** Premier essai : un schéma avec une clé par nutriment
  (`kcal`, `fat`…). Sur la canette de cola, le modèle glissait les valeurs d'une ligne à l'autre
  (protéines 10,6 g au lieu de 0, fibres inventées, portion 100 au lieu de 330). Le schéma retenu lui
  fait **recopier le tableau** tel qu'imprimé : les en-têtes de colonnes, puis chaque ligne avec son
  nom et ses valeurs. `LabelTable` en déduit la colonne pour 100 g ou 100 ml, l'unité, la portion
  (l'autre colonne, en g, ml ou cl) et chaque nutriment par des mots-clés en français, anglais,
  néerlandais et allemand (« saturés » avant « matières grasses », « sucres » avant « glucides »).
  Une énergie en kJ seulement est convertie, une valeur non imprimée reste inconnue.
- Mesuré sur le **Pixel 6 Pro**, CPU, Gemma 4 E2B, photos de `app/src/sharedTest/pictures/` (`NutritionLabelDeviceTest`,
  livrées avec l'APK de test) : environ **45 s** par étiquette, chargement compris,
  et toutes les valeurs justes, sur la canette (deux colonnes, énergie sur deux lignes) comme sur le
  sandwich (une colonne, bilingue français-néerlandais, texte clair sur fond orange).
- Le formulaire sert aussi sans modèle, ou quand la lecture échoue : les valeurs se saisissent, et la
  raison de l'échec est dite (pas de modèle, modèle sans vision, image illisible, aucun tableau).

## Aliments écrits

Au planning, un snack ou une boisson s'écrit comme on le dit : « 2 pommes, 1 café sans sucre »,
« 200 g de riz », « un verre de lait ». Tout passe d'abord **sans le modèle** :

- `FoodPhrases` découpe la phrase (virgule, « et », « + », retour à la ligne, pas la virgule d'un
  décimal) et lit la quantité de chaque aliment : un nombre de portions (chiffres, fractions, « deux »,
  « une demi »), un poids ou un volume (`g`, `kg`, `cl`, `l`…), ou une mesure de cuisine (verre
  200 ml, tasse 200 ml, bol 300 ml, cuillère à soupe 15, à café 5, canette 330, bouteille 500).
- `FoodTable` cherche l'aliment dans la **table Ciqual** de l'Anses (version 2025, Licence Ouverte
  Etalab 2.0), embarquée dans `assets/ciqual.tsv` : les 8 nutriments d'une étiquette pour 100 g, en
  français et en anglais (les deux langues sont cherchées). Le fichier se régénère avec
  `scripts/ciqual-table.py` depuis le XML publié sur ciqual.anses.fr ; une valeur « traces » ou
  « < x » y vaut 0, une valeur inconnue reste vide. Une boisson est comptée pour 100 ml (1 g ≈ 1 ml).
- `assets/basic_foods.tsv`, écrit à la main, donne aux aliments courants leur **nom usuel**
  (« pomme » plutôt que « Pomme, chair et peau, crue ») et leur **portion** (pomme 150 g, café
  150 ml, œuf 50 g…) : c'est elle qui fait de « 2 pommes » 300 g. Un aliment sans portion connue
  demande sa quantité à l'étape *Portion*. Pour en ajouter un, une ligne suffit (code Ciqual, portion,
  unité, noms français et anglais séparés par `|`).
- La recherche compare les mots sans casse, accents, pluriel ni petits mots (`FoodWords`) : tous les
  mots tapés doivent être dans le nom, le dernier peut être tronqué pendant la frappe, mais un mot
  entier passe avant (« lait » donne le lait avant la laitue).

Quand la table n'a pas **tous** les aliments de la phrase, et seulement alors, le modèle est
interrogé (`ModelFoodEstimator`) : texte seul, réponse courte (une liste `{name, grams, kcal}`),
température 0. Il **décompose** le plat en aliments pesés ; chacun est recherché dans la table, dont
les valeurs l'emportent sur les calories devinées par le modèle, qui ne servent que pour un aliment
absent de la table. L'écran dit que c'est une estimation, ligne par ligne, et la note Mealie porte la
mention « Estimation de l'IA locale ». Sans modèle, l'aliment se décrit à la main.

## Téléphones sans TPU : mesures sur un Pixel 6 Pro

Pixel 6 Pro (Tensor G1, 12 Go, GPU Mali-G78), Android 17, Gemma 4 E2B, 26/09/2026 :

| Mesure | CPU (4 096) | GPU (Mali-G78) |
|---|---|---|
| Chargement du modèle | **1,7 s** | **58 à 69 s**, cache compris ; Android ferme 5 à 16 autres applications pour faire de la place |
| Prompt d'import d'une vidéo (1 403 jetons lus, ~550 écrits) | **84 s** (lecture 53,5 jetons/s, écriture 10,5 jetons/s) | 87 s (lecture 259 jetons/s, écriture 6,9 jetons/s) |
| Planning, 25 recettes (réponse sans type, voir plus haut) | **41,8 s** | 35,5 s |
| Mémoire anonyme après une réponse | 1,7 Go | 0,5 Go (+ 3 Go de fichier mappé) |
| Une image décrite (768 px, 411 jetons lus, vision au GPU) | 20 s (30 s pour la première) | — |

Chargement compris, le CPU fait un import en ~86 s et un planning en ~44 s, contre ~150 s et ~94 s
au GPU : d'où l'ordre TPU → CPU → GPU. Le GPU reste utile pour un prompt de plus de 4 096 jetons,
qu'il lit cinq fois plus vite. Regarder une vidéo sans parole prend donc jusqu'à 10 minutes (30 images)
sur ce téléphone.

**Gemma 4 E4B** n'y est pas utilisable : au CPU, le prompt d'import n'avait pas de réponse au bout de
27 minutes, le swap plein (8 ko libres) et le processus à 2 cœurs sur 8, faute de mémoire. L'écran le
signale comme « gros pour ce téléphone » dès que son fichier dépasse 30 % de la mémoire
(`LocalAiUiState.isTight` : E2B fait 22 % des 12 Go, E4B 31 %), et sa description le dit.

Whisper, 109 s de parole française (synthèse vocale, `speech_fr.pcm`), 2 fils :

| Modèle | Durée | Français |
|---|---|---|
| Base | 34 s (4 fils : 46 s) | fautes (« petits pinfarci », « le vure-sèche ») |
| **Small** | **117 s** (4 fils : 130 s) | presque sans faute |
| Large v3 Turbo | **608 s** | juste, mais une phrase inventée (« Laisser cuillère à soupe de paprika ») |

Deux fils restent les plus rapides sur le Tensor G1 (ses deux grands cœurs). Small y écoute à peu
près en temps réel : une vidéo de 10 minutes s'écoute en 10 minutes environ. Turbo, 5,6 fois plus
lent que la vidéo, n'est pas à conseiller sur un tel téléphone : sa description le dit.
