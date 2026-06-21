# 🚀 Lancer & tester FloraPin

Ce guide explique comment lancer et tester FloraPin de bout en bout. Il y a
**deux blocs indépendants** : l'app Android (fonctionne seule, hors-ligne) et le
backend (cloud / partage). On peut tester l'app sans backend.

## Prérequis

| Outil | Version | Pour |
|-------|---------|------|
| JDK   | 17      | Build Android (Gradle) |
| Android SDK | API 35 (min 26) | App |
| Node.js | ≥ 20 (testé 22) | Backend |
| Docker | récent | PostgreSQL/PostGIS + MinIO |

---

## 1. Tester rapidement (sans installation lourde)

Les **tests unitaires** ne nécessitent ni émulateur, ni Docker.

```bash
# --- Tests Android (JVM, pas d'émulateur) ---
./gradlew testDebugUnitTest          # depuis la racine FloraPin/

# --- Tests backend (Jest) ---
cd backend
npm install
npm test                              # tests unitaires
```

C'est le moyen le plus rapide de vérifier que tout est sain.

---

## 2. Lancer l'app Android

`local.properties` doit contenir `sdk.dir`. La carte est optionnelle.

```bash
# (Optionnel) clé MapTiler pour la carte — sinon l'écran carte montre un message d'aide
echo "MAPTILER_API_KEY=TA_CLE" >> local.properties

# Build APK debug + tests
./gradlew testDebugUnitTest assembleDebug
# APK généré : app/build/outputs/apk/debug/app-debug.apk
```

**Lancer sur un appareil / émulateur :**
- Le plus simple : ouvrir `FloraPin/` dans **Android Studio**, choisir un
  émulateur (API ≥ 26) et cliquer ▶.
- En ligne de commande, avec un émulateur déjà démarré :
  ```bash
  ./gradlew installDebug      # installe l'APK sur le device connecté
  ```

> ⚠️ **URL backend** (`app/build.gradle.kts`) : en debug, l'app pointe par
> défaut sur `http://10.0.2.2:3000/api/v1/`, alias de `localhost` **vu depuis
> l'émulateur Android**.
> - Émulateur → laisser la valeur par défaut.
> - Appareil physique → mettre l'IP du PC :
>   ```bash
>   echo "API_BASE_URL=http://192.168.X.X:3000/api/v1/" >> local.properties
>   ```

Sans backend lancé, l'app fonctionne quand même : capture photo, galerie, carte
locale, GPS. Seuls login/sync échouent (normal).

---

## 3. Lancer le backend

### Option A — dev rapide, mode stub (sans MinIO)

Pas de stockage cloud ni d'identification, mais l'API tourne. **Postgres reste
requis** (TypeORM) ; le plus simple est de lancer la base via Docker (option B)
puis :

```bash
cd backend
cp .env.example .env
# dans .env : STORAGE_DRIVER=stub  (désactive MinIO)
npm install
npm run start:dev
```

### Option B — stack via Docker (recommandé)

**B.1 — Tout dockerisé (API comprise)** via `deploy/` :
```bash
cd deploy
cp .env.example .env       # remplir DATABASE_*, JWT_*, MINIO_*, DOMAIN
docker compose up -d       # db + minio + api + caddy (HTTPS)
```
Adapté à un déploiement type prod (Caddy gère le HTTPS via `DOMAIN`).

**B.2 — Infra en Docker, API en local** (le plus pratique pour développer) :
```bash
cd deploy
docker compose up -d db minio    # uniquement la base + le stockage

cd ../backend
cp .env.example .env
# garder DATABASE_HOST=localhost, MINIO_ENDPOINT=localhost, STORAGE_DRIVER=minio
npm install
npm run start:dev
```

### Vérifications backend
- API : `http://localhost:3000/api/v1`
- Swagger interactif : `http://localhost:3000/api/docs`
- Console MinIO : `http://localhost:9001` (login = `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`)

---

## 4. Test bout-en-bout (app ↔ backend)

1. Démarrer le backend (étape 3, B.2 conseillé).
2. Lancer l'app sur l'**émulateur** (URL par défaut `10.0.2.2` → atteint le
   backend local).
3. Dans l'app : **Register** un compte → arrivée sur la galerie, la sync démarre.
4. Capturer une fleur → elle est en local (`syncState=PENDING`) puis poussée vers
   le backend.
5. Vérifier côté serveur : Swagger (`GET /flowers`) ou la console MinIO (l'image
   uploadée).

---

## Récap des commandes essentielles

| But | Commande |
|---|---|
| Tests Android | `./gradlew testDebugUnitTest` |
| Build APK | `./gradlew assembleDebug` |
| Installer sur device | `./gradlew installDebug` |
| Tests backend | `cd backend && npm test` |
| Infra (db + minio) | `cd deploy && docker compose up -d db minio` |
| API en dev | `cd backend && npm run start:dev` |
| Stack complète | `cd deploy && docker compose up -d` |

---

## ⚠️ Points d'attention

- Ne **jamais** committer `local.properties`, `backend/.env`, `deploy/.env`
  (secrets).
- **Appareil physique** : surcharger `API_BASE_URL` (sinon `10.0.2.2` ne résout
  pas).
- Carte : sans `MAPTILER_API_KEY`, l'app marche mais l'écran carte affiche un
  message d'aide.
