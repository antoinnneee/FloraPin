## Imported Claude Cowork project instructions

## Durée du déploiement VPS

- Le script `deploy/deploy.sh` prend environ **2 minutes** (114 secondes observées
  le 26 juillet 2026).
- Lors de son lancement, utiliser directement un délai d'attente d'au moins
  **5 minutes** afin de couvrir la synchronisation, les builds, les migrations et
  les contrôles de santé sans provoquer de relance inutile.
