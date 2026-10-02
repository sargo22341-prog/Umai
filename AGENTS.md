# AGENTS.md

Règles obligatoires du dépôt **umai**. Ce fichier fait autorité ; `.claude/CLAUDE.md`
ne fait que l'importer. À lire entièrement avant toute modification.

---

## 0. Règles de base

Dix règles inspirées de *The Power of 10* (G. Holzmann, NASA/JPL), réécrites pour valoir
quel que soit le langage, le framework ou l'architecture. Chacune donne son **intention** :
c'est elle qui fait foi. Les exemples entre parenthèses illustrent, ils ne limitent pas ;
quand le projet n'a pas l'outil cité, appliquer l'équivalent qui sert la même intention.
Ces règles valent pour tout le code, existant comme nouveau.

Le but commun : un code prévisible, qu'un outil peut vérifier et qu'un humain relit vite.

1. **Flux de contrôle simple et exhaustif.** Pas de récursion ni de saut non structuré dans
   le code applicatif. Quand un choix porte sur un ensemble fermé de cas, les traiter tous
   explicitement, sans branche par défaut fourre-tout. Sortir tôt plutôt qu'imbriquer.
   *Intention :* chaque chemin d'exécution est visible, et l'outil signale le cas oublié
   quand on en ajoute un.
2. **Tout ce qui répète ou attend a une borne.** Une nouvelle tentative a un nombre maximal,
   une attente (réseau, disque, processus, verrou) a un délai, un parcours de données
   paginées ou en flux a un plafond. Une boucle sans fin n'est admise que si elle est
   annulable et a une sortie explicite. Tout travail de fond meurt avec ce qui l'a lancé.
   *Intention :* le programme ne peut ni tourner, ni attendre, ni consommer indéfiniment.
3. **Rien de lourd dans les chemins chauds.** Le code exécuté très souvent ou sous contrainte
   de temps (rendu, boucle d'événements, traitement par image ou par échantillon, requête
   fréquente) n'alloue pas sans besoin, ne fait pas d'E/S et ne calcule rien de coûteux. Le
   travail lourd sort du fil qui doit rester réactif. Une optimisation se mesure, elle ne
   se suppose pas.
   *Intention :* les performances sont prévisibles et la réactivité n'est jamais sacrifiée.
4. **Une fonction tient sur un écran.** Au plus ~60 lignes, quel que soit son rôle (logique,
   interface, configuration). Au-delà, extraire des sous-fonctions nommées par ce qu'elles
   font. Un fichier ou module a une seule responsabilité et reste sous ~600 lignes.
   *Intention :* une unité de code se comprend en une lecture, sans défilement.
5. **Des états impossibles plutôt que des vérifications.** Utiliser le typage, les
   structures et les constructeurs du langage pour qu'un état invalide ne puisse pas
   exister. Les vérifications explicites ne gardent que les préconditions que le type ne
   peut pas exprimer. Toute donnée extérieure (réseau, fichier, saisie, capteur, autre
   service) est validée une fois, à la frontière, puis considérée comme sûre.
   *Intention :* les invariants sont garantis par construction, pas espérés à l'exécution.
6. **Portée et mutabilité minimales.** Chaque nom a la plus petite visibilité et la plus
   courte durée de vie possibles. Immuable par défaut ; un état modifiable n'est exposé
   qu'en lecture seule à l'extérieur de ce qui le possède.
   *Intention :* on sait toujours qui peut lire et qui peut modifier une donnée.
7. **Aucune erreur silencieuse.** Tout résultat est utilisé, ou ignoré de façon explicite
   et volontaire. Aucune erreur n'est avalée : on la traite, on la transforme ou on la
   propage. Les signaux de contrôle du langage (annulation, interruption, arrêt) ne sont
   jamais interceptés par une capture générique. Chaque échec se traduit par un état
   visible, distinct d'un résultat vide.
   *Intention :* un problème se voit là où il survient, jamais plus tard sous une autre forme.
8. **Pas de magie.** Réflexion, génération de code, métaprogrammation, injection automatique
   et logique de build astucieuse seulement sur nécessité démontrée.
   *Intention :* ce que fait le programme se lit dans son source.
9. **Dépendances à sens unique, sans raccourci.** Les couches (présentation, état, données,
   sources externes, ou leur équivalent dans le projet) ne dépendent que de la couche
   inférieure, jamais de l'inverse, sans en sauter une. Pas de longues chaînes d'accès ni
   de rappels imbriqués.
   *Intention :* une donnée a un chemin unique et évident, et chaque couche se teste seule.
10. **Zéro avertissement, zéro passe-droit muet.** Compilateur, analyseurs et linters sont
    réglés au plus strict et leurs avertissements bloquent le build. Toute exclusion porte
    un commentaire qui la justifie. Le code bas niveau (C, C++, code non sûr) suit en plus
    les règles NASA d'origine telles quelles.
    *Intention :* l'outil voit tout ce qu'il peut voir, et rien n'est ignoré sans raison écrite.

---

## 1. Le projet

**umai** est un client Android natif pour une instance **Mealie** auto-hébergée.
Package : `org.opensources.umai` — le build debug s'installe sous
`org.opensources.umai.debug` (`applicationIdSuffix`), ce qui permet de garder les deux
côte à côte. Une seule application, un seul module Gradle (`:app`).

Mealie est le **seul** backend. L'application n'a pas de base de données propre,
pas de serveur, pas de synchronisation maison.

Avant de coder : lire ce fichier, parcourir le dépôt, et consulter `.context/`.
`.context/Mealie/openapi.json` est la **source de vérité de l'API**.

* Ne jamais inventer un endpoint, un champ ou un comportement absent de l'OpenAPI.
* Si une fonctionnalité demandée n'existe pas dans Mealie, ne pas la simuler
  localement : la signaler dans le rapport final (voir §14 *Limitations connues*).
* Ne pas laisser de bug connu, de `TODO`, de `FIXME` ni de dette technique volontaire.
* Ne pas sur-concevoir. Le code le plus simple qui répond au besoin est le bon.

---

## 2. Stack

| Élément | Version / choix |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose + Material 3 |
| `minSdk` = `targetSdk` = `compileSdk` | **37 (Android 17)** |
| Build | AGP 9.x + Gradle 9.x |
| Réseau | Retrofit 3 + OkHttp 5 + kotlinx.serialization |
| Images | Coil 3 (`coil-network-okhttp`) |
| Vidéo | Media3 ExoPlayer (+ HLS) |
| YouTube | NewPipeExtractor (JitPack), sur le client OkHttp « sites externes » |
| IA locale | LiteRT-LM (TPU Tensor → CPU → GPU, derrière `AiEngine`), modèles `.litertlm` téléchargés à part |
| Transcription | whisper.cpp (CPU, JNI, compilé depuis ses sources par CMake), modèles `ggml` téléchargés à part |
| Codes-barres | CameraX (aperçu en direct, zone du cadre de visée) + ZXing core ; produits cherchés dans Open Food Facts (client « sites externes ») |
| Aliments de base | Table Ciqual (Anses, Licence Ouverte 2.0) en `assets/ciqual.tsv`, régénérée par `scripts/ciqual-table.py`, + noms usuels et portions en `assets/basic_foods.tsv` |
| Stockage | DataStore Preferences + Android Keystore |
| Injection | `AppContainer` écrit à la main (§4) |

Règles de dépendances :

* **Licence : GPL-3.0-or-later** (imposée par NewPipeExtractor). Toute dépendance ajoutée doit être
  compatible avec la GPLv3 (Apache 2.0, MIT, BSD, MPL 2.0 conviennent ; une licence propriétaire
  ou « non commerciale » non).
* **Aucun Google Play Services**, aucun Firebase, aucune dépendance non open source.
  L'application doit fonctionner sur GrapheneOS, sans GMS. Si une bibliothèque tire
  GMS, elle est écartée — pas de contournement.
* Ajouter une dépendance seulement si elle remplace un vrai volume de code. Toute
  nouvelle dépendance passe par `gradle/libs.versions.toml`, jamais en dur.
* Les sommes de contrôle de tout ce que le build télécharge sont vérifiées
  (`gradle/verification-metadata.xml`). Après un ajout ou une montée de version, régénérer :
  ```powershell
  .\gradlew.bat --write-verification-metadata sha256 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintRelease
  ```
  Ce contrôle ne vaut qu'en local : le fichier, généré sous Windows, n'a pas les artefacts
  propres à Linux, et la CI passe `--dependency-verification off`. Un échec de vérification
  en local se corrige par cette régénération, jamais en coupant le contrôle.
* Vérifier l'APK après ajout : `com/google/android/gms`, `com/google/firebase` et
  `com/google/android/maps` doivent rester absents (§13).

Pièges du build, déjà rencontrés — ne pas les réintroduire :

* Le plugin `org.jetbrains.kotlin.android` est **incompatible** avec le DSL d'AGP 9.
  Le Kotlin intégré à AGP est utilisé ; ne pas appliquer ce plugin ni ajouter un bloc
  `kotlin { compilerOptions }` racine.
* Pas de rétrocompatibilité : utiliser directement les API Android 17, sans
  `Build.VERSION` ni bibliothèque de compat superflue.
* La bibliothèque de dispatch TPU Google Tensor est téléchargée à la compilation (release LiteRT
  épinglée + SHA-256, tâche `FetchTensorDispatch` de `app/build.gradle.kts`) et n'est **jamais**
  copiée dans le dépôt.
* LiteRT-LM est **épinglé à 0.14.0** : c'est la seule version construite depuis le même source
  LiteRT que ce dispatch (v2.1.6). Les suivantes le refusent ou plantent le TPU en code natif. Ne
  monter les deux qu'ensemble, puis repasser `LiteRtLmBackendTest` sur un Pixel Tensor G5/G6.
* NewPipeExtractor n'est publié que sur JitPack : `settings.gradle.kts` limite ce dépôt au groupe
  `com.github.TeamNewPipe` (`exclusiveContent`). Ne pas l'ouvrir à d'autres groupes.
* NewPipeExtractor : imposer la langue sur l'extracteur (`forceLocalization`), sa préférence globale
  ne suffit pas ; ses règles R8 (Rhino, protobuf-lite, `timeago.patterns`) sont dans
  `proguard-rules.pro`. Il n'expose que les chapitres posés par l'auteur, pas ceux que YouTube génère.
  Quand YouTube casse l'import, monter sa version plutôt que contourner. Détails : `docs/local-ai.md`.
* whisper.cpp est compilé depuis ses sources, téléchargées à la compilation (release épinglée + SHA-256,
  tâche `fetchWhisperSource`) et **jamais** copiées dans le dépôt. Seuls `CMakeLists.txt`, `cmake/`,
  `ggml/`, `include/`, `src/` sont extraits : les *bindings* et exemples contiennent des projets Gradle
  que l'extension Kotlin de l'IDE ouvre et verrouille. Il est compilé optimisé même en debug
  (`-O0` : 30 s de parole en 2 min 40), sur **2 fils** (4 et 6 sont plus lents, mesuré). Détails :
  `docs/local-ai.md`.
* `jniLibs.useLegacyPackaging = true` est nécessaire : LiteRT-LM charge le dispatch TPU par son
  chemin dans le dossier des bibliothèques natives, vide si elles restent dans l'APK.
* Ne jamais afficher un backend qui n'est pas prouvé : `DeviceAccelerators.missingDriver` vérifie
  que le pilote du backend est chargé dans le processus. Le contexte CPU reste à 4 096 jetons : au-delà,
  Android tue l'app (plafond mémoire de 4 Gio). Détails, mesures et choix du modèle : `docs/local-ai.md`.

---

## 3. Architecture

Sens de dépendance strict, jamais inversé :

```
UI (Composable) → ViewModel → Repository → API Mealie (Retrofit)
                                  ↓
                          modèles de domaine
```

* Un Composable n'appelle **jamais** l'API ni un repository.
* Un ViewModel ne contient **aucune** logique HTTP : ni URL, ni en-tête, ni code
  de statut, ni DTO.
* Les DTO (`core/network/dto`) ne sortent **jamais** de la couche data. Le mapping
  DTO → domaine se fait dans un `*Mapper.kt` du paquet de la fonctionnalité.
* L'UI ne consomme que les modèles de `core/model` et les `UiState` des ViewModels.

### Organisation par fonctionnalité

Le code est rangé **par fonctionnalité**, pas par couche technique :

```
org.opensources.umai
├── setup/       ui
├── home/        data · ui
├── search/      domain · ui
├── recipe/      data · domain · ui
├── cooking/     data · domain · ui
├── planning/    data · domain · ui
├── shopping/    data · ui
├── settings/    ui
├── profile/     data · ui
├── organizer/   data
├── provider/    data · domain · ui · (un paquet par site)
├── youtube/     data · domain
├── llm/         data · domain · ui
├── speech/      data · domain
├── navigation/
└── core/        di · download · format · image · markdown · model · network(api, dto) · service · session · settings · ui(component, theme)
```

* Une nouvelle fonctionnalité crée son propre paquet racine, avec ses sous-paquets
  `data` / `domain` / `ui` selon ce dont elle a besoin — pas plus.
* `core/` est réservé au **transversal réel** : utilisé par au moins deux
  fonctionnalités, ou infrastructure (réseau, session, thème, modèles partagés).
  Du code qui ne sert qu'à un écran n'a rien à faire dans `core/`.
* **Interdits** : `utils`, `helpers`, `common`, `misc`, `shared`, `tools`, `ext`,
  `base` et équivalents. Un fichier porte le nom de sa responsabilité
  (`RecipeFormatting.kt`, `MealieUrl.kt`), jamais celui d'un fourre-tout.

---

## 4. Conventions de code

**Écrans.** Chaque écran expose deux fonctions dans le même fichier :

```kotlin
@Composable
fun RecipeDetailRoute(...)          // se branche au ViewModel, collecte l'état

@Composable
fun RecipeDetailScreen(             // publique, sans état, testable directement
    state: RecipeDetailUiState,
    onAction: () -> Unit,
)
```

La version sans état ne connaît ni ViewModel, ni `Context`, ni repository : elle
reçoit son `UiState` et remonte les intentions par lambdas. C'est elle que testent
les tests instrumentés. Les destinations de navigation (`navigation/Destinations.kt`)
s'appellent `XxxDestination`, ce qui laisse le nom `XxxRoute` au composable avec état.

**État.** Un `data class ...UiState` par écran, exposé en `StateFlow` depuis le
ViewModel. Les états `loading`, contenu, **vide** et `error` sont explicites et
distingués : une liste vide n'est pas une erreur, une erreur n'est pas une liste vide.
Les propriétés dérivées (`isIdle`, `isEmptyResult`, `hasNoList`…) vivent sur l'`UiState`,
pas dans le Composable.

**ViewModels.** Créés via `viewModelFactory { initializer { } }`. Ils reçoivent des
repositories et des `Flow`, jamais un `Context` ni un `Application`, pour rester
testables sur JVM.

**Repositories.** Ils reçoivent un fournisseur d'API — `apiProvider: () -> MealieApi?`
— et non la session entière : l'instance peut changer à chaud, et un test injecte un
faux serveur en une ligne. Ils retournent un `ApiResult<T>` (`core/network/ApiCall.kt`)
et ne lèvent pas d'exception réseau. Un appel s'écrit `apiProvider.call { recipe(slug) }`,
un échec intermédiaire se rend par `valueOr { return it }`, une réponse attendue mais
absente devient `InvalidResponse` par `orInvalid()` ; pas de `when` recopié.
Une précondition que le ViewModel garantit (champ non vide…) se vérifie par `require` :
c'est une erreur de programmation, jamais un `NetworkError`.

**Caches.** Une donnée lue sur Mealie et gardée en mémoire est liée à l'instance et à
l'utilisateur (`InstanceCache`, `SessionManager.instanceKey()`) : connecté ailleurs,
elle est relue. Les données locales d'une instance (historique, plats) sont vidées
quand l'app se connecte ailleurs (`SessionManager.watchInstanceChanges`).

**Erreurs.** Toute panne devient un `NetworkError` (`Unreachable`, `Timeout`, `Tls`,
`Unauthorized` (401 : se reconnecter), `Forbidden` (403 : compte sans le droit), `NotFound`,
`Server`, `Http`, `InvalidResponse`, `NotMealie`, `Unknown`).
L'UI traduit ce type en message localisé ; elle ne voit jamais un code HTTP brut.

**Pagination.** `PagedItems<T>` (`core/model`) accumule les pages. Pas de logique de
pagination dupliquée dans les ViewModels.

**Injection.** `core/di/AppContainer.kt` construit les dépendances, `LocalAppContainer`
les fournit à l'arbre Compose. Pas de framework DI : il n'apporterait rien ici.

**Style.** Commentaires en anglais, rares, et qui expliquent *pourquoi* — jamais ce
que le code dit déjà. Nommage explicite. Pas de fonction d'extension « pratique »
déposée hors de son domaine.

---

## 5. Taille et découpage des fichiers

* Aucun fichier source ne dépasse **~600 lignes**.
* Découper **par responsabilité**, jamais par tranches arbitraires pour passer sous
  la limite. Exemple existant : `RecipeDetailScreen.kt` (structure de l'écran) et
  `RecipeContent.kt` (sections de contenu).
* Un fichier qui approche la limite signale généralement une responsabilité de trop.

---

## 6. Mealie

Couvrir, en s'appuyant sur l'OpenAPI : authentification, recettes, recherche et
filtres, images, planning, listes de courses, préférences, organizers
(catégories / tags / ustensiles / aliments).

* Mealie reste la source de vérité. Ne jamais dupliquer côté app une fonctionnalité
  déjà offerte par le serveur.
* Les seules données locales admises sont celles que Mealie **ne stocke pas** :
  préférences d'affichage, langue, session, historique de consultation. La table Ciqual des
  aliments de base est une donnée de référence en lecture seule, livrée avec l'app : Mealie n'a
  aucune valeur nutritionnelle sur ses aliments.
* `core/network/api/MealieApi.kt` est écrit d'après l'OpenAPI. Toute signature
  ajoutée doit être vérifiable dans `.context/Mealie/openapi.json`.

### Limitations connues de Mealie

Constatées sur une instance réelle. Les respecter, ne pas retenter de contournement :

* **Étapes de recette : pas de champ image.** Les photos d'étapes sont intégrées dans
  le texte Markdown/HTML de l'étape. `core/markdown/StepContent.kt` les extrait
  (`![](…)` et `<img src="">`) pour les afficher comme de vraies images.
* **Temps en texte libre** (`"15 minutes"`, `"PT1H"`) : aucun filtre numérique par
  durée n'est possible côté serveur. Ne pas en ajouter un côté client sur une page
  partielle de résultats.
* **Pas de notion de difficulté** dans l'API.
* **Pas d'historique de consultation** côté serveur : « vu récemment » est local
  (slugs uniquement).
* `orderBy=random` **exige** un `paginationSeed`, sinon la pagination se répète.
* Les favoris requièrent un compte utilisateur : un simple jeton d'API n'a pas de
  contexte utilisateur, l'app doit le signaler au lieu d'échouer.
* `lastMade IS NONE` renvoie 0 résultat sur instance réelle : filtre non exposé.
* `queryFilter` est une mini-langue (`rating >= 4`, `id IN ["…"]`, `createdAt > "…"`).
  Attention au séparateur : `joinToString` sans `separator = ","` casse `IN [...]`.
* **Pas de filtre « favoris » côté serveur** : le filtre passe par `id IN [...]` dans l'URL.
  Au-delà de quelques centaines de favoris, l'URL peut dépasser la limite d'un proxy inverse
  (8 Ko par défaut sur nginx) : l'erreur HTTP s'affiche. Ne pas tronquer la liste, ce qui
  donnerait des résultats faux sans le dire.
* **La déconnexion ne révoque rien** : `POST /api/auth/logout` n'efface qu'un cookie de
  navigateur ; un jeton reste valide jusqu'à son expiration (un jeton d'API jusqu'à sa
  suppression dans le profil). L'app oublie le jeton, elle n'appelle pas cet endpoint.
* `PUT /api/recipes/{slug}` remplace la recette entière ; `PATCH` fusionne les champs envoyés
  dans la recette telle que le serveur la tient. Pour un seul champ (étiquettes, assets),
  utiliser `PATCH`. L'édition complète reste en `PUT` : le serveur y dérive le slug du nom et
  nettoie les références d'ingrédients, ce que `PATCH` ne fait pas.

---

## 7. Authentification et sécurité

* Permettre de configurer, modifier et supprimer une instance Mealie.
* Accepter un domaine personnalisé (`mealie.ndd.custom`), une IP, un port, un
  sous-chemin. HTTPS par défaut ; **HTTP autorisé mais avec un avertissement visible**.
* Deux modes d'authentification : identifiant/mot de passe et jeton d'API.
* Gérer distinctement les erreurs réseau, HTTP, TLS et d'authentification (§4).
* **Ne jamais contourner TLS.** Pas de `TrustManager` permissif, pas de
  `hostnameVerifier` neutralisé. Pour une PKI privée (mkcert, step-ca), la bonne
  réponse est la confiance aux CA **utilisateur** via `network_security_config.xml`.
* Le jeton est scellé en **AES/GCM via l'Android Keystore** avant d'atteindre DataStore.
  Un **mot de passe n'est jamais persisté**, sous aucune forme.
* Ne jamais logger ni committer mot de passe, jeton, cookie, credential ou secret.
  Le log HTTP existe uniquement sous `BuildConfig.DEBUG`, avec `Authorization`,
  `Cookie` et `Set-Cookie` masqués. `ServerSession.toString()` masque le jeton.
* Aucun secret dans un log copié dans une réponse, un rapport ou un message de commit.

---

## 8. État, réseau et spécificités Android 17

* Coroutines et `StateFlow` ; les requêtes réseau partent d'un ViewModel ou d'un
  repository, **jamais d'un Composable**, et restent annulables
  (`CancellationException` est relancée, jamais avalée).
* **Réseau local.** Android 17 bloque le trafic vers le LAN sans la permission
  d'exécution `ACCESS_LOCAL_NETWORK` ; sans elle, les connexions expirent en silence.
  L'app la déclare et ne la demande **que** si l'adresse configurée résout vers une
  adresse locale (privée, link-local, loopback, ULA IPv6). Ne pas la demander
  systématiquement.
* **Langue.** Changement à chaud via `LocaleManager` (per-app language) +
  `res/xml/locales_config.xml` + `localeFilters`. Dans un Composable, lire la locale
  de façon observable (`core/format/LocalizedDates.kt`), jamais via `Locale.getDefault()`.

---

## 9. Internationalisation

* Français **et** anglais, à parité. Tout texte visible passe par `strings.xml`.
* Anglais = ressources par défaut ; français dans `values-fr/`.
* Langue par défaut : langue système si fr ou en, sinon anglais.
* La langue est changeable dans les réglages (Système / Français / English).
* Toute chaîne ajoutée est traduite **dans le même changement**. Une chaîne
  volontairement non traduite (nom du produit) est marquée `translatable="false"`
  avec un commentaire.

---

## 10. Design et accessibilité

* Material 3, sobre, orienté contenu. Thèmes clair **et** sombre.
* Palette **Claude Code / Anthropic** (`core/ui/theme/Color.kt`). Couleurs dynamiques
  proposées en option (AOSP, sans dépendance Google).
* Contraste conforme WCAG AA ; l'accent clair est assombri pour atteindre le ratio.
* La navigation basse compte 5 destinations, **Recherche au centre et mise en avant**.
* L'interface doit rester utilisable avec de grandes tailles de police.
* Toute image porteuse de sens a une `contentDescription`, y compris dans ses états
  chargement / erreur / absence d'image.

---

## 11. Tests

Tests JVM dans `app/src/test`, tests instrumentés dans `app/src/androidTest`, rangés
selon les mêmes paquets que le code testé.

Couvrir : authentification, repositories et API, mapping DTO → domaine, recherche,
filtres, planning, listes de courses, erreurs réseau, états vides, et les écrans
principaux.

Cas obligatoires : recette **avec** et **sans** image, étape **avec** et **sans**
image, aucun serveur configuré, serveur injoignable, authentification invalide,
résultats de recherche vides, liste de courses vide.

Outils et pièges déjà réglés :

* `FakeMealieServer` (MockWebServer) sert les réponses ; ne pas appeler le vrai
  serveur depuis un test.
* Pour un ViewModel faisant du vrai HTTP sur MockWebServer, utiliser
  `Dispatchers.setMain(Dispatchers.Unconfined)` + `runBlocking` +
  `withTimeout { state.first { … } }`. `StandardTestDispatcher` + `advanceUntilIdle()`
  ne voit jamais la fin de la requête.
* Tests Compose : `createComposeRule` **v2**, un seul `setContent` par test.
* Les photos de produits et d'étiquettes des tests sont dans `app/src/sharedTest/pictures/` :
  ressources des tests JVM, assets des tests instrumentés (`TestPictures`). Pas d'`adb push`.
* Quand un test échoue, corriger le **code**, pas l'attente du test — sauf si
  l'attente est elle-même fausse, et le dire.

---

## 12. Qualité — avant de déclarer une tâche terminée

Dans cet ordre, et tout doit passer :

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
.\gradlew.bat :app:connectedDebugAndroidTest    # appareil branché requis
```

* Zéro erreur, zéro avertissement de compilation, zéro erreur lint. Le build le garantit :
  le compilateur Kotlin a ses contrôles supplémentaires (`extraWarnings`) et traite tout
  avertissement en erreur ; lint active **tous** ses contrôles (`checkAllWarnings`) et
  traite ses avertissements en erreurs. Les rares exclusions sont justifiées dans
  `app/build.gradle.kts` et `app/lint.xml`.
* Tout résultat est utilisé (règle 7) : le vérificateur du compilateur
  (`-Xreturn-value-checker=full`) le contrôle. Un résultat ignoré exprès s'écrit
  `val _ = …` avec la raison en commentaire ; une fonction dont le résultat est facultatif
  (utilitaire de test qui attend un état) porte `@IgnorableReturnValue`.
* Ne jamais déclarer terminé avec un build cassé, un test rouge ou un problème
  important connu.
* Corriger proprement la cause ; ne pas empiler un workaround. Désactiver une règle
  lint exige une justification écrite en commentaire.

---

## 13. Téléphone et ADB

**ADB** : `C:\platform-tools\adb.exe` — appareil de test : Pixel 6 Pro, Android 17.

* Vérifier d'abord que l'appareil est visible (`adb devices`). Ne jamais supposer une
  connexion, ne jamais prétendre avoir testé si ADB ne voit pas l'appareil.
* L'appareil a plusieurs utilisateurs (propriétaire = `0`, profil professionnel = `11`).
  Toujours cibler explicitement `--user 0` pour `pm` : sinon `SecurityException`.

### Ne pas désinstaller l'application de debug

**L'application debug installée sur le téléphone doit rester installée, avec ses
données**, pour que l'instance Mealie et la session configurées soient conservées et
qu'il n'y ait pas à les ressaisir à chaque fois.

* **Interdits** sans demande explicite : `adb uninstall`, `pm uninstall`,
  `pm clear org.opensources.umai.debug`, suppression de DataStore, réinitialisation de
  l'appareil.
* Mettre à jour l'app par **réinstallation par-dessus**, qui préserve les données :
  ```powershell
  C:\platform-tools\adb.exe install -r -d app\build\outputs\apk\debug\app-debug.apk
  ```
* `connectedDebugAndroidTest` désinstallait les deux APK en fin de course, ce qui
  effaçait la session. `gradle.properties` porte désormais
  `android.injected.androidTest.leaveApksInstalledAfterRun=true` : AGP en déduit
  `android-test.uninstall-after-tests = false` et l'app reste en place avec ses
  données. Ne pas retirer cette ligne. L'APK de test
  (`org.opensources.umai.debug.test`) reste lui aussi installé : c'est attendu, il
  est remplacé à chaque exécution.
* Après une session de tests instrumentés, vérifier que l'app est toujours là et
  qu'elle démarre :
  ```powershell
  C:\platform-tools\adb.exe shell pm list packages --user 0 | Select-String umai
  C:\platform-tools\adb.exe shell am start -n org.opensources.umai.debug/org.opensources.umai.MainActivity
  ```
  Si la session a malgré tout été perdue, reconfigurer l'instance et le dire dans le
  rapport.
* En cas de problème, consulter `logcat` filtré sur le package — sans jamais recopier
  de secret.
* Vérification « sans GMS » sur l'APK release :
  ```powershell
  # aucune occurrence attendue
  unzip -l app\build\outputs\apk\release\app-release.apk | Select-String "com/google/android/gms|com/google/firebase"
  ```

---

## 14. Git, fichiers et rapport final

**Git.** Ne modifier que les fichiers liés à la tâche. Jamais de `git reset --hard`
ni de suppression de travail existant pour résoudre un conflit. **Aucun commit sans
demande explicite.** Changements petits et cohérents.

**Fichiers interdits au dépôt** : binaire généré, secret, `local.properties`, fichier
temporaire, code généré massif inutilisé, police sans licence explicite.

**Notes de version.** Toute modification de l'application (fonctionnalité, correction,
changement visible) ajoute **une courte phrase** dans `RELEASE_NOTES.md`, sous la ligne
`<!-- notes -->`, au format `- …`, dans le même changement. Ne jamais toucher à l'en-tête
ni au marqueur. La CI publie ces lignes comme description de la GitHub Release puis
**vide la liste** dans le commit `Version X.Y.Z` à chaque montée de version : ne pas la
vider à la main. Un changement purement interne (tests, doc, CI) n'a pas besoin de note.

**Version et release.** La version vit dans `app/version.properties` et n'est montée que
par la CI (`.github/workflows/ci.yml`, à chaque push sur `main` qui ne touche pas que la
documentation ou les tests) : ne pas la modifier à la main. Fonctionnement, secrets de signature et limites : `docs/release.md`. La clé de
signature et son mot de passe ne sont jamais dans le dépôt ni demandés par l'agent.

**Serveur Mealie de test.** Ne **rien** supprimer ni modifier sur l'instance fournie.
Créer une donnée de test puis la supprimer soi-même est acceptable ; éditer ou
supprimer une recette, un planning ou une liste préexistants ne l'est pas. Toute
modification involontaire doit être restaurée **et** signalée dans le rapport.

**Rapport final.** Il indique :

* résumé des changements ;
* fonctionnalités terminées ;
* principaux fichiers modifiés ;
* tests exécutés et résultats ;
* vérifications effectuées ;
* limitations réelles de Mealie rencontrées ;
* tout incident ou échec, y compris ceux qui n'ont pas bloqué la tâche.

Y ajouter un **exemple de commit prêt à copier, en français**.

---

## 15. Notification KDE

Uniquement après la fin complète du travail, build et vérifications réussis, exécuter
**une seule fois** :

```powershell
kdeconnect-cli --device 9d3e0da7eb0e4cacb95ff4869f8f669b --ping-msg "Le développement de l'application Mealie est terminé."
```

* **La sortie de cette commande peut être ignorée.** L'appareil est souvent
  injoignable et la commande affiche alors une erreur D-Bus
  (`No such object path … /ping`) : c'est sans conséquence sur le travail.
* **Ne jamais relancer la commande en boucle**, ne pas chercher un autre appareil,
  ne pas essayer un canal de remplacement. Un échec se mentionne en une ligne dans
  le rapport final, et rien de plus.

---

## 16. Principe

Code simple, clair, séparé, testable et maintenable.
Toujours corriger proprement la cause d'un problème plutôt que l'empiler sous forme
de contournement.
