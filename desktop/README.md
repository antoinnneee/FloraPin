# FloraPin — compagnon Windows

Application de bureau qui accompagne l'app mobile : **consulter ses photos,
gérer ses albums, récupérer ses fichiers, identifier les fleurs de ses amis et
tout revoir sur une carte**, au clavier et à la souris.

La prise de photo reste sur mobile — c'est la seule fonctionnalité absente. Le
compagnon est un client de l'API REST : il ne tient aucune base locale et
n'entre donc jamais en conflit de synchronisation avec le téléphone.

## Démarrer

```bash
./gradlew :desktop:run
```

Le compte se crée depuis l'application mobile ; le compagnon ne fait que s'y
connecter.

## Construire une version distribuable

```bash
# Dossier exécutable (aucun outil supplémentaire requis)
./gradlew :desktop:createDistributable
# → desktop/build/compose/binaries/main/app/FloraPin/FloraPin.exe

# Installeur MSI (nécessite WiX Toolset 3.x dans le PATH)
./gradlew :desktop:packageMsi
```

## Configuration

Les valeurs de build proviennent de `local.properties` (ou des variables
d'environnement `API_BASE_URL` / `MAPTILER_API_KEY`), exactement comme pour le
module Android.

À l'exécution, l'utilisateur peut surcharger sans reconstruire en créant
`%LOCALAPPDATA%\FloraPin\config.properties` :

```properties
apiBaseUrl=https://florapin.pattounecorp.ovh/api/v1/
maptilerApiKey=VOTRE_CLE
```

Le même dossier contient la session (`session.properties`), les préférences
d'affichage et le cache d'images.

## Choix d'architecture

**Compose Multiplatform plutôt qu'une réécriture.** Le module partage
directement les sources `network/dto`, `network/api`, `network/auth` et
`ui/theme` de `:app` (voir `sharedSourceDirs` dans `build.gradle.kts`) : du
Kotlin sans dépendance Android. Les contrats d'API et la charte graphique ne
peuvent donc pas diverger entre les deux clients — une évolution du backend se
répercute sur les deux à la compilation.

Trois éléments du module Android sont écartés car spécifiques à la plateforme,
et réimplémentés ici : `EncryptedTokenStore` (→ `FileTokenStore`), `Theme.kt` et
`Type.kt` (→ `DesktopTheme.kt`).

**Carte dessinée en Compose plutôt que MapLibre.** Le SDK MapLibre est propre à
Android. Plutôt que d'embarquer un moteur natif sur Windows — avec ce que cela
implique à l'installation — le compagnon dessine lui-même les tuiles raster
MapTiler (`map/TileMap.kt`). C'est suffisant pour une carte de consultation et
cela permet des interactions vraiment pensées pour la souris.

**Pas de base locale.** L'app mobile a Room et une synchronisation hors-ligne
parce qu'on photographie sur le terrain. Un poste fixe est connecté : le
compagnon lit l'API et met les images en cache disque, ce qui suffit à rendre la
navigation instantanée sans dupliquer la logique de résolution de conflits.

### Stockage des jetons

Windows n'offre pas d'équivalent à `EncryptedSharedPreferences` accessible
depuis la JVM sans dépendance native. Les jetons sont donc dans un fichier du
profil utilisateur, protégé par une ACL réduite au compte courant — pas de
chiffrement en trompe-l'œil dont la clé serait stockée à côté. La session reste
révocable : « Se déconnecter » l'invalide côté serveur.

## Commandes

| Raccourci | Action |
| --- | --- |
| `Ctrl+1` … `Ctrl+6` | Changer de section |
| `Ctrl+A` | Tout sélectionner |
| `Ctrl+F` | Rechercher |
| `Ctrl+E` | Récupérer la sélection |
| `Ctrl` `+` / `-` | Agrandir ou réduire les vignettes |
| `F5` | Actualiser |
| `Suppr` | Supprimer la sélection (avec confirmation) |
| `Échap` | Fermer la visionneuse, ou vider la sélection |
| `←` `→` | Photo précédente / suivante dans la visionneuse |

À la souris : clic pour sélectionner, `Ctrl`+clic pour ajouter, `Maj`+clic pour
étendre, double-clic pour ouvrir, clic droit pour le menu contextuel. Sur la
carte, glisser déplace et la molette zoome sur le point survolé.

## Tests

```bash
./gradlew :desktop:test
```

Les tests couvrent la logique vérifiable sans interface : sélection multiple
(`SelectionTest`), projection Web Mercator (`MapMathTest`), nommage des fichiers
exportés (`ExportNamingTest`) et clé de cache des images (`ImageCacheKeyTest`) —
cette dernière étant subtile, les URLs de photos étant présignées et donc
différentes à chaque réponse de l'API pour un même fichier.

## À venir

Import de photos existantes, non prises avec l'application. Le reste de la
chaîne (upload multipart des variantes WebP) est déjà exposé par l'API et
partagé par ce module.
