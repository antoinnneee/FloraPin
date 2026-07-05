# FloraPin — Plan d'implémentation

> Point de départ : **1.12.0** (bêta fermée Play Store, 4-5 testeurs).
> Voir [FEATURES.md](FEATURES.md) pour l'état actuel.
> ✔️ = manque vérifié dans le code le 2026-07-04. Chemins de fichiers vérifiés le 2026-07-05.
> Les étapes **non prioritaires** sont listées en fin de document **sans description**.

## Vision produit

- **Social first.** La niche de FloraPin, c'est le **partage et l'entraide entre
  amis** autour des fleurs. Pas d'identification automatique : Pl@ntNet existe
  déjà, on ne se bat pas sur ce terrain — chez nous, **ce sont les amis qui
  identifient**.
- **Entraide, pas compétition.** Pas de classements. Les badges et stats
  célèbrent des jalons personnels et l'aide apportée aux autres.
- **Hors-ligne toujours possible.** L'app reste 100 % fonctionnelle en local
  (device-first). Rester hors réseau est un choix assumé de l'utilisateur, qui
  renonce alors sciemment aux fonctionnalités majeures (partage, entraide).

## Écartés / en veille

| Sujet | Décision |
|---|---|
| Identification auto Pl@ntNet | ❌ Écarté — l'identification reste communautaire |
| Classements amicaux | ❌ Écarté — contraire à l'esprit entraide |
| Indicateurs de lecture (« vu par ») | ❌ Écarté — intrusif |
| Fiche espèce enrichie | 💤 En veille — suppose de connaître l'espèce exacte, pas réaliste à ce stade |

---

## ⚠️ Pièges transverses (valables pour tout le plan)

- **DI manuelle, pas de Hilt** : tout nouveau ViewModel suit le pattern
  `companion object { fun factory(context) }` et réutilise
  `NetworkModule.createAuthenticated(...)` (singleton OkHttp partagé — **ne
  jamais créer un second OkHttpClient**, c'est lui qui sérialise les refresh 401).
- **Room v13, `exportSchema=false`** : toute évolution de schéma = bump de
  version + migration ajoutée dans `app/src/main/java/com/florapin/app/data/FloraDatabase.kt`.
- **Backend sans migrations TypeORM** : le schéma vit dans `backend/db/schema.sql`
  (DDL idempotent, `synchronize:false`). Toute nouvelle colonne/table = entité
  TypeORM **et** bloc `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` dans `schema.sql`,
  à rejouer au déploiement (via WSL, cf. deploy.sh).
- **Fichier de prefs `florapin_sync` partagé** entre `SyncPreferences` et
  `PrefsLastSyncStore` (clés `sync_enabled` / `last_sync_at`) : **jamais de
  `.clear()`** sur ce fichier.
- **Device-first** : toute feature sociale suppose une fleur avec `serverId`
  (pattern `CommentsLockedNotice` dans `detail/FlowerComments.kt`) et doit
  dégrader proprement hors-ligne / sync OFF.
- **Push data-only** : ne jamais ajouter de bloc `notification` FCM (l'app
  perdrait la main en background) ; payload ≤ 4 Ko, pas d'URL présignée à courte
  durée de vie dedans.
- **`app/build/` pollue les recherches** (adapters Moshi générés par KSP) —
  toujours filtrer sur `app/src/main`.
- **CHANGELOG.md** : chaque tâche livrée = entrée dans la section « Non publié ».

---

# Phase 1 — Socle & fiabilité

Objectif : outillage (CI), débloquer les dépendances des phases suivantes
(pagination), corriger les manques ✔️ de base du compte.

### 1.1 CI — build APK + tests app/backend sur chaque PR (M)

GitHub Actions : job Android (assembleDebug + tests unitaires) + job backend
(`npm test` puis `npm run test:e2e`).

- **Fichiers** : `.github/workflows/ci.yml` (nouveau).
- **Pièges** :
  - Le plugin `google-services` exige `app/google-services.json` → l'injecter
    depuis un secret (ou fichier factice pour le build debug).
  - `BuildConfig.MAPTILER_API_KEY` vient de `local.properties` → prévoir une
    valeur par défaut vide en CI.
  - Pas de build release en CI (keystore `florapin-release.jks` absent) : debug only.
  - Les e2e backend utilisent **testcontainers** (Postgres/PostGIS) → runner
    avec Docker (ubuntu-latest OK). Le throttler est déjà neutralisé en test.

### 1.2 Pagination du feed — curseur `before` (M) 🔓 *débloque 3.6*

Aujourd'hui : `GET /feed` = delta `since` + tri/slice **en mémoire**, plafond 50
(défaut) / 200 (max). Ajouter une vraie pagination keyset descendante.

- **Fichiers backend** : `backend/src/feed/dto/feed.dto.ts` (param `before`),
  `backend/src/feed/feed.service.ts` (keyset SQL au lieu du slice mémoire),
  `backend/src/shares/shares.service.ts` (`sharedWithMe`/`broadcastWithMe`
  acceptent curseur + limit), `backend/test/app.e2e-spec.ts`.
- **Fichiers app** : `app/src/main/java/com/florapin/app/network/api/FeedApi.kt`,
  `feed/SharedFeedViewModel.kt` (accumulation des pages, détection de fin),
  `feed/SharedFeedScreen.kt` (chargement à l'approche du bas de liste).
- **Pièges** :
  - Le feed **fusionne deux sources** (partages ciblés + broadcast amis) avec
    déduplication : le curseur doit être un couple stable `(createdAt, id)`
    appliqué **aux deux requêtes** avant fusion, puis re-slice à `limit`.
  - Le tri `sort=likes` est incompatible avec un curseur temporel → réserver
    `before` au tri par date (documenter dans le DTO).
  - Ne pas casser le param `since` existant (delta consommé par l'app actuelle).

### 1.3 Pull-to-refresh (S)

Galerie, feed, écran À identifier.

- **Fichiers** : `gallery/GalleryScreen.kt` + `GalleryViewModel.kt`,
  `feed/SharedFeedScreen.kt` + `SharedFeedViewModel.kt`,
  `identify/IdentifyScreen.kt` + `IdentifyViewModel.kt`.
- **Pièges** : sur la galerie (device-first), refresh = relecture Room +
  `SyncScheduler.syncNow()` **seulement si la sync est activée** — ne jamais
  exiger le réseau. `PullToRefreshBox` Material3 est encore marqué expérimental
  dans le BOM 2024.12.01.

### 1.4 Onboarding 3 écrans (S)

Promesse sociale (capture géolocalisée → partage → identification par les amis),
permissions contextualisées, choix sync (device-first respecté).

- **Fichiers** : `app/src/main/java/com/florapin/app/onboarding/OnboardingScreen.kt`
  + `OnboardingPrefs.kt` (nouveaux), `navigation/FloraNavHost.kt` (route + start
  destination), réutilise `permission/PermissionUtils.kt` et l'écran
  `auth/NetworkOptionsScreen.kt` pour le volet sync.
- **Pièges** : la start destination dépend déjà du refreshToken (gallery vs
  login) — l'onboarding s'insère **avant** ce choix, uniquement à la première
  installation. Pour les installs existants, initialiser le flag « déjà vu » à
  vrai si la base contient des fleurs (pattern
  `SyncPreferences.migrateDefaultForExistingInstall` à imiter).

### 1.5 Sauvegarde locale — export/import ZIP (M)

Le filet de sécurité du mode 100 % local : photos + données en ZIP via SAF.

- **Fichiers** : `app/src/main/java/com/florapin/app/data/backup/BackupExporter.kt`
  + `BackupImporter.kt` (nouveaux), section dans `profile/ProfileScreen.kt` +
  `ProfileViewModel.kt`, photos via `capture/PhotoStorage.kt`, données via les
  DAOs (`data/FlowerDao.kt`, `AlbumDao.kt`, `PhotoDao.kt`).
- **Pièges** :
  - **Ne pas copier le fichier .db à chaud** (WAL) : exporter un dump JSON via
    les DAOs plutôt que la base brute.
  - Streamer le ZIP (photos volumineuses), jamais de byte array complet en mémoire.
  - Import = **fusion par `clientId`** (idempotence), pas d'écrasement ; préserver
    `serverId`/`syncState` sinon la sync re-poussera tout en double.

### 1.6 Changer le mot de passe ✔️ (S/M)

- **Fichiers backend** : `backend/src/auth/auth.controller.ts` +
  `auth.service.ts` (`POST /auth/change-password`, vérification de l'ancien mot
  de passe), `backend/src/auth/dto/auth.dto.ts`, tests unitaires + e2e.
- **Fichiers app** : `network/api/AuthApi.kt`, `network/dto/AuthDtos.kt`,
  section compte dans `profile/ProfileScreen.kt` + `ProfileViewModel.kt`.
- **Pièges** : révoquer les refresh tokens des **autres** sessions
  (`refresh-token.entity.ts`) mais réémettre un couple pour la session courante
  — sinon l'utilisateur est déconnecté de son propre appareil. Throttler sur
  l'endpoint (brute-force).

### 1.7 Modifier le nom d'affichage ✔️ (S)

- **Fichiers backend** : `backend/src/users/users.controller.ts` +
  `users.service.ts` (`PATCH /users/me` avec `displayName`), DTO.
- **Fichiers app** : API users (même service Retrofit que `GET /users/me`),
  `profile/ProfileScreen.kt` + `ProfileViewModel.kt`.
- **Pièges** : mêmes validations qu'à l'inscription (trim, longueur). Le nom
  apparaîtra dans les push « incarnés » (2.1) : toujours le résoudre **au moment
  de l'envoi**, jamais le figer dans une table.

### 1.8 Tests UI Compose — flux critiques (M)

Capture et partage, en s'appuyant sur l'existant (`NavAuthGateTest`, `SyncWorkerTest`).

- **Fichiers** : `app/src/androidTest/` (nouveaux tests), brancher sur le job CI 1.1.
- **Piège** : CameraX en instrumentation → tester `CaptureFlow` avec une source
  d'image factice plutôt que la caméra réelle de l'émulateur.

---

# Phase 2 — Notifications sociales (le levier n°1 de rétention)

Constats ✔️ : tap sans effet (pas de `contentIntent`), textes anonymes, un seul
canal `florapin_default`, payloads data-only sans nom d'ami.

### 2.1 Textes incarnés ✔️ — payload enrichi côté backend (S)

« **Marie** a partagé *Coquelicot* » : ajouter `byUserName` (+ `species`,
`thumbnailUrl` quand pertinent) au `data` de chaque type de push.

- **Fichiers backend** : `backend/src/likes/likes.service.ts`,
  `comments/comments.service.ts`, `proposals/proposals.service.ts`,
  `friendships/friendships.service.ts`, `shares/shares.service.ts`,
  `identification-requests/identification-requests.service.ts` (émetteurs du
  `data`), `notifications/notifications.service.ts` (`dispatchPush`),
  `push/fcm-push.sender.ts` (`toStringData`).
- **Fichiers app** : `push/FloraMessagingService.kt` (`titleFor`/`bodyFor`
  consomment les nouveaux champs, fallback sur les textes actuels).
- **Pièges** : rester **data-only** ; résoudre le `displayName` à l'envoi
  (cohérent avec 1.7) ; payload ≤ 4 Ko ; anciennes versions d'app doivent
  tolérer les champs supplémentaires (Moshi ignore l'inconnu — OK).

### 2.2 Tap → contenu concerné ✔️ (S/M)

- **Fichiers app** : `push/FloraMessagingService.kt` (PendingIntent vers
  `MainActivity` avec extras `type` + `flowerId`), `MainActivity.kt`
  (`onNewIntent`, launchMode) et `navigation/FloraNavHost.kt` (routage initial
  depuis l'intent).
- **Pièges** :
  - La route `detail/{id}` utilise l'**id local Room**, pas le `serverId` du
    payload → résoudre `serverId → id local` via `FlowerDao` ; pour une fleur
    d'ami (absente de Room), router vers le feed / la vue distante — deux
    chemins distincts à gérer.
  - `PendingIntent.FLAG_IMMUTABLE` obligatoire (targetSdk 35) ; distinguer cold
    start (extras dans `onCreate`) et app déjà ouverte (`onNewIntent`, `singleTop`).

### 2.3 Canaux Android par type (S)

Likes / commentaires / amis / identification.

- **Fichiers** : `push/FloraMessagingService.kt` (création des canaux
  `florapin_likes`, `florapin_comments`, `florapin_friends`,
  `florapin_identification` ; mapping type → canal).
- **Piège** : un canal existant **ne peut plus être modifié** après création →
  nouveaux ids, et supprimer l'ancien `florapin_default` via
  `deleteNotificationChannel` (ou le garder en fallback pour les types inconnus).

### 2.4 Regroupement (S)

- **Fichiers** : `push/FloraMessagingService.kt` (`setGroup` par fleur ou
  conversation + notification summary).
- **Piège** : le summary doit être reposté à chaque ajout, et les ids de
  notification doivent être stables par (type, flowerId) pour permettre la
  mise à jour plutôt que l'empilement.

### 2.5 Photo dans la notification (M)

`BigPictureStyle` avec la miniature de la fleur (URL fournie par 2.1).

- **Fichiers** : `push/FloraMessagingService.kt` ; réutiliser le client OkHttp
  simple de `sync/ImageCacher.kt`.
- **Pièges** : `onMessageReceived` a ~10 s de budget → téléchargement synchrone
  avec timeout court (2-3 s) et fallback sans image ; ne pas mettre d'URL
  présignée éphémère dans le payload (2.1 doit fournir une URL stable ou un
  `imageKey` re-signé à la volée).

### 2.6 Actions rapides (M)

❤️ ou « Répondre » (RemoteInput) directement depuis la notification.

- **Fichiers** : `app/src/main/java/com/florapin/app/push/NotificationActionReceiver.kt`
  (nouveau BroadcastReceiver, déclaré dans `AndroidManifest.xml`),
  `FloraMessagingService.kt` (ajout des actions), appels via
  `network/api/LikesApi.kt` et `CommentsApi.kt`.
- **Pièges** : pas d'appel réseau bloquant dans le receiver → `goAsync()` ou
  work one-shot WorkManager ; auth via `EncryptedTokenStore(applicationContext)`
  + `NetworkModule.createAuthenticated` (singleton, pas de nouveau client).

### 2.7 Centre de notifications in-app (M)

Cloche + badge non-lus ; le backend est déjà prêt
(`backend/src/notifications/notifications.controller.ts`, entité avec `readAt`).

- **Fichiers app** : `network/api/NotificationsApi.kt` + DTOs (nouveaux),
  `app/src/main/java/com/florapin/app/notifications/NotificationCenterScreen.kt`
  + ViewModel (nouveaux), route dans `navigation/FloraNavHost.kt`, cloche dans
  les top bars des écrans principaux.
- **Pièges** : le tap réutilise le routage de 2.2 (même résolution
  serverId → local) ; hors-ligne / sync OFF → écran indisponible, à assumer
  proprement (état vide explicite, pattern `EmptyState.kt`).

---

# Phase 3 — Feed & interactions sociales

### 3.1 Badge nouveautés sur l'onglet Partagées (S)

- **Fichiers** : `feed/FeedBadgeStore.kt` (nouveau, hérite de
  `util/SeenIdsStore.kt` comme `IdentifyBadgeStore`), `navigation/BottomNavBar.kt`
  (badge sur la destination FEED), `feed/SharedFeedViewModel.kt` (markSeen à
  l'ouverture).

### 3.2 Séparateur « Nouveau depuis votre dernière visite » (S)

- **Fichiers** : `feed/SharedFeedScreen.kt` + `SharedFeedViewModel.kt`
  (timestamp de dernière visite stocké dans `FeedBadgeStore` de 3.1).

### 3.3 Compteur de commentaires ✔️ (S)

💬 sur les cartes, à côté du cœur.

- **Fichiers backend** : `backend/src/flowers/flowers.service.ts`
  (`commentCount` dans `toResponse`/`toResponseMany`).
- **Fichiers app** : `network/dto/FlowerDtos.kt`, carte `SharedFlowerCard` dans
  `feed/SharedFeedScreen.kt` (~l.118).
- **Piège** : agréger comme `likeCount` (une requête groupée), **pas de N+1**
  sur la liste du feed.

### 3.4 Liste des likers ✔️ (S/M)

- **Fichiers backend** : `backend/src/likes/likes.controller.ts` +
  `likes.service.ts` (`GET /flowers/:id/likes` → liste `{userId, displayName}`),
  tests.
- **Fichiers app** : `network/api/LikesApi.kt`, `likes/LikersSheet.kt` (nouveau
  bottom sheet), tap sur le compteur dans `feed/SharedFeedScreen.kt` et
  `detail/DetailScreen.kt` via `likes/LikeViewModel.kt`.
- **Piège** : contrôle d'accès identique à `GET /flowers/:id` (owner ou ami
  ayant accès), ne pas exposer les likers d'une fleur privée.

### 3.5 Réactions enrichies (S annoncé, réellement M)

Jeu arrêté : **😍 🌸 🌹 🌼 🪻 🔍 👍**, compteurs par type.

- **Fichiers backend** : `backend/src/likes/flower-like.entity.ts` (colonne
  `reaction`, défaut = cœur), `backend/db/schema.sql`, `likes.controller.ts` +
  `likes.service.ts` (body optionnel sur le POST), agrégats par type dans
  `flowers/flowers.service.ts`.
- **Fichiers app** : `likes/LikeButton.kt` (long-press → picker),
  `likes/LikeViewModel.kt`, `network/dto/SocialDtos.kt`.
- **Pièges** : un seul like par utilisateur (clé composite `flowerId,userId`) —
  changer de réaction = **update**, pas insert ; compat ascendante : un POST
  sans body = réaction par défaut (anciennes apps) ; `ALTER TABLE` idempotent
  dans `schema.sql` à rejouer en prod.

### 3.6 Regroupement par lot (M/L) — *dépend de 1.2*

« Marie a partagé 48 fleurs » → un tap ouvre le lot.

- **Fichiers backend** : `backend/src/feed/feed.service.ts` + `dto/feed.dto.ts`
  (exposer `shareId`/`sharedAt` sur chaque item pour grouper fiablement).
- **Fichiers app** : `feed/SharedFeedViewModel.kt` (regroupement client par
  owner + shareId/fenêtre temporelle), `feed/SharedFeedScreen.kt` (carte-lot +
  vue dépliée).
- **Pièges** : un lot peut être **coupé entre deux pages** du curseur → le
  groupe doit se compléter au chargement de la page suivante (fusion par clé de
  lot, pas par position) ; garder un tri stable par date.

### 3.7 Brouillon de commentaire conservé (S)

- **Fichiers** : `detail/FlowerComments.kt` (persistance du texte saisi par
  `flowerServerId` — `rememberSaveable` + petit store prefs pour survivre à la
  fermeture de la sheet).

### 3.8 Éditer son commentaire (S/M)

- **Fichiers backend** : `backend/src/comments/flower-comment.entity.ts`
  (colonne `editedAt` — absente aujourd'hui, manque confirmé),
  `backend/db/schema.sql`, `comments.controller.ts` + `comments.service.ts`
  (PATCH, réservé à l'auteur).
- **Fichiers app** : `network/dto/CommentDtos.kt`, `detail/FlowerComments.kt`
  (menu Éditer, mention « modifié »).

### 3.9 Répondre à un commentaire (M)

Fil à un niveau (réponse citée).

- **Fichiers backend** : `flower-comment.entity.ts` (`replyToId` nullable),
  `schema.sql`, `comments.service.ts` (validation même fleur).
- **Fichiers app** : `detail/FlowerComments.kt` (UI citation + saisie).
- **Piège** : un seul niveau — une réponse à une réponse pointe le **parent
  racine** (aplatir côté serveur).

### 3.10 Mention @ami (M)

- **Fichiers backend** : `comments.service.ts` (parsing des mentions →
  notification `comment_mention` via `notifications.service.ts`).
- **Fichiers app** : `detail/FlowerComments.kt` (autocomplete alimenté par
  `network/api/FriendshipsApi.kt`).
- **Piège** : encoder la mention par **id** (`@[userId]`) et rendre le
  displayName à l'affichage — un renommage (1.7) ne doit pas casser les mentions.

### 3.11 Fleurs enregistrées — « ma sélection » (M)

Favori **privé et local** (device-first, pas d'API).

- **Fichiers** : `data/FloraDatabase.kt` (migration 13→14, table
  `saved_flowers`), nouvelle entité + DAO sous `data/`, action sur
  `SharedFlowerCard` (`feed/SharedFeedScreen.kt`), filtre « Ma sélection » dans
  le feed.
- **Piège** : la fleur d'ami n'existe **pas** en Room → stocker un snapshot
  (serverId, nom, miniature en cache) sinon rien à afficher hors-ligne ou si le
  partage est révoqué.

### 3.12 Feed en 2 colonnes (S)

- **Fichiers** : `feed/SharedFeedScreen.kt` (`LazyVerticalStaggeredGrid`).
- **Piège** : cartes-lot (3.6) et séparateurs (3.2) en pleine largeur
  (`StaggeredGridItemSpan.FullLine`).

---

# Phase 4 — Entraide / identification (le différenciateur)

### 4.1 Écran « Mes demandes » (M)

L'état de **ses propres** demandes : fleurs en attente, qui a proposé quoi.

- **Fichiers backend** : `backend/src/identification-requests/identification-requests.controller.ts`
  + `identification-requests.service.ts` (`GET /me/identification-requests` :
  mes fleurs `needsIdentification` + propositions reçues, en une requête — pas
  de composition N+1 côté client).
- **Fichiers app** : `identify/IdentifyScreen.kt` (onglet « Mes demandes ») +
  nouveau ViewModel, `network/api/IdentificationApi.kt` + DTOs.

### 4.2 Statut de la demande (S)

En attente / résolue, visible des deux côtés — dérivable de
`needsIdentification` + proposition acceptée, pas de nouvelle colonne.

- **Fichiers** : `identify/IdentifyScreen.kt`, `detail/ReceivedProposals.kt`,
  DTOs concernés.

### 4.3 « Merci 🌸 » en un tap (S)

- **Fichiers backend** : `backend/src/proposals/proposals.controller.ts` +
  `proposals.service.ts` (`POST .../proposals/:proposalId/thanks` → notification
  au proposeur), `notifications.service.ts` (nouveau type).
- **Fichiers app** : `detail/ReceivedProposals.kt`, `push/FloraMessagingService.kt`
  (texte du nouveau type).
- **Piège** : idempotent (un seul merci par proposition).

### 4.4 Relance manuelle (S)

- **Fichiers backend** : `identification-requests.controller.ts` + service
  (`POST /flowers/:id/identification-requests/remind`, re-notifie les amis).
- **Fichiers app** : bouton dans « Mes demandes » (4.1).
- **Piège** : anti-spam **côté serveur** (horodatage de dernière relance —
  colonne `lastRemindedAt` sur `flower.entity.ts` + `schema.sql` — refuser
  sous N jours), pas seulement un garde-fou UI.

### 4.5 Ajout d'ami par QR code (M)

Sur le terrain : j'affiche mon QR, l'ami le scanne → demande envoyée.
Fonctionne dès la bêta fermée (pas de lien web requis).

- **Fichiers app** : `friends/QrCodeSheet.kt` (affichage, génération
  zxing-core) + `friends/QrScanScreen.kt` (scan CameraX + ML Kit
  `camera-mlkit-vision` ou zxing) — nouveaux ; `friends/FriendsScreen.kt` +
  `FriendsViewModel.kt` (entrées UI), `gradle/libs.versions.toml` (dépendances),
  `network/api/FriendshipsApi.kt`.
- **Fichiers backend** : `friendships.controller.ts` + `friendships.service.ts`
  si la demande par **userId** n'existe pas encore (l'existant passe par email).
- **Pièges** : encoder le **userId (UUID)**, pas l'email (vie privée) ; la
  permission caméra est déjà gérée (`permission/`) mais le scan a son propre
  flux — réutiliser `PermissionUtils.kt` ; prévoir l'auto-acceptation croisée si
  chacun scanne l'autre (conflit `pending` symétrique, contrainte unique
  `requesterId,addresseeId`).

---

# Phase 5 — Profil, badges, herbier

Décisions actées (voir section « Décisions » en bas) : 3 onglets, table Room
`badges`, GPS → région embarqué, DA étoiles.

### 5.1 Refonte Profil / Réglages — 3 onglets (M/L)

**① Profil** (stats, image de profil, nb badges, herbier, dernières fleurs) ·
**② Badges** (grille) · **③ Configuration** (compte, sync, confidentialité,
déconnexion).

- **Fichiers app** : `profile/ProfileScreen.kt` (restructuration en onglets) +
  `ProfileViewModel.kt`, contenu Configuration = l'existant (toggle sync,
  logout, suppression de compte) + les ajouts 1.5/1.6/1.7.
- **Avatar** (rattaché) — backend : `backend/src/users/user.entity.ts`
  (colonne `avatarKey`), `schema.sql`, `users.controller.ts` + `users.service.ts`
  (upload via `storage/minio-storage.service.ts` + `image-processing.ts`) ;
  app : upload sur le modèle de `network/upload/ImageUploader.kt`, affichage Coil.
- **Piège** : ne pas casser le flux logout existant
  (`LocalSessionDataCleaner`, qui ne purge plus les fleurs — NODE-93, ne pas
  réintroduire).

### 5.2 Mapping GPS → région hors-ligne (M) 🔓 *débloque 5.3*

Polygones des 13 régions métropole + 5 outre-mer embarqués, point-in-polygon local.

- **Fichiers** : `app/src/main/assets/regions-fr.geojson` (nouveau — source
  [`france-geojson`](https://github.com/gregoiredavid/france-geojson), version
  **simplifiée**), `app/src/main/java/com/florapin/app/geo/RegionResolver.kt`
  (nouveau : parseur GeoJSON + ray-casting), tests unitaires avec points connus.
- **Pièges** :
  - Ordre GeoJSON = **`[longitude, latitude]`** — l'inversion est l'erreur classique.
  - Gérer `MultiPolygon` **et les anneaux intérieurs** (trous) du ray-casting.
  - Simplification trop agressive = fleurs mal classées près des limites
    régionales ; valider avec des points frontière.
  - Test bounding-box avant le point-in-polygon (perf) ; R-tree seulement si
    nécessaire.
  - Fleurs **sans GPS** : ne comptent pas (pas de région « inconnue » qui
    fausserait les paliers).

### 5.3 Badges collection — calcul local (M)

Liste arrêtée : 🌸 Première fleur · 📚 Herbier (10/50/100/250) · 🌿 Diversité
(10/25/50 espèces) · 🌷☀️🍁❄️ Saisons + 🍂 Quatre saisons · 🧭 Explorateur
(2/5/10/15/18 régions) · 🏝️ Outre-mer (5 badges) · 📍 Lieux distincts
(grille 5 km : 5/15/30/50/100).

- **Fichiers** : `data/FloraDatabase.kt` (migration : table `badges` —
  `badgeId`, `tier`, `unlockedAt`, `seen`), `data/BadgeEntity.kt` +
  `data/BadgeDao.kt` (nouveaux), `data/FlowerDao.kt` (requêtes agrégées : COUNT,
  `COUNT(DISTINCT speciesId)` avec fallback `species`, **toujours
  `deletedAt IS NULL`**), `app/src/main/java/com/florapin/app/badges/BadgeCalculator.kt`
  (nouveau : saisons via `takenAt`, grille 5 km, régions via `RegionResolver`).
- **Pièges** :
  - **Première exécution** sur une base existante : tout se débloque d'un coup
    → initialiser `seen=true` en masse (pas de pluie de célébrations), comme
    pour l'onboarding 1.4.
  - Grille 5 km : la largeur d'un degré de longitude varie avec la latitude —
    cellule = `floor(lat/Δ)`,`floor(lng/(Δ/cos(lat)))` ou assumer
    l'approximation (documenter le choix).
  - Saisons codées hémisphère nord (assumé, France d'abord).
  - Le `SeenIdsStore` existant **ne convient pas** (décision actée) — table Room.

### 5.4 Badges entraide — calcul serveur (M)

🤝 Amis (1/3/5/10) · 🔍 Proposer · 🎓 Acceptées (1/5/10/25/50) · ❓ Demander ·
✅ Accepter · 💬 Commenter · 👍 Réactions données (paliers) · ❤️ reçues (paliers).

- **Fichiers backend** : `backend/src/badges/badges.module.ts` +
  `badges.controller.ts` (`GET /me/badges`) + `badges.service.ts` (nouveaux,
  sur le modèle du module `likes` — agrégation en lecture, pas de table),
  enregistrement dans `backend/src/app.module.ts`, tests.
  Sources : `Friendship status='accepted'`, `SpeciesProposal`
  (`proposedBy`/`status='accepted'` — attention : **pas de colonne `accepted`
  booléenne**, c'est `status`), `Flower.needsIdentification`, `FlowerComment`,
  `FlowerLike` (donnés = `userId`, reçus = via `flower.ownerId`).
- **Fichiers app** : `network/api/BadgesApi.kt` + DTOs (nouveaux), fusion
  local + serveur dans le ViewModel de l'onglet Badges.
- **Pièges** : quelques COUNT ciblés en une méthode, pas de N+1 ; hors-ligne →
  badges serveur affichés depuis un cache local (dernière valeur) ou grisés,
  choix assumé device-first ; recalcul à la volée d'abord (table `user_badges`
  seulement si notification d'obtention côté serveur un jour).

### 5.5 UI Badges — grille + étoiles (M)

DA actée : rangée d'**étoiles grisées** = paliers atteignables, se remplissent ;
famille sans aucun palier = badge grisé. Progression « 34 / 50 ».

- **Fichiers** : `ui/components/BadgeCard.kt` (nouveau composant partagé),
  `ui/theme/Color.kt` (palette états), onglet Badges de 5.1, célébration au
  déblocage (haptique — cf. QOL 6.15).

### 5.6 Herbier / stats de collection (M/L)

Page « Mon herbier » : espèces distinctes, regroupement par familles botaniques
(référentiel embarqué + familles créables, normalisation backend).

- **Fichiers backend** : `backend/src/species/species.entity.ts` (le champ
  `family` existe déjà), `species.service.ts` (normalisation/rapprochement des
  familles, sur le modèle de `resolveOrCreateByName`), `backend/db/seed-species.sql`
  (référentiel de familles).
- **Fichiers app** : `app/src/main/java/com/florapin/app/herbier/HerbierScreen.kt`
  + ViewModel (nouveaux), route dans `FloraNavHost.kt`, entrée depuis l'onglet
  Profil (5.1), agrégats via `data/FlowerDao.kt`.
- **Piège** : la `family` vit sur `Species` **côté serveur** — les fleurs en
  espèce texte libre (sans `speciesId`) ne se regroupent qu'approximativement ;
  hors-ligne, le volet familles est partiel (assumé).

### 5.7 Profil d'ami + amis en commun + ancienneté (M + S + XS)

- **Fichiers backend** : endpoint profil public limité
  (`friendships.controller.ts` ou `users.controller.ts` :
  `GET /users/:id/profile` — stats visibles, fleurs partagées **avec moi**,
  espèces communes, nb d'amis en commun, `friendship.createdAt` pour
  « Amis depuis mai 2026 »).
- **Fichiers app** : `friends/FriendProfileScreen.kt` + ViewModel (nouveaux),
  route `FloraNavHost.kt`, entrées depuis `FriendsScreen.kt` et les cartes du feed.
- **Piège** : ne renvoyer **que** ce qui m'est déjà accessible (fleurs
  partagées avec moi ou broadcast) — pas les stats privées de l'ami.

---

# Phase 6 — QOL / confort

### Capture

| # | Tâche | Fichiers | Pièges |
|---|---|---|---|
| 6.1 | **Flash / torche** ✔️ (S) | `capture/CameraScreen.kt` | `controller.imageCaptureFlashMode` (flash à la prise) ≠ `enableTorch` (torche continue) — offrir les deux ; UI à côté du `FilterChip` macro existant |
| 6.2 | **Tap-to-focus** (S) | `capture/CameraScreen.kt` | `MeteringPointFactory` du `PreviewView` (coordonnées vue, pas capteur) ; ne pas casser `applyMacroFocus` (interop Camera2 existante) — le tap doit respecter le mode macro actif |
| 6.3 | **Grille de composition** (XS) | `capture/CameraScreen.kt` (overlay Canvas + toggle) | — |
| 6.4 | **Déclencheur au volume** (XS) | `capture/CameraScreen.kt`, `MainActivity.kt` (KeyEvent) | n'intercepter `VOLUME_DOWN` que quand l'écran capture est visible |
| 6.5 | **Indicateur de fix GPS** (S) | `capture/CaptureFlow.kt`, module `location/` | avertir si position indisponible **avant** la prise, pas après |

### Galerie

| # | Tâche | Fichiers | Pièges |
|---|---|---|---|
| 6.6 | **Multi-sélection appui long** (M) | `gallery/GalleryScreen.kt` + `GalleryViewModel.kt`, `albums/AddToAlbumSheet.kt` (réutilisé en lot) | suppression groupée = soft delete (`deletedAt`) pour rester compatible sync |
| 6.7 | **En-têtes par mois + fast scroller** (S) | `gallery/GalleryScreen.kt` | grouper par mois de `takenAt`, pas de date de création |
| 6.8 | **Densité de grille réglable** (XS) | `gallery/GalleryScreen.kt` + pref (store dédié, **pas** `florapin_sync`) | — |
| 6.9 | **États vides soignés** (S) | `ui/components/EmptyState.kt` (existant, à enrichir), galerie/feed/albums | — |

### Détail d'une fleur

| # | Tâche | Fichiers | Pièges |
|---|---|---|---|
| 6.10 | **Swipe entre fleurs** (M) | `detail/DetailScreen.kt` (+ `FloraNavHost.kt`) | `HorizontalPager` autour du détail : passer la liste ordonnée d'ids (ou partager le VM de la galerie), pas une navigation par fleur |
| 6.11 | **Ouvrir dans Maps / copier coordonnées** (XS) | `detail/DetailScreen.kt` (menu mini-carte, Intent `geo:`) | — |
| 6.12 | **Partage externe de la photo** (S) | `detail/DetailScreen.kt`, `AndroidManifest.xml` + `res/xml/file_paths.xml` (FileProvider — nouveau) | les photos sont en stockage privé (`PhotoStorage`) → FileProvider obligatoire, authority unique |
| 6.13 | **Annuler la suppression** (S) | `detail/DetailScreen.kt` / `gallery/GalleryViewModel.kt` (snackbar) | différer le soft delete de quelques secondes **ou** restaurer `deletedAt=null` avant la prochaine passe de sync (mutex `SYNC_LOCK` : pas de course tant que la passe n'a pas tourné) |

### Sync, réseau, général

| # | Tâche | Fichiers | Pièges |
|---|---|---|---|
| 6.14 | **État de sync visible** (M) | onglet Configuration (5.1), `gallery/GalleryScreen.kt` (badge discret sur fleurs `PENDING` — `FlowerEntity.syncState` existe déjà), `sync/SyncWorker.kt` (exposer résultat/erreur, `LastSyncStore` pour l'horodatage) | lire `last_sync_at` via `PrefsLastSyncStore`, ne pas toucher au fichier partagé autrement |
| 6.15 | **Retour haptique** (XS) | `util/Haptics.kt` (nouveau), points d'appel : `likes/LikeButton.kt`, obturateur `CameraScreen.kt`, déblocage badge (5.3) | — |
| 6.16 | **Erreurs réseau humaines** (S) | composant commun sous `ui/components/`, mapping IOException/HTTP dans les ViewModels réseau (feed, détail, amis, auth) | distinguer « mode avion » (IOException) de « serveur injoignable » (5xx/timeout) + bouton réessayer |
| 6.17 | **Transitions partagées** (S/M) | `gallery/GalleryScreen.kt` ↔ `detail/DetailScreen.kt`, `FloraNavHost.kt` | `SharedTransitionLayout` encore expérimental dans le BOM 2024.12.01 — isoler derrière un composant pour pouvoir désactiver |
| 6.18 | **Accessibilité** (M) | passe transverse : `contentDescription` (icônes emoji de `BottomNavBar.kt` incluses), cibles ≥ 48 dp, tailles de police dynamiques | les emojis utilisés comme icônes sont lus littéralement par TalkBack → libellés explicites |

---

# Phase 7 — Gros chantiers (L)

### 7.1 Albums collaboratifs = groupes (L) — décision actée n°1

Créer un album crée le groupe ; rattachement d'autres albums ; droits par album
(tout ouvert **ou** au cas par cas) ; découplé du partage réseau.

- **Fichiers backend** : nouveau modèle — `group.entity.ts`,
  `group-member.entity.ts`, droits sur `backend/src/albums/album.entity.ts`
  (rattachement `groupId` + table de droits), `schema.sql`, module
  `backend/src/groups/` (controller/service), extension d'`albums.service.ts`,
  notifications de groupe (`notifications.service.ts`).
- **Fichiers app** : `albums/AlbumsScreen.kt`, `AlbumDetailScreen.kt` + VMs
  (invitations, membres, droits), `network/api/AlbumsApi.kt` + DTOs, sync
  (`sync/AlbumSyncEngine.kt` — les albums de groupe entrent dans le moteur existant).
- **Pièges** : c'est le plus gros chantier du plan — **à découper en sous-tâches
  au démarrage** (modèle de données d'abord) ; la sync d'albums existante
  suppose des albums possédés — le modèle multi-membres impacte
  `AlbumSyncEngine.kt` (conflits d'édition concurrente) ; matrice de droits à
  tester côté backend en priorité (e2e).

---

# Dépendances entre tâches

```
1.2 Pagination ──────────────► 3.6 Regroupement par lot
1.7 displayName modifiable ──► 2.1 Textes incarnés (résolution du nom à l'envoi)
2.1 Payload enrichi ─────────► 2.2 Tap→contenu · 2.5 Photo notif
2.2 Routage notif ───────────► 2.7 Centre de notifications (même routage)
5.1 Profil 3 onglets ────────► 5.5 UI Badges · 5.6 Herbier (emplacements)
5.2 GPS→région ──────────────► 5.3 Badges collection
3.1 FeedBadgeStore ──────────► 3.2 Séparateur « Nouveau »
```

---

# Non prioritaire (sans description — voir historique si besoin)

- 🗺️ **Carte hors-ligne** — repoussée **le temps de vérifier les ToS MapTiler**
  (le cache offline de tuiles est-il autorisé par le plan actuel ? quotas ?).
  À réintégrer au plan une fois la question contractuelle levée ; les pièges
  techniques (API `OfflineManager` MapLibre, estimation du poids, clé API dans
  le style URL) sont notés dans l'historique git de ce fichier.
- 🐞 Bug : sync serveur non déclenchée à l'activation réseau (analyse détaillée
  faite le 2026-07-04 : piste principale = switch « Options réseau » pré-coché
  sur OFF, `FloraNavHost.kt:168` ; à confirmer en runtime — contrainte
  `NetworkType.CONNECTED`, partage du fichier de prefs `florapin_sync`).
- Recherche avancée · Carte heatmap/mode découverte · Filtres du feed ·
  Réactiver « Vérifier mon email » · Import depuis la galerie · Rappels
  saisonniers · Corriger la position · Modifier la date de capture ·
  Material You · App shortcuts · Tablette/pliables · Diagnostiquer Crashlytics ·
  Clarifier les analytics · Recruter 12+ testeurs · Bêta ouverte puis production ·
  Liens d'invitation/partage web · i18n anglais · iOS (KMP).

---

# Décisions de conception (actées)

1. **Albums collaboratifs = groupes** — ✅ acté. Créer un album crée le groupe ;
   rattachement d'autres albums ; droits par album (tout ouvert **ou** au cas
   par cas) ; découplé du partage réseau. → Phase 7.1.
2. **Badges** — ✅ acté. Liste arrêtée (local/serveur), Herbier + Diversité tous
   deux retenus, persistance en **table Room `badges`**, mapping GPS → région
   embarqué, paliers « Lieux distincts » figés (5/15/30/50/100), DA étoiles
   grisées, emplacement onglet « Badges » du Profil. → Phases 5.2–5.5.
3. **Refonte Profil/Réglages** — ✅ acté. 3 onglets Profil · Badges ·
   Configuration. → Phase 5.1.
4. **Regroupement du feed** — ✅ acté. Carte-lot « Marie a partagé 48 fleurs »,
   ouvrable au tap, articulée avec la pagination par curseur. → Phases 1.2 + 3.6.
