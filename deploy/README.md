# Déploiement FloraPin (POC auto-hébergé)

Stack de déploiement sur un VPS via Docker Compose : **API NestJS** +
**PostgreSQL/PostGIS** + **MinIO** + **Caddy** (reverse-proxy, HTTPS automatique).

## Prérequis

- Un VPS (Linux) avec Docker + Docker Compose.
- Un nom de domaine dont l'enregistrement A pointe vers l'IP du VPS
  (nécessaire pour le certificat HTTPS Let's Encrypt de Caddy).
- Ports 80 et 443 ouverts.

## Mise en route

```bash
cd deploy
cp .env.example .env        # renseigner DOMAIN + secrets (DB, JWT, MinIO)
docker compose up -d --build
```

- L'API est exposée en HTTPS sur `https://<DOMAIN>` (base `/api/v1`,
  doc Swagger sur `/api/docs`).
- Le schéma SQL (`backend/db/schema.sql`) est appliqué **au premier démarrage**
  d'une base vide (volume `db-data`).
- Le bucket MinIO est créé automatiquement par l'API au démarrage.

## Services & volumes

| Service | Rôle | Volume persistant |
|---------|------|-------------------|
| `db`    | PostgreSQL + PostGIS | `db-data` |
| `minio` | Stockage objet (images) | `minio-data` |
| `api`   | API NestJS (build local) | — |
| `proxy` | Caddy (TLS + reverse-proxy) | `caddy-data`, `caddy-config` |

## Exploitation

```bash
docker compose ps              # état
docker compose logs -f api     # logs API
docker compose pull && docker compose up -d --build   # mise à jour
docker compose down            # arrêt (les volumes sont conservés)
```

## Sauvegarde (POC — voir NODE-30)

Décision POC : **pas de sauvegarde robuste**. Les données survivent aux
redémarrages grâce aux volumes Docker persistants. Avant une opération risquée
(migration de schéma), faire un dump manuel :

```bash
docker compose exec db pg_dump -U "$DATABASE_USER" "$DATABASE_NAME" > backup_$(date +%F).sql
```

Stratégie robuste (pgBackRest, miroir MinIO hors-site, 3-2-1, test de
restauration) **reportée à la prod** — détaillée dans la note du nœud NODE-30.

## Sécurité

- Secrets uniquement via `deploy/.env` (jamais commité).
- HTTPS forcé par Caddy ; l'API n'est pas exposée en direct (seul le proxy
  publie 80/443).
- Images servies via URLs MinIO présignées (jamais publiques).
