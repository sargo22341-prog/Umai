# AGENTS.md

## Règles générales

* Lire `AGENTS.md`, le repository et `.context/` avant de modifier le code.
* Considérer l'OpenAPI Mealie dans `.context/` comme la source de vérité de l'API.
* Ne jamais inventer d'endpoint ou de comportement absent de l'API.
* Si une fonctionnalité demandée n'est pas supportée par Mealie, la signaler dans le rapport final.
* Ne pas laisser de bug connu, TODO, FIXME ou dette technique volontaire.
* Ne pas sur-engineerer.

## Stack

* Kotlin.
* Jetpack Compose / Material 3.
* Android 17 minimum.
* Pas de rétrocompatibilité avec les anciennes versions Android.
* Aucun Google Play Services ou depandance non open sources.
* L'application doit fonctionner sur GrapheneOS sans Google Play Services.
* Limiter les dépendances inutiles et vérifier qu'elles ne nécessitent pas Google Play Services.

## Architecture

Utiliser une architecture claire avec séparation :

`UI → ViewModel → Domain → Repository → Data → Mealie API`

Organiser principalement le code par fonctionnalité :

* `setup`
* `home`
* `search`
* `recipe`
* `cooking`
* `planning`
* `shopping`
* `settings`
* ect...

Le code transversal uniquement dans `core`.

Éviter les dossiers fourre-tout comme `utils`, `helpers`, `misc` ou équivalents.

Les Composables ne communiquent jamais directement avec l'API.

Les ViewModels ne contiennent pas de logique HTTP.

Ne pas propager inutilement les modèles API dans l'UI.

## Taille des fichiers

* Aucun fichier source ne doit dépasser environ 600 lignes.
* Découper les fichiers lorsqu'ils deviennent trop gros.
* Découper selon les responsabilités, pas artificiellement pour respecter la limite.

## Mealie

Utiliser l'OpenAPI pour implémenter :

* authentification ;
* recettes ;
* recherche et filtres ;
* images ;
* planning ;
* listes de courses ;
* préférences ;
* autres fonctionnalités disponibles.

Mealie reste la source de vérité. Ne pas créer un backend ou une logique locale parallèle lorsque Mealie fournit déjà la fonctionnalité.

## Authentification et sécurité

Permettre de configurer, modifier et supprimer une instance Mealie.

Gérer correctement les erreurs de connexion, HTTP, réseau, TLS et authentification.

Ne jamais logger ou committer :

* mots de passe ;
* tokens ;
* credentials ;
* secrets.

Ne jamais stocker un mot de passe en clair.

## État et réseau

Utiliser Coroutines et `StateFlow` lorsque pertinent.

Gérer explicitement les états loading, success, empty et error.

Les requêtes réseau ne doivent pas être lancées depuis les Composables.

Les opérations réseau doivent être annulables lorsque nécessaire.

## Téléphone, ADB, Git et identité

**ADB** : `C:\platform-tools\adb.exe`

Avant test : vérifier l'appareil visible, ne jamais supposer une connexion ni prétendre avoir testé si ADB ne le voit pas. Ne pas effacer les données utilisateur sans autorisation ni désinstaller inutilement. Installer le build debug, lancer l'activité principale, consulter `logcat` filtré sur le package en cas de problème. Jamais de secret dans les logs copiés.

**Git et fichiers** : modifier uniquement les fichiers liés à la tâche ; jamais `git reset --hard` ni suppression de travail existant pour résoudre un conflit ; pas de commit sans demande explicite ; petits changements cohérents. Pas de binaire généré, secret, fichier temporaire, code généré massif inutilisé, ou police sans licence explicite.

## Internationalisation

L'application doit être disponible en français et anglais.

Tous les textes visibles doivent être localisés.

Langue par défaut : langue système si français ou anglais, sinon anglais.

Permettre de changer la langue dans les paramètres.

## Tests

Ajouter les tests pertinents pour : authentification, API/repositories, mapping, recherche, filtres, planning, shopping list, erreurs réseau, états vides, principales interfaces. ect 

Tester notamment les recettes avec et sans images et les étapes avec et sans images.

## Qualité

Avant de terminer une tâche :

Compiler le projet, Exécuter les tests, Corriger les erreurs, Effectuer un build.

Ne pas déclarer la tâche terminée avec un build cassé ou un problème important connu.

## Rapport final

Le rapport final doit indiquer :

* résumé des changements ;
* fonctionnalités terminées ;
* principaux fichiers modifiés ;
* tests exécutés et résultats ;
* vérifications effectuées ;
* limitations réelles de Mealie, s'il y en a.

Ajouter un exemple de commit prêt à copier, en français.

Ne pas créer de commit automatiquement sauf demande explicite.

## Notification KDE

Uniquement après la fin complète du travail, avec build et vérifications réussis, exécuter :

```powershell
kdeconnect-cli --device 9d3e0da7eb0e4cacb95ff4869f8f669b --ping-msg "Le développement de l'application Mealie est terminé."
```

Si l'envoi échoue, le signaler dans le rapport final.

## Principe

Privilégier un code simple, clair, séparé, testable et maintenable.

Toujours corriger proprement un problème plutôt que l'empiler sous forme de workaround.