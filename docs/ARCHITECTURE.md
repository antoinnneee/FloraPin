# Architecture technique — FloraPin

Vue d'ensemble technique du système complet (application Android + backend +
déploiement). Documents liés :
[backend/ARCHITECTURE.md](../backend/ARCHITECTURE.md) (détail backend),
[backend/docs/API.md](../backend/docs/API.md) (contrats REST),
[backend/db/schema.sql](../backend/db/schema.sql) (schéma),
[deploy/README.md](../deploy/README.md) (déploiement).

## 1. Vue système

```
┌────────────────────────────┐         HTTPS (api/v1)        ┌──────────────┐
│  App Android (Compose)      │ ───────────────────────────▶ │ Caddy (proxy)│
│  - capture (CameraX)        │ ◀─────────────────────────── │  TLS auto    │
│  - Room (cache local)       │                              └──────┬───────┘
│  - carte (MapLibre)         │       URLs présignées               │
│            │                │  ── PUT/GET binaires images ─┐      ▼
└────────────┼───────────────┘                              │  ┌──────────┐
             │                                               └─▶│  MinIO   │
             │  tuiles                                          └──────────┘
             ▼                                              ┌──────────────┐
        MapTiler/OSM                              API NestJS │ PostgreSQL   │
        (tuiles carte)                            ──────────▶│  + PostGIS   │
                                                            └──────────────┘
```

- **Cœur hors-ligne** : capture, galerie et carte fonctionnent sans réseau
  (données dans Room + fichiers locaux).
- **Couche cloud** (optionnelle) : comptes, sauvegarde, partage, feed,
  identification — via l'API.
- **Images** : jamais servies par l'API ; transit direct client↔MinIO par URLs
  présignées.

## 2. Stack

| Domaine | Technologies |
|---------|--------------|
| App     | Kotlin, Jetpack Compose, CameraX, Room, MapLibre GL, Coil, Navigation Compose, Play Services Location |
| Backend | NestJS (TypeScript), TypeORM, PostgreSQL + PostGIS, MinIO (S3), JWT + refresh, Swagger |
| Infra   | Docker Compose, Caddy (HTTPS), VPS auto-hébergé |
| CI      | GitHub Actions (lint + tests + assembleDebug) |

## 3. Architecture de l'application Android

Organisation par fonctionnalité sous `com.florapin.app` :

- `capture/` — `CameraScreen` (preview + obturateur), `PhotoStorage` (JPEG dans
  le stockage privé), `CaptureFlow` (permission → capture → GPS → persistance).
- `location/` — `LocationProvider` (FusedLocationProvider, coroutine), `GeoPoint`.
- `data/` — `FlowerEntity`, `FlowerDao`, `FloraDatabase` (Room), `FlowerRepository`.
- `gallery/`, `detail/` — listes/écran détail (ViewModels + Flows).
- `map/` — `MapScreen` (MapLibre), `MapClustering` (source GeoJSON clusterisée),
  `MapViewModel`, filtres.
- `permission/`, `navigation/`, `ui/theme/` — transverses.

Patron : UI Compose → ViewModel (StateFlow) → Repository → Room. La position est
récupérée à la capture et stockée avec la fleur.

## 4. Architecture du backend

API modulaire NestJS (un module par domaine) : `auth`, `users`, `flowers`,
`friendships`, `shares`, `feed`, `notifications`, `identification`, `proposals`,
`sync`, `storage`. Détail : [backend/ARCHITECTURE.md](../backend/ARCHITECTURE.md).

Transverses : `ConfigModule` (env), `TypeOrmModule` (Postgres/PostGIS),
`ValidationPipe` global, `JwtAuthGuard` + `JwtStrategy`.

## 5. Modèle de données (résumé)

Schéma complet : [backend/db/schema.sql](../backend/db/schema.sql).

| Table | Rôle |
|-------|------|
| `users` | comptes (email, hash, display_name) |
| `refresh_tokens` | refresh tokens hashés (rotation/révocation) |
| `flowers` | image_key, `geography(Point,4326)`, accuracy, taken_at, notes, species, tags, visibility, soft-delete |
| `friendships` | relations (pending/accepted/blocked) |
| `shares` | partages (scope all/flower, include_gps) |
| `species_proposals` | identifications collaboratives |
| `notifications` | notifications in-app |

Index clés : GiST sur `flowers.location` (requêtes géo), GIN sur `flowers.tags`,
`updated_at` (sync).

## 6. Flux de données principaux

1. **Capture (hors-ligne)** : photo CameraX → fichier privé + position GPS →
   ligne Room (`FlowerRepository.saveCapture`).
2. **Authentification** : `register`/`login` → access JWT (court) + refresh
   (rotaté). Le client joint `Authorization: Bearer`.
3. **Upload cloud** : `POST /flowers` (métadonnées) → URL présignée PUT → le
   client envoie le binaire à MinIO. Lecture via URL présignée GET.
4. **Synchronisation** : `GET /sync?since=` (delta créées/maj + ids supprimés) ;
   `POST /sync/flowers` (push par lot). Conflits : last-write-wins (POC).
5. **Partage & feed** : partage configurable (périmètre + GPS) → `GET /shared` /
   `GET /feed` (trié récent→ancien) ; notifications in-app.
6. **Carte** : MapLibre charge les tuiles MapTiler ; les fleurs géolocalisées
   forment une source GeoJSON clusterisée (tap cluster = zoom, tap marqueur =
   détail).
7. **Identification** : `POST /flowers/:id/identify` (Pl@ntNet) ; ou un ami
   propose une espèce (`proposals`) que le propriétaire confirme.

## 7. Sécurité

- HTTPS terminé par Caddy ; l'API n'est pas exposée directement.
- JWT d'accès courts + refresh rotatés (révocables) ; guards par ressource.
- Images privées via URLs présignées expirantes.
- Partage avec option « sans GPS » (protection des spots).
- Secrets en variables d'environnement (`local.properties`, `backend/.env`,
  `deploy/.env`) jamais commités.

## 8. Setup local

- **App** : voir [README](../README.md) — `local.properties` (SDK + clé
  MapTiler), `./gradlew testDebugUnitTest assembleDebug`.
- **Backend** : `cd backend && cp .env.example .env && npm install &&
  npm run start:dev` (Swagger sur `/api/docs`).

## 9. Déploiement

Procédure complète : [deploy/README.md](../deploy/README.md).
Un `docker compose` sur VPS lance API + PostgreSQL/PostGIS + MinIO + Caddy
(HTTPS automatique). Le schéma SQL est appliqué au premier démarrage ; les
données persistent sur des volumes Docker. Sauvegarde POC : `pg_dump` manuel
(stratégie robuste reportée — NODE-30).
