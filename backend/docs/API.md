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
| POST    | `/auth/forgot-password` | `{ email }`                        | `200 { message }` **systématique** (anti-énumération) ; envoie un lien si le compte existe |
| POST    | `/auth/reset-password`  | `{ token, newPassword }`           | `200 { message }` ; `401` si token invalide/expiré/déjà utilisé |
| POST    | `/auth/email/verification` | — (JWT)                         | `200 { message }` ; envoie/renvoie un lien de vérification (sans effet si déjà vérifié) |
| POST    | `/auth/email/verify`    | `{ token }`                        | `200 { message }` ; `401` si token invalide/expiré/déjà utilisé |

- `accessToken` : JWT court (~15 min). `refreshToken` : opaque, long, **rotaté**
  à chaque refresh (l'ancien est révoqué). Détails en NODE-17.
- **Reset mot de passe** (NODE-116) : `forgot-password` crée un token à usage
  unique et durée limitée (`PASSWORD_RESET_TTL_MIN`, défaut 60 min), hashé en
  base, envoyé par email (lien `${APP_BASE_URL}/reset?token=...`). `reset-password`
  re-hashe le mot de passe et **révoque tous les refresh tokens** (déconnexion
  globale).

## Utilisateurs (`/users`)

| Méthode | Chemin        | Description |
|---------|---------------|-------------|
| GET     | `/users/me`   | Profil courant |
| PATCH   | `/users/me/email` | `{ email }` → user. **Autorisé uniquement tant que `emailVerified=false`** (NODE-117) ; `403` sinon, `409` si l'email est déjà pris. Réinitialise les tokens de vérification en cours. |
| DELETE  | `/users/me`   | `{ password }` → **204**. Effacement RGPD : supprime le compte et **toutes** ses données (fleurs, photos, albums, amitiés, partages, propositions, notifications, jetons d'appareil) ; purge aussi les objets image (MinIO). Re-authentification par mot de passe ; `401` si incorrect. Irréversible. |

`User` = `{ id, email, displayName, emailVerified, createdAt }` (jamais le `passwordHash`).

## Fleurs (`/flowers`)

### Création (upload en 2 temps)

`POST /flowers`
```json
{ "takenAt": "2026-06-21T09:14:00Z", "latitude": 48.8584, "longitude": 2.2945,
  "accuracyM": 5.0, "notes": "", "visibility": "private",
  "feedIncludeGps": true, "species": "Rosa canina", "tags": ["haie"] }
```
Seul `takenAt` est requis. `latitude`/`longitude` sont **liés** (les deux ou aucun).
`visibility` ∈ `private` (défaut) / `friends`. `feedIncludeGps` (défaut `true`)
diffuse ou masque le GPS dans le feed des amis (NODE-136). `species`, `tags`
(≤ 20) sont optionnels.

Réponse `201` :
```json
{ "flower": { "...": "Flower" },
  "upload": { "url": "https://minio/...signed", "method": "PUT", "expiresIn": 600 } }
```
La clé objet (`imageKey`) est générée côté serveur (ex. `flowers/{ownerId}/{uuid}.jpg`).
Le binaire image se téléverse ensuite de **deux** façons :

| Méthode | Chemin              | Description |
|---------|---------------------|-------------|
| PUT     | `upload.url`        | Upload présigné **direct** sur MinIO (via `upload.url` renvoyé à la création). |
| POST    | `/flowers/:id/image`| Upload **multipart** (champ `file`) transitant par l'API, qui **réencode en WebP** (pleine résolution + miniature) avant stockage. Voir limites d'upload plus bas. |

### Lecture / liste

| Méthode | Chemin                 | Description |
|---------|------------------------|-------------|
| GET     | `/flowers/:id`         | Une fleur **du propriétaire courant** (owner-only ; `404` sinon) + `imageUrl` présignée GET |
| GET     | `/flowers/:id/image-url` | (Re)génère une URL GET présignée |
| GET     | `/flowers`             | Liste **mes** fleurs (owner-only, plus récentes d'abord), filtrable |

`GET /flowers` ne renvoie **que les fleurs du propriétaire courant** (les fleurs
d'amis passent par le feed / les partages). Paramètres de filtre (combinables) :

| Param     | Exemple                | Effet |
|-----------|------------------------|-------|
| `species` | `species=Rosa`         | filtre par nom d'espèce (sous-chaîne, insensible à la casse) |
| `tag`     | `tag=haie`             | filtre par étiquette exacte |

### Modification / suppression

| Méthode | Chemin         | Corps / effet |
|---------|----------------|---------------|
| PATCH   | `/flowers/:id` | `{ notes?, visibility?, takenAt? }` (propriétaire seul) |
| DELETE  | `/flowers/:id` | **soft-delete** (`deleted_at`) — propagé par la sync |

`Flower` =
```json
{ "id", "ownerId", "imageUrl", "latitude", "longitude", "accuracyM",
  "takenAt", "notes", "visibility", "photos", "createdAt", "updatedAt" }
```
`imageUrl` = URL présignée de la photo de couverture. `photos` = liste
`[{ id, url, position, isCover }]` (NODE-104).

### Photos d'une fleur (NODE-104)

| Méthode | Chemin                              | Description |
|---------|-------------------------------------|-------------|
| POST    | `/flowers/:id/photos`               | Ajoute une photo → `{ photo, upload }` (PUT présigné direct) |
| POST    | `/flowers/:id/photos/:photoId/image`| Upload **multipart** (champ `file`) du binaire, réencodé en WebP par l'API |
| DELETE  | `/flowers/:id/photos/:photoId`      | Retire une photo (promeut une couverture si besoin) |
| PATCH   | `/flowers/:id/photos/order`         | `{ photoIds: [...] }` → réordonne |
| PATCH   | `/flowers/:id/photos/:photoId/cover`| Définit la photo de couverture |

## Albums (`/albums`)

Regroupement nommé de fleurs (NODE-98). Toutes les routes sont protégées (JWT)
et limitées aux albums du propriétaire.

| Méthode | Chemin                       | Corps / Description |
|---------|------------------------------|---------------------|
| POST    | `/albums`                    | `{ name, clientId? }` → crée un album. `clientId` (UUID) rend la création **idempotente** : un même `clientId` renvoie l'album existant au lieu d'un doublon (anti-doublon de sync). |
| GET     | `/albums`                    | Liste mes albums (plus récents d'abord) |
| GET     | `/albums/:id`                | Un album |
| PATCH   | `/albums/:id`                | `{ name }` → renomme |
| DELETE  | `/albums/:id`                | `204` (supprime l'album, pas les fleurs) |
| POST    | `/albums/:id/flowers`        | `{ flowerId }` → rattache une fleur (idempotent) |
| DELETE  | `/albums/:id/flowers/:flowerId` | Retire une fleur de l'album |

`Album` = `{ id, ownerId, name, clientId, flowerIds, createdAt }`.

## Amis (`/friendships`)

| Méthode | Chemin                     | Description |
|---------|----------------------------|-------------|
| GET     | `/friendships`             | Mes relations (pending/accepted), entrantes et sortantes |
| POST    | `/friendships`             | `{ email }` → invite un utilisateur par email, crée une demande (`pending`) |
| POST    | `/friendships/:id/accept`  | Accepte une demande reçue → `accepted` |
| DELETE  | `/friendships/:id`         | Refuse / annule / supprime la relation |

Unicité `(requester, addressee)` ; pas d'auto-amitié (contraintes en DB).

**Anti-énumération** (I3) : si l'email ne correspond à aucun compte, `POST
/friendships` renvoie une réponse **générique** de même forme que le cas nominal
(demande `pending` fantôme, aucune ligne ni notification créée) — **pas de `404`**,
afin de ne pas révéler l'existence d'un compte.

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
**ou** il existe un partage `shares(F, U)`.
Toujours : `F.deleted_at IS NULL`.

## Synchronisation (`/sync`) — NODE-19

| Méthode | Chemin                | Description |
|---------|-----------------------|-------------|
| GET     | `/sync?since=<ISO>`   | Delta depuis `since` |
| POST    | `/sync/flowers`       | Push d'un lot (≤ 200) de captures locales — création **idempotente** (dédoublonnée sur `localId`) |

Réponse `GET /sync` :
```json
{ "serverTime": "2026-06-21T09:20:00Z",
  "flowers": [ { "...": "Flower (créées/maj)" } ],
  "deletedIds": [ "uuid", "uuid" ] }
```
Conflits : **last-write-wins** sur `updated_at` au POC. Le client renvoie ensuite
`since = serverTime` au cycle suivant.

`POST /sync/flowers` prend `{ items: [ { ...CreateFlower, localId } ] }` et crée
une fleur par élément, en renvoyant pour chacune le mapping `localId` → fleur
serveur + l'URL présignée d'upload (comme `POST /flowers`). La création est
**idempotente** : `localId` est persisté (`flowers.client_id`, index unique
partiel par propriétaire) et un renvoi du même lot renvoie les fleurs existantes
au lieu de créer des doublons. Il ne s'agit toujours que de **création** : la
mise à jour d'une fleur déjà synchronisée passe par `PATCH /flowers/:id`.

## Feed des amis (`/feed`) — NODE-23/136

| Méthode | Chemin  | Description |
|---------|---------|-------------|
| GET     | `/feed` | Fleurs visibles par moi (partages ciblés + fleurs `visibility='friends'` des amis), dédoublonnées, plus récentes d'abord |

Paramètres : `since` (ISO, ne renvoie que les fleurs créées après), `limit`
(1..200, défaut 50), `sort` ∈ `date` (défaut) / `likes` (par nombre de cœurs,
la date départageant les ex æquo — NODE-139). Réponse : liste de `Flower`.

## Commentaires (`/flowers/:id/comments`) — NODE-141

Fil de discussion sur une fleur : **toute personne qui voit la fleur** peut lire
et commenter. Poster notifie le propriétaire.

| Méthode | Chemin                             | Description |
|---------|------------------------------------|-------------|
| POST    | `/flowers/:id/comments`            | `{ body }` (1..1000 car.) → commentaire |
| GET     | `/flowers/:id/comments`            | Liste chronologique des commentaires |
| DELETE  | `/flowers/:id/comments/:commentId` | `204` — auteur du commentaire **ou** propriétaire de la fleur |

`Comment` = `{ id, flowerId, authoredBy, authorName, body, canDelete, createdAt }`
(`canDelete` : le lecteur courant peut-il supprimer ce commentaire).

## Cœurs (`/flowers/:id/like`) — NODE-139

| Méthode | Chemin               | Description |
|---------|----------------------|-------------|
| POST    | `/flowers/:id/like`  | `204` — pose un cœur (**idempotent**) |
| DELETE  | `/flowers/:id/like`  | `204` — retire le cœur (**idempotent**) |

`likeCount` et `likedByMe` sont exposés dans chaque `Flower`.

## Propositions d'espèce (`/flowers/:id/proposals`)

Un ami propose une espèce pour une fleur partagée ; le propriétaire arbitre.

| Méthode | Chemin                                    | Description |
|---------|-------------------------------------------|-------------|
| POST    | `/flowers/:id/proposals`                  | `{ species }` (2..200 car.) → propose une espèce |
| GET     | `/flowers/:id/proposals`                  | Le propriétaire liste les propositions reçues |
| POST    | `/flowers/:id/proposals/:proposalId/accept` | `200` — le propriétaire accepte une proposition |
| DELETE  | `/flowers/:id/proposals/:proposalId`      | `204` — le propriétaire refuse (proposition retirée) |
| GET     | `/me/proposal-stats`                      | `{ acceptedProposals }` — nb de mes propositions acceptées par des amis |

## Identification par image (`/flowers/:id/identify`)

| Méthode | Chemin                  | Description |
|---------|-------------------------|-------------|
| POST    | `/flowers/:id/identify` | `200 { flowerId, suggestions }` — suggestions d'espèce (Pl@ntNet), **propriétaire seul** |

## Demandes d'identification collaborative (NODE-133)

| Méthode | Chemin                                   | Description |
|---------|------------------------------------------|-------------|
| POST    | `/flowers/:id/identification-requests`   | `204` — le propriétaire demande à ses amis d'identifier la fleur |
| DELETE  | `/flowers/:id/identification-requests`   | `204` — le propriétaire annule la demande |
| GET     | `/identification-requests`               | Les fleurs « à identifier » qui me sont partagées (vue côté ami) |

## Encyclopédie des espèces (`/species`) — NODE-125

| Méthode | Chemin            | Description |
|---------|-------------------|-------------|
| GET     | `/species`        | Liste paginée du référentiel. Paramètres : `page` (≥ 1), `limit` (1..200) |
| GET     | `/species/search` | Autocomplétion. Paramètres : `q` (requis), `limit` (1..50) |
| GET     | `/species/:id`    | Fiche détaillée d'une espèce |

`Species` = `{ id, scientificName, commonName, family, description, emoji }`.
La liste paginée renvoie `{ items, total, page, limit }`.

## Notifications in-app (`/notifications`)

| Méthode | Chemin                       | Description |
|---------|------------------------------|-------------|
| GET     | `/notifications`             | Mes notifications |
| GET     | `/notifications/unread-count`| `{ count }` — nombre de non lues |
| POST    | `/notifications/:id/read`    | Marque une notification comme lue |

Types (`type`) : `friend_request`, `friend_accepted`, `flower_shared`,
`species_proposed`, `species_confirmed`, `identification_requested`,
`flower_liked`, `flower_commented`. Chaque notification porte un `data` (jsonb)
contextuel et un `readAt` (null tant que non lue).

## Jetons d'appareil / Push (`/push`)

| Méthode | Chemin                  | Description |
|---------|-------------------------|-------------|
| POST    | `/push/devices`         | `{ token, platform }` — enregistre le jeton FCM/APNs de l'appareil courant |
| DELETE  | `/push/devices/:token`  | `{ ok: true }` — désenregistre un jeton (ne supprime que **ses** jetons, I2) |

`platform` ∈ `android` / `ios` / `web`.

## Limites & quotas

**Rate limiting** (`@nestjs/throttler`, par IP) :

| Route                     | Limite |
|---------------------------|--------|
| Global (toutes routes)    | 100 req / min |
| `POST /auth/login`        | 5 / min |
| `POST /auth/register`     | 3 / min |
| `POST /auth/forgot-password` | 3 / 15 min |
| `POST /auth/email/verification` | 3 / 15 min |

Dépassement → `429 Too Many Requests`.

**Upload d'image** (`POST /flowers/:id/image` et `POST /flowers/:id/photos/:photoId/image`) :

- taille max **15 Mo** (dépassement → `413 Payload Too Large`) ;
- types MIME acceptés : `image/jpeg`, `image/png`, `image/webp`, `image/heic`,
  `image/heif` (type refusé → `400`) ;
- le type déclaré est re-vérifié (magic bytes) au réencodage WebP côté serveur.

## Récapitulatif des codes

- `201` création, `200` succès, `204` sans contenu.
- `400` validation, `401` token absent/expiré, `403` non autorisé sur la
  ressource, `404` introuvable/masqué, `409` conflit (ex. amitié existante),
  `413` fichier trop volumineux, `429` quota de requêtes dépassé.
