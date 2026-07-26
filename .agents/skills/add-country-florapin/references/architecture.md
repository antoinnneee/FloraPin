# Architecture géographique FloraPin

## Contrat GeoJSON embarqué

Chaque asset `app/src/main/assets/regions-<pays>.geojson` est un
`FeatureCollection` WGS84 contenant des `Polygon` ou `MultiPolygon`.

Propriétés obligatoires de chaque feature :

| Propriété | Type | Rôle |
|---|---|---|
| `countryCode` | chaîne | ISO 3166-1 alpha-2 |
| `code` | chaîne | identifiant stable de la subdivision |
| `nom` | chaîne | libellé français affiché |
| `outreMer` | booléen | vrai uniquement pour les régions françaises ultramarines |

Dans chaque objet `geometry`, écrire `type` avant `coordinates` : le parseur
Moshi de `RegionResolver` lit le GeoJSON en streaming et dépend actuellement de
cet ordre. Les positions suivent toujours `[longitude, latitude]`.

## Fichiers à modifier

| Fichier | Responsabilité |
|---|---|
| `geo/RegionResolver.kt` | registre des assets, codes pays, parsing et point-in-polygon |
| `badges/BadgeCalculator.kt` | régions uniques globales, progressions par pays et déblocages |
| `badges/BadgeCatalog.kt` | carte du badge dans la section `COUNTRIES` |
| `profile/BadgesViewModel.kt` | passage de la progression à l’état UI |
| `geo/RegionResolverTest.kt` | données réelles, villes connues et rejets |
| `badges/BadgeCalculatorTest.kt` | isolation entre pays et paliers |
| `profile/BadgesViewModelTest.kt` | section « Pays », disponibilité et affichage |

## Invariants de badges

- L’identifiant historique `explorateur` appartient au badge « France » afin de
  conserver les paliers déjà stockés.
- Le nouvel « Explorateur » utilise `explorateur_regions` et compte les clés
  uniques `<code-pays>:<code-région>`.
- Ses seuils sont 1, 5, 10, 15 et 20 régions, tous pays confondus.
- Chaque badge pays compte ses subdivisions distinctes et possède des paliers
  cumulatifs. Le premier reste `1` pour préserver le déblocage dès la première
  visite ; le dernier correspond au nombre officiel total de subdivisions.
- Un badge pays ne dépasse jamais cinq étoiles. Le badge France suit lui aussi
  cette convention avec `1/5/10/15/18`.
- Pour un petit pays (jusqu'à quatre subdivisions), utiliser chaque entier. Pour
  un pays plus grand, viser quatre ou cinq étapes lisibles et inclure toujours la
  couverture complète (par exemple `1/5/10/15/20` pour 20 régions).
- Une observation étrangère ne modifie jamais `franceRegionCount`.
- Si le résolveur ne charge pas les assets, les progressions géographiques
  valent `BadgeCalculator.UNAVAILABLE`.

## Critères de source

Préférer dans cet ordre :

1. portail national officiel des limites administratives ;
2. portail open data gouvernemental ;
3. organisme cartographique national ;
4. source communautaire seulement si aucune source primaire exploitable
   n’existe, en signalant clairement cette limite.

Relever l’organisme producteur, le millésime, la licence, le système de
coordonnées, le niveau administratif et le nombre attendu de subdivisions.
