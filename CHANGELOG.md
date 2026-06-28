# Journal des modifications — FloraPin

Toutes les modifications notables de l'application sont consignées ici.

Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/)
et le projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

> ⚠️ **À tenir à jour à chaque modification.** Ajouter les changements sous
> « Non publié », puis les basculer dans une nouvelle version datée lors d'une
> release (en pensant à incrémenter `versionName`/`versionCode` dans
> `app/build.gradle.kts`).

## [1.4.2] — 2026-06-28

### Corrigé
- **Images de fleurs invisibles après synchronisation.** Une fleur synchronisée
  depuis le serveur (sans copie locale de l'image) s'affichait avec une vignette
  vide, en galerie comme au détail. Cause : le backend signait les URLs de lecture
  MinIO avec la même expiration courte que l'upload (10 min), alors que l'app
  device-first **persiste ces URLs en base locale** et les réaffiche bien plus
  tard → `403 Request has expired`. Double correctif :
  - **Backend** : l'expiration de lecture est désormais distincte et longue
    (7 jours, maximum SigV4 ; `STORAGE_DOWNLOAD_PRESIGN_EXPIRES`).
  - **App (durable)** : à la synchro, l'image d'une fleur/photo distante est
    **téléchargée dans le stockage privé** et `imagePath` est renseigné. L'affichage
    ne dépend plus jamais de l'expiration des URLs présignées. *(Pour récupérer des
    fleurs déjà synchronisées avec des URLs périmées : se déconnecter/reconnecter
    déclenche un pull complet qui régénère les URLs et télécharge les images.)*
- **Erreur 409 après suppression puis nouvelle demande d'identification.** Quand
  le propriétaire supprimait une identification puis en redemandait une, l'ami qui
  proposait une espèce recevait une erreur 409 (« Cette fleur est déjà
  identifiée »). Le garde-fou se basait sur le texte d'espèce résiduel au lieu de
  l'état réel « ouverte aux propositions » (`needsIdentification`), repositionné par
  la nouvelle demande. La proposition est désormais acceptée tant que la fleur
  attend une identification.

## [1.4.1] — 2026-06-28

### Corrigé
- **Swipe entre photos en plein écran.** Dans la visionneuse plein écran (avec
  zoom), le glissement à un doigt ne changeait plus de photo : le détecteur de
  zoom consommait tous les gestes, même image non zoomée. Désormais le geste n'est
  capté que lors d'un vrai zoom/déplacement (deux doigts ou image déjà zoomée) ;
  à l'échelle 1, le glissement passe au carrousel et fait défiler les photos de la
  fleur.

## [1.4.0] — 2026-06-28

### Ajouté
- **Badges de nouveautés dans la galerie** : un petit compteur sur les icônes de
  la barre du haut indique les demandes **non encore vues** —
  🔎 demandes d'identification d'amis, et 🤝 demandes d'amis entrantes. Ouvrir
  l'écran correspondant remet le badge à 0 — même sans rien traiter (proposer une
  espèce / accepter la demande). Le suivi des demandes vues est local à l'appareil
  (`SeenIdsStore`) ; les compteurs se recalculent au lancement et au retour sur la
  galerie (`GET /identification-requests` et `GET /friendships`).

### Corrigé
- **Duplication d'albums à la synchronisation.** La création d'album est désormais
  **idempotente** : l'app génère un `clientId` (UUID) stable, envoyé au serveur,
  qui retombe sur l'album existant si un push précédent a réussi mais que la
  réponse a été perdue (coupure réseau / crash après le POST). Le `pull` rattache
  aussi un album local par `clientId` quand son `serverId` n'a pas été persisté,
  au lieu d'insérer un doublon. Migration Room 11→12 (colonne `clientId` + index
  unique) et colonne `albums.client_id` côté backend (index unique partiel
  `(owner_id, client_id)`). Équivalent, pour les albums, du correctif déjà fait
  pour les fleurs (MIGRATION_9_10).
- Compilation des tests : stubs `IdentificationApi` complétés (`listProposals`,
  `acceptProposal`) — la suite de tests unitaires Android recompile.

### Modifié
- **Synchronisation cloud activée par défaut** (`SyncPreferences.DEFAULT = true`).
  Les nouvelles installations sauvegardent la bibliothèque sur le serveur dès la
  connexion ; le réglage reste désactivable dans Profil pour rester 100% local.
- Vitrine : le téléchargement enregistre désormais l'APK sous son **vrai numéro
  de version** (`florapin_1.3.0.apk` au lieu de `florapin_beta.apk`) et la mention
  sous le bouton affiche la version. La version est alignée automatiquement sur
  `versionName` (`app/build.gradle.kts`) : `deploy.sh` régénère
  `landing/src/version.json` (lu par `config.ts`) à chaque déploiement.
- Identification automatique Pl@ntNet **désactivée par défaut** (backend) tant
  qu'elle n'est pas configurée : il faut désormais `PLANTNET_ENABLED=true` *et*
  une clé d'API pour l'activer (sinon stub renvoyant des suggestions vides).
  Évite toute tentative d'appel à Pl@ntNet non configuré.

## [1.3.0] — 2026-06-28

### Ajouté
- Groupe de photos à la capture : après une prise, on peut **annuler** la photo,
  en **ajouter une autre au même groupe** (même fleur) ou **terminer**. La
  synchronisation cloud est déclenchée à « Terminer » (la fleur et toutes ses
  photos partent d'un coup ; une annulation avant n'a rien envoyé).
- Visionneuse photo **plein écran avec zoom** depuis le détail : toucher la photo
  principale ou une vignette ouvre un carrousel plein écran (swipe entre photos,
  pincement + double-tap pour zoomer).
- Affichage **multi-photos** côté ami : le flux « Partagées avec moi » et l'écran
  « Fleurs à identifier » montrent désormais toutes les photos d'une fleur
  (carrousel + plein écran/zoom au clic), au lieu de la seule couverture.

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
