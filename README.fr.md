<p align="center">
  <a href="README.md">English</a> ·
  <strong>Français</strong>
</p>

<p align="center">
  <img src="./docs/icon/umai_icon.svg" width="120" alt="Logo de umai" />
</p>

<h1 align="center">umai</h1>

<p align="center">
  Un client Android natif pour votre instance <a href="https://mealie.io">Mealie</a> auto-hébergée :<br />
  recettes, mode cuisine, planning des repas et listes de courses.
</p>

<p align="center">
  <a href="https://github.com/sargo22341-prog/umai/releases/latest"><img src="./docs/images/badges/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/sargo22341-prog/umai"><img src="./docs/images/badges/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Avertissement

> [!WARNING]
> Cette application a été **développée avec l'aide d'une intelligence artificielle**.
> Le code, les tests et la documentation ont été produits en grande partie par IA puis vérifiés
> par des tests automatisés, mais tout n'a pas été relu ligne à ligne ni validé dans toutes les
> situations réelles. Utilisez-la en connaissance de cause et consultez les
> [limites connues](#limites-connues).

## Présentation

**umai** met vos recettes Mealie dans votre poche, avec une interface pensée pour la cuisine :
texte lisible de loin et mode cuisine étape par étape, qui peut garder l'écran allumé pendant que
vous cuisinez.

Mealie est le **seul** backend : l'application n'a ni base de données, ni serveur, ni
synchronisation maison. Tout ce que vous faites — noter une recette, planifier un repas, cocher un
article — est écrit directement sur votre instance, et se retrouve aussi dans l'interface web de
Mealie.

Fonctionnalités principales :

- **accueil** : recettes consultées récemment et dernières recettes ajoutées à l'instance ;
- **recherche** par nom, ingrédient ou mot-clé, avec tri (date d'ajout, nom, note, dernière
  réalisation, au hasard ; croissant ou décroissant) et **filtres** : favoris, note minimale, date
  d'ajout, catégories et tags ;
- **fiche recette** : photo, durées, tags, favori et note sur 5 étoiles, **ajustement des
  portions** des ingrédients, instructions avec leurs photos, commentaires ;
- **mode cuisine** : une étape par écran, les ingrédients de l'étape en cours, écran maintenu
  allumé (facultatif) ;
- **planning** : vue jour par jour, ajout d'une recette ou d'une note à n'importe quel repas ;
- **listes de courses** : plusieurs listes, articles regroupés par étiquette, envoi des ingrédients
  d'une recette vers une liste (ajustés aux portions choisies) ;
- **création et modification de recettes** : import depuis une page web (analysée par Mealie),
  rédaction étape par étape avec brouillons conservés sur le téléphone, recadrage de la photo ;
- **profil** : compteurs de l'instance, photo de profil avec recadrage ;
- **deux langues** : français et anglais, modifiables dans les réglages ;
- thème clair, sombre ou système, Material 3, couleurs du fond d'écran en option ;
- **aucun compte autre que celui de Mealie, aucun analytics, aucun service Google Play** :
  fonctionne sur GrapheneOS.

### Aperçu

| Accueil | Recherche | Filtres |
| --- | --- | --- |
| ![Écran d'accueil avec les recettes récentes](docs/images/fr/home.png) | ![Résultats de recherche](docs/images/fr/search.png) | ![Filtres de recherche](docs/images/fr/filters.png) |

| Recette | Ingrédients | Mode cuisine |
| --- | --- | --- |
| ![Fiche recette](docs/images/fr/recipe.png) | ![Ingrédients avec ajustement des portions](docs/images/fr/ingredients.png) | ![Mode cuisine, une étape par écran](docs/images/fr/cooking.png) |

| Planning | Liste de courses | Thème sombre |
| --- | --- | --- |
| ![Planning des repas](docs/images/fr/planning.png) | ![Liste de courses regroupée par étiquette](docs/images/fr/shopping.png) | ![Fiche recette en thème sombre](docs/images/fr/dark.png) |

## Installation

Il n'existe pas de version publiée sur un store : l'APK signé de chaque version est joint aux
releases GitHub du dépôt (il peut être suivi avec Obtainium), ou l'application se compile depuis
les sources.

Prérequis : une instance **Mealie** joignable depuis le téléphone, et un téléphone sous
**Android 17 (API 37)** minimum. Pour compiler : Android Studio récent (JDK 21 embarqué) et SDK
Android 37.

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Release signée et CI : [Release signée](docs/release.md).

## Connexion à Mealie

1. Au premier lancement, saisir l'adresse de l'instance : un domaine (`mealie.example.com`), une
   adresse IP, un port ou un sous-chemin conviennent. Sans préfixe, HTTPS est utilisé.
2. Se connecter avec **identifiant et mot de passe**, ou coller un **jeton d'API** (Mealie →
   Réglages → Jetons d'API).
3. L'adresse et le mode de connexion se modifient ensuite depuis *Profil → Réglages Mealie*.

Bon à savoir :

- **HTTP** est accepté, mais l'application affiche un avertissement : le mot de passe et les
  recettes circuleraient en clair.
- Une **autorité de certification privée** (mkcert, step-ca…) est prise en charge via les
  certificats CA installés par l'utilisateur dans Android. TLS n'est jamais contourné.
- Pour une instance hébergée **sur le réseau local**, Android 17 exige la permission *accès au
  réseau local* ; l'application ne la demande que si l'adresse pointe vers le réseau local.
- Les favoris exigent un compte utilisateur : avec un simple jeton d'API, l'application le signale
  au lieu d'échouer.

## Confidentialité

- Le jeton d'API est chiffré par le Keystore Android (AES-GCM) avant d'être enregistré ; un **mot
  de passe n'est jamais enregistré**, sous aucune forme.
- Les seules données conservées sur le téléphone sont celles que Mealie ne stocke pas :
  préférences d'affichage, langue, session, liste des recettes consultées récemment et brouillons
  de recettes non terminés.
- Votre instance Mealie est le seul serveur que l'application contacte.

## Limites connues

Elles viennent de l'API Mealie, pas de l'application :

- les durées des recettes sont du texte libre (`"15 minutes"`, `"PT1H"`) : pas de filtre par
  durée ;
- Mealie n'a pas de notion de difficulté ;
- les photos d'étapes n'ont pas de champ dédié : umai affiche les images intégrées au texte de
  l'étape ;
- il n'y a pas d'historique de consultation côté serveur : « consultées récemment » est conservé
  sur le téléphone.

## Stack

| Couche | Technologie |
| --- | --- |
| Langage | Kotlin, coroutines, Flow |
| Interface | Jetpack Compose, Material 3, Navigation Compose |
| Réseau | Retrofit, OkHttp, Kotlin Serialization |
| Images | Coil |
| Stockage | DataStore |
| Sécurité | Android Keystore (AES-GCM) |
| Injection | Conteneur écrit à la main |
| Plateforme | Android 17 (API 37) minimum |

## Contribuer

Règles de contribution (humains et agents) : [`AGENTS.md`](AGENTS.md). La référence de l'API
Mealie utilisée par l'application est sa description OpenAPI.

## Licence

Copyright © 2026 sargo.

umai est un logiciel open source distribué sous **licence MIT** : vous pouvez l'utiliser, le
copier, le modifier et le redistribuer librement, à condition de conserver la mention de copyright
et le texte de la licence. Texte complet : [`LICENSE`](LICENSE).

umai est un projet indépendant, sans lien avec Mealie. Les recettes visibles sur les captures
appartiennent à leurs auteurs respectifs.
