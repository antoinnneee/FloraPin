# Modifications de déploiement — mise en ligne de FloraPin

Récapitulatif des changements effectués pour installer et exposer FloraPin sur le
VPS (`florapin.pattounecorp.ovh`). Deux catégories :

- **A. Fichiers du dépôt** (versionnés ici).
- **B. Configuration de l'hôte** (hors dépôt — nginx / systemd / certbot, à
  reproduire manuellement sur un nouveau serveur).

Contexte serveur : Docker est installé **via snap** (service systemd
`snap.docker.dockerd.service`, pas `docker.service`). Le **port 80 public n'atteint
pas ce serveur** (filtré en amont par la box) — d'où le recours au DNS-01 pour TLS.

---

## A. Fichiers du dépôt

### 1. `deploy/install-service.sh` — détection auto du service Docker
Le script référençait en dur `docker.service` dans l'unité systemd générée, ce qui
échouait sur cette machine (Docker = snap). Détection automatique :

```bash
DOCKER_SVC=""
for _svc in docker.service snap.docker.dockerd.service; do
    if systemctl cat "$_svc" &>/dev/null; then DOCKER_SVC="$_svc"; break; fi
done
DOCKER_SVC="${DOCKER_SVC:-docker.service}"
```

Et l'unité systemd utilise `$DOCKER_SVC` :
```ini
Requires=$DOCKER_SVC
After=$DOCKER_SVC network-online.target
```

### 2. `deploy/Caddyfile` — Caddy en HTTP simple (le TLS est géré par nginx)
Avant : `{$DOMAIN} { … }` → Caddy faisait l'auto-HTTPS (Let's Encrypt) et
redirigeait tout en 308 vers HTTPS, ce qui cassait l'accès puisque le TLS public est
assuré par le nginx de l'hôte. Après : le bloc serveur écoute en clair sur `:80`
(publié sur l'hôte en `:8088`) :

```caddyfile
:80 {
    encode gzip
    handle /api/* {
        reverse_proxy api:{$API_PORT:3000}
    }
    handle {
        root * /srv/landing
        file_server
        try_files {path} {path}/ /index.html
    }
}
```

### 3. `deploy/docker-compose.yml` — suppression de la publication du port 443
Caddy ne fait plus de HTTPS : la publication `443` devenait un port mort. Service
`proxy`, section `ports` :

```yaml
    ports:
      # HTTP seulement : le TLS est assuré par le nginx de l'hôte qui proxifie vers ce port.
      - "${HTTP_PORT:-80}:80"
```
(la ligne `- "${HTTPS_PORT:-443}:443"` a été retirée.)

### 4. `deploy/nginx-florapin.conf`
Vhost nginx de l'hôte : terminaison TLS sur `127.0.0.1:9443` → proxy vers Caddy
(`127.0.0.1:8088`) + redirection 80→443. À copier dans
`/etc/nginx/sites-available/` (voir section B).

### 5. `deploy/nginx-florapin-http.conf`
Variante HTTP-seule du vhost (utilisée pour une tentative d'obtention de certif en
webroot). **Inutile au final** (port 80 filtré → DNS-01). Conservé pour référence.

### 6. `landing/astro.config.mjs`
`site` corrigé vers `https://florapin.pattounecorp.ovh` (URLs canoniques / Open
Graph). Rebuild de la vitrine requis au déploiement.

---

## B. Configuration de l'hôte (hors dépôt — à reproduire manuellement)

> Toutes ces commandes nécessitent `sudo` et un terminal interactif.

### 1. Service systemd `florapin`
Installé une fois via le script :
```bash
cd ~/floraPin/deploy && ./install-service.sh
```
Pilotage : `sudo systemctl {start,stop,restart} florapin`.

### 2. DNS (OVH)
Enregistrement **A** : `florapin.pattounecorp.ovh` → `78.122.112.36`.

### 3. Certificat TLS — DNS-01 manuel (port 80 filtré)
```bash
sudo certbot certonly --manual --preferred-challenges dns-01 -d florapin.pattounecorp.ovh
```
certbot fournit une valeur à publier dans un TXT `_acme-challenge.florapin` chez OVH ;
une fois propagé, valider. Certif dans `/etc/letsencrypt/live/florapin.pattounecorp.ovh/`.

> ⚠️ **Non renouvelé automatiquement** (DNS-01 manuel). Expire le **2026-09-19** :
> relancer la même commande avant cette date.

### 4. Vhost nginx + route SNI
```bash
sudo cp ~/floraPin/deploy/nginx-florapin.conf /etc/nginx/sites-available/florapin
sudo ln -sf /etc/nginx/sites-available/florapin /etc/nginx/sites-enabled/florapin
```
Ajouter la route dans le routeur SNI (bloc `stream { map … }` de
`/etc/nginx/nginx.conf`), à côté de l'entrée `memoires-cevenoles` :
```nginx
        florapin.pattounecorp.ovh                       127.0.0.1:9443;
```
Puis :
```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 5. Vérification
```bash
curl -sI https://florapin.pattounecorp.ovh/        # -> HTTP/2 200
curl -s  https://florapin.pattounecorp.ovh/api/docs -o /dev/null -w '%{http_code}\n'  # -> 200
```

---

## Schéma du flux public

```
Internet :443
   └─▶ nginx stream{} (SNI router, /etc/nginx/nginx.conf)
         └─ SNI = florapin.pattounecorp.ovh ─▶ 127.0.0.1:9443
               └─ vhost nginx (terminaison TLS) ─▶ 127.0.0.1:8088
                     └─ Caddy (conteneur proxy) ─▶ vitrine (/srv/landing) + API (/api/* → NestJS:3000)
```

---

## TODO / suivi
- [ ] **Renouveler le certif avant le 2026-09-19** (DNS-01 manuel).
- [ ] Éventuellement automatiser le renouvellement (hook OVH API) pour supprimer la
      manip manuelle.
