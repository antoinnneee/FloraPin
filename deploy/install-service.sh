#!/bin/bash
# install-service.sh — bootstrap FloraPin sur le VPS (à lancer UNE FOIS).
# Équivalent de meowtrack/install-service.sh, mais adapté à une stack DOCKER :
# il n'y a pas de process Node à superviser — c'est `docker compose` qui gère
# db (PostGIS) + minio + api (NestJS) + proxy (Caddy). Ce script :
#   1. installe Docker + le plugin compose s'ils manquent ;
#   2. prépare deploy/.env (copie depuis .env.example si absent) ;
#   3. installe une unité systemd qui pilote la stack (démarrage au boot +
#      contrôle via `systemctl start|stop|restart florapin`) ;
#   4. active et démarre la stack.
# Ensuite, les mises à jour passent par deploy.sh (rsync + docker compose up).
#
# Usage (sur le serveur, depuis le dossier deploy/ déployé) :
#   ./install-service.sh
set -e

SERVICE_NAME="${SERVICE_NAME:-florapin}"
# Dossier deploy/ (contient docker-compose.yml) — quel que soit le CWD d'appel.
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
RUN_USER="${RUN_USER:-$(whoami)}"

echo "🔧 Installation du service systemd '$SERVICE_NAME'"
echo "   Dossier    : $APP_DIR"
echo "   Utilisateur: $RUN_USER"

# --- 1. Docker + plugin compose ---
if ! command -v docker &>/dev/null; then
    echo "⚠️  Docker non installé, tentative d'installation..."
    if command -v apt-get &>/dev/null; then
        sudo apt-get update -y
        sudo apt-get install -y docker.io docker-compose-plugin
        sudo systemctl enable --now docker
    else
        echo "❌ Installez Docker manuellement (https://docs.docker.com/engine/install/) puis relancez."
        exit 1
    fi
fi
# Le plugin compose (« docker compose », v2) est requis.
if ! docker compose version &>/dev/null; then
    echo "⚠️  Plugin docker compose absent, tentative d'installation..."
    if command -v apt-get &>/dev/null; then
        sudo apt-get install -y docker-compose-plugin
    else
        echo "❌ Installez le plugin docker compose manuellement puis relancez."
        exit 1
    fi
fi
echo "   Docker     : $(docker --version)"

# --- 2. Fichier d'environnement (secrets) ---
if [ ! -f "$APP_DIR/.env" ]; then
    echo "⚠️  Aucun .env trouvé dans $APP_DIR."
    echo "   Copie de .env.example → .env (À ÉDITER : DOMAIN + secrets DB/JWT/MinIO)."
    cp "$APP_DIR/.env.example" "$APP_DIR/.env"
    echo "❌ Éditez $APP_DIR/.env (DOMAIN + secrets), puis relancez ce script."
    exit 1
fi

# --- 3. Unité systemd pilotant la stack docker compose ---
# Type=oneshot + RemainAfterExit : systemd considère le service « actif » tant
# que la stack tourne. up/down/restart mappent sur docker compose. On utilise
# -f docker-compose.yml SEUL (pas d'override dev en prod).
DOCKER_BIN="$(command -v docker)"
echo "📝 Écriture de l'unité systemd..."
sudo tee "/etc/systemd/system/$SERVICE_NAME.service" > /dev/null <<EOF
[Unit]
Description=FloraPin (API NestJS + PostGIS + MinIO + Caddy, via docker compose)
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$APP_DIR
ExecStart=$DOCKER_BIN compose -f docker-compose.yml up -d --build
ExecStop=$DOCKER_BIN compose -f docker-compose.yml down
ExecReload=$DOCKER_BIN compose -f docker-compose.yml up -d --build
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
EOF

# --- 4. Activation + démarrage ---
echo "🔄 Activation et démarrage de la stack..."
sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE_NAME"
sudo systemctl restart "$SERVICE_NAME"

sleep 3
DOMAIN=$(grep -E '^DOMAIN=' "$APP_DIR/.env" | cut -d= -f2- | tr -d ' \r"')
echo ""
if sudo systemctl is-active --quiet "$SERVICE_NAME"; then
    echo "✅ Service '$SERVICE_NAME' actif."
    echo "   Vitrine : https://${DOMAIN:-<DOMAIN>}/   ·   API : https://${DOMAIN:-<DOMAIN>}/api/v1   ·   Swagger : /api/docs"
else
    echo "❌ Le service n'a pas démarré. Logs : sudo journalctl -u $SERVICE_NAME -n 50"
    echo "   Détail conteneurs : cd $APP_DIR && sudo docker compose logs"
    exit 1
fi
echo ""
echo "Commandes utiles :"
echo "  • Statut       : sudo systemctl status $SERVICE_NAME"
echo "  • Logs service : sudo journalctl -u $SERVICE_NAME -f"
echo "  • Logs API     : cd $APP_DIR && sudo docker compose logs -f api"
echo "  • Restart      : sudo systemctl restart $SERVICE_NAME"
echo ""
echo "⚠️  Ouvrez les ports 80 et 443 sur le firewall (HTTPS auto via Caddy),"
echo "    et vérifiez que l'enregistrement DNS A de ${DOMAIN:-<DOMAIN>} pointe sur ce serveur."
echo "ℹ️  Mises à jour ultérieures : lancez deploy/deploy.sh depuis votre poste."
