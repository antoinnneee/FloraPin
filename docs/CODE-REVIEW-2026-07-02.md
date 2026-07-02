# Revue complète du projet FloraPin

> Date : 2026-07-02 — Référence : `main` @ `daf59ec` (release 1.7.0)
> Périmètre : backend NestJS, app Android, sécurité transverse (secrets, déploiement), tests/CI/docs.

## Verdict global

Le projet est sain dans ses fondations — architecture claire, aucun secret dans le dépôt,
auth JWT bien conçue (rotation des refresh tokens hashés, anti-énumération, bcrypt),
tokens Android chiffrés, et une vraie couverture de tests (tous les modules backend ont
des specs de logique, 26 fichiers de tests Android + e2e Testcontainers).

La revue a trouvé **3 problèmes critiques** et une dizaine d'importants, concentrés sur
trois thèmes : les **uploads non bornés**, la **fiabilité de la sync** (perte de données
possible) et des **contrôles d'autorisation manquants**.

---

## 🔴 Critique

### C1. Upload sans limite de taille ni de type — DoS mémoire du backend

- `FileInterceptor('file')` sans `limits` ni `MulterModule` configuré
  (`backend/src/flowers/flowers.controller.ts:67`, `backend/src/flowers/flower-photos.controller.ts:42`).
  Multer charge tout en RAM : n'importe quel compte (inscription ouverte) peut poster un
  fichier de plusieurs Go et faire tomber le serveur en OOM.
- Aucun contrôle MIME : un fichier non-image fait planter sharp
  (`backend/src/storage/image-processing.ts:20-33`) → 500 au lieu de 400.
- Même faille côté URLs présignées MinIO (`backend/src/storage/minio-storage.service.ts:81-88`) :
  `presignedPutObject` sans policy → taille illimitée, contenu arbitraire, contournement
  du réencodage WebP et de la génération de miniature.

### C2. Aucun rate limiting sur toute la chaîne

- Ni `@nestjs/throttler` (absent de `backend/package.json`), ni `limit_req` nginx
  (`deploy/nginx-florapin.conf`), ni directive Caddy (`deploy/Caddyfile`).
- Conséquences : brute-force illimité sur `POST /auth/login`, et **flooding d'emails
  réels** via `POST /auth/forgot-password` et `POST /auth/email/verification`
  (`backend/src/auth/auth.service.ts:120-140, 175-195`) — spam de la victime,
  blacklistage SMTP, remplissage de la table de tokens.

### C3. Sync Android : suppression jamais propagée + pull qui écrase les éditions locales

- Supprimer une fleur fait un **hard-delete** Room (`app/.../data/FlowerDao.kt:61-62` via
  `detail/DetailViewModel.kt:78-84`) sans jamais poser `deletedAt` : la branche `deleted`
  du push (`app/.../sync/SyncEngine.kt:101-108`) est du **code mort**. La fleur reste sur
  le serveur (visible des amis) et **ressuscite** au prochain full-pull.
- Le pull applique l'état serveur **sans vérifier `syncState`**
  (`sync/SyncEngine.kt:131-141`, contrairement à `AlbumSyncEngine.kt:97-99`) : une
  édition locale dont le push a échoué est **écrasée définitivement** au pull du même
  cycle. Race jumelle : `markSynced` inconditionnel (`data/FlowerDao.kt:74-78`) peut
  marquer SYNCED une édition jamais poussée.

### C4. `allowBackup="true"` sans règles d'exclusion → crash en boucle après restauration

- `app/src/main/AndroidManifest.xml:29` : pas de `dataExtractionRules`/`fullBackupContent`.
  Auto Backup embarque `florapin_auth` (EncryptedSharedPreferences) ; après restauration
  sur un autre appareil, la clé Keystore manque → exception dans `FlorapinApp.onCreate`
  (`FlorapinApp.kt:29`) → **crash au lancement irrécupérable** sans effacer les données.
- Correctif minimal : exclure `florapin_auth` du backup (ou `allowBackup="false"`).

---

## 🟠 Important

### Autorisations backend manquantes

| # | Problème | Localisation |
|---|----------|--------------|
| I1 | Likes sans contrôle de visibilité : on peut liker une fleur **privée** par UUID et déclencher notification + push chez le propriétaire ; sonde l'existence d'un flowerId (404 vs 204) | `backend/src/likes/likes.service.ts:23-45` |
| I2 | `DELETE /push/devices/:token` supprime par valeur sans vérifier le propriétaire → quiconque connaît un token FCM peut couper les push d'un autre compte | `backend/src/push/push.controller.ts:33-37`, `device-tokens.service.ts:33-35` |
| I3 | Énumération d'emails via `POST /friendships` (404 « Utilisateur introuvable » = oracle) ; contredit l'anti-énumération de forgot-password. Oracle temporel de second ordre sur forgot-password (SMTP awaité seulement si l'email existe) | `backend/src/friendships/friendships.service.ts:32-41`, `auth.service.ts:139` |

### Fuites et incohérences de stockage (RGPD)

- **I4.** `deleteAccount` ne collecte jamais les `thumbnailKey`
  (`backend/src/users/users.service.ts:139-146`) → les miniatures WebP **survivent à
  l'effacement du compte**. Aggravé par : soft-delete de fleur sans purge MinIO
  (`flowers.service.ts:242-248`, aucun job de purge) et suppression de photo sans
  suppression des objets (`flower-photos.service.ts:81-103`).
- **I5.** Chaque ré-upload orpheline l'ancienne miniature (`flowers.service.ts:155`,
  `flower-photos.service.ts:68-77` — seule `imageKey` est capturée).
- **I6.** `thumbnail_key` de la fleur jamais mis à jour lors de la promotion/changement de
  couverture (`flower-photos.service.ts:100, 136`) → galerie/feed affichent une miniature
  incohérente, voire supprimée.
- Symétriquement côté Android : les fichiers de `filesDir/photos` ne sont jamais supprimés
  hors suppression de compte (`PhotoStorage.clearAll` uniquement), et
  `flower_photos`/`flower_album_cross_ref` sans FK/CASCADE → lignes orphelines.

### Fiabilité de la sync

- **I7.** `POST /sync/flowers` non idempotent : pas de dédup sur `localId`
  (`backend/src/sync/sync.service.ts:62-72`, contrairement aux albums qui ont `clientId`) ;
  boucle séquentielle sans transaction → un item invalide au milieu laisse les précédents
  créés sans mapping renvoyé. Un retry client crée des **doublons**.
- **I8.** Deux workers WorkManager peuvent tourner en parallèle (unique-works distinctes
  `florapin-sync-periodic` / `florapin-sync-now`, `app/.../sync/SyncScheduler.kt:25-57`)
  → double push des créations. Les rustines existantes (détection « twin »
  `SyncEngine.kt:150-154`, migration 9→10 de dédoublonnage) prouvent que c'est déjà arrivé.
- **I9.** Échec d'upload d'image avalé après `markSynced` (`SyncEngine.kt:74-78`,
  `PhotoSyncEngine.kt:44-53`) : aucun retry ni état `IMAGE_PENDING` → la fleur reste
  **grise pour toujours** côté serveur.
- **I10.** Un élément en erreur permanente (404/409) fait échouer toute la sync
  albums/photos → `Result.retry()` **infini**, le pull ne s'exécute plus jamais
  (`AlbumSyncEngine.kt:32-57`, `PhotoSyncEngine.kt:36-61`, `SyncWorker.kt:29`).

### UX / plomberie Android

- **I11.** `POST_NOTIFICATIONS` déclarée mais **jamais demandée en runtime** → tous les
  push sont invisibles sur Android 13+ (`AndroidManifest.xml:11`,
  `push/FloraMessagingService.kt:46`).
- **I12.** Erreur réseau pendant le refresh de token → **déconnexion silencieuse**
  (`network/auth/TokenRefresher.kt:20-23` ne distingue pas IOException et 401 ;
  `TokenAuthenticator.kt:34-38` purge les tokens).
- **I13.** `AuthInterceptor` exclut tout chemin `/auth/`, or `POST auth/email/verification`
  exige un JWT (`network/auth/AuthInterceptor.kt:13-15`, `api/AuthApi.kt:43-44`) →
  échec garanti si l'access token est expiré.
- **I14.** Suppression de fleur **sans confirmation** (`detail/DetailScreen.kt:142-144`) —
  combiné à C3, destructif en un tap.

### Déploiement

- **I15.** Mot de passe SSH/sudo interpolé en clair dans les commandes distantes
  (`deploy/deploy.sh:204, 216, 236, 242, 250` — `echo '$REMOTE_PASSWORD' | sudo -S`) →
  visible dans `ps`/logs d'audit du VPS à chaque déploiement. → Clés SSH + règle sudoers
  `NOPASSWD` ciblée pour `docker`.
- **I16.** Le script peut relancer la prod avec les secrets `change-me` de `.env.example`
  sans le détecter (`deploy/deploy.sh:187-197`, `deploy/.env.example:21-32`). → Ajouter un
  grep de refus sur `change-me` avant `docker compose up`.
- **I17.** Clé API Firebase lisible dans l'historique git (commit `06aede0`,
  `app/google-services.json` retiré ensuite) : clé `AIzaSy…` du projet `florapin`. →
  Restreindre par package + empreinte SHA-1 dans la console Google Cloud ; purge
  d'historique si le dépôt devient public.

### CI

- **I18.** Le backend n'est **pas testé en CI** : `.github/workflows/android-ci.yml` est
  le seul workflow (lint + tests + APK Android). Les 22 specs et le e2e Testcontainers
  (exécutable sur `ubuntu-latest`) ne tournent qu'en local. Les tests instrumentés
  Android ne tournent nulle part non plus.

### Performance backend (bloquant avec le volume)

- **I19.** `toResponse` : 4-6 requêtes/presign **par fleur** (`flowers.service.ts:255-313`) ;
  `search` charge toutes les fleurs et filtre en mémoire (`flowers.service.ts:189-202` —
  les index `idx_flowers_species`/`idx_flowers_tags` et l'index GiST PostGIS ne servent
  jamais) ; chaque POST de commentaire/proposition recalcule **tout le feed** avec URLs
  présignées juste pour tester la visibilité (`comments.service.ts:137-143`,
  `proposals.service.ts:69`) ; feed trié/tronqué en mémoire (`feed.service.ts:25-52`).

---

## 🟡 Mineur

- CORS `*` et pas de helmet (`backend/src/main.ts:11`) ; Swagger `/api/docs` exposé
  publiquement en prod (routé par le Caddyfile) ; conteneur API en root (pas de
  `USER node` dans `backend/Dockerfile`).
- Races check-then-insert → 500 au lieu de 409/204 : `register` concurrent
  (`auth.service.ts:70-76`), `refresh` concurrent sans détection de réutilisation
  (`auth.service.ts:88-111`), double-like (`likes.service.ts:29-32`), doublons `shares`
  (aucune contrainte d'unicité, `schema.sql:239-252`) et amitiés croisées.
- ILIKE non échappé dans species (`species.service.ts:48, 87-91, 106`) : « R\_sa » peut
  matcher « Rosa » et lier le mauvais `speciesId` à la fleur.
- JWT valide ~15 min après suppression du compte (`jwt.strategy.ts:27-29`) → 500 (FK) au
  lieu de 401.
- Transactions manquantes : création fleur+photo, `proposals.accept`, `resetPassword`
  (token réutilisable si crash entre hash et `usedAt`), `reorder`/`setCover`.
- Notification bloquante après succès (`shares.service.ts:85`, `comments.service.ts:58`,
  `friendships.service.ts:63`) : 500 alors que l'action est faite.
- Spam de notifications d'identification à chaque re-POST
  (`identification-requests.service.ts:38-51`) ; `sync.pull` sans `viewerId` →
  `likedByMe` toujours false (`sync.service.ts:56`).
- `bcryptjs` (pur JS, sur l'event loop) → préférer `bcrypt` natif ou argon2.
- Android : `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` déclarées sans fonctionnalité
  (risque de rejet Play Store) ; heuristique « twin » destructive sur simple égalité de
  `createdAt` (`SyncEngine.kt:150-154`) ; course `PushTokenRegistrar`/logout
  (`FloraNavHost.kt:253-259`) ; `loggedIn` figé au premier rendu (`FloraNavHost.kt:80-83`) ;
  messages d'erreur techniques affichés bruts (`AuthViewModel.kt:75`) ;
  `ExampleUnitTest.kt` à supprimer.
- **Écart avec la décision projet** : `SyncPreferences.kt:17-26` a `DEFAULT = true` alors
  que la décision consignée est sync **OFF par défaut** — à arbitrer.
- Hygiène dépôt : `dist/` racine (38 Mo d'artefacts release 1.7.0) à gitignorer ;
  `deploy/deploy.sh` marqué modifié = pur CRLF/LF → `.gitattributes` avec `*.sh text eol=lf` ;
  `.claude/` à gitignorer ou tracker délibérément.
- Docs périmées : `backend/docs/API.md` manque 9 modules (comments, feed, likes,
  notifications, push, species, proposals, identification, identification-requests) et
  documente des endpoints/filtres inexistants (`PATCH /users/me`, `GET /users?query=`,
  filtres `mine`/`bbox`/`radius`/`cursor`, `flower_shares`) ; `backend/ARCHITECTURE.md`
  décrit des répertoires inexistants (`src/config/`, `src/common/`, `src/database/`) ;
  README : listes de modules incomplètes. Le **CHANGELOG est à jour**.
- Versions : stack Android fin 2024 (Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, Compose BOM
  2024.12) — prévoir la montée targetSdk 36 avant l'échéance Play S2 2026. Landing :
  4 vulnérabilités npm (astro, js-yaml via gray-matter) — impact réduit (site statique),
  `npm audit fix` pour js-yaml. CI sans bloc `permissions:` (ajouter `contents: read`).

---

## ✅ Points sains vérifiés

- **Secrets** : aucun `.env`, `.jks`, `keystore.properties`, `local.properties`,
  `.deployEnv` tracké ni dans tout l'historique (seule exception : la clé Firebase, I17).
- **SQL** : zéro requête brute (pas de `query()`/`Raw()`) → pas d'injection possible.
- **Auth** : `ValidationPipe` whitelist + forbidNonWhitelisted ; secrets JWT via
  `getOrThrow` sans fallback ; rotation des refresh tokens hashés SHA-256 à usage unique ;
  anti-énumération sur forgot-password.
- **Android** : tokens en EncryptedSharedPreferences (AES256-GCM) ; pas de cleartext HTTP
  en release ; logging HTTP BODY en debug uniquement ; migrations Room 1→12 cohérentes ;
  CameraX et MapLibre correctement libérés ; règles R8 présentes.
- **Tests** : les 20 modules backend ont chacun un spec réel (2 909 lignes, logique et pas
  instanciation) + e2e Testcontainers (Postgres/PostGIS + MinIO réels) ; 26 fichiers de
  tests unitaires Android (moteurs de sync, repositories, ViewModels, TokenAuthenticator)
  + 5 tests instrumentés (migrations, DAO, SyncWorker).

---

## Plan d'action suggéré (par priorité)

1. **Backend — uploads & throttling** : limites Multer (taille + MIME), policy sur les PUT
   présignés ; `@nestjs/throttler` sur `/auth/*` (C1, C2).
2. **Android — sync** : soft-delete propagé au push, respect de `syncState` au pull,
   verrou unique sur les workers, état « image en attente d'upload » (C3, I8-I10).
3. **Android — plomberie** : `dataExtractionRules` excluant `florapin_auth`, demande
   runtime de `POST_NOTIFICATIONS`, confirmation de suppression, distinction
   IOException/401 au refresh (C4, I11-I14).
4. **Backend — autorisations** : visibilité sur likes, propriété sur device tokens,
   réponse générique sur friendships (I1-I3).
5. **Deploy** : clés SSH + sudoers, garde-fou `change-me`, restreindre la clé Firebase
   (I15-I17).
6. **CI** : job backend `npm ci && lint && build && test` (+ e2e) (I18).
7. Ensuite : purge MinIO/thumbnails RGPD (I4-I6), idempotence `POST /sync/flowers` (I7),
   performances (I19), doc API, hygiène dépôt.
