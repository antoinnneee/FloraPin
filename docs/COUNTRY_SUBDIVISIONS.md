# Subdivisions géographiques par pays

Ce document recense les subdivisions retenues par FloraPin pour les badges
géographiques. Le niveau « région » sert à la progression des badges, afin de
conserver un nombre raisonnable d'objectifs. Le niveau « département » peut
servir à une localisation plus précise, sans nécessairement créer un badge.

La France sert de référence avec **18 régions** et **101 départements**.

## Choix validés

| Pays | Niveau des badges (« région ») | Nombre | Niveau détaillé (« département ») | Nombre |
|---|---|---:|---|---:|
| France | Région | 18 | Département | 101 |
| Suisse | Canton | 26 | District | 144 |
| Belgique | Région | 3 | Province | 10 |
| Angleterre | Région | 9 | Comté cérémoniel | 48 |
| Irlande | Province historique | 4 | Comté traditionnel | 26 |
| Espagne | Communauté ou ville autonome | 19 | Province | 50 |
| Italie | Région | 20 | Territoire de niveau provincial | 110 |
| Japon | Grande région traditionnelle | 8 | Préfecture | 47 |

## Précisions par pays

### Suisse

Les **26 cantons** sont utilisés comme équivalent des régions françaises. Les
**144 districts** constituent le niveau détaillé le plus proche des
départements, mais ils n'ont pas les mêmes compétences partout et certains
cantons ne les emploient pas comme échelon administratif.

Les cantons sont pris en charge hors ligne à partir de
[swissBOUNDARIES3D](https://opendata.swiss/fr/dataset/swissboundaries3d-kantonsgrenzen)
de swisstopo, millésime 2026 en WGS84. Les frontières communes sont simplifiées
topologiquement avec Mapshaper en conservant 30 % des sommets, puis normalisées
avec les abréviations cantonales et les noms français. Les conditions
opendata.swiss autorisent l'utilisation libre et recommandent l'indication de la
source, conservée ici et dans la documentation du résolveur.

### Belgique

Les badges suivent les **3 régions** : Région flamande, Région wallonne et
Région de Bruxelles-Capitale. Le niveau détaillé comprend **10 provinces**.
Bruxelles-Capitale n'appartient à aucune province.

Les limites embarquées proviennent d'[AdminVector NGI-IGN, millésime
2025](https://www.odwb.be/explore/dataset/regionsgeweste-belgium/). Elles sont
livrées en WGS84 et déjà généralisées par le diffuseur.

### Angleterre

Les badges suivent les **9 régions anglaises**, plutôt que les **48 comtés
cérémoniels**, afin de limiter leur nombre. Les comtés cérémoniels restent le
niveau géographique le plus proche des départements français, même s'ils ne
forment pas un échelon administratif uniforme.

La couche embarquée est issue des [Regions (December 2024) Boundaries EN
BGC](https://www.data.gov.uk/dataset/c4e720d2-f4fc-4076-9ec1-20e225122741/regions-december-2024-boundaries-en-bgc)
de l'Office for National Statistics. Sa généralisation 20 mètres a été
simplifiée topologiquement avec Mapshaper 0.7.48 (`weighted 25%`,
`keep-shapes`, `clean`) : les neuf régions sont conservées et l'asset final
pèse 775 212 octets.

### Irlande

Les badges suivent les **4 provinces historiques** :

- Connacht ;
- Leinster ;
- Munster ;
- Ulster.

Ce choix privilégie des territoires connus et identitaires plutôt que les
3 régions administratives et statistiques NUTS 2. Pour FloraPin, l'Ulster
irlandais couvre uniquement les **3 comtés situés en République d'Irlande** :
Cavan, Donegal et Monaghan.

Les **26 comtés traditionnels** forment le niveau détaillé comparable aux
départements français. Ils sont distincts des 31 collectivités locales qui
assurent actuellement l'administration du territoire.

Les limites proviennent de [Province Boundaries Generalised 50m
2015](https://data.gov.ie/dataset/province-boundaries-generalised-50m-national-administrative-boundaries-20151/resource/d4577389-e161-48bc-92b2-f1cb14a2f0f1),
publié par Tailte Éireann sous licence CC BY 4.0. La source EPSG:2157 a été
explicitement reprojetée en WGS84 ; les quatre provinces occupent 1 245 477
octets.

### Espagne

Les badges suivent les **17 communautés autonomes**, auxquelles s'ajoutent les
**2 villes autonomes** de Ceuta et Melilla, soit **19 territoires** couvrant
l'ensemble du territoire espagnol.

Les **50 provinces** forment le niveau détaillé comparable aux départements
français. Ceuta et Melilla n'appartiennent à aucune province.

Les géométries sont extraites de l'[API OGC Features des unités
administratives](https://api-features.ign.es/openapi?f=html) de l'Instituto
Geográfico Nacional, au niveau `2ndOrder`, sous [licence CC BY
4.0](https://centrodedescargas.cnig.es/CentroDescargas/detalleArchivo?sec=9000029).
L'entrée technique « territoires non associés » a été écartée pour conserver
les 19 collectivités attendues. La couche de 156,3 Mo a été simplifiée
topologiquement avec Mapshaper 0.7.48 à 6 % (`keep-shapes`, `clean`) ; l'asset
WGS84 final pèse 1 459 619 octets sans perte de subdivision.

### Italie

Les badges suivent les **20 régions italiennes**. Cinq d'entre elles disposent
d'un statut spécial, mais toutes appartiennent au même niveau retenu pour la
progression.

Le niveau détaillé comprend **110 territoires de niveau provincial** depuis la
réforme territoriale de la Sardaigne entrée en vigueur en 2026. Selon les
régions, ces territoires peuvent être des provinces, des villes métropolitaines,
des libres consortiums municipaux ou d'autres unités territoriales
supracommunales équivalentes.

Les limites embarquées proviennent des [confins administratifs Istat au
1er janvier 2026](https://www.istat.it/notizia/confini-delle-unita-amministrative-a-fini-statistici-al-1-gennaio-2018-2/).
La couche généralisée officielle des régions a été reprojetée de WGS84 / UTM
zone 32N vers WGS84 puis normalisée ; les 20 régions occupent 1 284 915 octets.

### Japon

Les badges suivent les **8 grandes régions traditionnelles** :

- Hokkaidō ;
- Tōhoku ;
- Kantō ;
- Chūbu ;
- Kansai ;
- Chūgoku ;
- Shikoku ;
- Kyūshū–Okinawa.

Ces régions géographiques et culturelles n'ont pas d'administration propre,
mais elles constituent un découpage largement reconnu et permettent de limiter
la progression à **8 badges**. Le nom « Kansai » est retenu dans l'interface
plutôt que « Kinki », et « Kyūshū–Okinawa » rend explicite l'inclusion
d'Okinawa.

Les **47 préfectures** forment le niveau administratif détaillé comparable aux
départements français. Hokkaidō présente la particularité d'être à la fois une
grande région et une préfecture.

La géométrie repose sur le [Global Map Japan 2.1 du Geospatial Information
Authority of Japan](https://www.gsi.go.jp/kankyochiri/gm_jpn.html), millésime
2015. Les 2 914 polygones municipaux WGS84 ont été regroupés par préfecture dans
les huit grandes régions, puis dissous topologiquement. L'asset final pèse
1 197 187 octets.
