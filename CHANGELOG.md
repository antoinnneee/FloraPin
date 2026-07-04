# Journal des modifications — FloraPin

Toutes les modifications notables de l'application sont consignées ici.

Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/)
et le projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

> ⚠️ **À tenir à jour à chaque modification.** Ajouter les changements sous
> « Non publié », puis les basculer dans une nouvelle version datée lors d'une
> release (en pensant à incrémenter `versionName`/`versionCode` dans
> `app/build.gradle.kts`).

## [Non publié]

### Corrigé
- **Partage d'une fleur — erreur 409 supprimée.** Re-partager une fleur (ou un
  album / toutes ses fleurs) au même ami ne renvoie plus « Conflict » : le
  partage existant est mis à jour (utile notamment pour basculer l'inclusion du
  GPS) au lieu d'être rejeté. Côté app, les erreurs de partage affichent
  désormais le message renvoyé par le serveur (ex. « Le partage est réservé aux
  amis acceptés. ») au lieu d'un « HTTP 4xx » technique.

### Modifié
- **Landing — sentier animé.** Le tracé suit désormais de près la position de
  défilement (au lieu de prendre près d'un écran d'avance), serpente en un
  méandre sinueux et continu (spline de Catmull-Rom) de haut en bas comme un
  chemin de randonnée, se faufile _derrière_ les cartes de contenu (visible sur
  les fonds, masqué par les cartes), et se termine par une épingle plantée juste
  au-dessus du logo « FloraPin » du pied de page. Les tiges des fleurs ne
  poussent qu'au passage du trait, avant l'éclosion de la corolle.

## [1.11.0] — 2026-07-04

_Première version publiée sur le Google Play Store (test fermé) — versionName
1.11.0, versionCode 19._

### Supprimé
- **Permission `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` retirée.** Reliquat
  du POC : l'app ne lit jamais la galerie de l'appareil (les photos sont prises
  par la caméra et écrites dans le répertoire privé). La permission était déclarée
  mais aucun code ne l'utilisait, ce qui déclenchait un avertissement Play Console
  sur les autorisations photos/vidéos. Manifeste et enum `AppPermission.MEDIA_IMAGES`
  nettoyés.

### Ajouté
- **Page publique de suppression de compte (`/suppression-compte`).** Conformité
  à la politique Google Play : une URL accessible sans l'app et sans connexion
  décrit comment supprimer son compte (depuis l'app : Profil → « Supprimer mon
  compte » ; ou par email si l'utilisateur n'a plus accès à l'app), liste les
  données effacées et le délai de traitement. Lien ajouté au pied de page de la
  vitrine. URL à renseigner dans la Play Console (section « Suppression de
  compte »).
- **Page publique des nouveautés (`/changelog`).** La vitrine expose le journal
  des modifications (rendu du `CHANGELOG.md`), avec un lien « Nouveautés » au
  pied de page. Le fichier est copié dans la landing au déploiement (même
  mécanique que la version affichée).

### Sécurité
- **Backend — en-têtes & surface d'API.** Ajout de `helmet` (en-têtes de sécurité
  HTTP : `nosniff`, HSTS, anti-clickjacking…). Le CORS n'autorise plus toutes les
  origines par défaut : il se restreint à la liste `CORS_ORIGINS` (vide = aucune
  origine navigateur, l'app Android n'étant pas concernée). La documentation
  Swagger (`/api/docs`) est masquée en production sauf `SWAGGER_ENABLED=true`. Le
  conteneur API ne tourne plus en root (`USER node`).
- **Backend — JWT d'un compte supprimé.** Un jeton d'accès encore valide après
  suppression du compte renvoie désormais un `401` propre au lieu d'un `500`
  (violation de clé étrangère) : la stratégie JWT vérifie l'existence du compte.
- **Backend — recherche d'espèces.** Les jokers `%`, `_` et `\` d'un terme de
  recherche/rapprochement d'espèce sont échappés : « R_sa » ne peut plus matcher
  « Rosa » par accident (et rattacher la fleur à la mauvaise espèce).

### Corrigé
- **Backend — actions plus robustes.** Un échec d'envoi de notification (partage,
  commentaire, demande d'ami, demande d'identification) ne renvoie plus un `500`
  alors que l'action a bien été effectuée : la notification devient best-effort
  (journalisée si elle échoue).
- **Backend — anti-spam d'identification.** Redemander une identification sur une
  fleur déjà « à identifier » est désormais idempotent : les amis ne sont plus
  re-notifiés à chaque nouvel appel.
- **Backend — synchronisation.** Le pull renseigne à nouveau `likedByMe` sur les
  fleurs tirées (le propriétaire voyait toujours `false` sur ses propres cœurs).
- **Android — renvoi de l'email de vérification.** `POST /auth/email/verification`
  exige un JWT mais l'intercepteur excluait tous les chemins `/auth/` : la requête
  échouait systématiquement en `401`. Le jeton n'est plus retiré que sur les
  endpoints d'authentification réellement publics.

### Modifié
- **Inscription au test fermé Google Play mise en avant sur la vitrine.** Le CTA
  principal invite à rejoindre la bêta via le Play Store (rejoindre le groupe de
  testeurs, puis activer l'accès) ; le téléchargement direct de l'APK passe en
  lien discret. Bascule pilotée par `PLAY_TEST_GROUP_URL` dans `config.ts`.
- **Synchronisation cloud désactivée par défaut (device-first).** Le nouveau
  défaut est **OFF** : l'app reste 100 % locale tant que l'utilisateur n'active
  pas explicitement la sync (l'interrupteur de l'inscription est décoché par
  défaut ; réglable à tout moment dans Profil). Une **migration** préserve les
  installations existantes : lors d'une mise à jour, un appareil déjà connecté
  qui n'avait jamais réglé l'option conserve son ancien comportement (sync ON) —
  seules les nouvelles installations prennent le défaut OFF.
- **Refonte visuelle de la landing page.** Nouvelle identité « sous-bois » :
  palette encre de forêt / tilleul / rose églantine / jaune pollen (exit les
  verts Tailwind), typographies Fraunces (titres) + Karla (texte) + IBM Plex
  Mono (coordonnées GPS décoratives qui structurent la page). Hero « carte
  vivante » plein écran : courbes de niveau, sentier pointillé, épingles-fleurs
  qui éclosent en séquence et bulle de commentaire d'ami (« Une anémone
  sylvie ! ») pour incarner le côté social. Cartes de fonctionnalités façon
  planches d'herbier (tampon de coordonnées), étapes reliées par le même
  sentier, encart vie privée avec coordonnées masquées, mockups inclinés,
  révélations au scroll (`prefers-reduced-motion` respecté). La copy validée de
  `CONTENT.md` est inchangée.
- **Sentier continu sur toute la page.** Le chemin pointillé du hero se
  poursuit désormais jusqu'au footer (tracé calculé selon la hauteur réelle de
  la page, zigzag dans les marges entre les sections). Il se dessine avec le
  défilement — lissage et vitesse plafonnée (~0,28 page/s) : un scroll éclair
  ne révèle pas tout d'un coup, le trait rattrape en douceur — et 8 fleurs
  éclosent au passage du trait. Masqué sur mobile (<720 px),
  `prefers-reduced-motion` = tout visible sans animation.
- **Encart vie privée : switch interactif.** Le bouton « Partager la
  localisation » de la démo bascule réellement : coordonnées masquées
  (`●●.●●●° N`) ↔ révélées, texte d'aide adapté, état `aria-checked` à jour.
- **Interface allégée & plus lisible.** La barre du haut de l'Accueil était
  surchargée : les **Albums** rejoignent la barre de navigation du bas (nouvel
  onglet 📁, à côté de l'Accueil) et le **tri** descend dans la vue sous forme
  d'une pastille affichant le critère courant en toutes lettres (« Tri : Plus
  récentes »), au lieu d'une icône ↕️ dans la barre du haut. Il ne reste plus que
  les entrées à notifier (🔎 identification, 🤝 amis) dans l'entête. Sur la
  **Carte**, le choix du style rejoint lui aussi la barre de filtres (chip
  « 🗺️ {style} »), vidant complètement sa barre du haut. Sur le **détail d'une
  fleur**, la suppression (destructive) passe dans un menu de débordement « ⋮ »
  pour éviter les touchers accidentels à côté des actions « Album » et
  « Partager ».
- **Gestion du bouton retour en trois temps.** Le retour matériel suit désormais
  une séquence explicite : depuis la visu d'une fleur (ou tout écran poussé), il
  revient à la page courante ; depuis un onglet secondaire (Carte, Partagées,
  Profil), il ramène à l'Accueil ; depuis l'Accueil, il quitte l'application. Les
  gestes de retour internes (visionneuse plein écran, fil de commentaires, étapes
  de capture) restent prioritaires.

## [1.10.1] — 2026-07-02

### Corrigé
- **Erreur 401 / déconnexions intempestives (feed « Partagées avec moi » et
  autres écrans).** Chaque ViewModel construisait son propre client réseau ;
  quand le token d'accès expirait, deux clients pouvaient rafraîchir en parallèle
  le même refresh token. La rotation en révoquait un, dont le refresh échouait
  alors en 401 et purgeait la session. Le client authentifié est désormais
  **partagé** dans toute l'app (un seul authenticator) : les refresh se
  sérialisent et les requêtes concurrentes rejouent avec le token rafraîchi.

## [1.10.0] — 2026-07-02

### Ajouté
- **Discuter des demandes d'identification.** Le fil de commentaires d'une fleur
  est désormais ouvert aux amis sollicités par une demande d'identification, même
  si la fleur n'est ni partagée ni publiée au flux : on peut ainsi discuter du
  milieu, demander une photo supplémentaire, etc. Un bouton « 💬 Discuter »
  apparaît sur chaque fleur de l'écran « Fleurs à identifier ».

## [1.9.0] — 2026-07-02

### Ajouté
- **Bouton « Tout synchroniser ».** Dans le profil, un bouton force une
  synchronisation complète immédiate (push + pull), même lorsque la
  synchronisation automatique est désactivée — pratique en mode device-first.

### Modifié
- **Réglage de synchronisation.** L'interrupteur « Synchronisation cloud » devient
  une case **« Synchroniser automatiquement »** : cochée, la sync tourne en
  arrière-plan (périodique, au retour réseau, après chaque modification) ;
  décochée, l'app reste locale jusqu'à un « Tout synchroniser » manuel.

## [1.8.1] — 2026-07-02

### Modifié
- **Commentaires — invitation à synchroniser.** Sur l'écran détail d'une fleur
  non synchronisée, la section commentaires n'est plus masquée silencieusement :
  elle affiche un message invitant à se connecter et activer la synchronisation
  pour lancer la discussion (les commentaires vivent côté serveur).

## [1.8.0] — 2026-07-02

### Sécurité
- **Backend — limites d'upload.** Les endpoints d'upload d'image (`POST
  /flowers/:id/image`, photos additionnelles) refusent désormais les fichiers
  de plus de 15 Mo (413) et les types non-image (400) : filtre MIME Multer +
  vérification des magic bytes via sharp (un binaire corrompu renvoie un 400
  propre au lieu d'un 500). L'expiry des URLs présignées MinIO est plafonné à
  300 s.
- **Backend — rate limiting.** `@nestjs/throttler` global (100 req/min) avec
  limites strictes sur l'authentification : login 5/min, register 3/min,
  forgot-password et renvoi d'email de vérification 3/15 min. Bloque le
  brute-force et le flooding d'emails.
- **Backend — autorisations.** Liker/déliker une fleur exige désormais de la
  voir (propriétaire, partage ciblé ou diffusion réseau) — plus de likes ni de
  notifications sur des fleurs privées d'inconnus. `DELETE /push/devices/:token`
  ne supprime plus que les jetons du compte authentifié. Inviter un email sans
  compte renvoie une réponse générique au lieu d'un 404 (anti-énumération
  d'adresses).
- **Android — sauvegarde.** Règles de backup (`dataExtractionRules` +
  `fullBackupContent`) excluant les jetons d'auth ; `EncryptedTokenStore`
  survit à une restauration sur un autre appareil (prefs indéchiffrables →
  reset + reconnexion) au lieu de crasher au lancement en boucle.
- **Backend — autorisations.** Liker/déliker une fleur exige désormais de la
  voir (plus de likes ni de notifications sur les fleurs privées d'inconnus) ;
  `DELETE /push/devices/:token` ne supprime que les jetons du compte
  authentifié ; inviter un email sans compte renvoie une réponse générique
  (anti-énumération d'adresses).
- **Backend — RGPD.** La suppression de compte purge désormais aussi les
  miniatures (fleurs et photos, y compris soft-deleted) du stockage MinIO, qui
  survivaient jusqu'ici à l'effacement.

### Corrigé
- **Backend — fuites de stockage.** Le remplacement d'une image de fleur ou de
  photo supprime l'ancienne miniature ; la suppression d'une photo purge ses
  objets ; changer la photo de couverture met à jour image ET miniature de la
  fleur (plus d'affichage incohérent).
- **Sync — doublons de fleurs.** `POST /sync/flowers` est désormais idempotent
  (dédoublonnage sur `localId`) : un renvoi du même lot ne crée plus de
  doublons côté serveur.
- **Backend — performances.** Les listes de fleurs (galerie partagée, feed,
  recherche) chargent photos et cœurs en requêtes groupées au lieu d'un N+1 par
  fleur ; la recherche filtre en SQL (exploite les index) ; les contrôles
  d'accès aux commentaires/propositions ne recalculent plus tout le feed.
- **Sync — suppression propagée au serveur.** Supprimer une fleur synchronisée
  fait un soft-delete poussé au serveur (puis purge locale de la ligne, du
  fichier image et des photos) ; la fleur disparaît des autres appareils et du
  feed des amis, et ne « ressuscite » plus au full-pull suivant. Une
  confirmation est demandée avant la suppression.
- **Sync — plus d'écrasement des éditions locales.** Le pull n'applique plus
  l'état serveur sur une fleur dont des modifications locales n'ont pas encore
  été poussées, et `markSynced` ne bascule en SYNCED que si la fleur n'a pas
  été éditée pendant le push.
- **Sync — fiabilité.** Verrou process-wide sur `SyncWorker` (le périodique et
  le one-shot ne tournent plus en parallèle → plus de doublons serveur) ; un
  échec d'upload d'image est marqué (`imagePendingUpload`, migration Room
  v13) et retenté aux syncs suivantes au lieu d'être perdu ; un élément en
  erreur permanente (404/409) ne bloque plus toute la sync albums/photos en
  retry infini.
- **Auth — déconnexions intempestives.** Une erreur réseau pendant le refresh
  du token n'efface plus la session (seul un refus 401/403 du serveur
  déconnecte).
- **Notifications visibles sur Android 13+.** La permission
  `POST_NOTIFICATIONS` est demandée (une seule fois) à l'arrivée sur la
  galerie ; les push (partages, amis, commentaires) s'affichent enfin sur les
  appareils récents.

## [1.7.0] — 2026-06-30

### Ajouté
- **Commentaires sur les fleurs partagées.** Un fil de discussion est attaché à
  chaque fleur : toute personne qui voit la fleur (propriétaire, partage ciblé ou
  diffusion au réseau) peut commenter et lire les commentaires. Côté propriétaire,
  la section apparaît en bas du détail (`DetailScreen`) une fois la fleur
  synchronisée ; côté ami, un bouton **« 💬 Commenter »** sur chaque carte du feed
  « Partagées avec moi » ouvre le fil en bottom sheet. Chacun supprime ses propres
  messages ; le propriétaire peut modérer n'importe quel message de sa fleur. Le
  propriétaire reçoit une notification `flower_commented`. Nouveau module backend
  `comments` (`GET/POST/DELETE flowers/{id}/comments`, table `flower_comments`).

## [1.6.0] — 2026-06-30

### Ajouté
- **Ma position sur la carte.** La carte affiche désormais l'indicateur « ma
  position » de MapLibre (point bleu + halo de précision). La permission de
  localisation est demandée à l'ouverture de la carte, et un bouton flottant
  📍 recentre la vue sur la position courante.
- **Fleurs des amis sur la carte.** Le chip **« Ami »** ajoute désormais sur la
  carte les fleurs partagées par les amis (flux `FeedApi`) dont la position GPS a
  été diffusée (`feedIncludeGps`). Jusqu'ici le filtre ne portait que sur la base
  locale et n'affichait donc jamais de fleurs d'amis. Ces marqueurs ne sont pas
  cliquables (pas de page détail locale).

## [1.5.0] — 2026-06-29

### Ajouté
- **Choix de la synchronisation cloud à l'inscription.** L'écran d'inscription
  propose désormais un interrupteur **« Synchronisation cloud »** (activé par
  défaut) : l'utilisateur décide dès la création de compte s'il veut sauvegarder
  ses fleurs sur le serveur (et les retrouver sur ses autres appareils) ou rester
  100 % local. Le choix est persisté dans `SyncPreferences` avant l'inscription
  (`OnAuthSuccess` → `startSync` est no-op si désactivée) et reste modifiable à
  tout moment dans Profil.

## [1.4.4] — 2026-06-29

### Corrigé
- **CI lint : opt-in Camera2 non pris en compte.** Le mode macro à la capture
  utilise l'interop Camera2 (`Camera2CameraControl`), une API expérimentale dont
  le marqueur `ExperimentalCamera2Interop` repose sur `@RequiresOptIn` de Java :
  le `@OptIn` de Kotlin n'avait donc aucun effet et `lintDebug` échouait
  (`UnsafeOptInUsageError`, 6 erreurs). Remplacé par `androidx.annotation.OptIn`
  avec `markerClass`.

## [1.4.3] — 2026-06-28

### Ajouté
- **Propositions d'identification : auteur visible et refus possible.** Sur le
  détail d'une fleur, chaque proposition d'espèce reçue affiche désormais
  **« Proposé par <nom> »**, et le propriétaire peut la **Refuser** (en plus de
  l'accepter). Une proposition refusée est retirée. *(Backend :
  `DELETE /flowers/:id/proposals/:proposalId` + nom de l'auteur dans la liste.)*
- **Compteur d'identifications acceptées sur le profil.** La page Profil affiche
  le **nombre de mes propositions d'espèce acceptées** par des amis.
  *(Backend : `GET /me/proposal-stats`.)*

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
