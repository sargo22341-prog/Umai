# Migration LiteRT-LM sur Tensor G5 — 14 octobre 2026

LiteRT-LM **0.18.0** est retenu après une génération réelle sur le NPU d'un **Pixel 10 Pro XL,
Tensor G5**, GrapheneOS Android 17, build système `2026100601`. Le modèle existant
`gemma-4-E2B-it_Google_Tensor_G5.litertlm` est conservé. Aucun firmware, service ni protection
GrapheneOS n'a été changé ; aucune donnée Mealie n'a été modifiée.

Le travail reste sur `research/litertlm-tensor-g5-upgrade`, sans commit. `main` demeure au commit
`6e298573b4abdf5f5c63c038bd0f71de5860b5c6`, avec la configuration fonctionnelle **0.14.0/v2.1.6**.
L'application release du téléphone et sa session sont conservées. Les installations portent sur
debug et son APK de tests, par remplacement sans désinstallation.

La référence utilise l'archive officielle
[`v2.1.6/litert_npu_runtime_libraries.zip`](https://github.com/google-ai-edge/LiteRT/releases/download/v2.1.6/litert_npu_runtime_libraries.zip),
SHA-256 `98aabbdce8607f6dc6ab7cb92217326eef24a8c97b973b69e62bd0ce14b7495b`.

## Versions exécutées

Les commits sont lus dans le `WORKSPACE` de chaque tag officiel. Les dispatch ont effectivement
été compilés pour Android arm64, puis emballés dans les APK d'essai. Une compilation Kotlin
réussie n'est jamais utilisée comme preuve de compatibilité NPU.

| LiteRT-LM testé | Commit LiteRT | Dispatch et correctif | Résultat matériel | Lecture / génération | Temps du test court |
|---|---|---|---|---|---|
| 0.14.0, référence | `622f1f3c1352f4bc2925061b8cb72e9ce52874fe` | Dispatch publié v2.1.6, référence existante | NPU, réponse correcte | 131,9 / 13,9 jetons/s | 13,079 s |
| 0.15.0 | `3cb830ad9c94f9922f0a88dd431b005413628919` | Reconstruit depuis ce commit, sans patch natif | NPU, réponse correcte | 399,2 / 13,9 jetons/s | 12,178 s |
| 0.16.1 | `0ff28117f1cb5556d0e015bf80b773f74e2bee51` | Reconstruit depuis ce commit | Échec : `Unsupported dispatch runtime version`, puis échec du warmup | Aucun jeton | 0,795 s, échec |
| 0.17.1 | `9fe5be45564c868408e6514c8aabb83e211a0911` | Reconstruit depuis ce commit, sans patch natif | NPU, réponse correcte | 328,7 / 14,8 jetons/s | 11,588 s |
| **0.18.0** | `26895c9fbcc25c43faa8c1a98cd1fd28951602c3` | Reconstruit ; retrait de la dépendance privée de profilage inutilisée ; GCC hôte explicite | **NPU, réponse correcte** | **588,9 / 14,2 jetons/s** | **11,593 s** |

Le prompt court contient 130 jetons ; chaque réponse en compte 50 et classe correctement les
lasagnes, la tarte aux pommes et le mojito. La mémoire anonyme après génération reste proche
de **147 Mio**, sans swap. Les chiffres sont des échantillons isolés, avec des caches et états
thermiques différents : ils ne démontrent pas à eux seuls un gain de vitesse entre versions.

Les tags **0.16.0** et **0.17.0** ont été inspectés et partagent respectivement les mêmes commits
LiteRT que 0.16.1 et 0.17.1 ; leurs APK n'ont pas été exécutés séparément. Le dispatch public
0.16.1 compilé est identique octet pour octet à celui de 0.15.0, mais le runtime Maven 0.16.1
le rejette. La correspondance des pins publics ne suffit donc pas toujours ; aucune compatibilité
0.16 n'est revendiquée.

## Preuves d'exécution NPU

`theTensorTpuRunsTheModel` charge directement `AiBackend.TPU`, sans passer par la sélection
TPU/CPU/GPU du modèle local. Le test vérifie le pilote avant et après la génération, le backend,
la présence de jetons et le JSON de sortie. Le loader contrôle `/proc/self/maps` pour
`libLiteRtDispatch_GoogleTensor.so` et `libedgetpu_litert.so`.

Les journaux natifs 0.18 confirment également un contexte SouthBound et la délégation **de tous
les nœuds** des trois graphes principaux au DispatchDelegate :

```text
SouthBound context created.
Replacing 1 out of 1 node(s) with delegate (DispatchDelegate) node ... subgraph 0 (decode).
Replacing 1 out of 1 node(s) with delegate (DispatchDelegate) node ... subgraph 1 (prefill_128).
Replacing 1 out of 1 node(s) with delegate (DispatchDelegate) node ... subgraph 2 (verify).
Backend: TPU | Model: Gemma 4 E2B | SoC: Tensor G5
prompt 130 tokens at 588,9/s | answer 50 tokens at 14,2/s
```

Ces preuves associent délégation native, pilote chargé et génération correcte ; elles vont
au-delà du simple succès d'initialisation. Le traitement de texte et la conversion JSON restent
naturellement du travail CPU, sans fallback du graphe LLM vers CPU/GPU.

Les APK, sorties d'instrumentation, logs natifs et logs de compilation des essais sont conservés
localement dans `build/litert-research/results/`, ignoré par Git. L'ELF Build ID reste identique
entre le dispatch compilé et celui extrait de l'APK ; leur SHA-256 peut différer car AGP retire
les symboles de débogage.

## Tests fonctionnels supplémentaires sur 0.18

Trois tests ciblés passent ensemble en **72,738 s** :

| Test | Mesure / résultat |
|---|---|
| Import d'une vidéo, prompt réel | 1 403 jetons lus, 505 écrits, **42,020 s** ; estimation 2 866, contexte TPU 4 096 |
| Planning, 25 recettes | 1 586 jetons lus, 200 écrits, **18,931 s** ; chargement 2,987 s |
| Planning, 3 recettes | 355 jetons lus, 29 écrits, **5,767 s** |
| Échec d'initialisation | La cause JNI originale est conservée dans `AiBackendUnavailable` |

Les 25 recettes reçoivent une réponse conforme. Le lot plein classe `r17` « autre » au lieu de
« dessert » : une erreur de modèle, dans la tolérance de l'oracle existant (au plus une erreur).
Le petit lot répond correctement aux trois recettes. Aucun test n'a été assoupli pour la migration.

La première suite complète confirme aussi le CPU (15,508 s pour le test court), le GPU
(66,630 s, chargement inclus), la vision (22,545 s), et les deux étiquettes nutritionnelles
(28,642 et 25,526 s), sans échec dans ces tests IA.

## Intégration et build

`gradle/libs.versions.toml` épingle 0.18.0. `FetchTensorDispatch` construit le dispatch à partir
du workspace LiteRT-LM au commit **`b2f686e2ed4718fb84ec398a61dd59ca0f0aff27`**. Le manifeste
`scripts/tensor-dispatch/sources.json` vérifie les 47 fichiers nécessaires par SHA-256, sans
télécharger les centaines de Mo de fixtures de modèle de l'archive complète.

Docker Linux, sur hôte x86-64, est un nouveau prérequis de build, **sans effet sur le téléphone**.
La recette épingle l'image par digest, Bazel 7.6.1, le NDK r28c, le SDK et les outils de build par
SHA-256. Les dépendances Bazel gardent leurs contrôles de checksum. APT vérifie ses métadonnées
signées et les checksums des paquets ; ses versions transitives peuvent évoluer. Le build n'est
donc pas revendiqué comme reproductible octet pour octet entre dates.

Le SDK 36 de ce conteneur sert seulement au dispatch natif : le module Android Umai reste en
**SDK 37**, sans changement de minSdk, targetSdk ou compileSdk. Le seul artefact extrait dans
`app/build/tensor-dispatch/arm64-v8a/` est le dispatch, pas les outils ni le pilote constructeur.

Le patch `google-tensor-public-hooks.patch` retire la dépendance à
`litert/vendors/google_tensor/hooks`, dont le BUILD réclame le paquet Google interne absent
`platforms/darwinn/devtools/power_stats`. Les hooks sont déjà conditionnels dans le C++ public ;
la définition qui les active reste absente. Aucune vérification ABI ou sécurité n'est supprimée.

Chaque tentative Bazel est limitée à dix minutes. Seuls les échecs DNS explicites sont retentés,
trois fois au maximum, avec un cache BuildKit pour conserver les téléchargements vérifiés.
Une erreur de compilation, de checksum ou un timeout arrête immédiatement le build.
Les commandes Docker ont elles aussi une borne ; un client interrompu est arrêté.

## Recherche publique

- [WORKSPACE 0.15.0](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.15.0/WORKSPACE),
  [0.16.1](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/WORKSPACE),
  [0.17.1](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/WORKSPACE),
  [0.18.0](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/WORKSPACE) : pins officiels.
- [LiteRT #7787](https://github.com/google-ai-edge/LiteRT/issues/7787) : signalement du problème
  de runtime/dispatch sur Pixel 10 ; ne constitue pas une preuve pour les versions testées ici.
- [Recette Tensor G5 publique](https://github.com/bejoyfuuul/litert-lm-tensor-g5-npu) : construction
  runtime/dispatch depuis un même pin. Elle porte sur 0.14 alpha ; ses mesures ne sont pas les nôtres.
- [Box](https://github.com/jegly/Box/blob/main/README.md) : revendique des corrections sur ROMs
  alternatives mais ne fournit pas, par ce README, de preuve de compatibilité 0.18 pour Umai.
- [LiteRT-LM #3878](https://github.com/google-ai-edge/LiteRT-LM/issues/3878) : surcoût mémoire
  signalé dans `ModelInfo.from()` de 0.18 ; Umai n'appelle pas cette API dans ce changement.

## Incidents et limites

- ADB était vide au départ ; le téléphone est devenu visible après reconnexion par l'utilisateur.
- Les restrictions initiales sur Git, les caches Gradle et les téléchargements ont été résolues
  par exécution autorisée hors bac à sable. Aucune approbation automatique n'a rejeté ces actions.
- PowerShell a échoué au téléchargement TLS/proxy initial ; Python et Docker ont permis de lire
  les sources sans désactiver TLS.
- Bazel Windows a échoué à créer un symlink ; Docker Linux a remplacé cette compilation.
- La première préparation Linux a commencé avant la fin de la copie du SDK. Le paquet build-tools
  Linux officiel et vérifié a remplacé la copie du SDK Windows pour les essais.
- Une archive complète 0.18 a dépassé le délai et est restée partielle ; son hash n'est pas utilisé.
  Le build intégré télécharge seulement les sources nécessaires, chacune vérifiée.
- Un modèle copié dans le stockage externe était refusé sous l'identité d'instrumentation. Il a
  été copié en stockage interne debug avec `run-as`, sans modifier les permissions système.
- Cet échec a révélé `Engine.close()` sur un moteur non initialisé, qui masquait l'erreur JNI.
  Le loader et un test de régression corrigent cette cause.
- Une première compilation du test a signalé un résultat `load()` inutilisé ; le résultat est
  désormais fermé si un moteur était créé de façon inattendue. Le DSL Gradle n'accepte pas
  `val _` ; la terminaison forcée utilise et vérifie son résultat avec un délai.
- Des échecs DNS temporaires ont touché Abseil et Rust ; les reprises restent explicites et bornées.
- La première recette minimale omettait le paquet `cxxbridge_cmd` du workspace ; ses trois fichiers
  ont été ajoutés au manifeste vérifié. Le build intégré a ensuite réussi depuis cette recette.
- La première suite complète a trouvé un échec dans le test UI du sélecteur Whisper : le titre
  Turbo était visible mais le bouton de téléchargement restait hors écran. Le test fait désormais
  défiler jusqu'au bouton et vérifie sa visibilité avant de cliquer. L'attente du modèle Turbo est
  inchangée ; le test ciblé passe en 2,850 s. L'UI de production n'a pas été modifiée pour ce cas.
- La deuxième passe complète a révélé une autre instabilité du harnais UI : le formulaire alimentaire
  recevait toujours un nom vide après la saisie, puis l'IME réinitialisait le champ au changement de
  focus. Le harnais réinjecte maintenant le nom dans l'UiState du composant contrôlé. L'attente
  « Cola » est conservée et l'activation de « Suivant » est également vérifiée. Les **16 tests**
  de la classe passent en **16,469 s** ; le formulaire de production n'a pas changé.
- Quelques commandes de diagnostic ont échoué (chemin source `southbound.cc` absent, syntaxe Python
  provisoire, lecture d'un rapport déjà remplacé par la relance). Ces incidents n'ont affecté ni
  les données ni le source de l'application.
- Les outils SDK signalent un `emulator.backup` déjà présent. Le JDK Android Studio signale aussi
  l'usage déprécié d'Unsafe dans DataStore et Gradle des fonctions dépréciées. Ces avertissements
  d'environnement/outillage sont distincts des contrôles Kotlin et lint de l'application. Docker
  a également signalé la dépréciation de `stop --time` lors du nettoyage du conteneur temporaire.
- GMS est installé sur le téléphone : cette session n'est **pas** une exécution sur appareil sans
  GMS installé. L'absence de dépendance GMS est vérifiée séparément dans le graphe et les APK.
- Tensor G6, Pixel 10 de base à 12 Go, autres modèles et toutes les versions de GrapheneOS ne sont
  pas validés par ce test sur un seul Pixel 10 Pro XL.
- Aucune limitation de l'API Mealie n'a été rencontrée : aucun accès au serveur n'était nécessaire.

## Vérifications du build intégré

- Régénération complète de `gradle/verification-metadata.xml` avec toutes les tâches prescrites :
  réussite. Les commentaires justifiant les exclusions IDE ont été conservés après régénération.
- Nouvelle exécution avec vérification stricte des dépendances : **771 tests JVM**, zéro échec ;
  `lintDebug`, `lintRelease`, APK debug/release et APK de tests : réussite.
- Graphe `releaseRuntimeClasspath` et recherche dans les noms et les DEX des deux APK : aucune
  référence GMS, Firebase ou Maps.
- `git diff --check` : réussite. Aucun binaire, modèle, secret, fichier temporaire ou commit ajouté
  au dépôt ; les nouveaux fichiers suivables sont uniquement du source, des pins et la documentation.

- `connectedDebugAndroidTest` : **BUILD SUCCESSFUL en 8 min 18 s**, **292 tests réussis**, quatre
  préconditions non réunies (code-barres devant la caméra et trois tests sans modèle Whisper),
  **zéro échec réel**. Le XML UTP encode ces quatre `AssumptionViolatedException` comme des balises
  `failure` ; le runner et Gradle les traitent correctement comme préconditions ignorées.
- Les tests NPU, CPU, GPU, vision, les prompts d'import/planning et les deux étiquettes passent
  dans cette suite du build intégré. La génération courte NPU prend 12,467 s, l'import 42,771 s.
- Après les tests, les packages release, debug et tests sont toujours installés sous l'utilisateur
  0, et `MainActivity` debug démarre. Aucune désinstallation ni remise à zéro des données.
- Le conteneur de recherche est arrêté et supprimé ; l'image de build et les caches normaux restent
  disponibles pour les compilations suivantes. La notification KDE est tentée une seule fois après
  ces vérifications.
- Notification KDE : tentative unique, échec D-Bus `No such object path … /ping`, sans effet sur
  le développement ; aucune relance ni canal de remplacement.
