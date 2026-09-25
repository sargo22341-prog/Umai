# IA locale

umai peut faire tourner un modèle de langage **sur le téléphone**, sans service distant. Le
modèle se télécharge à part, depuis *Profil › IA locale*, et se remplace sans mettre à jour
l'application. Toutes les fonctions qui s'en servent marchent aussi sans lui, avec un algorithme
classique.

## Où le modèle sert, et où il ne sert pas

| Fonction | Avec le modèle | Sans le modèle |
|---|---|---|
| Import d'une vidéo YouTube | Lit titre, description, chapitres et transcription horodatée ; écrit des étapes rédigées et titrées, le début de chaque étape dans la vidéo, et les ingrédients quand ni la page de recette ni la description ne les donnent. | Ingrédients de la page de recette ou de la description, étapes lues dans la description, sinon étapes = chapitres (texte tiré de la transcription), placées par les chapitres ou par alignement mots-transcription. |
| Planning automatique | Classe les recettes que rien ne situe (ni catégorie, ni tag, ni nom, ni historique). | Ces recettes sont jugées sur leurs ingrédients (sucré seul = dessert). |

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

## Runtime : LiteRT-LM, sur le TPU, le GPU ou le CPU

- **LiteRT-LM** (Google, Apache 2.0), dépendance Maven `com.google.ai.edge.litertlm:litertlm-android`,
  sans aucun service Google Play. Il est isolé derrière l'interface `AiEngine`
  (`llm/domain/AiEngine.kt`) : seul `llm/data/LiteRtLmEngine.kt` l'importe.
- **Ordre des backends : TPU → GPU → CPU** (`LocalLanguageModel`). Un backend n'est retenu qu'une
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
- Journal (`adb logcat -s UmaiAi`) : `Backend: TPU | Model: Gemma 4 E2B | SoC: Tensor G5` au
  chargement, puis après chaque réponse les vitesses mesurées par le runtime
  (`prompt 130 tokens at 136,6/s | answer 50 tokens at 13,9/s`).
- Modèle chargé en `mmap`, libéré une minute après la dernière utilisation. Un service de premier
  plan tient l'app en vie pendant une génération.

### Le TPU Tensor

- LiteRT-LM passe par une **bibliothèque de dispatch** Google Tensor, qui remet le modèle compilé au
  pilote TPU du téléphone (`/vendor/lib64/libedgetpu_litert.so`, déclaré public par le fabricant,
  `<uses-native-library>` dans le manifeste). Google la publie précompilée avec chaque release de
  LiteRT (`litert_npu_runtime_libraries.zip`, Apache 2.0, construite depuis
  `litert/vendors/google_tensor/dispatch`) ; elle est téléchargée à la compilation, à une release
  épinglée, vérifiée par SHA-256 (`FetchTensorDispatch`, `app/build.gradle.kts`), jamais commitée.
  Box fait de même (en la commitant dans son dépôt) ; umai reprend le mécanisme, pas Box.
- **Le dispatch et le runtime doivent venir du même source LiteRT** : l'API qu'ils partagent change
  sans numéro de version. Essais sur le Pixel 10 Pro XL :

  | LiteRT-LM (LiteRT embarqué) | Dispatch | Résultat |
  |---|---|---|
  | 0.17.1 (`9fe5be4`, 27/08) | v2.2.0 | SIGSEGV dans le dispatch (options Google Tensor) |
  | 0.16.1 (`0ff2811`, 03/08) | v2.1.6 | « Unsupported dispatch runtime version » |
  | 0.15.0 (`3cb830a`, 28/07) | v2.1.6 | SIGSEGV (pointeur de fonction nul) |
  | **0.14.0** (`622f1f3`, 29/06) | **v2.1.6** (`1461b6b`, un commit plus tôt) | **TPU utilisé** |

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
  pour lesquelles Google publie des modèles compilés pour le TPU. Sur les Tensor G1 à G4 : GPU puis
  CPU.
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
  quelques jours.

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
