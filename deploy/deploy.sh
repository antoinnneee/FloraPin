#!/bin/bash
# Déploiement de FloraPin sur un VPS Docker.
# Même pattern que meowtrack/deploy.sh : connexion SSH multiplexée (un seul
# prompt de mot de passe via sshpass) puis copie des sources et reconstruction
# de la stack docker compose.
#
# Différences avec meowtrack (qui copie des .js + npm install + systemctl) :
#   - On synchronise des ARBORESCENCES (backend/, landing/, deploy/) via rsync,
#     en excluant node_modules/, dist/ et les secrets.
#   - Côté distant : build de la vitrine (Astro) puis `docker compose up -d --build`.
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

# S'assurer que le dossier distant existe.
remote_ssh "mkdir -p '$REMOTE_DIR'"

# Vérifier que le .env de prod existe côté serveur (secrets — jamais copié d'ici).
if ! remote_ssh "test -f '$REMOTE_DIR/deploy/.env'"; then
    echo "❌ $REMOTE_DIR/deploy/.env introuvable sur le serveur."
    echo "   Créez-le à partir de deploy/.env.example (DOMAIN + secrets DB/JWT/MinIO)"
    echo "   puis relancez. (Il n'est jamais copié depuis cette machine.)"
    exit 1
fi

# --- Synchronisation des sources ---
# backend/ : contexte du build Docker (Dockerfile compile src/ dans l'image),
#            donc node_modules/ et dist/ locaux sont inutiles.
# landing/ : sources Astro, buildées sur le VPS (dist/ monté dans Caddy).
# deploy/  : compose + Caddyfile. On EXCLUT .env (secrets) et l'override dev
#            (docker-compose.override.yml, qui exposerait db/minio sur l'hôte).
echo "📦 Synchronisation de backend/..."
remote_sync --exclude 'node_modules/' --exclude 'dist/' \
    "$REPO_ROOT/backend/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/backend/" || exit 1

echo "📦 Synchronisation de landing/..."
remote_sync --exclude 'node_modules/' --exclude 'dist/' \
    "$REPO_ROOT/landing/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/landing/" || exit 1

echo "📦 Synchronisation de deploy/..."
remote_sync --exclude '.env' --exclude '.deployEnv' \
    --exclude 'docker-compose.override.yml' --exclude 'deploy.sh' \
    "$REPO_ROOT/deploy/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR/deploy/" || exit 1

# --- Build de la vitrine + (re)build de la stack docker ---
# La vitrine est buildée sur le VPS (landing/dist est gitignoré et monté dans
# Caddy). On utilise -f docker-compose.yml SEUL : pas d'override dev en prod.
echo "🏗️  Build de la vitrine (Astro) sur le serveur..."
if ! remote_ssh "cd '$REMOTE_DIR/landing' && npm ci && npm run build"; then
    echo "❌ Échec du build de la vitrine."
    exit 1
fi

echo "🔄 (Re)construction et démarrage de la stack docker compose..."
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
