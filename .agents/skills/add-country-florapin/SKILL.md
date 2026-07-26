---
name: add-country-florapin
description: Ajouter et valider un nouveau pays dans FloraPin à partir de ses subdivisions administratives GeoJSON, avec résolution GPS hors ligne, badge pays, progression Explorateur, tests et changelogs. Utiliser pour toute demande d’intégration, de prise en charge ou de test d’un pays, de ses régions/cantons/États/provinces, ou d’un nouvel asset `regions-XX.geojson` dans le dépôt FloraPin.
---

# Ajouter un pays à FloraPin

Intégrer les subdivisions administratives de premier niveau d’un pays sans
régression sur les pays déjà pris en charge. Garder le calcul entièrement hors
ligne et préserver les identifiants de badges déjà persistés.

## Préparer le travail

1. Lire `AGENTS.md`, vérifier `git status --short` et préserver les changements
   sans rapport avec la demande.
2. Lire [references/architecture.md](references/architecture.md) avant toute
   modification.
3. Confirmer ou déterminer :
   - le code ISO 3166-1 alpha-2 du pays ;
   - le niveau administratif équivalent à une région FloraPin ;
   - la langue des libellés affichés ;
   - le nombre officiel de subdivisions attendu ;
   - le comportement du badge pays. Par défaut, le débloquer à la première
     observation dans le pays.

## Obtenir les limites

1. Rechercher une source officielle ou gouvernementale à jour. Utiliser des
   sources techniques primaires et conserver l’URL, l’organisme, le millésime et
   la licence.
2. Préférer un `FeatureCollection` GeoJSON simplifié en WGS84/EPSG:4326.
3. Si la source utilise une autre projection, la convertir explicitement avec un
   outil géospatial adapté. Ne jamais traiter des coordonnées projetées comme des
   longitudes/latitudes.
4. Télécharger la source dans `build/country-import/<iso>/` (ignoré par Git) ou
   dans un autre dossier temporaire, jamais directement dans
   `app/src/main/assets`.

## Normaliser le GeoJSON

Exécuter le script fourni depuis la racine du dépôt :

```powershell
node .agents/skills/add-country-florapin/scripts/normalize-regions.mjs `
  --input build/country-import/xx/source.geojson `
  --output build/country-import/xx/regions-xx.geojson `
  --country-code XX `
  --code-property code_source `
  --name-property nom_source `
  --expected-count 12 `
  --max-output-bytes 1500000
```

Répéter `--input` quand une API livre les subdivisions en plusieurs lots. Ajouter
`--mapping mapping.json --require-mapping` si les codes ou libellés doivent être
remplacés. Le fichier de mapping est un objet indexé par le code source :

```json
{
  "source-code": {"code": "stable-code", "name": "Nom français"}
}
```

Le script :

- accepte `Polygon` et `MultiPolygon` ;
- produit uniquement `countryCode`, `code`, `nom` et `outreMer` ;
- garantit l’ordre `type` puis `coordinates` exigé par le parseur en streaming ;
- valide les coordonnées `[longitude, latitude]`, les anneaux et les doublons ;
- fusionne plusieurs entrées et accepte aussi une réponse API enveloppée dans
  `{ "feature": ... }` ;
- vérifie le nombre de subdivisions avec `--expected-count` et peut refuser un
  asset trop lourd avec `--max-output-bytes`.

Inspecter la taille et quelques propriétés du résultat.

### Simplifier un asset trop lourd

Si le résultat dépasse `1 500 000` octets, simplifier toute la couche dans une
même opération topologique. Ne jamais simplifier chaque subdivision séparément :
les frontières communes pourraient alors créer des trous ou des chevauchements.

Utiliser Mapshaper temporairement, sans l’ajouter aux dépendances du projet :

```powershell
npx.cmd --yes mapshaper@latest `
  build/country-import/xx/regions-xx-normalized.geojson `
  -simplify weighted 30% keep-shapes `
  -clean `
  -o format=geojson precision=0.00001 force `
  build/country-import/xx/regions-xx-topo.geojson
```

`weighted` conserve mieux la forme des frontières, `keep-shapes` empêche la
disparition des petits territoires et `-clean` contrôle la couche après
simplification. `precision=0.00001` arrondit à environ un mètre, suffisamment
précis face à la précision GPS habituelle.

Le taux de `30%` est un point de départ, pas une valeur universelle. Essayer
plusieurs taux et retenir le plus élevé qui respecte la limite de taille. Après
Mapshaper, relancer obligatoirement `normalize-regions.mjs` sur le fichier
topologique avec `--expected-count`, `--max-output-bytes` et, si nécessaire,
`--mapping ... --require-mapping`.

Valider ensuite :

- le nombre de subdivisions et de codes uniques, sans feature supprimée ;
- les types `Polygon`/`MultiPolygon`, les anneaux et l’ordre `type` puis
  `coordinates` ;
- une ville intérieure représentative par subdivision, les enclaves/exclaves et
  au moins un point hors du pays avec le vrai `RegionResolver` ;
- l’absence de changement de canton pour les points de contrôle entre la source
  brute et la version simplifiée ;
- la taille finale, le nombre de positions et la présence de l’asset dans l’APK.

Consigner la version de Mapshaper, la commande, le taux retenu et les tailles
avant/après dans le changelog technique ou la documentation de la source.

## Intégrer le pays

1. Copier le résultat validé vers
   `app/src/main/assets/regions-<iso-minuscule>.geojson`.
2. Dans `RegionResolver.kt` :
   - ajouter une constante d’asset et l’enregistrer dans `ASSET_NAMES` ;
   - ajouter la constante du code pays ;
   - mettre à jour la documentation et le nombre attendu de régions.
3. Dans `BadgeCalculator.kt` :
   - conserver la clé globale `${countryCode}:${regionCode}` utilisée par
     « Explorateur » ; ses paliers 1/5/10/15/20 compteront automatiquement les
     nouvelles régions ;
   - suivre les régions du nouveau pays séparément pour son badge dédié ;
   - exposer une progression disponible/indisponible cohérente avec les autres
     badges géographiques ;
   - ne jamais renommer les identifiants historiques de badges.
4. Dans `BadgeCatalog.kt`, ajouter le badge sous `COUNTRIES`.
5. Dans `BadgesViewModel.kt`, mapper sa progression dans la section « Pays ».
6. Mettre à jour `PlaceNameResolver` seulement si le chargement générique des
   assets ne suffit plus.
7. Documenter la source des limites dans le KDoc du résolveur.

## Tester

Ajouter ou adapter les tests suivants :

- chargement du nouveau total de subdivisions ;
- une ville intérieure représentative par subdivision ;
- capitale nationale et cas d’enclave/exclave ou de `MultiPolygon` ;
- au moins un point juste hors du pays ;
- code pays et libellé attendus ;
- badge pays débloqué une seule fois ;
- progression « Explorateur » augmentée par les régions du nouveau pays ;
- progression du badge « France » inchangée par les observations étrangères ;
- résolveur absent : tous les badges géographiques restent indisponibles.

Exécuter au minimum :

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.florapin.app.geo.RegionResolverTest" `
  --tests "com.florapin.app.badges.BadgeCalculatorTest" `
  --tests "com.florapin.app.profile.BadgesViewModelTest"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Vérifier enfin que l’APK contient tous les assets `regions-*.geojson`.

## Finaliser

1. Mettre à jour `CHANGELOG.md` sous « Non publié ».
2. Mettre à jour `CHANGELOG_SIMPLE.md` sous « En préparation » puisque la prise
   en charge d’un pays et son badge sont visibles.
3. Exécuter `git diff --check` sur les fichiers modifiés.
4. Résumer le pays, le niveau administratif, la source, le nombre de
   subdivisions, le badge et les validations réalisées.
