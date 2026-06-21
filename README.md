# 🌸 FloraPin

FloraPin est une application Android (Kotlin / Jetpack Compose) pour **capturer
des photos de fleurs géolocalisées**, les revoir dans une galerie et sur une
carte, puis — via un backend optionnel — **les sauvegarder dans le cloud et les
partager entre amis**.

L'app fonctionne **100 % hors-ligne** pour le cœur (capture, galerie, carte
locale) ; le backend ajoute comptes, stockage cloud, partage et identification.

## Fonctionnalités

- 📷 Capture photo (CameraX) avec position GPS et précision
- 🖼️ Galerie locale + écran détail (photo, coordonnées, mini-carte, notes)
- 🗺️ Carte des fleurs (MapLibre + tuiles MapTiler/OSM), marqueurs + clustering,
  filtre par période
- ☁️ Backend : comptes (JWT), upload cloud (MinIO), synchronisation, partage
  configurable (toutes mes fleurs / une fleur, avec ou sans GPS)
- 👥 Amis, feed, notifications in-app
- 🌿 Identification d'espèce (Pl@ntNet) + identification collaborative entre amis
- 🏷️ Espèce & tags sur les fleurs, recherche

## Structure du dépôt

```
FloraPin/
├─ app/                  # Application Android (Kotlin + Jetpack Compose)
│  └─ src/main/java/com/florapin/app/
│     ├─ capture/        # CameraX : capture + enregistrement
│     ├─ location/       # FusedLocationProvider (GPS)
│     ├─ data/           # Room (FlowerEntity, DAO, repository)
│     ├─ gallery/        # Galerie (liste + ViewModel)
│     ├─ detail/         # Écran détail d'une fleur
│     ├─ map/            # Carte MapLibre, clustering, filtres
│     ├─ permission/     # Permissions runtime
│     ├─ navigation/     # Graphe de navigation
│     └─ ui/theme/       # Thème Compose
├─ backend/              # API NestJS (TypeScript)
│  ├─ src/               # modules auth, users, flowers, friendships,
│  │                     #   shares, feed, notifications, identification,
│  │                     #   proposals, sync, storage
│  ├─ db/schema.sql      # schéma PostgreSQL + PostGIS de référence
│  ├─ docs/API.md        # contrats REST
│  └─ ARCHITECTURE.md    # architecture backend
└─ .github/workflows/    # CI (build/test Android)
```

## Prérequis

| Outil | Version | Pour |
|-------|---------|------|
| JDK   | 17      | Build Android (Gradle) |
| Android SDK | API 35 (min 26) | App |
| Node.js | ≥ 20 (testé 22) | Backend |
| Docker | récent | Postgres/PostGIS + MinIO (backend) |

## Lancer l'application Android

```bash
# Configurer l'emplacement du SDK (une fois)
echo "sdk.dir=/chemin/vers/Android/Sdk" > local.properties

# (Optionnel) carte : clé MapTiler gratuite
echo "MAPTILER_API_KEY=VOTRE_CLE" >> local.properties

# Build + tests + APK debug
./gradlew testDebugUnitTest assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

Ouvrir le projet dans Android Studio pour lancer sur un émulateur/appareil.
Sans clé MapTiler, l'app fonctionne mais l'écran carte affiche un message d'aide.

## Lancer le backend

```bash
cd backend
cp .env.example .env        # puis adapter les secrets
npm install
npm run start:dev           # API sur http://localhost:3000
```

- Doc interactive (Swagger) : `http://localhost:3000/api/docs`
- Base API : `http://localhost:3000/api/v1`
- Tests : `npm test`

Le backend a besoin de **PostgreSQL + PostGIS** et **MinIO**. Pour le dev sans
MinIO, mettre `STORAGE_DRIVER=stub` dans `.env`. Le schéma de référence est dans
`backend/db/schema.sql` (en prod : migrations TypeORM).

## Stack technique

- **App** : Kotlin, Jetpack Compose, CameraX, Room, MapLibre GL, Coil,
  Navigation Compose, Play Services Location
- **Backend** : NestJS, TypeORM, PostgreSQL + PostGIS, MinIO (S3),
  JWT + refresh tokens, Swagger
- **CI** : GitHub Actions (lint + tests + assembleDebug)

Détails d'architecture : [`backend/ARCHITECTURE.md`](backend/ARCHITECTURE.md).
Contribuer : voir [`CONTRIBUTING.md`](CONTRIBUTING.md).
