# Déploiement FloraPin (POC auto-hébergé)

Stack de déploiement sur un VPS via Docker Compose : **API NestJS** +
**PostgreSQL/PostGIS** + **MinIO** + **Caddy** (reverse-proxy, sert vitrine + API).

> **TLS** : sur le déploiement de référence (`florapin.pattounecorp.ovh`), le HTTPS
> public est terminé par le **nginx de l'hôte** qui proxifie vers Caddy en HTTP — voir
> [`DEPLOYMENT.md`](DEPLOYMENT.md). Caddy peut aussi gérer l'auto-HTTPS lui-même
> (Let's Encrypt) si les ports 80/443 publics atteignent directement la machine ;
> il faut alors rétablir le bloc `{$DOMAIN}` dans le `Caddyfile` et publier le 443.

## Prérequis

- Un VPS (Linux) avec Docker + Docker Compose.
- Un nom de domaine dont l'enregistrement A pointe vers l'IP du VPS
  (nécessaire pour le certificat HTTPS Let's Encrypt de Caddy).
- Ports 80 et 443 ouverts.

## Mise en route

```bash
cd deploy
cp .env.example .env        # renseigner DOMAIN + secrets (DB, JWT, MinIO) + ports
docker compose up -d --build
```

- L'API est servie sous `/api` (base `/api/v1`, doc Swagger sur `/api/docs`) et la
  vitrine à la racine, derrière Caddy.
- **Ports** (configurables dans `.env`, défauts entre parenthèses) :
  `HTTP_PORT` (80) publie Caddy sur l'hôte — **mettre une valeur non privilégiée
  (ex. `8088`) quand un reverse-proxy hôte est devant**, cf. `DEPLOYMENT.md` ;
  `API_PORT` (3000) port interne de l'API ; et `DB_HOST_PORT`/`MINIO_HOST_PORT`/
  `MINIO_CONSOLE_HOST_PORT` (5432/9000/9001) publiés sur l'hôte **en dev seulement**.
- Le schéma SQL (`backend/db/schema.sql`) est appliqué **au premier démarrage**
  d'une base vide (volume `db-data`), suivi du seed du catalogue d'espèces
  (`backend/db/seed-species.sql`, idempotent) qui alimente l'autocomplétion.

## Déploiement automatisé (`deploy.sh`)

Depuis une machine de dev, `deploy/deploy.sh` pousse tout sur le VPS via SSH
(un seul prompt de mot de passe, multiplexing `sshpass`) :

```bash
cd deploy
./deploy.sh        # 1er lancement : crée .deployEnv (à remplir) puis s'arrête
```

Le script, de bout en bout :

1. **(re)génère l'APK debug** (`assembleDebug`) et le dépose dans
   `landing/public/florapin.apk` — la vitrine sert ainsi **la dernière version**
   de l'app à chaque déploiement (voir « APK de téléchargement » ci-dessous) ;
2. **synchronise** (`rsync`) les sources `backend/`, `landing/` et `deploy/`
   vers le VPS (en excluant `node_modules/` et `dist/`) ;
3. **build la vitrine sur le VPS dans un conteneur** `node:22-alpine`
   (`npm ci && npm run build` → `landing/dist`, monté dans Caddy) — Astro recopie
   `public/florapin.apk` dans `dist/`, donc Caddy l'expose à `/florapin.apk` ;
4. lance `docker compose up -d --build` côté serveur — ce qui **installe et
   compile le backend dans l'image** (npm ci + nest build) puis (re)démarre
   db + minio + api + proxy.

Prérequis : `sshpass` et `rsync` en local ; **Docker seul** sur le VPS (rien
n'est buildé en local — ni Node ni npm requis sur le poste, ce qui évite les
soucis de build sur un FS Windows monté dans WSL) ; et `deploy/.env` déjà
présent côté VPS (les secrets ne sont jamais copiés). La cible SSH est définie
dans `deploy/.deployEnv` (gitignoré).
- Le bucket MinIO est créé automatiquement par l'API au démarrage.

## Bootstrap serveur (`install-service.sh`)

À lancer **une seule fois** sur le VPS (dans `deploy/`) pour préparer l'hôte :

```bash
cd deploy
./install-service.sh
```

Il (1) installe Docker + le plugin compose si absents, (2) crée `.env` depuis
`.env.example` (à éditer puis relancer), et (3) installe une **unité systemd
`florapin`** qui pilote la stack (`docker compose`) : démarrage automatique au
boot et contrôle via `systemctl start|stop|restart florapin`.

Ensuite, les mises à jour se font depuis le poste de dev avec `deploy.sh`
(`docker compose up -d --build` ne recrée que les conteneurs modifiés — downtime
minimal, sans couper la base).

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

## Vitrine (site landing)

Le même domaine sert la **vitrine statique** (à la racine) et l'**API** (sous
`/api/*`) — voir `Caddyfile`. Caddy sert le build Astro monté depuis
`../landing/dist`.

```bash
# 1. Construire la vitrine (génère landing/dist)
npm --prefix ../landing ci
npm --prefix ../landing run build

# 2. (Re)démarrer le proxy pour servir le nouveau build
docker compose up -d proxy
```

- Vitrine : `https://<DOMAIN>/`
- API : `https://<DOMAIN>/api/v1` · Swagger : `https://<DOMAIN>/api/docs`
- DNS : un seul enregistrement A `<DOMAIN>` → IP du VPS (déjà requis pour l'API).
- `landing/dist` est gitignoré : il faut **builder sur le VPS** (ou copier le
  build) avant `up`. `astro.config.mjs` fixe `site: https://florapin.fr` — adapter
  si le domaine diffère (impacte les URLs Open Graph/canoniques).

> Alternatives sans VPS : Vercel/Netlify (déploiement Git + CDN) ou GitHub Pages
> (domaine custom + HTTPS). Dans ce cas, la vitrine est indépendante de cette
> stack et l'API garde son domaine.

### APK de téléchargement (bêta — NODE-152)

La vitrine héberge **directement l'APK** pour la bêta (pas de Play Store ni de
GitHub Releases pour l'instant) :

- `deploy.sh` (re)génère l'**APK debug** (`./gradlew :app:assembleDebug`) et le
  copie dans `landing/public/florapin.apk` **avant** le rsync. C'est donc
  toujours la dernière version qui part en ligne.
- Astro recopie `public/` dans `dist/` ; Caddy (`file_server`) sert le fichier à
  l'URL stable **`https://<DOMAIN>/florapin.apk`**.
- `landing/public/*.apk` est **gitignoré** (binaire ~58 Mo régénéré au déploiement).
- Build local requis : le SDK Android (le conteneur `node:22-alpine` du VPS ne
  peut pas compiler une app Android). Si le build échoue, `deploy.sh` réutilise
  l'APK existant plutôt que de bloquer le déploiement.
- C'est un **APK debug non signé en release** : Android affichera l'avertissement
  « sources inconnues ». Acceptable pour la bêta ; passage à un APK signé
  (NODE-142) puis au Play Store (NODE-147) prévu ensuite.
- Le bouton de téléchargement sur la page (`DOWNLOAD_URL = /florapin.apk`) est
  l'objet de **NODE-153**.

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
- HTTPS assuré en façade (nginx hôte sur le déploiement de référence, ou Caddy en
  auto-HTTPS en standalone) ; l'API n'est jamais exposée en direct, tout passe par
  le proxy.
- Images servies via URLs MinIO présignées (jamais publiques).
