# Revue complète du projet FloraPin

> Date : 2026-07-26 — Référence : `main` @ `43764d3` (release 1.23.0)
> Périmètre : backend NestJS, app Android, landing, sécurité transverse, tests/CI/docs.
> Revue précédente : [`CODE-REVIEW-2026-07-02.md`](CODE-REVIEW-2026-07-02.md) (1.7.0).

## Verdict global

Progression très nette depuis la revue du 2026-07-02. **Les 4 problèmes critiques et la
quasi-totalité des 19 importants sont corrigés**, avec des correctifs qui vont au-delà du
minimum demandé (verrou de sync process-wide, état `imagePendingUpload` et retry,
anti-énumération sans 404, purge MinIO planifiée, échappement LIKE, garde-fou `change-me`
au déploiement, job backend + e2e en CI).

État de santé mesuré ce jour :

| Contrôle | Résultat |
|---|---|
| Tests backend (`npm test`) | **258 passés / 258**, 32 suites |
| Tests Android (`testDebugUnitTest`) | **312 passés / 312**, 55 classes |
| Lint Android (`lintDebug`) | **0 erreur**, 137 warnings |
| Secrets dans le dépôt / l'historique | aucun (hors `google-services.json`, déjà connu) |
| Injection SQL | aucune (interpolations issues de constantes uniquement) |
| Modules backend sans spec | **aucun** (23/23 couverts) |

Les problèmes restants ne sont plus des défauts de conception : ce sont **deux échéances
externes** (une CVE dans une dépendance de traitement d'image, la deadline Play Store API 36)
et de la dette d'hygiène.

---

## 🔴 Critique

### ~~C1. `sharp` < 0.35.0 — 4 CVE libvips sur le chemin des images utilisateur~~ — ✅ traité le 2026-07-26

`GHSA-f88m-g3jw-g9cj` : CVE-2026-33327, -33328, -35590, -35591, héritées de libvips
(deux en gravité haute CVSSv4). Le backend tournait sur **sharp 0.33.5 / libvips 8.15.3** ;
correctif à partir de sharp 0.35.0 (libvips 8.18.3).

**Exposition réelle, vérifiée** — plus étroite que ne le laissait entendre la première
rédaction de cette fiche, qui s'arrêtait à « sharp reçoit des octets utilisateur » :

- Les formats visés par l'avis sont **GIF, TIFF et VIPS** (la parade officielle consiste à
  bloquer `VipsForeignLoadNsgif`, `VipsForeignLoadTiff`, `VipsForeignLoadVips`).
- Cinq points d'entrée authentifiés (inscription ouverte) passent des octets bruts à sharp :
  image de fleur legacy, variantes de fleur, photos additionnelles (deux chemins) et avatar
  (`users.service.ts:138`).
- Le filtre MIME de `image-upload.options.ts` ne protège pas — il ne lit que le type
  **déclaré**, donc un GIF envoyé en `Content-Type: image/jpeg` passe.
- **Mais** `encodeWebp` applique une liste blanche sur le format RÉEL (`jpeg`, `png`,
  `webp`, `heif`) et `validateClientImageVariants` exige `webp` : un GIF/TIFF est rejeté
  **avant tout décodage de pixels**.
- Le loader vulnérable reste néanmoins sollicité pour lire l'en-tête. Vérifié
  empiriquement : `sharp(gif).metadata()` renvoie `format = 'gif'`, `sharp(tiff)` renvoie
  `'tiff'` — le code du loader s'exécute donc bel et bien.

Conclusion : exposition partielle, limitée à la phase d'en-tête, d'exploitabilité
indéterminée (l'avis ne publie pas le type exact de chaque faille). Correctif d'hygiène
justifié, pas une porte grande ouverte.

**Appliqué** : `sharp@0.35.3` → **libvips 8.18.3** confirmé au runtime. Aucune API utilisée
ici n'a changé (`sharp(buf, {failOn}).metadata()`, `.rotate()`, `.resize()`, `.webp()`,
`.toBuffer()`). Build OK, **258 tests passés / 258**, dont `image-processing.spec.ts` et
`client-image-variants.spec.ts` qui couvrent précisément ces chemins.

### ~~C2. `targetSdk 35` face à l'échéance Play Store API 36 (≈ 31 août 2026)~~ — ✅ traité le 2026-07-26

L'app ciblait API 35 à un mois de l'échéance Play Store, et **AGP 8.7.3 (déc. 2024) ne
savait pas compiler contre API 36**. Montée effectuée :

- `gradle/libs.versions.toml` — AGP 8.7.3 → **8.11.1** (premier palier supportant
  officiellement `compileSdk 36`) ;
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.11.1 → **8.13** (minimum exigé
  par cet AGP) ;
- `app/build.gradle.kts` — `compileSdk` et `targetSdk` à **36**.

Les trois changements de comportement du palier ne s'appliquent pas à cette app :
edge-to-edge était déjà en place (`MainActivity.enableEdgeToEdge()` + insets gérés écran
par écran, l'app ciblant déjà 35 où l'enforcement existe) ; aucun `screenOrientation` ni
`resizeableActivity` déclaré ; aucun service au premier plan (`FloraMessagingService` est
un service FCM, la sync passe par WorkManager). `BackHandler` (androidx.activity.compose)
reste compatible avec le predictive back activé par défaut en API 36.

Validé : `testDebugUnitTest` **342 passés / 342**, `lintDebug` **0 erreur**,
`assembleDebug` + `assembleDebugAndroidTest` OK, et `aapt2 dump badging` confirme
`targetSdkVersion:'36'` / `compileSdkVersion='36'` dans l'APK produit.

> Effet de bord local : lint 8.11 applique la règle `PropertyEscape` et refusait
> `sdk.dir=C:/…` dans `local.properties` (fichier machine, gitignoré) — le `:` doit
> s'écrire `C\:/…`. Sans incidence en CI, où `local.properties` n'existe pas.

---

## 🟠 Important

### I1. Autres vulnérabilités npm — ✅ traité le 2026-07-26 (backend)

| Paquet | Sévérité | Avis | Avant → Après |
|---|---|---|---|
| `typeorm` | moderate | `GHSA-2rp8-mm9q-fp49` — injection template literal dans `migration:generate` | 0.3.30 → **0.3.31** |
| `fast-xml-parser` | high | `GHSA-8r6m-32jq-jx6q` (via le SDK MinIO) | 5.9.3 → **5.10.1** |
| `brace-expansion` | high | `GHSA-3jxr-9vmj-r5cp`, `GHSA-mh99-v99m-4gvg` (DoS) | 1.1.15/2.1.1/5.0.6 → **1.1.16/2.1.2/5.0.8** |
| `protobufjs` | moderate | `GHSA-j3f2-48v5-ccww` (via firebase-admin) | 7.6.4 → **7.6.5** |

À noter, la CVE TypeORM ne concernait pas ce projet en pratique : elle vise
`migration:generate`, or le schéma vit dans `db/schema.sql` avec `synchronize: false` et le
projet n'utilise pas les migrations TypeORM.

> `npm audit fix` était temporairement inutilisable — l'endpoint
> `/-/npm/v1/security/advisories/bulk` renvoyait un corps gzip corrompu (« invalid json
> response body »). Les paquets ont donc été montés directement (`npm i typeorm@^0.3.31`,
> `npm update fast-xml-parser brace-expansion protobufjs`), puis l'audit rejoué une fois le
> registre revenu.

**Reste 1 vulnérabilité, laissée en l'état délibérément** : `brace-expansion` 2.1.2 sous
`node_modules/glob/node_modules/` (`GHSA-mh99-v99m-4gvg`, DoS par expansion d'accolades ;
le range vulnérable `<=5.0.7` englobe toute la branche 2.x). Les 5 autres lignes du rapport
d'audit ne sont que sa chaîne de dépendants (minimatch → glob → rimraf → google-gax / typeorm).

Les deux issues sont refusées, sur vérification et non par principe :

1. **Override vers 5.0.8 : casserait le graphe.** `brace-expansion` 5.x exporte un objet
   (`{ EXPANSION_MAX, EXPANSION_MAX_LENGTH, expand }`) là où 2.x exportait la fonction
   directement — vérifié en installant le paquet à part. `minimatch@9.0.9` fait
   `const expand = require('brace-expansion')` puis `expand(...)` : l'override produirait un
   `TypeError` dans glob, donc dans rimraf, google-gax, **firebase-admin (les push)** et typeorm.
2. **`npm audit fix --force` : disproportionné.** Son seul chemin est `typeorm@1.1.0`, un
   saut de version majeure sur l'ORM, pour une faille non atteignable ici.

Non atteignable, en effet : l'exploitation suppose de contrôler un pattern glob, or aucun
des consommateurs en production (rimraf via firebase-admin, typeorm) n'en reçoit un qui
vienne d'un utilisateur de l'API. À revoir quand `minimatch` publiera une version alignée
sur `brace-expansion` 5.x.

Landing : **non traitée** — 6 vulnérabilités, Astro 4.16 alors que la 7 est disponible.
Impact réel faible (build statique, pas de rendu serveur), mais le décalage majeur va finir
par coûter cher.

### I2. `screenshots/` (65 Mo) et `.codex-remote-attachments/` (1,1 Mo) ni trackés ni ignorés

`git status` liste 18 entrées, dont ces deux répertoires en `??`. Un `git add -A` ou un
`git add .` réflexe injecte **66 Mo de binaires** dans l'historique — irréversible sans
réécriture. Le `.gitignore` couvre `/dist/` mais pas ceux-là.

**Correctif** : ajouter `/screenshots/` et `/.codex-remote-attachments/` au `.gitignore`
(ou les tracker délibérément si les captures Play Store doivent l'être).

### I3. URLs de téléchargement présignées à 7 jours, non révocables et non documentées

`backend/src/storage/storage.module.ts:75` : `STORAGE_DOWNLOAD_PRESIGN_EXPIRES` vaut
**604 800 s (7 jours)** par défaut — le maximum SigV4. Le choix est motivé et commenté
(`minio-storage.service.ts:48-56`) : l'app persiste ces URLs en base locale.

Deux conséquences à assumer explicitement :

- **Aucune révocation possible.** Retirer un ami, supprimer un partage ou repasser une fleur
  en `private` ne coupe pas l'accès : qui détient déjà l'URL lit l'image jusqu'à 7 jours.
  Cela mérite une ligne dans la politique de confidentialité.
  (L'effacement de compte, lui, est propre : `users.service.ts:195-216` collecte
  `imageKey` + `thumbnailKey` des fleurs — soft-deletes inclus via `withDeleted` —, des
  photos et l'avatar, puis supprime les objets. La suppression de l'objet invalide l'URL.)
- **La variable n'est documentée nulle part** : absente de `backend/.env.example` (qui ne
  déclare que `STORAGE_PRESIGN_EXPIRES=600`), de `deploy/.env.example` et de
  `deploy/docker-compose.yml`. Un opérateur ne peut pas la régler sans lire le code.

Piste : 24 h suffisent si la sync rafraîchit les URLs à chaque passe (elle le fait déjà) —
`ImageCacher` télécharge de toute façon les images en local pour ne plus en dépendre.

### I4. Modules Android sans tests unitaires — ✅ `albums/` traité le 2026-07-26

18 modules sur 27 avaient des tests. Le plus risqué, **`albums/`** — module refondu
récemment (`bfa4041`), avec une logique de permissions (`open` / `restricted`, `canEdit`)
au coût d'erreur réel — en a maintenant **30** :

- `AlbumsViewModelTest` (13) : création locale et collaborative (nom nettoyé, `clientId`
  idempotent, album rendu déjà synchronisé, échec réseau ⇒ message sans création locale),
  renommage, suppression, rattachement de fleurs en lot, couvertures vivantes ;
- `AlbumCollaborationViewModelTest` (17) : chargement solo (aucun appel réseau) vs
  collaboratif, filtrage des amis invitables (membres et demandes `pending` exclus),
  `isOwner`, préservation du nom local en attente de sync lors d'un réglage serveur, et
  les droits — `setMemberCanEdit` fusionne les entrées existantes et force `restricted`,
  un retrait est une entrée `false` explicite, un album non synchronisé n'émet aucun appel.

Prérequis appliqué : les trois ViewModels d'albums passent d'`AndroidViewModel` (qui
construisait `AlbumRepository`/`NetworkModule` en interne, donc intestable en JVM pur) à
l'injection par constructeur + `companion object { fun factory(context) }` — le pattern
déjà en vigueur ailleurs (cf. `SharedFeedViewModel`) et prescrit par `ROADMAP.md`.

Restent sans tests : `likes/` (3 fichiers), `permission/` (5), `location/` (3),
`onboarding/`, `update/`, `util/`.

### I5. `bcryptjs` bloque l'event loop

`backend/package.json` : `bcryptjs` (JS pur) avec `BCRYPT_ROUNDS = 10`
(`auth.service.ts:24`). Chaque `register`, `login`, `changePassword` et `resetPassword`
occupe le thread principal Node ~100 ms, **sans rendre la main** — contrairement au `bcrypt`
natif qui délègue au threadpool libuv. Avec le throttler en place le risque de DoS est
contenu, mais la latence est subie par toutes les requêtes concurrentes.

Déjà signalé en mineur le 2026-07-02, non traité. `bcrypt` (natif) ou `argon2` sont des
remplacements directs ; la migration des hashes existants est transparente pour `bcrypt`.

---

## 🟡 Mineur

- ~~**`npm run lint` du backend est cassé**~~ — ✅ traité le 2026-07-26. Le script appelait
  `eslint`, absent des `devDependencies` **et** du `package-lock.json` ; invisible parce que
  la CI enchaînait `build` + `test` + `test:e2e` sans jamais appeler `lint`. Désormais :
  ESLint 9 en flat config (`backend/eslint.config.mjs`) avec `typescript-eslint` type-aware,
  **sans Prettier** (il aurait reformaté tout le code existant et noyé l'historique Git).
  Le lint est branché au job backend de la CI, entre `npm ci` et `npm run build`.
  **Résultat final : 0 erreur, 0 avertissement.**

  Le premier passage donnait 18 erreurs et 93 avertissements. Traitement :

  | Constat | Traitement |
  |---|---|
  | 7 assertions de type inutiles | retirées (`--fix`) |
  | `jwt.decode(...) as {exp:number}` | → `jwt.decode<{exp:number}>()` |
  | 2 `unbound-method` (`species.service.ts`) | méthode statique en référence → lambda |
  | `no-unsafe-enum-comparison` | seuil 5xx extrait en constante `number` |
  | 1 variable morte (spec) | affectation retirée |
  | 7 `require-await` | règle désactivée — pilotes de repli honorant un contrat `Promise<T>` (stub-storage, -mail, -push, -identifier) ; retirer `async` casserait l'interface |
  | 15 `any` dans `admin.service.ts` | helper `rows<T>()` typant les retours de `DataSource.query` (via `unknown`), + interfaces de lignes (`CountRow`, `RecentUserRow`, `ClientLogRow`…) |
  | 7 `any` dans `minio-storage.service.ts` | `listObjectsV2` typé (`ObjectListStream`) au lieu de `(...args: any[]) => any` |
  | 2 `any` dans `current-user.decorator.ts` | `getRequest<{ user: AuthenticatedUser }>()` |
  | 1 `any` dans `update-profile.dto.ts` | `@Transform(({ value }: { value: unknown }) => …)` |
  | 4 `any` dans des specs | paramètres de `jest.fn()` typés, `expect.stringMatching` nommé une fois |
  | 63 `any` restants (specs + e2e) | règles « unsafe » désactivées **pour les fichiers de test uniquement** |

  Cette dernière ligne est un choix, pas un abandon : les matchers Jest
  (`expect.objectContaining`) et le `.body` de supertest rendent `any` **par
  conception**, et aucun typage raisonnable ne les rattrape. En contrepartie, les
  règles « unsafe » passent de `warn` à **`error` sur `src/`** — le code de
  production étant à zéro, tout nouveau `any` qui s'échappe fait désormais échouer
  la CI au lieu de se fondre dans un bruit de fond.
- **Dossier `app/src/main/java/com/florapin/app/herbier/` vide** (0 fichier) — reliquat à supprimer.
- **`SharedHeaderAction` : paramètre mort** (travail en cours, `feed/SharedFeedScreen.kt:338-343`).
  `badge: Int = 0` n'est fourni par aucun des deux appelants (l. 131, 136) : le `BadgedBox`
  qui l'entoure ne s'affichera jamais. Soit câbler le badge de demandes d'amis (cohérent avec
  la galerie, qui l'affiche déjà — sinon l'accès « Amis » du feed ne signale pas les demandes
  en attente), soit retirer le paramètre et le `BadgedBox`.
- **Toutes les chaînes UI sont codées en dur dans le Kotlin.** `app/src/main/res/values/strings.xml`
  ne contient que 5 entrées pour 244 composables. L'i18n est hors de portée sans une passe
  d'extraction complète, et les textes ne sont pas relisibles hors du code. À trancher
  explicitement : soit c'est un choix assumé (app FR uniquement), soit c'est de la dette à
  provisionner avant qu'elle ne double.
- **Écrans monolithiques** : `detail/DetailScreen.kt` 1802 l. / 25 composables,
  `capture/CameraScreen.kt` 1481 l. / 18, `profile/ProfileScreen.kt` 1358 l. / 18,
  `gallery/GalleryScreen.kt` 1238 l. Aucun n'est incohérent, mais ils dépassent le seuil où
  une modification locale se relit confortablement. Découpage par sous-fichiers de composants
  (le module `albums/` montre déjà le bon pattern).
- **Docs en décalage** :
  - `ROADMAP.md:37` annonce « Room v13 » alors que `FloraDatabase.kt:25` est en **v18** ;
  - `backend/docs/API.md` ne documente ni `badges`, ni `groups`, ni `admin` ;
  - `README.md:23-60` liste `herbier` implicitement absent et ne mentionne pas `badges`/`groups`.
- **Couverture des migrations Room** : `MigrationTest` a 2 cas (v1→latest, v12→v13).
  L'enchaînement complet est bien traversé, mais aucun test n'entre à une version
  **intermédiaire** (une base v15 en production qui migre vers v18). Un cas paramétré
  vN→latest pour les 3-4 dernières versions couvrirait le scénario réel de mise à jour.
- **`hs_err_pid18488.log`** (348 Ko, crash JVM du 2026-07-10) traîne à la racine.
  Ignoré par `*.log`, mais à supprimer.
- **Dashboard admin** : `GET /api/v1/admin/dashboard` sert le HTML **sans guard** (les
  endpoints de données, eux, sont protégés par `AdminGuard` en `timingSafeEqual`). C'est
  documenté comme voulu (« coquille sans données sensibles ») et le token vit en
  `sessionStorage`. Correct, mais la page révèle publiquement l'existence et la structure de
  la console — un `AdminGuard` sur la coquille aussi ne coûterait rien.
- **Lint** : 137 warnings, dont `DefaultLocale` (conversions de casse sans locale — piège
  classique en turc), `AutoboxingStateCreation`, `ExifInterface` (préférer
  `androidx.exifinterface`), `UnusedResources`. Aucun bloquant ; une passe de nettoyage
  ramènerait le bruit à un niveau où un nouveau warning se voit.

---

## ✅ Corrigé depuis la revue du 2026-07-02

Vérifié dans le code, point par point :

| Réf. | Sujet | État |
|---|---|---|
| C1 | Uploads non bornés | ✅ `imageUploadOptions` (15 Mo, filtre MIME, `files: 1/2`) + `MAX_UPLOAD_EXPIRY_SECONDS = 300` sur les PUT présignés |
| C2 | Rate limiting | ✅ `ThrottlerModule` global 100 req/min + `@Throttle` strict sur `/auth/*` et `/diagnostics/logs` |
| C3 | Sync : suppression non propagée, pull écrasant | ✅ soft-delete propagé + `purgeLocal`, `syncState` respecté au pull (`SyncEngine.kt:213`), 404 idempotent, `HttpException` sur `Response<T>` |
| C4 | `allowBackup` sans règles | ✅ `dataExtractionRules` + `fullBackupContent` déclarés |
| I1 | Likes sans contrôle de visibilité | ✅ `visibleFlowerOrThrow` via `SharesService.isVisibleTo` |
| I2 | Device token supprimable par autrui | ✅ `unregister(userId, token)`, `purgeToken` non exposé |
| I3 | Énumération d'emails | ✅ réponse synthétique de même forme, aucune ligne créée |
| I4-I6 | Fuites MinIO / RGPD | ✅ `StorageCleanupService` + purge des `thumbnailKey` |
| I7 | `POST /sync/flowers` non idempotent | ✅ dédup sur `clientId` (`flowers.service.ts:118-127`) |
| I8 | Workers de sync concurrents | ✅ `Mutex` process-wide dans `SyncWorker.doWork` |
| I9 | Upload d'image perdu après `markSynced` | ✅ `setImagePendingUpload` + `retryPendingImageUploads` |
| I11 | `POST_NOTIFICATIONS` jamais demandée | ✅ `permission/NotificationPermission.kt` |
| I15/I16 | Secrets de déploiement | ✅ `SSHPASS` par env + mot de passe par stdin ; garde-fou `grep change-me` |
| I18 | Backend absent de la CI | ✅ job `backend` : `npm ci` + build + unit + **e2e Testcontainers** |
| I19 | N+1 backend | ✅ batch des likes et `COUNT ... GROUP BY` des commentaires |
| — | CORS `*`, helmet, Swagger public | ✅ CORS fermé par défaut, `helmet()`, Swagger masqué en prod |
| — | JWT valide après suppression de compte | ✅ `JwtStrategy.validate` vérifie l'existence → 401 |
| — | ILIKE non échappé | ✅ `escapeLike` (`species.service.ts:45`) |
| — | Sync ON par défaut vs décision projet | ✅ OFF par défaut + `migrateDefaultForExistingInstall` |
| — | `permissions:` absent de la CI | ✅ `contents: read` + `concurrency` |

Restent non traités : `bcryptjs` (I5 ci-dessus), clé Firebase dans l'historique git
(I17 — sans gravité tant que le dépôt est privé et la clé restreinte par package + SHA-1),
et la doc API incomplète.

---

## Plan d'action suggéré

1. ~~`sharp@^0.35.3` + montée des dépendances vulnérables~~ — ✅ fait le 2026-07-26 (C1, I1).
   Reste à faire tourner `npm run test:e2e` (Docker indisponible localement au moment de
   l'opération ; la CI l'exécute sur `ubuntu-latest`).
2. ~~Montée API 36~~ — ✅ fait le 2026-07-26 (C2).
3. `.gitignore` : `/screenshots/`, `/.codex-remote-attachments/` ; supprimer `hs_err_pid18488.log`
   et le dossier `herbier/` vide (I2).
4. Documenter `STORAGE_DOWNLOAD_PRESIGN_EXPIRES` dans les deux `.env.example` + le
   `docker-compose.yml`, et arbitrer 7 j vs 24 h (I3).
5. ~~Tests unitaires du module `albums/`~~ — ✅ fait le 2026-07-26, 30 cas (I4).
   Reste `likes/`, `permission/`, `location/`.
6. Trancher `SharedHeaderAction` : câbler le badge amis ou retirer le paramètre (travail en cours).
7. Remettre les docs à jour : ROADMAP (Room v18), API.md (badges/groups/admin), README.
8. Ensuite : `bcrypt` natif, découpage des écrans > 1000 lignes, passe lint, extraction des
   chaînes si l'i18n est au programme.
