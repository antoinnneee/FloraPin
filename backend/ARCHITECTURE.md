# Architecture du backend FloraPin

> Statut : conception (NODE-16). Ce document matérialise l'architecture globale
> du backend. Les détails sont approfondis dans les nœuds dédiés :
> API & schéma (NODE-27), authentification (NODE-17), stockage cloud (NODE-18),
> stockage objet MinIO (NODE-28), synchronisation (NODE-19), hébergement &
> déploiement (NODE-29).

## 1. Objectif & contexte

FloraPin est d'abord une app Android **hors-ligne** (capture de fleurs géolocalisées,
galerie et carte locales — voir MVP NODE-5/NODE-11). Le backend ajoute :

- des **comptes utilisateurs** ;
- le **stockage cloud** des photos et de leurs métadonnées GPS ;
- le **partage entre amis** (NODE-20) ;
- les **requêtes géospatiales** côté serveur (fleurs proches, dans une bbox/rayon).

Contrainte POC : **auto-hébergé** sur un VPS perso, en conteneurs Docker, simple à
exploiter. Pas de haute disponibilité ni de sauvegarde robuste au POC (cf. NODE-30).

## 2. Vue d'ensemble

```
                          HTTPS
   ┌─────────────┐   (TLS, domaine)   ┌──────────────────────────┐
   │  App Android │ ─────────────────▶ │  Reverse-proxy           │
   │  (Retrofit)  │ ◀───────────────── │  Caddy ou Nginx          │
   └─────────────┘                     └────────────┬─────────────┘
                                                     │  HTTP interne
                                                     ▼
                                       ┌──────────────────────────┐
                                       │  API NestJS (Node.js)     │
                                       │  modules: auth, users,    │
                                       │  flowers, friendships,    │
                                       │  shares, storage, sync    │
                                       └───────┬───────────┬───────┘
                                               │           │
                          SQL (TypeORM)        │           │  S3 API (presigned)
                                               ▼           ▼
                              ┌────────────────────┐  ┌────────────────────┐
                              │ PostgreSQL + PostGIS│  │ MinIO (S3-compat.)  │
                              │ métadonnées + géo   │  │ fichiers images     │
                              └────────────────────┘  └────────────────────┘

   Tout tourne dans un seul `docker compose` sur le VPS (voir NODE-29).
```

Principe clé : **la base ne stocke que les métadonnées** (utilisateurs, fleurs,
positions, liens d'amitié, partages). Les **binaires images vivent dans MinIO** ;
la base ne garde que la clé objet. Le client upload/télécharge via des **URLs
présignées** générées par l'API (l'API ne sert pas les octets elle-même).

## 3. Stack technique (actée)

| Couche            | Choix                          | Raison |
|-------------------|--------------------------------|--------|
| Runtime           | Node.js                        | Écosystème, vélocité |
| Framework         | **NestJS** (TypeScript)        | Structure imposée, DI, validation (`class-validator`), modules |
| ORM               | **TypeORM**                    | Support PostGIS (type `geography`), migrations |
| Base de données   | **PostgreSQL + PostGIS**       | Requêtes géospatiales (rayon, bbox, KNN) + index GiST |
| Stockage objet    | **MinIO** (S3-compatible)      | Auto-hébergeable, API S3 standard, URLs présignées |
| Auth              | **JWT + refresh tokens**       | Sans état côté API, simple pour mobile (cf. NODE-17) |
| Reverse-proxy     | Caddy ou Nginx + HTTPS         | TLS, terminaison, à trancher en NODE-29 |
| Exécution         | Docker + Docker Compose        | Repro, déploiement VPS simple |

## 4. Structure du projet NestJS

```
backend/
├─ src/
│  ├─ main.ts                  # bootstrap (ValidationPipe global, CORS, Helmet)
│  ├─ app.module.ts            # assemblage des modules + ThrottlerModule (rate limit)
│  ├─ auth/                    # login/refresh, reset/vérif email, JwtStrategy, guards (NODE-17)
│  ├─ users/                   # profil courant, changement d'email, suppression RGPD
│  ├─ flowers/                 # CRUD fleurs + photos + validation des WebP clients (NODE-27/104)
│  ├─ albums/                  # albums (regroupements nommés de fleurs) — NODE-98
│  ├─ friendships/             # demandes/relations d'amitié par email (NODE-20)
│  ├─ shares/                  # partage ciblé de fleurs/albums entre amis (NODE-20/22)
│  ├─ feed/                    # feed des amis (partages + visibilité « amis ») — NODE-23/136
│  ├─ comments/                # fil de discussion sur une fleur — NODE-141
│  ├─ likes/                   # cœurs sur les fleurs — NODE-139
│  ├─ proposals/               # propositions d'espèce entre amis + stats de profil
│  ├─ identification/          # identification par image (Pl@ntNet)
│  ├─ identification-requests/ # demandes d'identification collaborative — NODE-133
│  ├─ species/                 # encyclopédie / référentiel d'espèces — NODE-125
│  ├─ notifications/           # notifications in-app
│  ├─ push/                    # jetons d'appareil FCM/APNs + envoi push
│  ├─ mail/                    # envoi d'emails (SMTP ou stub)
│  ├─ storage/                 # MinIO : SHA-256, déduplication et nettoyage des orphelins (NODE-28)
│  ├─ sync/                    # endpoints de synchronisation delta (NODE-19)
│  └─ observability/           # filtre d'exceptions global + reporting d'erreurs
├─ db/schema.sql               # schéma PostgreSQL + PostGIS de référence
├─ test/                       # tests e2e
└─ Dockerfile
```

Chaque domaine est un **module NestJS** autonome (controller + service +
entité + DTO) ; le backend en compte **19** (plus l'`AppModule` racine). Les
dépendances transverses (auth, storage, notifications) sont injectées. Le
`docker compose` (API + Postgres/PostGIS + MinIO + proxy) n'est pas dans
`backend/` mais dans **`deploy/docker-compose.yml`** (+ override) — cf. NODE-29.

## 5. Modèle de données (vue haute)

Détail complet et DDL en **NODE-27**. Entités principales :

- **users** : id, email (unique), password_hash, display_name, created_at.
- **flowers** : id, owner_id → users, image_key (clé objet MinIO),
  `location geography(Point,4326)` (nullable), accuracy_m, taken_at, notes,
  created_at, updated_at, deleted_at (soft-delete pour la sync).
- **friendships** : (user_id, friend_id, status) — demande/accepté/bloqué.
- **shares** : fleur partagée → ami (ou visibilité « amis »).

Géo : colonne `geography(Point,4326)` + **index GiST** pour `ST_DWithin`
(rayon) et requêtes par bbox. Le format GPS échangé reste lat/long + précision
(aligné sur le modèle Android `GeoPoint`).

## 6. Flux principaux

1. **Inscription / connexion** (NODE-17)
   `POST /auth/register`, `POST /auth/login` → `{ accessToken, refreshToken }`.
   `POST /auth/refresh` échange un refresh valide contre un nouvel access.

2. **Upload d'une photo** (NODE-18/NODE-28)
   - `POST /flowers` (métadonnées) → l'API crée la ligne + renvoie une **URL
     présignée PUT** vers MinIO.
   - Le client **upload le binaire directement** sur MinIO via cette URL.
   - Lecture : l'API renvoie une **URL présignée GET** à durée limitée.

3. **Carte / fleurs proches** (NODE-27)
   `GET /flowers?bbox=...` ou `?lat&lng&radius=` → PostGIS `ST_DWithin` /
   intersection bbox, filtré par visibilité (soi + amis).

4. **Synchronisation** (NODE-19)
   `GET /sync?since=<timestamp>` renvoie les créations/maj/suppressions depuis
   le dernier sync (soft-delete + `updated_at`). Le client pousse ses captures
   locales puis tire les changements. Stratégie de conflit : *last-write-wins*
   au POC.

5. **Partage entre amis** (NODE-20)
   Demande d'ami → acceptation → les fleurs « amis » deviennent visibles dans
   les requêtes de l'ami.

## 7. Sécurité

- **Transport** : HTTPS obligatoire (terminaison au reverse-proxy).
- **Auth** : JWT d'accès courts + refresh tokens (rotation) — NODE-17.
- **Autorisation** : guards par ressource (un utilisateur ne voit que ses
  fleurs + celles partagées par ses amis).
- **Images** : jamais publiques ; accès uniquement via **URLs présignées**
  expirantes générées après contrôle d'autorisation.
- **Validation** : `ValidationPipe` global + DTO `class-validator` sur toutes
  les entrées.
- **Secrets** : variables d'environnement (jamais commitées), injectées par
  Docker Compose / `.env`.

## 8. Déploiement (vue haute)

Détaillé en **NODE-29**. Un seul `docker compose` sur le VPS :

- service `api` (image NestJS buildée par le `Dockerfile`) ;
- service `db` (PostGIS) avec **volume persistant** ;
- service `minio` avec **volume persistant** ;
- service `proxy` (Caddy/Nginx) pour TLS + routage.

Sauvegarde POC (NODE-30) : volumes persistants + `pg_dump` manuel ponctuel ;
stratégie robuste (pgBackRest, 3-2-1) reportée à la prod.

## 9. Décisions & renvois

| Sujet                         | Statut       | Détail dans |
|-------------------------------|--------------|-------------|
| Framework / runtime / DB      | Acté         | ce document |
| Schéma DB & endpoints REST    | À concevoir  | NODE-27 |
| Authentification (JWT)        | À implémenter| NODE-17 |
| Upload images + métadonnées   | À implémenter| NODE-18 |
| Stockage objet (MinIO/presign)| À implémenter| NODE-28 |
| Synchronisation local↔cloud   | À implémenter| NODE-19 |
| Compose, proxy, HTTPS         | À implémenter| NODE-29 |
| Sauvegarde                    | POC minimal  | NODE-30 |

## 10. Hors-scope POC

- Pas de Google OAuth (repoussé après le POC — NODE-17).
- Pas de haute disponibilité / réplication DB.
- Pas de sauvegarde hors-site ni chiffrée (NODE-30).
- Pas de CDN devant MinIO (presign direct suffisant au POC).
