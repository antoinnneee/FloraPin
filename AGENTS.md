## Imported Claude Cowork project instructions

## Durée du déploiement VPS

- Le script `deploy/deploy.sh` prend environ **2 minutes** (114 secondes observées
  le 26 juillet 2026).
- Lors de son lancement, utiliser directement un délai d'attente d'au moins
  **5 minutes** afin de couvrir la synchronisation, les builds, les migrations et
  les contrôles de santé sans provoquer de relance inutile.

## Mise à jour des changelogs

- Après chaque modification apportée à une tâche FloraPin, mettre à jour
  `CHANGELOG.md` dans la section « Non publié ».
- Si la modification est visible par l'utilisateur, mettre également à jour
  `CHANGELOG_SIMPLE.md` dans la section « En préparation ».

## Skill projet — ajout d'un pays

- **add-country-florapin**
  (`.agents/skills/add-country-florapin/SKILL.md`) décrit la méthode complète
  pour ajouter ou tester les régions administratives d'un pays.
- Pour toute demande d'ajout, de prise en charge ou de test d'un pays et de ses
  régions, lire et suivre ce skill avant de modifier l'application.
- Déclencheur explicite : `$add-country-florapin`.

## Autorisation de publication FloraPin

- Antoine accorde une autorisation permanente pour publier les releases FloraPin sur la piste Google Play **Alpha**, sans demander une nouvelle confirmation à chaque publication.
- Cette autorisation couvre le déclenchement du workflow officiel de publication Alpha après les validations habituelles de release.
- Elle ne s'étend pas aux pistes bêta ou production, qui nécessitent toujours une confirmation explicite.

## Émulateur Android local

- Des AVD sont installés dans `C:\Users\Antoine\.android\avd`.
- Le profil de contrôle principal est `Medium_Phone_API_36.1`.
- Si `emulator.exe -list-avds` ne retourne rien, définir d'abord
  `ANDROID_AVD_HOME=C:\Users\Antoine\.android\avd` dans le processus courant.
