#!/bin/bash
# Déploiement de FloraPin sur un VPS Docker.
# Même pattern que meowtrack/deploy.sh : connexion SSH multiplexée (un seul
# prompt de mot de passe via sshpass) puis copie des sources et reconstruction
# de la stack docker compose.
#
# Ce que fait le script, de bout en bout :
#   1. Synchronisation via rsync (sources, PAS de node_modules/dist locaux) :
#        - backend/  : contexte de build Docker (l'image installe + compile).
#        - landing/  : sources Astro de la vitrine.
#        - deploy/   : docker-compose.yml + Caddyfile.
#   2. Build de la VITRINE sur le VPS, DANS UN CONTENEUR Docker node:22-alpine
#      (npm ci + npm run build) -> landing/dist, monté tel quel dans Caddy.
#   3. `docker compose up -d --build`
#        -> build de l'image API (npm ci + nest build dans le Dockerfile),
#           (re)démarrage de db (PostGIS) + minio + api + proxy (Caddy).
#
# Conséquence : la SEULE dépendance requise sur le VPS est Docker. RIEN n'est
# buildé en local (ni Node, ni npm requis sur le poste) — ce qui évite les
# soucis de build sur un FS Windows monté dans WSL (verrou esbuild.exe, EPERM).
#
# Différences avec meowtrack (qui copie des .js + npm install + systemctl) :
#   - On synchronise des ARBORESCENCES via rsync (au lieu de scp fichier/fichier).
#   - Le redémarrage passe par `docker compose` (au lieu de systemctl).
#   - Le .env (secrets) n'est JAMAIS copié : il doit déjà exister sur le VPS.
echo "Déploiement de FloraPin..."

# Se placer dans le dossier du script (deploy/) quel que soit le CWD d'appel.
cd "$(dirname "$0")" || exit 1
# Racine du dépôt (parent de deploy/) — source des arborescences à copier.
REPO_ROOT="$(cd .. && pwd)"

# Charger les variables depuis .deployEnv
if [ ! -f .deployEnv ]; then
    echo "⚠️  Fichier .deployEnv introuvable. Création d'un template..."
    cat <<EOF > .deployEnv
# Configuration de déploiement FloraPin
REMOTE_USER="votre-utilisateur"
REMOTE_HOST="votre-ip-ou-domaine"
REMOTE_DIR="/chemin/vers/destination/florapin"
REMOTE_PASSWORD="mot-de-passe-distant"
EOF
    echo "❌ Un template .deployEnv a été créé. Veuillez le remplir avant de relancer le déploiement."
    exit 1
fi

set -a
. ./.deployEnv
set +a

# Nettoyage des variables (enlève les guillemets et les \r Windows)
REMOTE_USER=$(echo "$REMOTE_USER" | sed 's/[\"\r]//g')
REMOTE_HOST=$(echo "$REMOTE_HOST" | sed 's/[\"\r]//g')
REMOTE_DIR=$(echo "$REMOTE_DIR" | sed 's/[\"\r]//g')
REMOTE_DIR="${REMOTE_DIR%/}"   # retire un éventuel / final (évite les // dans les chemins)
REMOTE_PASSWORD=$(echo "$REMOTE_PASSWORD" | sed 's/[\"\r]//g')

if [ -z "$REMOTE_USER" ] || [ -z "$REMOTE_HOST" ] || [ -z "$REMOTE_DIR" ] || [ -z "$REMOTE_PASSWORD" ]; then
    echo "❌ Erreur : Variables de déploiement manquantes dans .deployEnv."
    exit 1
fi

# SSH multiplexing (ne demande le mot de passe qu'une fois)
SSH_MUX_SOCKET="/tmp/ssh_mux_florapin_${REMOTE_HOST}_${REMOTE_USER}"
SSH_OPTS="-o ControlMaster=auto -o ControlPath=$SSH_MUX_SOCKET -o ControlPersist=600"

cleanup_ssh() {
    if [ -S "$SSH_MUX_SOCKET" ]; then
        echo "🔒 Fermeture de la connexion SSH..."
        ssh -O exit -o "ControlPath=$SSH_MUX_SOCKET" "$REMOTE_USER@$REMOTE_HOST" 2>/dev/null
    fi
}
trap cleanup_ssh EXIT

echo "🚀 Début du déploiement vers $REMOTE_HOST..."
echo "🔑 Connexion au serveur..."
# Connexion maître via sshpass : le mot de passe ($REMOTE_PASSWORD) n'est demandé
# qu'une fois ; toutes les commandes rsync/ssh suivantes réutilisent le socket de
# multiplexing ($SSH_MUX_SOCKET) et ne re-saisissent pas le mot de passe.
if ! command -v sshpass &>/dev/null; then
    echo "⚠️  sshpass non installé, installation..."
    if command -v apt-get &>/dev/null; then
        sudo apt-get install -y sshpass
    elif command -v brew &>/dev/null; then
        brew install sshpass
    else
        echo "❌ Installez sshpass manuellement (requis pour l'auth par mot de passe)."
        exit 1
    fi
fi
if ! command -v rsync &>/dev/null; then
    echo "❌ rsync est requis (côté local ET distant). Installez-le et relancez."
    exit 1
fi

export SSHPASS="$REMOTE_PASSWORD"
# Purge d'un éventuel socket de multiplexing périmé (laissé par un run précédent
# interrompu). Sa présence fait afficher à ssh « Control socket connect: Connection
# refused », ce qui perturbe la détection du prompt de mot de passe par sshpass.
ssh -O exit -o "ControlPath=$SSH_MUX_SOCKET" "$REMOTE_USER@$REMOTE_HOST" 2>/dev/null
rm -f "$SSH_MUX_SOCKET"
if ! sshpass -e ssh $SSH_OPTS -fNM "$REMOTE_USER@$REMOTE_HOST"; then
    echo "❌ Erreur : impossible d'établir la connexion SSH vers $REMOTE_USER@$REMOTE_HOST."
    exit 1
fi

# Raccourcis pour réutiliser le socket multiplexé.
remote_ssh() { ssh -o "ControlPath=$SSH_MUX_SOCKET" "$REMOTE_USER@$REMOTE_HOST" "$@"; }
remote_sync() {
    # $1 = source locale (avec / final pour le contenu), $2 = sous-dossier distant
    rsync -az --delete -e "ssh -o ControlPath=$SSH_MUX_SOCKET" "$@"
}
# Exécute une commande distante nécessitant les droits root. Le mot de passe est
# transmis à `sudo -S` par le canal stdin de SSH (le master multiplexé est déjà
# authentifié, il ne consomme pas ce stdin) : il n'apparaît JAMAIS dans la ligne
# de commande distante — contrairement à `echo '$PW' | sudo -S ...`, visible dans
# `ps`/les logs d'audit du VPS. La commande passée doit invoquer `sudo -S -p ''`.
# NB : une seule invocation sudo par appel (le mot de passe stdin n'est lu qu'une
# fois) ; à terme, migrer vers clé SSH + sudoers NOPASSWD ciblé sur docker.
remote_sudo() {
    printf '%s\n' "$REMOTE_PASSWORD" \
        | ssh -o "ControlPath=$SSH_MUX_SOCKET" "$REMOTE_USER@$REMOTE_HOST" "$1"
}

# S'assurer que les dossiers distants existent.
remote_ssh "mkdir -p '$REMOTE_DIR/backend' '$REMOTE_DIR/landing' '$REMOTE_DIR/deploy'"

# --- Synchronisation vers le VPS ---
# Pour backend/ ET landing/ on EXCLUT node_modules/ et dist/ : ce sont des
# artefacts buildés (et les binaires natifs Windows, ex. esbuild.exe, seraient
# inutilisables sur Linux). backend/ est compilé dans son image ; landing/ est
# buildé dans un conteneur Node sur le VPS (étape suivante).
# deploy/ : compose + Caddyfile + .env.example. On EXCLUT .env (secrets) et
#           l'override dev (docker-compose.override.yml, qui exposerait db/minio).
# Les TROIS arborescences sont synchronisées d'abord : ainsi même au 1er
# déploiement (où l'on s'arrête pour le .env), tous les fichiers sont déjà
# présents sur le serveur.
echo "📦 Synchronisation de deploy/..."
remote_sync --exclude '.env' --exclude '.deployEnv' \
    --exclude 'docker-compose.override.yml' --exclude 'deploy.sh' \
    "$REPO_ROOT/deploy/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/deploy/" || exit 1

echo "📦 Synchronisation de backend/..."
remote_sync --exclude 'node_modules/' --exclude 'dist/' \
    "$REPO_ROOT/backend/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/backend/" || exit 1

# --- APK de téléchargement (bêta) ---
# La vitrine sert l'APK directement : on dépose la DERNIÈRE version debug
# construite dans landing/public/ AVANT le rsync. Astro recopie public/ tel quel
# dans dist/, donc Caddy l'expose à https://<DOMAIN>/florapin.apk.
#
# On NE build PAS l'APK ici : un APK exige le SDK Android, absent de
# l'environnement de déploiement (WSL/Docker ; le wrapper gradlew a aussi des
# fins de ligne CRLF inexploitables sous WSL). L'APK se construit côté Windows
# (Android Studio, ou `gradlew.bat :app:assembleDebug` en PowerShell). Le script
# se contente d'expédier le binaire le plus récent disponible.
APK_BUILT="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
APK_PUB="$REPO_ROOT/landing/public/florapin.apk"
if [ -f "$APK_BUILT" ] && { [ ! -f "$APK_PUB" ] || [ "$APK_BUILT" -nt "$APK_PUB" ]; }; then
    cp "$APK_BUILT" "$APK_PUB"
    echo "📱 APK debug mis à jour depuis le build Android ($(du -h "$APK_PUB" | cut -f1))."
elif [ -f "$APK_PUB" ]; then
    echo "📱 APK debug : landing/public/florapin.apk déjà à jour ($(du -h "$APK_PUB" | cut -f1))."
else
    echo "⚠️  Aucun APK trouvé (ni app/build/outputs/, ni landing/public/)."
    echo "    Construis-le d'abord côté Windows : gradlew.bat :app:assembleDebug"
    echo "    La vitrine sera déployée SANS fichier de téléchargement (/florapin.apk → 404)."
fi

# --- Version de l'app pour la vitrine ---
# La vitrine affiche/nomme le téléchargement avec la VRAIE version (florapin_<ver>.apk).
# La source de vérité est `versionName` dans app/build.gradle.kts, mais le dossier
# app/ N'EST PAS synchronisé sur le VPS (le build Astro ne voit que landing/). On
# extrait donc la version ICI (app/ disponible en local) et on l'écrit dans
# landing/src/version.json, lu par config.ts au build. Fait AVANT le rsync de landing/.
GRADLE_FILE="$REPO_ROOT/app/build.gradle.kts"
VERSION_JSON="$REPO_ROOT/landing/src/version.json"
if [ -f "$GRADLE_FILE" ]; then
    APP_VERSION=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' "$GRADLE_FILE" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
    if [ -n "$APP_VERSION" ]; then
        printf '{\n  "version": "%s"\n}\n' "$APP_VERSION" > "$VERSION_JSON"
        echo "🏷️  Version de la vitrine alignée sur l'app : $APP_VERSION."
    else
        echo "⚠️  versionName introuvable dans build.gradle.kts ; version.json inchangé."
    fi
else
    echo "ℹ️  app/build.gradle.kts absent ; version.json inchangé (valeur commitée conservée)."
fi

# --- Changelog pour la vitrine ---
# La page /changelog affiche le CHANGELOG.md (racine du repo). Ce fichier N'EST
# PAS dans landing/, donc invisible du build Astro sur le VPS. On en copie une
# version dans landing/src/ (comme version.json) AVANT le rsync de landing/.
CHANGELOG_SRC="$REPO_ROOT/CHANGELOG.md"
CHANGELOG_DEST="$REPO_ROOT/landing/src/changelog.md"
if [ -f "$CHANGELOG_SRC" ]; then
    cp "$CHANGELOG_SRC" "$CHANGELOG_DEST"
    echo "📄 Changelog copié dans la vitrine (landing/src/changelog.md)."
else
    echo "ℹ️  CHANGELOG.md absent ; changelog de la vitrine inchangé (copie commitée conservée)."
fi

echo "📦 Synchronisation de landing/ (sources + APK)..."
remote_sync --exclude 'node_modules/' --exclude 'dist/' \
    "$REPO_ROOT/landing/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/landing/" || exit 1

# Vérifier que le .env de prod existe côté serveur (secrets — jamais copié d'ici).
# S'il manque (1er déploiement), on le crée depuis .env.example et on s'arrête
# pour que l'humain renseigne DOMAIN + secrets DB/JWT/MinIO. Les fichiers sont
# déjà tous synchronisés ci-dessus : seul le build/démarrage est différé.
if ! remote_ssh "test -f '$REMOTE_DIR/deploy/.env'"; then
    echo "⚠️  $REMOTE_DIR/deploy/.env introuvable (1er déploiement ?)."
    if remote_ssh "cp '$REMOTE_DIR/deploy/.env.example' '$REMOTE_DIR/deploy/.env'"; then
        echo "📝 .env créé sur le serveur depuis .env.example."
    fi
    echo "ℹ️  Fichiers (backend/, landing/, deploy/) déjà déployés sur le serveur."
    echo "❌ Éditez $REMOTE_DIR/deploy/.env (DOMAIN + secrets DB/JWT/MinIO) sur le"
    echo "   serveur, puis relancez ce script pour builder et démarrer la stack."
    echo "   (Le .env n'est jamais copié d'ici.)"
    exit 1
fi

# Refuse de (re)démarrer la prod avec des secrets par défaut non modifiés :
# .env.example contient des marqueurs « change-me » (JWT_ACCESS_SECRET,
# DATABASE_PASSWORD, MINIO_*…). Les laisser en place exposerait la stack.
if remote_ssh "grep -q 'change-me' '$REMOTE_DIR/deploy/.env'"; then
    echo "❌ $REMOTE_DIR/deploy/.env contient encore des secrets par défaut « change-me »."
    echo "   Éditez-le (JWT_ACCESS_SECRET, JWT_REFRESH_SECRET, DATABASE_PASSWORD,"
    echo "   MINIO_ROOT_PASSWORD, …) avec de vraies valeurs, puis relancez ce script."
    exit 1
fi

# --- Build de la vitrine dans un conteneur Docker (sur le VPS) ---
# node:22-alpine avec landing/ monté : npm ci + npm run build -> landing/dist
# (que Caddy sert via le montage défini dans docker-compose.yml). Aucun Node
# requis sur l'hôte ; le build tourne sous Linux (binaires natifs corrects).
echo "🏗️  Build de la vitrine (Astro) dans un conteneur Docker sur le VPS..."
if ! remote_sudo "cd '$REMOTE_DIR' && sudo -S -p '' docker run --rm -v '$REMOTE_DIR/landing':/app -w /app node:22-alpine sh -c 'npm ci && npm run build'"; then
    echo "❌ Échec du build de la vitrine (conteneur Docker)."
    exit 1
fi

# --- (Re)build de l'image API + (re)démarrage de la stack ---
# `up -d --build` reconstruit l'image API (npm ci + nest build dans le Dockerfile)
# puis (re)démarre db + minio + api + proxy. -f docker-compose.yml SEUL : pas
# d'override dev en prod.
echo "🔄 (Re)construction de l'API et démarrage de la stack docker compose..."
# --wait : bloque jusqu'à ce que db + minio soient *healthy* (healthchecks), pour
# que l'application du schéma ci-dessous tape sur une base prête.
if ! remote_sudo "cd '$REMOTE_DIR/deploy' && sudo -S -p '' docker compose -f docker-compose.yml up -d --build --wait"; then
    echo "❌ Erreur lors du build/démarrage de la stack docker compose."
    exit 1
fi

# --- Application idempotente du schéma (migrations) à CHAQUE déploiement ---
# docker-entrypoint-initdb.d ne s'exécute QUE sur un volume vierge : sur une base
# DÉJÀ existante, les changements de schéma ultérieurs (nouvelles colonnes/tables)
# ne sont jamais appliqués → erreurs 500 (typiquement au login, quand TypeORM
# SELECT une colonne absente comme users.email_verified).
# schema.sql est désormais idempotent (CREATE ... IF NOT EXISTS + ALTER ... ADD
# COLUMN IF NOT EXISTS) : on le REJOUE ici. Le fichier est déjà monté (bind, donc
# à jour après le rsync) dans le conteneur db ; psql utilise les identifiants
# internes du conteneur ($POSTGRES_USER/$POSTGRES_DB) — aucun secret côté script.
echo "🗄️  Application du schéma (migrations idempotentes) à la base..."
# On COPIE le fichier fraîchement synchronisé dans le conteneur (docker compose cp)
# au lieu de lire le bind-mount /docker-entrypoint-initdb.d/01-schema.sql : rsync
# remplace l'inode du fichier hôte, mais un conteneur db créé lors d'un déploiement
# antérieur garde l'ANCIEN inode monté → il verrait un schéma périmé. La copie
# garantit que psql applique bien la version à jour.
if ! { remote_sudo "cd '$REMOTE_DIR/deploy' && sudo -S -p '' docker compose -f docker-compose.yml cp '$REMOTE_DIR/backend/db/schema.sql' db:/tmp/schema.sql" \
    && remote_sudo "cd '$REMOTE_DIR/deploy' && sudo -S -p '' docker compose -f docker-compose.yml exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -f /tmp/schema.sql'"; }; then
    echo "❌ Échec de l'application du schéma à la base (voir docker compose logs db)."
    exit 1
fi
echo "✅ Schéma à jour (colonnes/tables manquantes ajoutées si besoin)."
# Recharge le catalogue d'espèces (idempotent) — non bloquant. Même précaution (cp).
if remote_sudo "cd '$REMOTE_DIR/deploy' && sudo -S -p '' docker compose -f docker-compose.yml cp '$REMOTE_DIR/backend/db/seed-species.sql' db:/tmp/seed-species.sql" >/dev/null 2>&1 \
    && remote_sudo "cd '$REMOTE_DIR/deploy' && sudo -S -p '' docker compose -f docker-compose.yml exec -T db sh -c 'psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -f /tmp/seed-species.sql'" >/dev/null 2>&1; then
    echo "✅ Catalogue d'espèces rechargé (seed idempotent)."
else
    echo "ℹ️  Seed d'espèces non rejoué (non bloquant)."
fi

sleep 3
echo "🩺 Vérification de l'état des services..."
if remote_sudo "cd '$REMOTE_DIR/deploy' && sudo -S -p '' docker compose -f docker-compose.yml ps"; then
    echo "✅ Déploiement terminé ! Stack FloraPin (re)démarrée sur $REMOTE_HOST."
    echo "   Vitrine : https://<DOMAIN>/   ·   API : https://<DOMAIN>/api/v1"
    echo "   (Swagger /api/docs masqué en prod — SWAGGER_ENABLED=true pour l'exposer.)"
else
    echo "⚠️  Déploiement copié mais l'état des services n'a pu être lu."
    echo "   Vérifier : cd $REMOTE_DIR/deploy && docker compose logs -f"
    exit 1
fi
