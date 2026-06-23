# API REST FloraPin (NODE-27)

> Conception des endpoints REST et des contrats. Schéma DB de référence :
> `backend/db/schema.sql`. Architecture : `backend/ARCHITECTURE.md`.

## Conventions

- Base URL : `https://<host>/api/v1`.
- Format : JSON ; dates en **ISO 8601 UTC** ; identifiants en **UUID**.
- Auth : header `Authorization: Bearer <accessToken>` (sauf `/auth/*` publics).
- Erreurs : `{ "statusCode", "message", "error" }` (format NestJS), codes HTTP
  standard (400 validation, 401 non authentifié, 403 interdit, 404 absent,
  409 conflit).
- Validation des entrées via DTO `class-validator`.

### Format des métadonnées GPS

| Champ        | Type   | Détail |
|--------------|--------|--------|
| `latitude`   | number | degrés décimaux, WGS84 (SRID 4326), -90..90 |
| `longitude`  | number | degrés décimaux, WGS84, -180..180 |
| `accuracyM`  | number | précision horizontale en mètres (≥ 0) |

`latitude`/`longitude` sont **optionnels et liés** (les deux, ou aucun). Stockés
en `geography(Point,4326)` ; restitués sous forme lat/long + `accuracyM`.

## Auth (`/auth`)

| Méthode | Chemin            | Corps                                   | Réponse |
|---------|-------------------|-----------------------------------------|---------|
| POST    | `/auth/register`  | `{ email, password, displayName }`      | `201 { user, accessToken, refreshToken }` |
| POST    | `/auth/login`     | `{ email, password }`                   | `200 { user, accessToken, refreshToken }` |
| POST    | `/auth/refresh`   | `{ refreshToken }`                       | `200 { accessToken, refreshToken }` (rotation) |
| POST    | `/auth/logout`    | `{ refreshToken }`                       | `204` (révoque le refresh) |

- `accessToken` : JWT court (~15 min). `refreshToken` : opaque, long, **rotaté**
  à chaque refresh (l'ancien est révoqué). Détails en NODE-17.

## Utilisateurs (`/users`)

| Méthode | Chemin        | Description |
|---------|---------------|-------------|
| GET     | `/users/me`   | Profil courant |
| PATCH   | `/users/me`   | `{ displayName? }` |
| GET     | `/users?query=` | Recherche par email/nom (pour ajouter un ami) — résultats limités |

`User` = `{ id, email, displayName, createdAt }` (jamais le `passwordHash`).

## Fleurs (`/flowers`)

### Création (upload en 2 temps)

`POST /flowers`
```json
{ "takenAt": "2026-06-21T09:14:00Z", "latitude": 48.8584, "longitude": 2.2945,
  "accuracyM": 5.0, "notes": "", "visibility": "private" }
```
Réponse `201` :
```json
{ "flower": { "...": "Flower" },
  "upload": { "url": "https://minio/...signed", "method": "PUT", "expiresIn": 600 } }
```
Le client **PUT** ensuite le binaire image directement sur `upload.url` (MinIO).
La clé objet (`imageKey`) est générée côté serveur (ex. `flowers/{ownerId}/{uuid}.jpg`).

### Lecture / liste

| Méthode | Chemin                 | Description |
|---------|------------------------|-------------|
| GET     | `/flowers/:id`         | Une fleur (si visible par l'appelant) + `imageUrl` présignée GET |
| GET     | `/flowers/:id/image-url` | (Re)génère une URL GET présignée |
| GET     | `/flowers`             | Liste filtrable (voir paramètres) |

Paramètres de `GET /flowers` (combinables) :

| Param     | Exemple                         | Effet |
|-----------|---------------------------------|-------|
| `mine`    | `mine=true`                     | uniquement mes fleurs |
| `bbox`    | `bbox=minLng,minLat,maxLng,maxLat` | intersection rectangle (carte) |
| `lat`,`lng`,`radius` | `lat=48.85&lng=2.29&radius=1000` | `ST_DWithin` (rayon en mètres) |
| `since`   | `since=2026-06-20T00:00:00Z`    | maj depuis (sync) |
| `limit`,`cursor` | `limit=50`               | pagination |

Périmètre de visibilité par défaut : **mes fleurs + celles visibles via amis**
(voir règle ci-dessous). Réponses sans `location` exclues des requêtes géo.

### Modification / suppression

| Méthode | Chemin         | Corps / effet |
|---------|----------------|---------------|
| PATCH   | `/flowers/:id` | `{ notes?, visibility?, takenAt? }` (propriétaire seul) |
| DELETE  | `/flowers/:id` | **soft-delete** (`deleted_at`) — propagé par la sync |

`Flower` =
```json
{ "id", "ownerId", "imageUrl", "latitude", "longitude", "accuracyM",
  "takenAt", "notes", "visibility", "createdAt", "updatedAt" }
```

## Albums (`/albums`)

Regroupement nommé de fleurs (NODE-98). Toutes les routes sont protégées (JWT)
et limitées aux albums du propriétaire.

| Méthode | Chemin                       | Corps / Description |
|---------|------------------------------|---------------------|
| POST    | `/albums`                    | `{ name }` → crée un album |
| GET     | `/albums`                    | Liste mes albums (plus récents d'abord) |
| GET     | `/albums/:id`                | Un album |
| PATCH   | `/albums/:id`                | `{ name }` → renomme |
| DELETE  | `/albums/:id`                | `204` (supprime l'album, pas les fleurs) |
| POST    | `/albums/:id/flowers`        | `{ flowerId }` → rattache une fleur (idempotent) |
| DELETE  | `/albums/:id/flowers/:flowerId` | Retire une fleur de l'album |

`Album` = `{ id, ownerId, name, flowerIds, createdAt }`.

## Amis (`/friendships`)

| Méthode | Chemin                     | Description |
|---------|----------------------------|-------------|
| GET     | `/friendships`             | Mes relations (pending/accepted), entrantes et sortantes |
| POST    | `/friendships`             | `{ addresseeId }` → crée une demande (`pending`) |
| POST    | `/friendships/:id/accept`  | Accepte une demande reçue → `accepted` |
| DELETE  | `/friendships/:id`         | Refuse / annule / supprime la relation |

Unicité `(requester, addressee)` ; pas d'auto-amitié (contraintes en DB).

## Partage (`/flowers/:id/share`, `/shared`)

Deux mécanismes complémentaires :
1. `flowers.visibility = 'friends'` : visible par tous les amis acceptés.
2. Partage **explicite** à un ami précis.

| Méthode | Chemin                          | Description |
|---------|---------------------------------|-------------|
| POST    | `/shares`                       | `{ friendId, scope, flowerId?, albumId?, includeGps? }` — `scope` ∈ `all`/`flower`/`album` |
| GET     | `/shares`                       | Mes partages émis |
| DELETE  | `/shares/:id`                   | Révoque un partage |
| GET     | `/shared`                       | Fleurs partagées avec moi |

`scope='flower'` requiert `flowerId` ; `scope='album'` requiert `albumId`
(les fleurs de l'album sont alors résolues, NODE-101).

### Règle de visibilité (côté serveur)

Une fleur `F` est visible par l'utilisateur `U` si :
`F.owner = U`
**ou** (`F.visibility='friends'` **et** amitié `accepted` entre `U` et `F.owner`)
**ou** il existe `flower_shares(F, U)`.
Toujours : `F.deleted_at IS NULL`.

## Synchronisation (`/sync`) — NODE-19

| Méthode | Chemin                | Description |
|---------|-----------------------|-------------|
| GET     | `/sync?since=<ISO>`   | Delta depuis `since` |
| POST    | `/sync/flowers`       | Push d'un lot de captures locales (création/maj) |

Réponse `GET /sync` :
```json
{ "serverTime": "2026-06-21T09:20:00Z",
  "flowers": [ { "...": "Flower (créées/maj)" } ],
  "deletedIds": [ "uuid", "uuid" ] }
```
Conflits : **last-write-wins** sur `updated_at` au POC. Le client renvoie ensuite
`since = serverTime` au cycle suivant.

## Récapitulatif des codes

- `201` création, `200` succès, `204` sans contenu.
- `400` validation, `401` token absent/expiré, `403` non autorisé sur la
  ressource, `404` introuvable/masqué, `409` conflit (ex. amitié existante).
