# Journal des modifications — FloraPin

Toutes les modifications notables de l'application sont consignées ici.

Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/)
et le projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

> ⚠️ **À tenir à jour à chaque modification.** Ajouter les changements sous
> « Non publié », puis les basculer dans une nouvelle version datée lors d'une
> release (en pensant à incrémenter `versionName`/`versionCode` dans
> `app/build.gradle.kts`).

## [Non publié]

### Modifié
- **Feed en 2 colonnes (mosaïque).** Le fil « Partagées avec moi » s'affiche
  désormais dans une `LazyVerticalStaggeredGrid` à deux colonnes : les fleurs
  seules occupent chacune une colonne (hauteurs variables) pour un rendu type
  mosaïque, tandis que la barre de filtres, les cartes-lot (3.6), le séparateur
  « nouveautés » (3.2), l'indicateur de pagination et le mode « Ma sélection »
  restent en pleine largeur (`StaggeredGridItemSpan.FullLine`).

### Ajouté
- **Ajout d'ami par QR code (TÂCHE 4.5).** Sur le terrain, sans lien web : depuis
  l'écran « Amis », chacun peut afficher son QR code (« Mon QR code ») ou scanner
  celui d'un ami (« Scanner un QR »). Le scan envoie une demande d'amitié. Le QR
  encode l'**identifiant** (UUID) de l'utilisateur, jamais son email (vie privée).
  Génération et décodage 100 % locaux via ZXing (`com.google.zxing:core`, aucune
  dépendance réseau/Play Services). Nouvel endpoint `POST /friendships/by-id`
  (corps `{ userId }`) : contrairement à l'ajout par email, l'**acceptation
  croisée est automatique** quand chacun scanne l'autre (consentement mutuel), et
  le re-scan (demande déjà envoyée ou déjà amis) est idempotent — pas de 409. Le
  scan réutilise le flux de permission caméra existant (`permission/`).
- **Relance manuelle d'une demande d'identification.** Depuis l'onglet « Mes
  demandes », un bouton « 🔔 Relancer mes amis » re-sollicite tout le réseau
  d'amis sur une fleur toujours « à identifier ». Nouvel endpoint
  `POST /flowers/{id}/identification-requests/remind` qui re-notifie
  (`identification_requested`, push data-only). Anti-spam **côté serveur** (pas
  seulement UI) : la colonne `flowers.last_reminded_at` horodate la dernière
  sollicitation (ouverture + relances) et une relance sous 24 h est refusée
  (409 → « Vous avez déjà relancé vos amis récemment »).
- **« Merci 🌸 » en un tap (identification collaborative).** Sur le détail d'une
  fleur, le propriétaire peut désormais remercier l'auteur d'une proposition
  d'espèce d'un simple tap, sans avoir à l'accepter. Nouvel endpoint
  `POST /flowers/{id}/proposals/{proposalId}/thanks` (idempotent : un seul merci
  par proposition, matérialisé par `species_proposals.thanked_at`) qui notifie le
  proposeur (`species_thanked`, push data-only « Marie vous remercie pour …
  🌸 »). Le bouton passe à « Merci envoyé 🌸 » une fois envoyé.
- **Statut d'une demande d'identification (En attente / Résolue).** Une pastille
  de statut apparaît désormais des deux côtés de l'entraide : sur les cartes
  « À identifier » (côté ami), « Mes demandes » (côté propriétaire) et sur la
  section « Propositions de vos amis » du détail d'une fleur. Le statut est
  entièrement *dérivé* de l'état existant — « Résolue » dès que la fleur
  n'attend plus d'identification (`needsIdentification = false`) et qu'une
  proposition a été acceptée, « En attente » sinon — sans nouvelle colonne ni
  changement de schéma.
- **Écran « Mes demandes » (identification collaborative).** L'écran
  d'identification propose désormais deux onglets : « À identifier » (les fleurs
  d'amis que je peux aider à identifier, inchangé) et « Mes demandes », qui
  montre l'état de mes propres sollicitations — mes fleurs en attente et « qui a
  proposé quoi ». Nouvel endpoint `GET /me/identification-requests` : le serveur
  compose mes fleurs `needsIdentification` avec les propositions reçues (auteurs
  batchés) en une seule requête, sans composition N+1 côté client. Nouveau
  `MyRequestsViewModel` + DTO `MyIdentificationRequestDto`. L'accept/refus d'une
  proposition reste sur le détail de la fleur ; l'onglet est une vue d'état.
- **Fleurs enregistrées — « Ma sélection ».** Chaque fleur d'ami du feed propose
  une étoile (⭐/☆) pour l'enregistrer en favori PRIVÉ et LOCAL, sans aucune API
  dédiée (device-first). Comme la fleur d'un ami n'existe pas en base locale, on
  fige un snapshot autonome (id serveur, espèce, nom de l'ami, miniature mise en
  cache sur l'appareil) : la sélection reste consultable hors ligne et même si le
  partage d'origine est révoqué. Une puce « ⭐ Ma sélection » filtre le feed pour
  n'afficher que ces favoris (liste locale, tirage et pagination désactivés).
  Nouvelle table Room `saved_flowers` (migration 13→14) + entité/DAO/dépôt sous
  `data/`. La sélection est purgée à la suppression de compte (NODE-93).
- **Mention d'un ami dans un commentaire (`@ami`).** En saisissant `@` dans le
  fil de discussion, une liste d'amis acceptés s'affiche en autocomplete ;
  choisir un ami insère une mention rendue « @Nom » (colorée) dans le champ.
  La mention encode l'IDENTIFIANT de l'ami (`@[userId]`) et non son nom : un
  renommage (1.7) ne casse donc pas la mention, le nom étant re-résolu à chaque
  lecture (nouveau champ `mentions` sur chaque commentaire renvoyé par l'API).
  Côté serveur, `POST`/`PATCH flowers/{id}/comments` détecte les amis mentionnés
  et leur envoie une notification `comment_mention` (nouveau type, canal
  « Commentaires », action rapide « Répondre »). Restreint au réseau d'amis
  acceptés de l'auteur ; l'auteur et le propriétaire (déjà averti par
  `flower_commented`) sont exclus, et l'édition ne re-notifie que les mentions
  nouvellement ajoutées.
- **Réponse à un commentaire (fil à un niveau).** Chaque commentaire propose un
  bouton « Répondre » qui ouvre une réponse citée : un bandeau « En réponse à … »
  s'affiche au-dessus de la saisie et la réponse rappelle l'auteur et le texte du
  commentaire visé. Le fil reste volontairement à un seul niveau — répondre à une
  réponse est aplati côté serveur pour pointer la racine. Côté API, `POST
  flowers/{id}/comments` accepte un `replyToId` optionnel (validé sur la même
  fleur) et chaque commentaire renvoie `replyToId`/`replyToAuthorName`/
  `replyToBody` pour la citation. Nouvelle colonne `reply_to_id` sur
  `flower_comments`.
- **Édition d'un commentaire.** L'auteur d'un commentaire peut désormais le
  modifier via le menu « Éditer » (⋮) du fil de discussion : le texte est
  ré-ouvert en édition inline (Enregistrer / Annuler). Un suffixe « · modifié »
  s'affiche à côté de l'ancienneté. Côté serveur, un `PATCH
  flowers/{id}/comments/{commentId}` réservé à l'auteur met à jour le texte et
  horodate la colonne `edited_at` (nouvelle sur `flower_comments`).
- **Brouillon de commentaire conservé.** Le texte saisi dans le fil de discussion
  d'une fleur n'est plus perdu si l'on ferme la bottom sheet (ou redémarre
  l'appli) sans envoyer : il est persisté par fleur (`flowerServerId`) dans un
  fichier de prefs dédié (`florapin_comment_drafts`) et restauré à la réouverture.
  Le brouillon est effacé une fois le commentaire envoyé.
- **Regroupement du feed par lot.** Quand un ami partage plusieurs fleurs d'un
  même geste, le feed « Partagées avec moi » les réunit en une carte-lot
  « Marie a partagé N fleurs » (aperçu de 3 miniatures) ; un tap déplie les
  fleurs du lot juste en dessous, sans quitter le feed. Le regroupement se fait
  par clé de lot (partage ciblé `shareId`, sinon repli « ami + jour » pour les
  fleurs diffusées au réseau), pas par position : un lot coupé entre deux pages
  de pagination se recompose dès le chargement de la page suivante, en gardant un
  tri stable par date. L'API du feed expose désormais `shareId`/`sharedAt` sur
  chaque item pour un regroupement fiable côté client.
- **Réactions enrichies sur les fleurs.** Le cœur devient un jeu de réactions :
  un appui long sur l'emoji ouvre un sélecteur (😍 🌸 🌹 🌼 🪻 🔍 👍) ; un simple
  tap pose (ou retire) la réaction par défaut ❤️. Le libellé récapitule les types
  présents suivis du total, et la liste des likers affiche l'emoji de chacun.
  Côté API, `POST /flowers/:id/like` accepte un corps optionnel `{ reaction }`
  (absent = cœur, compat ascendante des anciennes apps) ; changer de réaction met
  à jour la ligne existante (une seule réaction par fleur et par utilisateur, pas
  de doublon). Les réponses fleur exposent désormais `reactionCounts` (décompte
  par type) et `myReaction`, en plus de `likeCount`/`likedByMe` conservés. Colonne
  `flower_likes.reaction` ajoutée (défaut `heart`, migration idempotente).
- **Liste des personnes ayant liké une fleur.** Un tap sur le compteur de cœurs
  (détail comme feed « Partagées avec moi ») ouvre un bottom sheet listant les
  likers par leur nom d'affichage. Servi par un nouvel endpoint
  `GET /flowers/:id/likes` soumis au même contrôle d'accès que le like (fleur
  visible par le viewer, sinon 404), noms résolus en une requête groupée.
- **Compteur de commentaires sur les cartes du feed.** Chaque fleur partagée
  affiche désormais une puce 💬 avec le nombre de commentaires reçus, à côté du
  cœur ; un clic ouvre le fil. Le compte est renvoyé par l'API (`commentCount`)
  et agrégé en une seule requête groupée côté backend (pas de N+1 sur la liste).
- **Séparateur « Nouveau depuis votre dernière visite » dans le feed.** En tri par
  date, un filet libellé s'insère juste avant la première fleur déjà présente à la
  précédente ouverture de l'onglet 🖼️, isolant les nouveautés en tête de liste.
  Repère de visite mémorisé par appareil dans `FeedBadgeStore` ; absent à la
  première visite ou lorsqu'il n'y a rien à distinguer (aucune nouveauté, ou feed
  entièrement nouveau).
- **Badge de nouveautés sur l'onglet « Partagées ».** L'onglet 🖼️ de la barre du
  bas porte désormais un badge du nombre de fleurs non encore vues dans le feed
  d'amis. Le compteur est recalculé à chaque changement d'onglet et remis à zéro
  dès l'ouverture de l'onglet (les fleurs affichées sont marquées « vues » par
  appareil, via `FeedBadgeStore`). Silencieux hors-ligne / non connecté (la
  dernière valeur connue est conservée).
- **Centre de notifications in-app.** Une cloche 🔔 dans la barre du haut de
  l'Accueil, surmontée d'un badge du nombre de non-lus, ouvre un centre de
  notifications listant les nouveautés reçues (demandes/acceptations d'ami,
  partages, propositions et confirmations d'espèce, demandes d'identification,
  cœurs et commentaires), plus récentes d'abord, avec un point « non lu » et
  l'ancienneté. Un tap marque la notification lue et route vers le contenu
  concerné en réutilisant le routage des push (résolution serverId → fleur
  locale, sinon repli feed/amis/accueil). Fonctionnalité collaborative servie
  par le backend : indépendante de la synchronisation device-first mais
  nécessitant le réseau ; hors-ligne ou non connecté, l'écran affiche un état
  « indisponible » explicite et le badge reste masqué.
- **Actions rapides depuis la notification.** Les push référençant une fleur
  proposent désormais des boutons d'action sans ouvrir l'app : « ❤️ J'aime »
  (partage reçu uniquement) et « Répondre » (RemoteInput → commentaire ;
  partage, commentaire, cœur reçu). L'appel réseau est effectué hors du thread
  principal (`goAsync` + coroutine IO), authentifié via le client partagé, et la
  notification est retirée en cas de succès (laissée en place, pour réessai, en
  cas d'échec). Aucun bouton « J'aime » sur « on a aimé/commenté VOTRE fleur »
  (commentaire et cœur ne sont notifiés qu'au propriétaire : aimer reviendrait à
  aimer sa propre fleur).
- **Photo de la fleur dans la notification.** Les push référençant une fleur
  affichent désormais sa miniature (BigPictureStyle) : vignette en mode replié
  et grande image une fois dépliée. L'URL de la miniature (présignée de lecture,
  longue durée) est fournie dans le payload par le backend ; l'app la télécharge
  à la réception, de façon synchrone et bornée par un timeout court (~2,5 s),
  puis retombe proprement sur une notification sans image en cas d'échec, d'URL
  absente ou de push sans fleur.
- **Regroupement des notifications par fleur / conversation.** Les push sont
  désormais regroupés côté système : toutes les notifications concernant une même
  fleur (cœur, commentaire, proposition d'espèce, demande d'identification…) sont
  collapsées sous un résumé unique — « Activité sur une fleur » — au lieu de
  s'empiler ; les notifications sans fleur (demandes d'ami…) se regroupent par
  type. Les ids de notification sont stables par (type, fleur) : un nouveau push
  du même couple met à jour la notification existante plutôt que d'en créer une
  nouvelle, et le résumé est reposté à chaque ajout.
- **Canaux de notification par type.** Les push sont désormais rangés dans des
  canaux Android dédiés — Cœurs (`florapin_likes`), Commentaires
  (`florapin_comments`), Amis (`florapin_friends`) et Identification
  (`florapin_identification`) — permettant à l'utilisateur de couper ou
  personnaliser chaque catégorie depuis les réglages système. Les partages et
  les types inconnus retombent sur le canal historique « Général »
  (`florapin_default`). Le mapping type FCM → canal est fait à la réception.
- **Tap sur une notification → contenu concerné.** Toucher une notification push
  ouvre désormais l'app directement sur le contenu visé plutôt que sur l'Accueil.
  Le `PendingIntent` (FLAG_IMMUTABLE, targetSdk 35) transporte le type et le
  `serverId` de la fleur ; au tap, l'app résout ce `serverId` → id local Room et
  ouvre le détail de la fleur (mes fleurs : cœur, commentaire, proposition…),
  ou retombe sur le feed « Partagées » quand la fleur appartient à un ami (donc
  absente de Room : partage, demande d'identification). Les notifications sans
  fleur routent par type (demande/acceptation d'ami → écran Amis). Gère le
  démarrage à froid (extras dans `onCreate`) comme l'app déjà ouverte
  (`onNewIntent`, activité en `singleTop`).
- **Notifications push « incarnées ».** Les push disent désormais *qui* fait quoi
  et *sur quelle fleur* : « Marie a partagé Coquelicot avec vous », « Paul a
  commenté votre Coquelicot », « Léa a aimé votre fleur », etc. Le backend
  enrichit le `data` de chaque push (data-only) à l'envoi avec le nom
  d'affichage de l'émetteur (`byUserName`, jamais figé — résolu au moment de
  l'envoi, cohérent avec la modification de nom), et, quand une fleur est
  concernée, son espèce (`species`) et l'URL de sa miniature (`thumbnailUrl`,
  présignée à longue durée). L'app compose le texte à partir de ces champs et
  retombe proprement sur les libellés génériques quand ils sont absents
  (anciens payloads tolérés). La notification in-app persistée conserve, elle,
  ses identifiants bruts.
- **Modification du nom d'affichage depuis le profil.** La carte du profil
  propose désormais un bouton « Modifier le nom » ouvrant un dialogue pré-rempli
  avec le nom courant. Nouvel endpoint `PATCH /users/me` (JWT requis) qui
  applique les mêmes règles qu'à l'inscription (trim + 1..80 caractères) puis
  renvoie le profil à jour. Le nom n'est jamais figé ailleurs : il reste résolu
  au moment de l'envoi (ex. futurs push « incarnés »). L'app reflète le nouveau
  nom dans l'état et le persiste localement (affichage immédiat au prochain
  lancement).
- **Changement de mot de passe depuis le profil.** Une section « Sécurité » du
  profil ouvre un dialogue qui demande le mot de passe actuel (vérifié côté
  serveur) puis le nouveau, confirmé localement (≥ 8 caractères). Nouvel endpoint
  `POST /auth/change-password` (JWT requis, throttlé à 5/min par IP) : il vérifie
  l'ancien mot de passe, re-hash le nouveau, révoque **toutes** les sessions
  (déconnexion des autres appareils) puis **réémet une paire de jetons pour
  l'appareil courant** afin de ne pas déconnecter l'utilisateur de son propre
  téléphone. L'app persiste immédiatement les jetons réémis.
- **Sauvegarde locale — export/import ZIP (device-first).** Le profil propose
  désormais d'exporter toute la bibliothèque (fleurs, albums, appartenances et
  photos) dans une archive ZIP choisie via le sélecteur de documents (SAF), puis
  de la réimporter — entièrement hors ligne, filet de sécurité du mode 100 %
  local. L'export sérialise un dump JSON via les DAO (jamais le `.db` à chaud,
  pour éviter les incohérences WAL) et copie les images en flux (aucune image ni
  archive entière chargée en mémoire). L'import est une **fusion idempotente**
  sans écrasement : dédoublonnage des albums par `clientId`, des fleurs par
  `serverId` (sinon date de capture) et des photos par `serverId` (sinon couple
  fleur/position) ; les identifiants locaux sont remappés pour reconstruire les
  relations, et les champs de synchronisation (`serverId`, `syncState`,
  `updatedAt`…) sont restaurés tels quels afin qu'une bibliothèque déjà
  synchronisée ne soit pas re-poussée en double. Nouveaux composants
  `BackupExporter`/`BackupImporter` (paquet `data/backup`).
- **Onboarding en trois écrans (première installation).** Au tout premier
  lancement, FloraPin présente sa promesse sociale (capture géolocalisée →
  partage → identification par les amis), explique les accès caméra et
  localisation *avant* de les demander (permissions contextualisées), puis
  propose le choix de synchronisation cloud en réutilisant l'écran « Options
  réseau » (device-first : sync OFF par défaut, choix par appareil). L'onboarding
  s'insère avant l'aiguillage Login/Galerie et ne s'affiche qu'une fois : le
  drapeau « déjà vu » est figé à vrai pour les installations existantes (session
  active ou base locale déjà créée), afin qu'une simple mise à jour ne le
  ré-affiche pas. Nouveau fichier de préférences dédié `florapin_onboarding`.
- **Tirer pour rafraîchir (pull-to-refresh).** La galerie, le feed « Partagées
  avec moi » et l'écran « Fleurs à identifier » se rafraîchissent désormais d'un
  simple geste de tirage vers le bas. Sur la galerie (device-first), le geste
  relit la bibliothèque locale et ne relance une passe de synchronisation cloud
  que si la sync est activée — jamais de réseau exigé ; il rafraîchit aussi les
  badges de nouveautés (identifications et invitations). Sur le feed et
  « À identifier », il recharge la première page depuis le serveur. Le geste
  reste déclenchable même quand l'écran est vide.
- **Pagination du feed d'amis (défilement infini).** Le flux « Partagées avec
  moi » charge désormais les fleurs par pages et complète la liste à l'approche
  du bas de l'écran, au lieu de plafonner à un lot unique. Côté serveur, une
  vraie pagination *keyset* descendante s'appuie sur un curseur `before` — le
  couple stable `(createdAt, id)` — appliqué aux deux sources du feed (partages
  ciblés + diffusion réseau) avant fusion, puis re-tranché à la limite. Nouveau
  paramètre `GET /feed?before=<ISO8601>_<id>`, réservé au tri par date
  (incompatible avec `sort=likes`, qui renvoie alors un 400). Le paramètre
  `since` (delta de synchronisation) reste inchangé.

### Interne
- **Tests UI Compose des flux critiques capture & partage.** Nouveaux tests
  instrumentés `app/src/androidTest/…/capture/CaptureFlowTest` (écran de revue
  de la photo piloté avec une source d'image factice — l'aperçu CameraX n'étant
  pas instrumentable sur émulateur) et `…/share/ShareFlowerSheetTest` (sélection
  d'un destinataire, partage réseau « tous mes amis », révocation, via un
  `ShareViewModel` alimenté par des APIs factices, comme `ShareViewModelTest`).
  L'écran de revue `CapturedPhotoScreen` (et les types `Captured`/`LocationState`)
  passent de `private` à `internal` pour la testabilité. La CI compile désormais
  la variante `androidTest` (`assembleDebugAndroidTest`) à chaque PR pour éviter
  leur rot ; leur exécution réelle reste locale (`connectedDebugAndroidTest`,
  émulateur requis).
- **CI unifiée sur chaque PR (`.github/workflows/ci.yml`).** Le workflow
  d'intégration continue construit et teste l'app *et* le backend à chaque
  push/pull request : job Android (lint, `testDebugUnitTest`, `assembleDebug`
  en debug uniquement, APK publié en artefact) et job Backend (`npm ci`,
  `npm run build`, `npm test`, puis `npm run test:e2e` via Testcontainers sur
  le démon Docker d'`ubuntu-latest`). Un `google-services.json` factice est
  injecté pour satisfaire le plugin `google-services`, et `MAPTILER_API_KEY`
  retombe sur une valeur vide (aucune clé requise pour compiler). Fichier
  renommé depuis `android-ci.yml` pour refléter sa couverture app + backend.

## [1.12.0] — 2026-07-04

_versionName 1.12.0, versionCode 20._

### Ajouté
- **Partage à tout son réseau d'amis (présents et futurs).** Nouveau mode de
  partage « 👥 Tous mes amis » : le périmètre choisi (une fleur, un album ou
  toutes mes fleurs) est partagé avec l'ensemble du réseau d'amis en un seul
  partage persistant. Contrairement à un partage figé, **un ami ajouté plus tard
  y accède automatiquement**, sans avoir à re-partager (nouvel endpoint
  `POST /shares/all-friends`, audience `all_friends`).
- **Sélection des amis plus visible.** La liste des amis n'est plus cachée dans
  un menu déroulant : elle s'affiche directement sous forme de puces
  sélectionnables, avec l'option « 👥 Tous mes amis » en tête.
- **Destinataire affiché dans le récap des partages.** Chaque partage existant
  indique désormais son destinataire, en plus du périmètre et de l'état du GPS :
  un badge coloré distinctif « 👥 Tous mes amis » pour un partage réseau, ou le
  nom de l'ami pour un partage ciblé.
- **Écran « Options réseau » après connexion.** Après une connexion par
  email/mot de passe, une page présente la synchronisation cloud (sauvegarde des
  fleurs sur le serveur, multi-appareils, partage avec les amis) et laisse
  l'activer via un interrupteur unique — pré-coché sur le choix courant de
  l'appareil (device-first : désactivé par défaut). Le choix est enregistré puis
  la synchronisation est amorcée si activée. Reste modifiable dans Profil.

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
