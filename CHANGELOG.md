# Journal des modifications — FloraPin

Toutes les modifications notables de l'application sont consignées ici.

Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/)
et le projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

> ⚠️ **À tenir à jour à chaque modification.** Ajouter les changements sous
> « Non publié », puis les basculer dans une nouvelle version datée lors d'une
> release (en pensant à incrémenter `versionName`/`versionCode` dans
> `app/build.gradle.kts`).

## [Non publié]

## [1.2.0] — 2026-06-28

### Ajouté
- Contrôles de capture photo : **zoom** (pincement à deux doigts + curseur
  synchronisés, avec affichage du facteur ex. « 2.0× ») et **mode macro**
  (bascule la mise au point rapprochée `CONTROL_AF_MODE_MACRO` via l'interop
  Camera2, pour les sujets très proches). Le curseur n'apparaît que si l'appareil
  offre une plage de zoom.

## [1.1.0] — 2026-06-28

### Ajouté
- Identification collaborative — côté propriétaire (NODE-134) : section
  **« Propositions de vos amis »** sur le détail d'une fleur non identifiée,
  chargée en direct du serveur (`GET flowers/{id}/proposals`), avec bouton
  **« Accepter »** (`POST .../proposals/{id}/accept`) qui applique l'espèce à la
  fleur (localement + serveur). Boucle la fonctionnalité : demande → proposition
  → acceptation.
- Sélecteur de style de carte (combobox) dans l'onglet **Carte** : 9 styles
  MapTiler (Rues, Plein air, Topographique, Satellite, Hybride, Épuré, Clair,
  Dataviz, Hiver). Choix mémorisé par appareil et réutilisé par la mini-carte
  des fiches.

### Corrigé
- Proposition d'espèce refusée (403) côté ami : `propose()` vérifiait l'accès via
  `sharedWithMe` (partages ciblés) alors que l'ami voit la fleur via la diffusion.
  Aligné sur `needsIdentificationFromFriends` (même périmètre que la liste).
- Espèce non rafraîchie après acceptation d'une proposition : le champ « Espèce »
  gardait son état mémorisé (`remember(flowerId)`) et n'affichait la valeur qu'au
  retour sur l'écran. Clés du `remember` étendues à la valeur initiale.
- Demande d'identification invisible côté ami : le propriétaire envoyait bien la
  demande (fleur marquée « à identifier » + amis notifiés), mais l'écran « Fleurs
  à identifier » de l'ami restait vide. `listForViewer` ne lisait que les partages
  ciblés (`sharedWithMe`) alors que la demande sollicite *tous* les amis acceptés.
  Désormais l'ami voit toutes les fleurs `needsIdentification` de ses amis, sans
  exiger de partage ciblé ni de publication au flux (GPS masqué sauf opt-in).
- Barre de navigation système (mode 3 boutons, ex. Xiaomi/MIUI) qui recouvrait
  le bouton de capture et les boutons « Reprendre / Terminer » du flux photo :
  ces écrans plein écran sans `Scaffold` ne réservaient qu'un padding fixe.
  Ajout de `windowInsetsPadding(navigationBars)`. Invisible en émulateur
  (navigation gestuelle, inset bas plus fin).
- Grand espace vide au-dessus du titre « 🌸 FloraPin » (et des autres écrans) :
  trois `Scaffold` empilés appliquaient chacun l'inset de la status bar. Un seul
  consommateur d'inset par écran désormais.
- Images illisibles au pull depuis un autre appareil : les URLs présignées
  pointaient vers l'hôte Docker interne `minio:9000` (injoignable). Ajout d'un
  client de signature avec endpoint **public** (`MINIO_PUBLIC_ENDPOINT`) et
  d'une route proxy `/{bucket}/*` → MinIO (Host préservé pour la signature
  SigV4). Correction d'un port présigné en chaîne (`"443"`) qui ajoutait `:443`
  au Host signé → `SignatureDoesNotMatch`.

### Modifié
- Bouton « Vérifier mon email » grisé temporairement : l'envoi d'emails n'est
  pas encore opérationnel (configuration DNS en cours).

## [1.0.0] — 2026-06-27

Première version de FloraPin : carnet botanique photo, hors-ligne d'abord, avec
synchronisation cloud optionnelle et partage entre amis.

### Capture & bibliothèque
- Prise de photo géolocalisée d'une fleur et enregistrement local.
- Galerie en grille, recherche (espèce, notes, étiquettes), tri, et affichage du
  nom d'espèce quand il est disponible (sinon la date).
- Fiche détaillée d'une fleur : carrousel de photos, notes, espèce, et
  mini-carte interactive MapLibre du lieu de capture.
- Albums pour regrouper les fleurs.
- Identification d'espèce.

### Carte
- Carte MapLibre/MapTiler de toutes les fleurs géolocalisées, regroupées en
  clusters, avec filtres (période, espèce, amis).

### Compte & social
- Inscription, connexion, mot de passe oublié / réinitialisation, vérification
  d'email.
- Profil utilisateur et suppression de compte.
- Amis et feed des photos partagées (lecture seule), avec « j'aime ».

### Synchronisation
- Synchronisation cloud **optionnelle** (désactivée par défaut, réglage par
  appareil) : l'app reste 100 % locale tant qu'elle n'est pas activée.
- Réconciliation push/pull par identifiant serveur, résolution de conflits
  (le serveur fait foi), anti-doublon à la réconciliation.
- Miniatures WebP : aperçu léger en liste, image pleine résolution chargée
  uniquement au clic ; ré-encodage WebP côté serveur (sharp).

### Notifications
- Notifications push (Firebase Cloud Messaging).

[Non publié]: https://github.com/antoinnneee/FloraPin/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/antoinnneee/FloraPin/releases/tag/v1.0.0
