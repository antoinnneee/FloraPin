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

# S'assurer que les dossiers distants existent.
remote_ssh "mkdir -p '$REMOTE_DIR/backend' '$REMOTE_DIR/landing' '$REMOTE_DIR/deploy'"

# --- Synchronisation vers le VPS ---
# Pour backend/ ET landing/ on EXCLUT node_modules/ et dist/ : ce sont des
# artefacts buildés (et les binaires natifs Windows, ex. esbuild.exe, seraient
# inutilisables sur Linux). backend/ est compilé dans son image ; landing/ est
# buildé dans un conteneur Node sur le VPS (étape suivante).
# deploy/ : compose + Caddyfile + .env.example. On EXCLUT .env (secrets) et
#           l'override dev (docker-compose.override.yml, qui exposerait db/minio).
# deploy/ est synchronisé EN PREMIER : il amène .env.example, indispensable au
# bootstrap du .env juste après.
echo "📦 Synchronisation de deploy/..."
remote_sync --exclude '.env' --exclude '.deployEnv' \
    --exclude 'docker-compose.override.yml' --exclude 'deploy.sh' \
    "$REPO_ROOT/deploy/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/deploy/" || exit 1

# Vérifier que le .env de prod existe côté serveur (secrets — jamais copié d'ici).
# S'il manque (1er déploiement), on le crée depuis .env.example et on s'arrête
# pour que l'humain renseigne DOMAIN + secrets DB/JWT/MinIO.
if ! remote_ssh "test -f '$REMOTE_DIR/deploy/.env'"; then
    echo "⚠️  $REMOTE_DIR/deploy/.env introuvable (1er déploiement ?)."
    if remote_ssh "cp '$REMOTE_DIR/deploy/.env.example' '$REMOTE_DIR/deploy/.env'"; then
        echo "📝 .env créé sur le serveur depuis .env.example."
    fi
    echo "❌ Éditez $REMOTE_DIR/deploy/.env (DOMAIN + secrets DB/JWT/MinIO) sur le"
    echo "   serveur, puis relancez ce script. (Le .env n'est jamais copié d'ici.)"
    exit 1
fi

echo "📦 Synchronisation de backend/..."
remote_sync --exclude 'node_modules/' --exclude 'dist/' \
    "$REPO_ROOT/backend/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/backend/" || exit 1

echo "📦 Synchronisation de landing/ (sources)..."
remote_sync --exclude 'node_modules/' --exclude 'dist/' \
    "$REPO_ROOT/landing/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/landing/" || exit 1

# --- Build de la vitrine dans un conteneur Docker (sur le VPS) ---
# node:22-alpine avec landing/ monté : npm ci + npm run build -> landing/dist
# (que Caddy sert via le montage défini dans docker-compose.yml). Aucun Node
# requis sur l'hôte ; le build tourne sous Linux (binaires natifs corrects).
echo "🏗️  Build de la vitrine (Astro) dans un conteneur Docker sur le VPS..."
if ! remote_ssh "cd '$REMOTE_DIR' && echo '$REMOTE_PASSWORD' | sudo -S docker run --rm -v '$REMOTE_DIR/landing':/app -w /app node:22-alpine sh -c 'npm ci && npm run build'"; then
    echo "❌ Échec du build de la vitrine (conteneur Docker)."
    exit 1
fi

# --- (Re)build de l'image API + (re)démarrage de la stack ---
# `up -d --build` reconstruit l'image API (npm ci + nest build dans le Dockerfile)
# puis (re)démarre db + minio + api + proxy. -f docker-compose.yml SEUL : pas
# d'override dev en prod.
echo "🔄 (Re)construction de l'API et démarrage de la stack docker compose..."
if ! remote_ssh "cd '$REMOTE_DIR/deploy' && echo '$REMOTE_PASSWORD' | sudo -S docker compose -f docker-compose.yml up -d --build"; then
    echo "❌ Erreur lors du build/démarrage de la stack docker compose."
    exit 1
fi

sleep 3
echo "🩺 Vérification de l'état des services..."
if remote_ssh "cd '$REMOTE_DIR/deploy' && echo '$REMOTE_PASSWORD' | sudo -S docker compose -f docker-compose.yml ps"; then
    echo "✅ Déploiement terminé ! Stack FloraPin (re)démarrée sur $REMOTE_HOST."
    echo "   Vitrine : https://<DOMAIN>/   ·   API : https://<DOMAIN>/api/v1   ·   Swagger : /api/docs"
else
    echo "⚠️  Déploiement copié mais l'état des services n'a pu être lu."
    echo "   Vérifier : cd $REMOTE_DIR/deploy && docker compose logs -f"
    exit 1
fi
