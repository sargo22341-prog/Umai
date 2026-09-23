# Release signée

La version de production est signée avec une clé Android conservée **hors du dépôt** : sur un
support externe, et dans les secrets GitHub pour la release automatique.

## Release automatique (GitHub Actions)

Le workflow `.github/workflows/ci.yml` :

- sur **chaque pull request et chaque push** :
  - `testDebugUnitTest`, `lintDebug`, `assembleDebug` et `compileDebugAndroidTestKotlin` (les
    rapports de tests et de Lint sont joints au run en cas d'échec) ;
  - pas de tests instrumentés : l'émulateur Android 17 plante sur les runners Linux ; ils se
    lancent à la main sur un appareil (`AGENTS.md` §12) ;
- sur **chaque push sur `main`**, si ce job passe :
  1. `scripts/bump-version.sh` incrémente le patch de `versionName` et `versionCode` dans
     `app/version.properties` ;
  2. `scripts/release-notes.sh` relève les notes de `RELEASE_NOTES.md` puis vide la liste ;
  3. compile `assembleRelease`, signe l'APK avec `apksigner` et vérifie la signature ;
  4. committe `Version X.Y.Z [skip ci]` (version montée **et** notes vidées), crée le tag
     `vX.Y.Z` et pousse les deux de façon atomique ;
  5. publie une GitHub Release `vX.Y.Z` avec `umai-X.Y.Z.apk` ; sa description est le texte
     relevé à l'étape 2, ou la liste des commits générée par GitHub s'il était vide.
- **Actions → CI → Run workflow** sur `main` permet de choisir `minor` ou `major` au lieu de
  `patch`.

### Notes de version

Chaque changement de l'application ajoute une courte phrase dans `RELEASE_NOTES.md`, à la racine,
**sous** la ligne `<!-- notes -->` (une ligne `- …` par changement), committée avec le travail
concerné. Tout ce qui suit le marqueur devient la description de la prochaine release ; ce qui le
précède (titre, mode d'emploi) est conservé. Si le marqueur est supprimé, le job de release échoue
avant toute publication.

Les notes ne sont vidées que dans le commit de version : si le push de ce commit est refusé,
elles restent en place et partent avec la release suivante. Après une release, `git pull` avant
d'ajouter de nouvelles notes, sinon la liste vidée par la CI entre en conflit avec l'ancienne.

### Exécution sur GitHub uniquement

Le workflow ne s'exécute que sur GitHub : un serveur Gitea ou Forgejo qui héberge une copie du
dépôt lit aussi `.github/workflows`, mais ses jobs y sont ignorés (`github.server_url`). Le commit
de version, poussé avec le jeton du workflow, ne relance pas la CI. Si `main` a avancé pendant le
build, le push est refusé et rien n'est publié : le push suivant produit la version. Après une
release, **récupérer `main`** (`git pull`) avant de travailler, sinon le `versionCode` local est
en retard.

### Secrets à créer

Settings → Secrets and variables → Actions :

| Secret | Contenu |
| --- | --- |
| `UMAI_KEYSTORE_BASE64` | le fichier `umai.jks` encodé en base64 |
| `UMAI_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `UMAI_KEY_ALIAS` | alias de la clé (facultatif, `umai` par défaut) |
| `UMAI_KEY_PASSWORD` | mot de passe de la clé (facultatif, celui du keystore par défaut) |

Création unique de la clé (remplacer `E:` par la lettre du support externe) :

```powershell
New-Item -ItemType Directory -Force "E:\umai-signing" | Out-Null

& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" `
  -genkeypair -v `
  -keystore "E:\umai-signing\umai.jks" `
  -storetype PKCS12 `
  -alias umai -keyalg RSA -keysize 4096 -validity 10000
```

Encodage sans fichier intermédiaire, puis coller le presse-papiers dans le secret :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("E:\umai-signing\umai.jks")) | Set-Clipboard
```

Sans `UMAI_KEYSTORE_BASE64` ou `UMAI_KEYSTORE_PASSWORD`, le job de release échoue avant toute
montée de version : aucune release non signée n'est publiée. **Perdre la clé ou son mot de passe
empêche toute mise à jour de l'application installée** : en garder une copie de sauvegarde. Si
`main` est protégée, autoriser GitHub Actions à y pousser.

La clé est décodée dans le dossier temporaire du runner, puis supprimée à la fin du job.

## Mises à jour

- Chaque release installée par-dessus une précédente doit avoir un `versionCode` supérieur
  (`app/version.properties`, monté par la CI).
- La version debug (`org.opensources.umai.debug`) et la release (`org.opensources.umai`) ont des
  identifiants différents : elles s'installent côte à côte, sans conflit de signature.

## Limites de sécurité

Toute personne pouvant modifier le workflow sur `main` peut faire signer un APK : les secrets ne
sont pas exposés aux pull requests venant de forks, mais un collaborateur disposant du droit
d'écriture y a indirectement accès. Relire tout changement de `.github/workflows/`.
