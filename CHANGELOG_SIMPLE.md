# Les nouveautés de FloraPin

Ce que l'application vous apporte de nouveau, version après version — sans jargon.

> ⚠️ **Règle d'ajout (pour l'équipe, masquée sur le site).** Ce fichier est le
> changelog **public**, affiché sur la page « Nouveautés » de florapin.fr. À
> chaque modification, on écrit **deux entrées** : le détail technique dans
> `CHANGELOG.md`, et ici un résumé **compréhensible par tout le monde**.
> - Une phrase par nouveauté, à la place de l'utilisateur : ce qu'il peut faire,
>   pas comment c'est codé.
> - Jamais de nom de fichier, de classe, d'endpoint, de table ni de numéro de
>   tâche. Pas d'anglais technique.
> - Trois rubriques seulement : **Nouveau**, **Amélioré**, **Réparé**. Ce qui
>   n'a aucun effet visible pour l'utilisateur (tests, CI, refactorisations)
>   n'est **pas** mentionné.
> - On ajoute sous « En préparation », puis on bascule dans une version datée
>   lors d'une publication, en même temps que `CHANGELOG.md`.

## En préparation

### Réparé
- **Les photos supprimées restent vraiment privées.** Si le serveur ne confirme
  pas encore leur suppression, FloraPin réessaie au lieu de les oublier sur le
  téléphone alors qu'un ancien partage pourrait toujours les rendre visibles.

## Version 1.14.2 — 10 juillet 2026

### Nouveau
- **Les mises à jour sont plus faciles à installer.** FloraPin vous prévient
  lorsqu'une nouvelle version est disponible et vous permet d'ouvrir directement
  sa page Play Store, ou de masquer le rappel pour cette version.

### Amélioré
- **Les fleurs proches sont beaucoup plus faciles à distinguer sur la carte.**
  Leur photo s'écarte naturellement des autres, reste reliée à sa position par
  une courbe pointillée et se déplace sans saut pendant le zoom ou le glissement.
- **La carte est plus simple à régler.** Un filtre compact choisit la période,
  les fleurs d'amis sont visibles par défaut et les fonds Clair, Satellite,
  Hybride et Hiver se choisissent directement sur la carte. Les photos d'amis
  sont reconnaissables à leur fin contour orange.
- **La galerie s'adapte vraiment à la rotation du téléphone.** En portrait, les
  commandes restent au-dessus des photos ; en paysage, elles passent dans un
  panneau latéral pour conserver de grandes images faciles à parcourir.
- **La barre d'accueil reprend pleinement l'identité FloraPin.** Le vrai logo
  accompagne le titre et les raccourcis vers l'identification, les amis et les
  notifications utilisent de nouvelles illustrations botaniques assorties.

## Version 1.14.1 — 10 juillet 2026

### Nouveau
- **Le site montre enfin l'application.** Trois écrans (la carte et ses bouquets
  de fleurs regroupées, le détail d'une fleur, le flux de vos amis) remplacent
  les cadres vides qui annonçaient des images « à venir ».

### Amélioré
- **Nouvelle police pour les titres**, dans l'application comme sur le site :
  plus classique et plus facile à lire.
- **Un aperçu s'affiche quand on partage le lien du site.** Envoyer l'adresse de
  FloraPin par message ou sur un réseau social fait maintenant apparaître une
  belle image au lieu d'un rectangle vide.
- **Installation simplifiée.** L'application s'installe uniquement depuis le Play
  Store : plus de fichier à télécharger à la main, ni d'avertissement Android sur
  les « sources inconnues ».

### Réparé
- Le lien « Mentions légales » du bas de page menait à une page inexistante.

## Version 1.14 — 9 juillet 2026

### Nouveau
- **Réglages de partage par défaut.** Choisissez une fois pour toutes avec qui
  vos fleurs sont partagées, si la position est incluse, et si les nouvelles
  fleurs doivent partir automatiquement. Les mêmes questions vous sont posées à
  la première ouverture de l'application.
- **Recherche d'un ami au moment de partager.** Au-delà des raccourcis vers vos
  derniers destinataires, un bouton « … » permet de chercher un ami par son nom
  ou son email.
- **Les photos de vos amis s'ouvrent depuis la carte.** Un appui sur la fleur
  d'un ami affiche sa photo en grand.
- **Des photos sur la carte.** En zoomant, les fleurs isolées affichent un
  aperçu rond de leur photo au lieu de l'emoji de leur espèce. Plus vous zoomez,
  plus l'aperçu grandit — sans jamais recouvrir celui d'à côté.
- **Une fleur pour les groupes.** Là où la carte regroupait plusieurs fleurs,
  une jolie fleur remplace le rond uni : ses pétales et sa couleur disent d'un
  coup d'œil s'il y en a quelques-unes ou beaucoup.

### Amélioré
- **Partage simplifié.** On partage désormais la fleur affichée, tout
  simplement : les options « un album » et « toutes mes fleurs » disparaissent.
  Les partages de ce type déjà créés restent visibles et révocables.
- **Publier auprès de vos amis** se fait maintenant par le partage « Tous mes
  amis », et non plus par un réglage séparé sur la fiche de la fleur.
- **Cartes du fil « Partagées ».** Le cœur et l'étoile passent sur la photo, et
  l'affichage est allégé.
- **Barre du bas plus lisible** : icônes plus grandes, libellés qui ne passent
  plus à la ligne, et de nouvelles illustrations botaniques à la place des
  emojis.
- **Cloche de notifications** : un simple point signale les nouveautés, sans
  chiffre.
- **Écran en paysage** : les barres se compactent pour laisser plus de place à
  vos photos.

### Réparé
- **Annuler un partage** ne fait plus remonter la liste tout en haut.

## Version 1.13 — 5 juillet 2026

### Nouveau
- **Albums collaboratifs.** Un album peut être partagé avec un groupe d'amis qui
  y ajoutent leurs propres fleurs. Vous choisissez si tous les membres peuvent
  modifier l'album ou si vous accordez ce droit personne par personne.
- **Photographier plus facilement.** L'écran de capture gagne le flash et la
  torche, la mise au point d'un simple appui sur l'écran, une grille de cadrage,
  le déclenchement par les touches de volume et un indicateur de qualité du GPS
  qui prévient *avant* la photo si la position n'est pas encore trouvée.
- **Feuilleter ses fleurs.** Depuis la fiche d'une fleur, un glissement
  horizontal passe à la suivante.
- **Annuler une suppression.** Après avoir supprimé une fleur, un message
  « Annuler » vous laisse quelques secondes pour revenir en arrière.
- **Partager la photo ailleurs.** La photo d'une fleur peut être envoyée vers
  n'importe quelle autre application.
- **Ouvrir le lieu dans Maps** ou copier les coordonnées depuis la mini-carte.
- **Mon herbier.** Un écran qui récapitule votre collection : espèces
  différentes, nombre de fleurs et regroupement par familles botaniques.
- **Des badges à débloquer.** Une grille de badges récompense votre collection
  (nombre de fleurs, espèces, saisons, régions visitées) et votre entraide
  (amis, identifications proposées et acceptées, commentaires, réactions).
- **Photo de profil.** Choisissez un avatar depuis votre galerie.
- **Profil d'un ami.** En touchant le nom d'un ami, vous voyez depuis quand vous
  êtes amis, vos amis en commun, vos espèces communes et ses fleurs.
- **Ajouter un ami par QR code**, en se croisant sur le terrain, sans échanger
  d'adresse email.
- **Relancer ses amis** sur une fleur toujours pas identifiée (une fois par
  jour).
- **Dire « Merci 🌸 »** à l'ami qui vous propose une espèce, en un appui.
- **Écran « Mes demandes »** pour suivre l'avancement de vos demandes
  d'identification et voir qui a proposé quoi.
- **Enregistrer les fleurs de vos amis** en favoris privés, consultables même
  hors connexion.
- **Mentionner un ami** dans un commentaire avec « @ », et **répondre** à un
  commentaire précis. Vos commentaires sont modifiables, et un brouillon non
  envoyé est conservé.
- **Réactions.** Un appui long sur le cœur ouvre un choix d'emojis (😍 🌸 🌹 🌼
  🪻 🔍 👍). Un appui sur le compteur affiche qui a réagi.
- **Centre de notifications.** Une cloche dans l'écran d'accueil rassemble
  toutes vos notifications, avec un point sur celles non lues.
- **Des notifications plus vivantes.** Elles disent qui fait quoi, sur quelle
  fleur, avec la photo. Vous pouvez aimer ou répondre directement depuis la
  notification, et chaque catégorie (cœurs, commentaires, amis, identification)
  peut être coupée dans les réglages Android.
- **Sauvegarde locale.** Exportez toute votre bibliothèque dans un fichier, et
  réimportez-la — entièrement hors connexion.
- **Écrans d'accueil à la première installation**, qui expliquent l'application
  et les autorisations demandées avant de les demander.
- **Tirer vers le bas pour rafraîchir**, dans la galerie, le fil des fleurs
  partagées et l'écran des fleurs à identifier.
- **Le fil des amis se charge en continu** au fil du défilement, au lieu de
  s'arrêter après quelques fleurs.
- **Modifier son nom** et **changer son mot de passe** depuis le profil.
- **Voir où en est la synchronisation** : date de la dernière réussie, erreur
  éventuelle, et un repère sur les fleurs pas encore envoyées.

### Amélioré
- **Le fil « Partagées avec moi » s'affiche en mosaïque**, sur deux colonnes.
- **Galerie plus agréable** : fleurs regroupées par mois, défilement rapide sur
  le côté, taille des vignettes réglable, sélection multiple par appui long pour
  ranger ou supprimer plusieurs fleurs d'un coup.
- **Plusieurs fleurs partagées en même temps** sont regroupées dans une seule
  carte « Marie a partagé 5 fleurs », et un trait signale ce qui est nouveau
  depuis votre dernière visite.
- **Écrans vides plus accueillants**, avec une illustration et un bouton pour
  passer à l'action.
- **Vibrations discrètes** quand vous aimez une fleur, déclenchez une photo ou
  débloquez un badge.
- **Messages d'erreur en français**, qui distinguent une absence de connexion
  d'un serveur momentanément injoignable, avec un bouton « Réessayer » quand
  cela a du sens.
- **Application lisible par les lecteurs d'écran** : les icônes ne sont plus lues
  comme des emojis, et les boutons sont plus faciles à viser.
- **Ouverture d'une fleur en douceur** : la vignette s'agrandit jusqu'à la photo.

## Version 1.12 — 4 juillet 2026

### Nouveau
- **Partager avec tout son réseau d'amis**, y compris ceux que vous ajouterez
  plus tard : ils accèdent automatiquement au partage.
- **Choisir la synchronisation cloud** juste après la connexion, sur un écran
  qui explique à quoi elle sert.

### Amélioré
- **Choix des amis plus visible** au moment de partager, et chaque partage
  existant indique clairement son destinataire.
- **Le site s'anime** : un sentier de randonnée serpente le long de la page
  jusqu'à une épingle plantée sous le logo.

### Réparé
- **Repartager une fleur au même ami** ne provoque plus d'erreur : le partage
  est simplement mis à jour.

## Version 1.11 — 4 juillet 2026

_Première version publiée sur le Google Play Store, en test fermé._

### Nouveau
- **Page publique de suppression de compte** et **page des nouveautés** sur le
  site.

### Amélioré
- **L'application ne demande plus l'accès à vos photos** : elle n'en a jamais eu
  besoin, les photos étant prises par l'appareil photo.
- **La synchronisation cloud est désactivée par défaut** : FloraPin reste
  100 % sur votre téléphone tant que vous ne l'activez pas. Les installations
  existantes gardent leur réglage.
- **Nouveau site**, aux couleurs de sous-bois, avec l'inscription à la bêta mise
  en avant.
- **Sécurité renforcée côté serveur**, et un jeton d'un compte supprimé ne peut
  plus être utilisé.

### Réparé
- **L'email de vérification** peut à nouveau être renvoyé.
- **Vos propres cœurs** s'affichent correctement après une synchronisation.

## Version 1.10 — 2 juillet 2026

### Nouveau
- **Discuter d'une fleur à identifier.** Vos amis sollicités peuvent commenter la
  fleur pour demander une précision ou une autre photo.

### Réparé
- **Déconnexions intempestives** sur le fil « Partagées avec moi » et d'autres
  écrans.

## Version 1.9 — 2 juillet 2026

### Nouveau
- **Bouton « Tout synchroniser »** dans le profil, pour envoyer et récupérer vos
  fleurs à la demande même si la synchronisation automatique est coupée.

### Amélioré
- **Le réglage de synchronisation** devient « Synchroniser automatiquement » :
  décoché, l'application reste locale jusqu'à ce que vous le demandiez.

## Version 1.8 — 2 juillet 2026

### Amélioré
- **Sécurité et fiabilité du serveur** : limites sur les envois de photos et les
  tentatives de connexion, et une fleur privée ne peut plus être aimée par une
  personne qui ne devrait pas la voir. La suppression de compte efface bien
  toutes vos images.

### Réparé
- **Notifications enfin visibles sur Android 13 et plus récent.**
- **Fleurs en double** après une synchronisation, **suppressions qui
  revenaient**, et **modifications locales écrasées** : corrigés.
- **Plus de déconnexion** lors d'une simple coupure de réseau.
- **Application plus rapide** sur les listes de fleurs et la recherche.

### Amélioré (1.8.1)
- **Commentaires sur une fleur non synchronisée** : au lieu de disparaître, la
  section explique qu'il faut se connecter et activer la synchronisation.

## Version 1.7 — 30 juin 2026

### Nouveau
- **Commentaires sur les fleurs partagées.** Chaque fleur a son fil de
  discussion. Vous supprimez vos propres messages, et le propriétaire de la
  fleur peut modérer les siens.

## Version 1.6 — 30 juin 2026

### Nouveau
- **Ma position sur la carte**, avec un bouton pour se recentrer.
- **Les fleurs de vos amis apparaissent sur la carte** quand ils ont partagé leur
  position.

## Version 1.5 — 29 juin 2026

### Nouveau
- **Choisir la synchronisation cloud dès l'inscription**, et la modifier à tout
  moment dans le profil.

## Version 1.4 — 28 juin 2026

### Nouveau
- **Pastilles de nouveautés** sur les demandes d'identification et les demandes
  d'amis en attente.
- **Voir qui propose une espèce**, et pouvoir refuser une proposition.
- **Compteur d'identifications acceptées** sur votre profil.

### Réparé
- **Albums en double** après une synchronisation.
- **Photos de fleurs invisibles** après une synchronisation depuis un autre
  téléphone.
- **Glisser entre les photos en plein écran** fonctionne à nouveau.

## Version 1.3 — 28 juin 2026

### Nouveau
- **Plusieurs photos pour une même fleur** : après une prise, ajoutez-en une
  autre, annulez, ou terminez.
- **Visionneuse plein écran avec zoom** (pincement et double appui).
- **Les fleurs de vos amis s'affichent avec toutes leurs photos.**

## Version 1.2 — 28 juin 2026

### Nouveau
- **Zoom** et **mode macro** à la prise de photo, pour les sujets très proches.

## Version 1.1 — 28 juin 2026

### Nouveau
- **Identification collaborative bouclée** : vos amis proposent une espèce, vous
  l'acceptez d'un appui et elle s'applique à la fleur.
- **9 styles de carte** au choix (Rues, Plein air, Topographique, Satellite,
  Hybride, Épuré, Clair, Dataviz, Hiver).

### Réparé
- **Demandes d'identification invisibles** chez vos amis.
- **Boutons masqués** par la barre de navigation Android sur certains téléphones.
- **Photos illisibles** après une récupération depuis un autre appareil.

## Version 1.0 — 27 juin 2026

Première version de FloraPin : un carnet botanique photo qui fonctionne sans
connexion, avec une synchronisation cloud facultative et le partage entre amis.

- **Photographier une fleur** avec sa position, et la retrouver dans une galerie
  avec recherche, tri et albums.
- **Une carte** de toutes vos fleurs géolocalisées, avec filtres par période,
  espèce et amis.
- **Un compte, des amis, un fil des fleurs partagées** avec des « j'aime ».
- **Une synchronisation cloud facultative**, désactivée par défaut : sans elle,
  tout reste sur votre téléphone.
- **Des notifications** pour ne rien manquer.
