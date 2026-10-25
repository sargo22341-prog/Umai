# Analyse des ingrédients et portions

L’OpenAPI de `.context/Mealie/openapi.json` fait foi. L’application appelle
`POST /api/parser/ingredients` avec le parseur `nlp` et les lignes à analyser.
Cette opération retourne des propositions ; leur enregistrement dans la recette
est une opération distincte.

## Parcours

- Création manuelle : analyse avant la création sur Mealie. Les lignes incertaines
  ouvrent une correction ; aucune recette supplémentaire n’est créée à chaque tentative.
- Import de page, vidéo et génération IA : analyse après création, avant l’annonce
  de fin. Les résultats sûrs sont enregistrés ; les autres restent dans leur texte
  initial et une notification invite à les vérifier dans l’éditeur.
- Édition : le bouton **Analyser les ingrédients** traite les lignes non structurées.
  Modifier une ligne invalide son ancienne analyse. Enregistrer des ingrédients
  modifiés lance aussi l’analyse si nécessaire.
- La correction propose quantité, aliment, unité et note de préparation. Les
  valeurs structurées restent modifiables. Une ligne volontairement non quantifiée,
  par exemple « sel à convenance », peut être conservée comme texte non ajustable.

Les références des ingrédients vers les étapes sont conservées. La quantité est
enregistrée dans son unité d’origine : les portions multiplient cette quantité,
sans exiger une conversion en grammes. Le nombre initial de portions doit être
renseigné dans la recette.

## État et confiance

`ParsedIngredient.confidence` peut fournir `average`, `quantity`, `unit`, `food`,
`name` et `comment`. Ces scores sont facultatifs : l’API n’offre aucun booléen qui
garantirait que l’analyse est correcte.

L’application demande une vérification si la quantité positive ou l’aliment manque,
si l’aliment n’a pas encore été rapproché d’un aliment Mealie, si aucun score n’est
disponible, ou si un score pertinent est invalide ou inférieur à 0,8. Ce seuil est
une précaution de l’application, pas une garantie de Mealie. Le plus faible score
disponible est affiché pendant la correction.

Les scores et la provenance IA ne font pas partie de l’ingrédient enregistré par
Mealie. À la réouverture, l’application peut vérifier la présence des données
structurées, mais pas retrouver la confiance historique du parseur. De même,
Mealie ne distingue pas un texte volontairement non quantifié d’un texte restant
à analyser ; cette ligne pourra donc être proposée à nouveau dans l’éditeur.

## Données disponibles

| Source | Données utiles | Usage |
|---|---|---|
| Mealie, `GET /api/foods` | Identifiant et nom des aliments de l’instance ; l’API des aliments connaît aussi leurs alias et noms pluriels | Recherche et réutilisation des aliments existants |
| Mealie, `GET /api/units` | Noms, abréviations, pluriels, alias, fractions ; le schéma expose aussi `standardQuantity` et `standardUnit`, facultatifs | Choix d’une unité et conservation de la quantité d’origine |
| `assets/ciqual.tsv` | Noms français/anglais et composition nutritionnelle des aliments de référence | Suggestions de noms complémentaires |
| `assets/basic_foods.tsv` | Noms usuels et portions de référence, liés à Ciqual | Suggestions de noms usuels ; aucune conversion automatique d’une pièce en poids |
| Texte initial | Quantité et unité effectivement écrites, préparation et remarques | Source de la correction manuelle et des propositions IA |

Les listes Mealie sont recherchées côté serveur et bornées ; les suggestions ne
prétendent pas être un inventaire exhaustif. Une recherche plus précise permet de
retrouver d’autres entrées. Un nom absent peut être saisi : Mealie autorise un
aliment ou une unité à créer dans le document de recette.

Il n’existe pas de masse universelle d’une cuillère ou d’une pièce : elle dépend
de l’aliment, de sa préparation et de sa taille. Les portions Ciqual usuelles ne
prouvent pas le grammage d’une recette particulière. L’application ne déduit donc
pas de grammes à partir de ces valeurs.

## IA locale et bornes

L’IA locale ne s’exécute qu’après Mealie, pour les lignes restant incertaines,
si un modèle est disponible et activé. Elle reçoit la ligne et des suggestions
de noms Mealie/Ciqual et d’unités Mealie. Elle peut proposer un nom absent des
listes, mais jamais fabriquer un identifiant Mealie : seuls les identifiants des
candidats fournis sont réutilisés. Toute proposition IA exige une confirmation.

L’analyse est bornée à 100 lignes de 2 000 caractères ; l’assistance IA complète
est limitée à deux minutes. Une réponse Mealie incomplète ou dans un ordre
incohérent est refusée. Une panne d’analyse après import annonce la recette déjà
créée et la vérification nécessaire, pour éviter d’inviter à recréer un doublon.
Les avertissements concernant les médias restent visibles avec celui de l’analyse.

Aucun appel au parseur `openai` de Mealie n’est effectué : l’assistance utilise
le modèle local de l’application.
