# Journal des modifications — FloraPin

Toutes les modifications notables de l'application sont consignées ici.

Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/)
et le projet suit le [versionnage sémantique](https://semver.org/lang/fr/).

> ⚠️ **À tenir à jour à chaque modification.** Ajouter les changements sous
> « Non publié », puis les basculer dans une nouvelle version datée lors d'une
> release (en pensant à incrémenter `versionName`/`versionCode` dans
> `app/build.gradle.kts`).
>
> Ce fichier est le journal **technique**, interne. Toute modification visible
> par l'utilisateur doit *aussi* être résumée, sans jargon, dans
> [`CHANGELOG_SIMPLE.md`](CHANGELOG_SIMPLE.md) — c'est **ce dernier** qui est
> publié sur la page « Nouveautés » du site.

## [Non publié]

### Ajouté
- **Commentaires non lus signalés par fleur.** La galerie récupère les
  notifications `flower_commented` et `comment_mention` encore non lues, puis
  affiche une pastille 💬 sur la vignette concernée. Ouvrir cette fleur retire
  immédiatement le marqueur et marque toutes ses notifications de commentaire
  comme lues côté serveur.

### Modifié
- **Validation explicite des champs mono-ligne.** Les 22 champs concernés
  présentent maintenant l'action IME « Terminé » : Entrée retire le focus,
  ferme explicitement le clavier Android et confirme la prise en compte avec un
  retour haptique léger. Les champs multilignes conservent leur comportement.
- **Densités de galerie distinctes sur téléphone.** En portrait, les modes
  Compacte, Confort et Grande imposent respectivement trois, deux et une
  colonnes. La grille reste adaptative en paysage et sur les écrans larges.
- **Commentaires encore resserrés.** L'espace entre le nom de l'auteur et le
  texte est réduit, ainsi que la hauteur visuelle des actions de réponse et de
  menu.
- **Statistique d'identification plus explicite.** Le profil affiche désormais
  directement « 1 identification » ou « N identifications », suivi du détail
  des propositions acceptées.
- **Icône d'ajout à un album harmonisée dans le détail.** L'action de la fiche
  fleur utilise maintenant le pictogramme botanique d'album avec sa pastille
  d'ajout, déjà employé dans la sélection multiple.
- **Titre des partages raccourci.** L'écran « Partagées avec moi » adopte le
  titre plus direct « Partagées », cohérent avec son onglet de navigation.
- **Action de commentaire enrichie dans les partages.** Chaque carte affiche le
  nombre courant de commentaires à côté de « Commenter », réduit l'espace sous
  cette action et rafraîchit le compteur à la fermeture de la discussion.
- **Auteur des fleurs partagées simplifié.** Les cartes retirent le préfixe
  « Partagée par » et placent directement le nom de l'ami à droite, tout en
  conservant son accès au profil.
- **Cartes partagées harmonisées avec l'accueil.** Le nom de la plante apparaît
  désormais en bas de la photo sur un dégradé. Les actions de réaction et de
  sélection sont déplacées en haut, dans deux pastilles translucides de même
  taille ; l'étoile vectorielle ne change plus de dimensions lorsqu'elle est
  sélectionnée.
- **Mentions d'amis fiabilisées dans les commentaires.** La saisie `@` propose
  les amis acceptés même si leur chargement réseau se termine après le début de
  la frappe. Une notification `comment_mention` n'est maintenant créée que si
  la personne mentionnée est amie avec l'auteur et possède réellement un droit
  d'accès à la fleur (partage, diffusion au réseau ou demande d'identification).
  Dans un commentaire affiché, toucher le `@Nom` d'un ami ouvre désormais sa
  fiche profil ; une mention qui ne correspond plus à un ami reste non cliquable.

### Corrigé
- **Actions de l'accueil qui se chevauchaient.** Les trois boutons de l'en-tête
  disposent maintenant d'un espacement dédié et d'une marge à droite, afin que
  leurs surfaces et leurs badges restent séparés sur les téléphones étroits.
- **Carte retardée pendant les transitions de sortie.** La surface MapLibre est
  masquée dès la mise en pause de sa destination et restaurée à la reprise. La
  carte principale et la mini-carte du détail disparaissent ainsi en même temps
  que le reste de leur écran.

## [1.18.0] — 2026-07-24

### Modifié
- **Navigation principale recentrée sur la photo.** Les quatre destinations
  persistantes encadrent désormais une action de capture centrale. La barre,
  plus fine, épouse le bouton photo avec un dégagement régulier et ne masque
  plus prématurément la fin de la galerie.
- **Accueil photo-first réorganisé.** La galerie adopte de plus grandes vignettes
  par défaut. Les demandes d'identification, les amis et les notifications sont
  regroupés dans l'en-tête, tandis que les filtres rejoignent la recherche et
  que la carte reste accessible depuis la même rangée.
- **Compteur de notifications explicite.** La cloche affiche maintenant le
  nombre de notifications non lues, avec une description adaptée aux lecteurs
  d'écran.
- **Commentaires plus compacts.** Les cartes utilisent des espacements réduits et
  placent l'action « Répondre » à côté du texte afin d'afficher davantage de
  messages à l'écran tout en conservant des cibles tactiles accessibles.
- **Ajout à un album harmonisé.** L'action disponible pendant la sélection de
  fleurs reprend désormais le pictogramme d'album fermé avec marque-page utilisé
  dans la navigation, complété par une pastille d'ajout botanique.

### Corrigé
- **Champ de commentaire écrasé.** Dans la feuille de discussion, seule la liste
  des commentaires occupe l'espace restant et devient défilable. Le champ de
  saisie conserve ainsi sa hauteur minimale, même avec de nombreux messages et
  le clavier ouvert.

_versionName 1.18.0, versionCode 33._

## [1.17.0] — 2026-07-23

### Ajouté
- **Deux expériences de prise de vue.** Le nouveau mode Classique privilégie une
  capture immédiate avec des commandes compactes pour le zoom, le macro, le
  flash, la torche et la grille. Le mode Pro expose les réglages ISO, temps de
  pose, mise au point manuelle, balance des blancs, compensation d'exposition et
  zoom lorsque le capteur les prend en charge.
- **Contrôles Pro reliés au capteur.** Les plages disponibles sont lues depuis
  Camera2, les paramètres incompatibles sont désactivés et la capture Pro
  privilégie la qualité maximale.
- **Nouvelle collection d'icônes Albums.** Cinq propositions vectorielles
  botaniques sont ajoutées avec une planche de comparaison.

### Modifié
- **Viseur entièrement redessiné.** L'interface adopte des surfaces fumées, un
  réticule de mise au point, des données techniques compactes, un obturateur
  personnalisé et un accent ambre réservé au mode Pro.
- **Pictogramme Albums harmonisé.** L'état vide et la navigation principale
  utilisent désormais l'album avec marque-page botanique.

### Corrigé
- **Clé MapTiler des builds Play Store.** Le workflow de publication injecte
  désormais la clé MapTiler dans le bundle Android et interrompt la release si
  le secret GitHub correspondant est absent.

_versionName 1.17.0, versionCode 32._

## [1.16.0] — 2026-07-23

### Modifié
- **Raccourcis du profil harmonisés.** La statistique des identifications
  acceptées adopte l'alignement des cartes Badge et Herbier avec l'icône loupe
  botanique. Les libellés « Configuration » et « Partagées » restent complets
  sur une seule ligne, dans une barre d'onglets pleine largeur dont chaque onglet
  occupe un tiers de l'écran.
  La statistique d'identifications est regroupée directement sous les badges.
- **États des badges plus distincts.** Les cartes utilisent désormais le vert
  botanique et un sceau doré une fois tous les paliers obtenus, et un vert plus
  doux tant qu'une progression reste à compléter.

### Corrigé
- **Retrait involontaire d'une fleur d'album.** Un appui long sur une photo ouvre
  désormais une confirmation avant de retirer la fleur de l'album ; la fleur
  reste dans la collection.
- **En-tête du centre de notifications sur petits écrans.** Le titre ne revient
  plus à la ligne et l'action « Tout marquer comme lu » devient une icône de
  double validation, accessible avec son libellé complet.
- **Demandes d'amis lisibles sur petits écrans.** Le nom et les actions d'une
  demande reçue ne se compressent plus dans une même ligne : les boutons sont
  placés sous l'identité de la personne.
- **Barres Android pendant la capture.** La visée et la revue d'une photo
  réservent maintenant les zones système, afin que les barres d'état et de
  navigation ne recouvrent plus l'application.

_versionName 1.16.0, versionCode 31._

## [1.15.1] — 2026-07-22

### Corrigé
- **Validation du bundle Android.** Les ressources d'avatars et le chargement des
  libellés de localisation passent désormais les contrôles de la build release.

### Infrastructure
- **Publication Play Store automatisée.** Un workflow GitHub Actions construit
  et signe l'Android App Bundle depuis un tag, s'authentifie auprès de Google
  Cloud par fédération d'identité, puis publie la version sur la piste choisie.

_versionName 1.15.1, versionCode 30._

## [1.15.0] — 2026-07-22

### Ajouté
- **Nettoyage des images orphelines et déduplication du stockage.** Les variantes
  WebP sont adressées par leur empreinte SHA-256 afin qu'un même fichier ne soit
  stocké qu'une fois par propriétaire. Une tâche périodique supprime les objets
  MinIO anciens qui ne sont plus référencés, après un délai de sécurité.
- **Présentation interactive du flux photo.** La landing explique le parcours de
  la capture au serveur et propose un comparateur avant/après sur une photo réelle.
  Son laboratoire plein écran combine cinq résolutions, six qualités (Q70 à Q95)
  et trois filtres de réduction sur 78 fichiers réels, avec taille mesurée,
  comparaison par séparation et inspection à 100 %. Le sans-perte reste disponible
  comme témoin à la définition originale ; Lanczos3 est présélectionné comme filtre retenu.
- **Bibliothèque d'avatars FloraPin.** Onze compagnons botaniques générés sont
  embarqués en WebP 256 px Q80. Un avatar stable est attribué par identifiant aux
  profils sans photo, et le sélecteur permet soit d'en téléverser un, soit de
  conserver le choix d'une image du téléphone.

### Modifié
- **Compression des photos déplacée dans l'app.** Après la capture, un Worker
  applique Lanczos3 puis encode hors du thread d'interface l'image principale en
  WebP. Le profil Standard limite le bord à 3 200 px en Q70 ; le profil Premium à
  4 000 px en Q90. `PREMIUM_FOR_ALL_BETA=true` attribue temporairement le profil
  Premium à tous les comptes. La miniature reste limitée à 400 px en Q70,
  l'utilisateur peut continuer à naviguer pendant le traitement et l'upload
  attend des fichiers valides.
- **Upload des variantes sans réencodage serveur.** Les nouveaux clients envoient
  directement l'image principale et la miniature WebP ; l'API valide leur format
  et leurs dimensions avant stockage. L'ancien endpoint de conversion reste
  temporairement disponible pour les versions de l'app pas encore mises à jour.
- **Icônes botaniques harmonisées.** Les écrans Albums, détail d'album, détail de
  fleur, fiche espèce et commentaires remplacent leurs emojis système par des
  ressources FloraPin multicolores. Les icônes existantes de l'accueil sont
  réutilisées lorsque leur action correspond, avec de nouveaux pictogrammes pour
  le retour, l'ajout, l'édition, la suppression, le partage et les menus.
- **Fiche fleur simplifiée.** Le bouton de réaction affiche un unique emoji dont
  l'état change, suivi du seul compteur numérique. La localisation réutilise le
  géocodage ville/région du flux partagé à la place des coordonnées brutes, et
  l'éditeur d'espèce masque les étiquettes tout en préservant les valeurs déjà
  enregistrées.
- **Avatars dans les relations sociales.** La liste d'amis affiche à gauche du
  nom, sans email, la photo distante ou le compagnon attribué. Les photos choisies
  avant cette mise à jour sont récupérées depuis le profil public lorsque
  l'ancienne réponse de liste ne contient pas encore l'avatar. Les réponses de
  l'API d'amitié exposent maintenant l'URL présignée de l'avatar, et la fiche
  publique d'un ami utilise le même repli botanique.

_versionName 1.15.0, versionCode 29._

## [1.14.6] — 2026-07-21

### Ajouté
- **Emprises des cartes hors ligne.** Les zones téléchargées sont dessinées sur
  la carte et l'action « Voir » recentre la vue sur l'emprise correspondante.
- **Zones cartographiques hors ligne.** La zone visible peut être téléchargée
  depuis la carte avec deux niveaux de détail. MapLibre conserve les tuiles du
  style choisi, expose la progression et la taille occupée, et permet de mettre
  en pause, reprendre ou supprimer chaque zone enregistrée.
- **Observations proches dans le détail d'une plante.** La mini-carte distingue
  la plante ouverte des autres photos prises dans un rayon de 500 mètres. Un appui
  sur un repère photo ouvre directement la fiche de la fleur correspondante.
- **Suppression des notifications par swipe.** Une ligne du centre peut être
  balayée vers la droite ou la gauche. Elle disparaît immédiatement, est supprimée
  côté serveur via `DELETE /notifications/:id`, puis restaurée à sa position si
  l'appel échoue; le serveur vérifie systématiquement son propriétaire.
- **Badge sur l'icône de l'application.** Tous les canaux Android autorisent
  explicitement les badges et chaque notification est déclarée comme un événement
  comptable. Les launchers compatibles, dont Samsung One UI, peuvent afficher le
  nombre de notifications système en attente à côté de l'icône FloraPin.
- **Explication des badges par appui long.** Chaque famille de badge possède une
  description métier et ses paliers. Un maintien sur sa carte ouvre une infobulle
  Material 3; la même explication est exposée aux lecteurs d'écran.
- **Détails des fleurs d'amis depuis la carte.** La visionneuse plein écran
  propose désormais un bouton « Détails » donnant accès à l'espèce, au
  commentaire, à la date de prise de vue et aux tags, avec retour direct à la
  photo. Les métadonnées et toutes les photos du flux sont conservées dans le
  marqueur sélectionné sans créer de copie locale de la fleur.

### Modifié
- **Contrôles compacts sur la carte.** Le sélecteur de période quitte la barre
  permanente pour un menu contextuel. Le filtre de temps, les cartes hors ligne
  et le thème sont regroupés dans un dock discret à icônes, avec micro-badges pour
  la période active et le nombre de zones enregistrées.
- **Association visuelle entre pins et photos.** Le placement pénalise désormais
  les croisements, effectue une passe finale de démêlage et route chaque courbe en
  tenant compte des chemins existants. Chaque liaison partage une couleur stable
  avec la bordure de sa photo, dans une palette distincte pour les fleurs d'amis.
- **Gestion étendue des zones hors ligne.** Le plafond passe à 40 000 tuiles par
  zone et 250 000 au total. Le géocodeur Android propose la commune ou la région
  comme nom, et les zones enregistrées utilisent une ligne nettement plus compacte.
- **États des contrôles superposés de la carte.** Les boutons hors ligne, amis et
  style utilisent désormais un rendu plat avec un contour fin. Seuls un filtre
  réellement actif ou un téléchargement en cours reçoivent l'accent vert.
- **Synchronisation demandée une seule fois.** L'onboarding de première
  installation passe directement des permissions aux préférences de partage et
  comporte désormais trois étapes. Le choix de synchronisation reste proposé
  après la connexion, où il est pertinent et n'est plus présenté en double.
- **Libellé compact sans GPS dans le flux partagé.** Une fleur dont la position
  n'est pas diffusée affiche « Sans position », sur une seule ligne dans les
  cartes étroites, au lieu de « Position non partagée ».
- **Lieu lisible dans le flux partagé.** Les coordonnées GPS brutes sont remplacées
  par la commune ou le village obtenu par géocodage inverse Android. La région
  embarquée sert de repli hors ligne, et les résultats sont mis en cache par zone.
- **Zoom des groupes sur la carte.** Un appui sur un groupe centre désormais la
  caméra sur son point réel et utilise le niveau d'expansion calculé par
  MapLibre, au lieu d'ajouter arbitrairement deux niveaux de zoom.
- Barre de tri compacte sur la page « Partagées avec moi » : les boutons
  « Récentes », « Meilleures photos » et « Ma sélection » prennent moins de place
  sur petits écrans grâce à un affichage plus compact et un scroll horizontal.

### Corrigé
- **Ouverture de la carte avec le mode hors ligne.** MapLibre est maintenant
  initialisé avant son gestionnaire de régions, ce qui évite le crash immédiat
  lors de l'entrée sur la carte après l'ajout des téléchargements.
- **Liste d'amis actualisée en direct.** Les push `friend_request` et
  `friend_accepted` émettent maintenant un événement interne consommé par l'écran
  Amis. Une demande ou une acceptation reçue pendant que la page est visible
  apparaît immédiatement, sans changement d'onglet ni clignotement de chargement.
- **Encodage de l'écran « Partagées avec moi ».** Les libellés accentués et les
  pictogrammes de favori, localisation, espèce et commentaire utilisent désormais
  des échappements Unicode stables et ne s'affichent plus sous forme de caractères
  UTF-8 corrompus dans l'APK release.

_versionName 1.14.6, versionCode 28._

## [1.14.5] — 2026-07-11

### Corrigé
- **Synchronisation bloquée par `UNIQUE constraint failed: flowers.serverId`
  (I11).** Une capture poussée dont la réponse était perdue (app tuée / réseau
  coupé après la création côté serveur, fréquent pendant l'indisponibilité du
  serveur) restait `serverId=null`. Au `pull`, le garde-fou anti-doublon ne
  couvrait que les twins déjà synchronisés : la fleur était alors ré-insérée en
  doublon fantôme, puis le `push` suivant tentait de poser le même `serverId`
  dessus → collision `UNIQUE`, la synchro échouant à chaque passe. Désormais le
  `pull` n'insère plus de doublon quand une capture locale correspond (le prochain
  `push` la lie via la déduplication serveur sur `clientId`), et le `push`
  auto-répare les bases déjà corrompues en purgeant le doublon fantôme avant le
  `markSynced`. Couvert par `pull_doesNotDuplicateUnsyncedLocalCapture` et
  `push_purgesPhantomDuplicateHoldingServerId`.
- **Déploiement : la stack ne démarrait pas au boot si le démon Docker n'était
  pas encore prêt.** `florapin.service` pouvait échouer avec « failed to connect
  to the docker API … daemon is running » (socket `/var/run/docker.sock` absent)
  et rester en `failed` jusqu'à un restart manuel. L'unité systemd attend
  maintenant la disponibilité du démon (`docker info`, jusqu'à ~60 s) via un
  `ExecStartPre` avant de lancer `compose up`.

_versionName 1.14.5, versionCode 27._

## [1.14.4] — 2026-07-11

### Ajouté
- **Navigation gestuelle dans l'onboarding.** Un glissement vers la gauche passe
  à l'étape suivante, y compris sur les choix réseau et de partage, tout en
  conservant les boutons existants.
- **Connexion hors ligne.** Après une première authentification réussie, le
  dernier compte peut se reconnecter sans réseau avec son dernier mot de passe
  valide. Seul un vérificateur PBKDF2 salé est conservé dans le stockage chiffré,
  jamais le mot de passe en clair. Le changement de mot de passe remplace ce
  vérificateur et la suppression du compte l'efface.

### Modifié
- **Autorisations indépendantes.** Les cartes Caméra et Localisation de
  l'onboarding déclenchent chacune leur propre demande système. La localisation
  est explicitement facultative et l'utilisateur peut continuer sans GPS ; la
  demande principale ne sollicite plus que la caméra.

_versionName 1.14.4, versionCode 26._

## [1.14.3] — 2026-07-11

### Ajouté
- **Console de supervision serveur.** Un dashboard d'administration protégé
  par jeton expose les volumes PostgreSQL, les derniers comptes et fleurs ainsi
  qu'un inventaire paginé des médias avec aperçus présignés.
- **Lecture groupée des notifications.** Le centre propose une action « Tout
  marquer comme lu ». L'état est appliqué immédiatement dans l'interface puis
  confirmé par une mise à jour groupée côté serveur, avec restauration en cas
  d'échec réseau.

### Modifié
- **Saisie du mot de passe.** Les écrans de connexion et d'inscription disposent
  désormais d'un bouton accessible pour afficher ou masquer le mot de passe.

### Corrigé
- **Suppression de fleurs synchronisées.** Le client ne purge plus sa copie
  locale tant que le serveur n'a pas confirmé la suppression. Les erreurs HTTP
  restent en attente de nouvelle tentative, ce qui empêche une fleur masquée
  localement de demeurer accessible via un ancien partage.
- **Médias du dashboard dupliqués.** L'inventaire regroupe les références par
  clé de stockage : la couverture présente à la fois sur la fleur et dans sa
  collection de photos ne produit plus deux cartes ni un total gonflé.

_versionName 1.14.3, versionCode 25._

## [1.14.2] — 2026-07-10

### Ajouté
- **Alerte de mise à jour Play Store.** Au démarrage, l'app interroge Play Core et
  affiche un dialogue lorsqu'une nouvelle version est disponible. Le bouton
  ouvre directement la fiche FloraPin dans le Play Store, avec un repli vers sa
  version web. L'utilisateur peut masquer l'alerte pour le `versionCode` annoncé
  sans désactiver les alertes des versions suivantes.
- **Appels photo plus lisibles sur la carte.** À fort zoom, l'emoji reste ancré
  sur la position de la fleur et une ligne pointillée le relie à une bulle photo
  déportée. Un placement par forces repousse les photos entre elles, les garde
  à l'écart des emojis et allonge leur liaison si nécessaire, sans les réduire.
  Les angles sont choisis selon l'espace réellement libre et les liaisons suivent
  une courbe douce qui contourne les autres fleurs. Le placement suit le drag et
  le zoom en direct, puis s'affine à l'arrêt avec une interpolation des positions
  et des courbes pour supprimer les téléportations visuelles.
- **Filtre de durée compact.** Les quatre grandes chips deviennent un sélecteur
  segmenté à trois boutons : Toutes, 7 jours et 30 jours.
- **Styles de carte recentrés.** Le sélecteur propose uniquement Clair, Satellite,
  Hybride et Hiver dans un contrôle superposé en haut à droite de la carte ; les
  anciens choix retirés reviennent automatiquement à Clair.
- **Contrôles de carte allégés.** Le filtre Espèce disparaît ; l'affichage des
  fleurs d'amis est activé par défaut et piloté depuis un bouton en overlay. Les
  contrôles superposés gagnent un fond opaque et une ombre pour rester lisibles
  sur les cartes claires. Les previews d'amis portent un fin liseré orange, contre
  un fin liseré blanc pour les fleurs personnelles.
- **Overlay carte affiné.** Les boutons Amis et style sont alignés sur une ligne,
  Amis à gauche, sans double contour. Le halo bleu de précision GPS est masqué
  pour ne conserver que l'indicateur de position.

### Modifié
- **Galerie adaptée à chaque orientation.** En portrait, la recherche et les
  filtres restent empilés au-dessus d'une grille de grandes cartes. En paysage,
  ils basculent dans un panneau latéral fixe et la galerie utilise toute la
  hauteur disponible, sans réduire artificiellement les photos.
- **Barre d'accueil botanique.** La fleur du titre est remplacée par le logo
  FloraPin recadré. Les actions identification, amis et notifications utilisent
  désormais des illustrations vectorielles multicolores cohérentes avec la
  navigation principale. Le badge non lu se place au cœur de la fleur portée
  par la cloche.
- **Validation visuelle sur émulateur.** Un jeu de données instrumenté réservé
  aux builds de test permet de contrôler la galerie en portrait et en paysage
  sans compte de production ni données personnelles.

## [1.14.1] — 2026-07-10

### Ajouté
- **Vitrine — aperçu de l'app.** La section « Captures d'écran à venir » est
  remplacée par trois mockups d'écran (carte + regroupements, détail d'une fleur,
  flux d'amis) reconstitués en HTML/CSS d'après `map/`, `detail/` et `feed/` :
  mêmes couleurs Material, mêmes libellés, barre de navigation à cinq onglets
  (`PhoneNav.astro`). L'échelle suit la colonne via une requête de conteneur.
- **Vitrine — image Open Graph.** `public/img/og-image.png` (1200×630) : les
  partages de lien renvoyaient jusqu'ici un 404 (`/og-image.png` n'existait pas).
  Ajout de `og:image:width/height/alt`, `og:site_name`, `twitter:image:alt` et
  `theme-color`.

### Modifié
- **Typographie des titres : Fraunces → Lora.** Appliqué des deux côtés pour
  garder une marque cohérente : `--font-display` de la vitrine
  (`Layout.astro`) et `DisplayFontFamily` de l'app (`ui/theme/Type.kt`, toujours
  via Downloadable Fonts, aucun binaire embarqué). Lora est variable (400→700) :
  les graisses en place (460/560/640 sur le web, Medium/SemiBold côté app)
  passent inchangées. L'og-image est régénérée en Lora : elle est rendue par une
  capture headless d'une page HTML (vraies polices web) au lieu d'être composée
  par sharp, qui n'avait accès qu'aux polices système (Georgia).
- **Vitrine — distribution par Google Play uniquement.** Le téléchargement direct
  de l'APK est retiré (bouton du hero, repli du CTA final, `DownloadButton.astro`,
  `DOWNLOAD_URL`/`DOWNLOAD_NOTE`/`DOWNLOAD_FILENAME`). `deploy.sh` ne copie plus
  `app-debug.apk` dans `landing/public/` ; le rsync `--delete` retire le binaire
  du VPS au prochain déploiement. `version.json` reste alimenté par `deploy.sh`
  et sert désormais la mention « Bêta &lt;version&gt; · Android 8+ ».
- **Vitrine — page d'accueil dans un `<main>`.** Les sélecteurs du sentier animé
  (`TrailOverlay`) passent de `body > section` à `main > section`.

### Corrigé
- **Vitrine — lien mort.** Le pied de page pointait vers `/mentions-legales`,
  page qui n'existe pas ; le lien est retiré.
- **Vitrine — données structurées.** `offers.price` sans `priceCurrency` rendait
  le bloc `SoftwareApplication` invalide.
- **Vitrine — bascule GPS.** Le `<button>` de la démo vie privée n'avait pas de
  `type="button"`.

## [1.14.0] — 2026-07-09

### Ajouté
- **Réglages de partage par défaut.** Une nouvelle section « Partage par défaut »
  (Profil › Configuration) fixe le destinataire par défaut, l'inclusion de la
  position GPS et le partage automatique des nouvelles fleurs. Les mêmes questions
  sont posées à la première ouverture, dans un 4ᵉ écran d'onboarding placé après
  le choix de synchronisation — et sauté si le cloud est refusé, puisque partager
  suppose des fleurs déposées sur le serveur. Le partage automatique s'applique dès
  qu'une fleur obtient son id serveur, après l'envoi de sa photo (`SyncEngine`), et
  n'interrompt jamais la synchronisation s'il échoue.
- **Recherche d'un ami dans la feuille de partage.** Au-delà de 4 raccourcis, un
  bouton « … » ouvre une recherche par nom ou email. Les raccourcis affichent les
  derniers amis avec qui vous avez partagé.
- **Photo d'une fleur d'ami depuis la carte.** Un tap sur le marqueur d'une fleur
  d'ami — jusqu'ici inerte, faute de détail local — ouvre sa photo en plein écran.
- **Pastilles photo sur la carte.** Au-delà du zoom 16, les fleurs isolées
  troquent leur emoji d'espèce contre un aperçu rond de leur photo (256 px),
  cerclé de blanc. Leur taille suit le zoom — doublement tous les deux niveaux —
  mais se bride dès que deux voisines se recouvriraient de plus de 10 % de leur
  diamètre, mesuré sur les fleurs visibles à l'écran. Les vignettes sont décodées
  après l'affichage (la carte n'attend pas) et plafonnées à 80 par passe ; celles
  qui manquent gardent leur emoji, à leur taille habituelle.
- **Badge floral pour les groupes de fleurs.** Les cercles unis des clusters
  cèdent la place à une fleur stylisée dont la couleur, la taille et le nombre de
  pétales croissent avec le nombre de fleurs regroupées (vert < 10, ambre < 50,
  terracotta au-delà). Le compte s'inscrit dans son cœur plein.

### Modifié
- **La feuille de partage ne concerne plus que la photo affichée.** Les périmètres
  « Un album » et « Toutes mes fleurs » disparaissent, de même que le sélecteur
  d'album. Les partages « toutes mes fleurs » déjà créés restent listés et
  révocables, signalés comme hérités.
- **Publication au flux d'amis.** Le toggle « Publier sur mon flux d'amis » (et son
  option GPS) est retiré de l'écran de détail : le partage « 👥 Tous mes amis »
  le remplace.
- **Cartes du flux « Partagées ».** Le cœur et l'étoile passent sur la photo, en
  bas à droite. Le nombre de cœurs et le compteur de commentaires ne sont plus
  affichés (le bouton « Commenter » demeure). Le compteur de photos du carrousel
  migre en haut à droite, là où les nouvelles pastilles le recouvraient.
- **Barre du bas.** Icônes agrandies (30 dp au lieu de 26) et libellés bornés à
  une ligne — « Partagées » revenait à la ligne et décalait les icônes.
- **Cloche de notifications.** Le badge chiffré cède la place à un simple point de
  présence ; le centre de notifications garde son point « non lu » par ligne.
- **Barres en paysage.** Barre haute (48 dp) et barre basse (56 dp, libellés
  masqués) se compactent en paysage pour rendre la hauteur au contenu.
- **Icônes de la barre de navigation.** Les emojis des 5 onglets (Accueil,
  Albums, Carte, Partagées, Profil) sont remplacés par un set d'illustrations
  botaniques maison (fleurs bleues + vert forêt, marque FloraPin), générées puis
  détourées en PNG transparents (`res/drawable-nodpi/ic_nav_*`). Rendues via
  `Image` (et non `Icon`) pour préserver les couleurs ; l'état actif reste porté
  par l'indicateur de la `NavigationBar` et le libellé.

### Interne
- **Changelog public séparé (`CHANGELOG_SIMPLE.md`).** La page « Nouveautés » du
  site rend désormais un journal grand public — une phrase par nouveauté, du
  point de vue de l'utilisateur, sans nom de fichier ni d'endpoint, en trois
  rubriques (Nouveau / Amélioré / Réparé) — au lieu de ce fichier technique.
  `deploy.sh` copie `CHANGELOG_SIMPLE.md` vers `landing/src/changelog.md` ; la
  règle d'ajout est rappelée en tête des deux fichiers (le bloc de citation
  reste masqué sur la vitrine par le CSS de `changelog.astro`).

### Corrigé
- **Révocation d'un partage.** Révoquer ne recharge plus tout l'état de la feuille.
  La liste des partages ne se vidait qu'un instant, mais cela suffisait à ramener
  la vue en haut et forçait à re-scroller. La ligne disparaît désormais
  immédiatement, et revient à sa place si l'appel échoue.

## [1.13.0] — 2026-07-05

_versionName 1.13.0, versionCode 21._

### Ajouté
- **Albums collaboratifs = groupes (TÂCHE 7.1).** Un album peut désormais devenir
  collaboratif : le créer coche « Album collaboratif » crée aussi un **groupe**
  (décision actée n°1), et plusieurs albums peuvent partager le même groupe.
  Chaque album de groupe porte un **régime de droits** — *tout ouvert* (chaque
  membre édite) ou *au cas par cas* (droit d'édition accordé membre par membre) —
  découplé du partage réseau. Backend : nouveau module `groups/` (entités
  `Group`/`GroupMember`, controller/service, invitations réservées aux amis,
  acceptation, retrait/quitter, suppression détachant les albums), colonnes
  `albums.group_id` / `albums.permission_mode` + table `album_permissions`, DDL
  idempotent dans `db/schema.sql`, notifications `group_invited` /
  `group_member_joined` (push data-only), matrice de droits couverte par des
  tests unitaires et e2e. App : API `GroupsApi` + endpoints album
  `PATCH /albums/:id/group` et `/permissions`, migration Room 15→16
  (`groupId`/`permissionMode`/`canEdit` sur les albums), panneau de collaboration
  (membres, invitations d'amis, droits) dans le détail d'album, et
  `AlbumSyncEngine` durci contre les conflits d'édition concurrente (je ne
  renomme/supprime que mes albums, la réconciliation d'appartenance ne touche que
  mes propres fleurs, un 403 abandonne l'édition locale).

### Modifié
- **Feed en 2 colonnes (mosaïque).** Le fil « Partagées avec moi » s'affiche
  désormais dans une `LazyVerticalStaggeredGrid` à deux colonnes : les fleurs
  seules occupent chacune une colonne (hauteurs variables) pour un rendu type
  mosaïque, tandis que la barre de filtres, les cartes-lot (3.6), le séparateur
  « nouveautés » (3.2), l'indicateur de pagination et le mode « Ma sélection »
  restent en pleine largeur (`StaggeredGridItemSpan.FullLine`).

### Ajouté
- **Accessibilité — passe transverse (TÂCHE 6.18).** Les emojis servant
  d'icônes (barre de navigation, boutons d'action, FAB, cloche de notifications,
  flèches « retour », actions de sélection…) étaient lus littéralement par
  TalkBack (« maison à trois étages », « visage »…). Deux composants communs
  (`ui/components/EmojiIcon.kt`) corrigent le tir : `EmojiIcon` remplace le
  glyphe par un `contentDescription` parlant (« Retour », « Capturer une
  fleur », « Supprimer »…), et `DecorativeEmoji` retire de l'arbre
  d'accessibilité les emojis purement décoratifs déjà doublés d'un libellé
  (onglets de la bottom bar, icône de recherche, chip de style de carte). Les
  cibles tactiles des mini-actions par photo (couverture/suppression) passent
  de 36 à 48 dp. Le menu de débordement « ⋮ » de chaque commentaire
  (éditer/supprimer) et le bouton « ✕ » de fermeture de la visionneuse plein
  écran reçoivent également un `contentDescription` parlant (« Actions du
  commentaire », « Fermer »), et la cible du bouton de fermeture passe de 40 à
  48 dp.
- **Transitions partagées galerie ↔ détail (TÂCHE 6.17).** L'ouverture d'une
  fleur depuis la galerie fait glisser/agrandir sa vignette en continu vers
  l'image du détail (élément partagé keyé sur l'id local), et inversement au
  retour. Le `NavHost` est enveloppé dans un `SharedTransitionLayout` et chaque
  destination transmet sa portée via un `FloraSharedScope` nullable. L'API
  `SharedTransitionScope` étant encore expérimentale (BOM 2024.12.01), toute la
  mécanique est isolée dans `ui/transition/FloraSharedTransition.kt` derrière un
  interrupteur `SHARED_TRANSITIONS_ENABLED` et un modificateur `sharedFlowerImage`
  qui dégrade en no-op (aperçus, tests, portée absente). Seule la page ouverte au
  démarrage du pager du détail porte l'élément partagé ; les pages voisines du
  balayage s'affichent normalement.
- **Erreurs réseau humaines (TÂCHE 6.16).** Un composant commun `NetworkError`
  (ui/components/) traduit les `Throwable` réseau bruts (`IOException` OkHttp,
  `HttpException` Retrofit) en messages français lisibles et, surtout, distingue
  le **mode avion / pas de connexion** (`UnknownHostException`, `IOException`)
  du **serveur injoignable** (timeout `SocketTimeoutException` ou 5xx) — les deux
  restant « réessayables », contrairement à une erreur applicative 4xx définitive.
  Le composable `NetworkErrorState` réutilise l'illustration d'`EmptyState` et
  n'affiche le bouton « Réessayer » que quand un nouvel essai a du sens. Les
  ViewModels réseau (feed, détail, amis, auth) passent désormais par ce mapping
  (`networkErrorMessage` / `networkErrorInfo`), avec surcharge des 4xx propres à
  l'auth (401 « Identifiants invalides », 409 « Compte déjà existant »…). Le feed
  vide en erreur affiche l'écran illustré avec bouton de réessai.
- **Retour haptique (TÂCHE 6.15).** Un utilitaire `Haptics` (util/) centralise
  les vibrations sémantiques de l'app (`tap` léger, `celebrate` appuyé) au-dessus
  du `LocalHapticFeedback` de Compose (respecte le réglage système, aucune
  permission). Points d'appel : like/réaction (`LikeButton`), déclenchement de
  l'obturateur (`CameraScreen`) et déblocage d'un palier de badge (`BadgesTab`,
  qui appelait déjà le retour haptique directement).
- **État de synchronisation visible (TÂCHE 6.14).** L'onglet Configuration
  affiche désormais l'état de la dernière passe du worker (en cours / réussie /
  échec + message d'erreur) et l'horodatage de la dernière synchro réussie (lu via
  `PrefsLastSyncStore` / curseur `last_sync_at`). Le `SyncWorker` publie son
  résultat dans un `SyncStatusStore` dédié (fichier de prefs `florapin_sync_status`,
  disjoint de `florapin_sync`), exposé en direct à l'UI via un flux. En galerie, un
  badge discret (« ☁️ » en attente d'envoi, « ⚠️ » en échec) surmonte les vignettes
  non synchronisées — uniquement lorsque la sync automatique est active
  (device-first : une fleur PENDING sync OFF est l'état de repos normal).
- **Annuler la suppression d'une fleur (TÂCHE 6.13).** Supprimer une fleur depuis
  le détail pose désormais un soft-delete immédiat (la fleur disparaît des listes)
  puis, de retour sur la galerie, affiche un snackbar « Fleur supprimée / Annuler ».
  « Annuler » restaure la fleur ; à l'expiration du snackbar, la suppression est
  finalisée (purge physique si jamais synchronisée, sinon propagation au serveur au
  prochain sync). Aucune sync n'est déclenchée pendant la fenêtre d'annulation :
  tant que la passe n'a pas tourné, la suppression reste locale et annulable (pas de
  course). Un balayage de sécurité à l'ouverture de la galerie purge les
  soft-deletes locaux jamais finalisés (app tuée pendant la fenêtre, détail ouvert
  hors galerie…). `FlowerRepository.softDelete/restore/finalizeDelete/purgeExpiredLocalDeletions`.
- **Partage externe de la photo (TÂCHE 6.12).** Le menu de débordement (⋮) du
  détail propose « 📷 Partager la photo » : la photo de couverture est partagée
  vers une autre application via un `Intent.ACTION_SEND` (`image/jpeg`). Les
  photos vivant en stockage privé (`filesDir/photos`), un `FileProvider`
  (authority `${applicationId}.fileprovider`, `file_paths.xml`) concède un accès
  temporaire en lecture (`content://`, `FLAG_GRANT_READ_URI_PERMISSION`).
  Device-first : sans fichier local (fleur seulement distante), un message
  prévient au lieu d'échouer.
- **Ouvrir dans Maps / copier les coordonnées (TÂCHE 6.11).** La mini-carte du
  détail expose un menu de débordement (⋮) superposé à son coin : « Ouvrir dans
  Maps » lance un Intent `geo:lat,lng?q=lat,lng` (repère sur la position, message
  si aucune application de cartes) et « Copier les coordonnées » place les
  coordonnées décimales (Locale.US, 6 décimales) dans le presse-papiers.
- **Balayage entre fleurs dans le détail (TÂCHE 6.10).** L'écran de détail est
  désormais enveloppé dans un `HorizontalPager` : on passe d'une fleur à l'autre
  d'un simple glissement horizontal, dans l'ordre par défaut de la galerie (plus
  récentes d'abord). La liste ordonnée d'ids provient de la même source Room que
  la galerie (`DetailPagerViewModel`) et transite d'un bloc vers le pager — pas de
  navigation fleur par fleur. Chaque page conserve son propre état (ViewModels
  keyés sur l'id de la fleur). Tant que la liste n'est pas chargée, la fleur ciblée
  s'affiche seule, le balayage s'activant dès que la liste est prête (device-first).
- **États vides soignés (TÂCHE 6.9).** Le composant réutilisable `EmptyState`
  (galerie, feed, albums, notifications…) gagne une illustration mise en valeur
  dans une pastille circulaire teintée et un bouton d'action optionnel
  (call-to-action). La galerie vide propose désormais « 📷 Capturer une fleur »
  et l'écran Albums vide « ➕ Créer un album », menant directement au bon geste.
- **Densité de grille réglable (TÂCHE 6.8).** Une pastille « Densité » dans la
  galerie, à côté du tri, permet de choisir la taille des vignettes (Compacte /
  Confort / Grande) : la grille adaptative resserre ou élargit ses colonnes en
  conséquence. Le choix est persisté par appareil dans un store dédié
  (`florapin_gallery`) et réappliqué au prochain lancement.
- **En-têtes par mois + fast scroller (TÂCHE 6.7).** La galerie regroupe les
  vignettes par mois de **capture** (`createdAt`), avec un en-tête pleine largeur
  introduisant chaque mois (ex. « Juin 2026 ») lorsqu'elle est triée par date. Une
  poignée latérale de défilement rapide (fast scroller) apparaît dès que la grille
  déborde de l'écran : on la fait glisser pour parcourir la galerie, et une bulle
  affiche le mois pointé pendant le glissement. Le tri par espèce reste à plat
  (sans en-têtes ni fast scroller).
- **Multi-sélection par appui long (TÂCHE 6.6).** Dans la galerie, un appui long
  sur une vignette entre en mode sélection (liseré + pastille ✅ sur les fleurs
  cochées) ; un simple tap coche/décoche ensuite les autres. Une barre du haut
  contextuelle affiche le nombre de fleurs sélectionnées et propose « tout
  sélectionner » (☑️), l'ajout groupé à un album (📁, feuille `AddToAlbumSheet`
  réutilisée en lot) et la suppression groupée (🗑️, avec confirmation). La
  suppression reste un soft delete (deletedAt + PENDING) pour les fleurs déjà
  synchronisées, propagé puis purgé par la sync ; la croix ✕ ou le retour arrière
  quittent le mode. Le bouton de capture s'efface pendant la sélection.
- **Indicateur de fix GPS (TÂCHE 6.5).** L'écran de capture affiche désormais,
  en haut à gauche, une pastille d'état de la position GPS sondée en continu
  pendant la visée (`📡 GPS…` en recherche, `📍 GPS ±N m` une fois fixée,
  `⚠️ GPS indisponible` sinon). Quand aucune position n'est disponible, un
  avertissement s'affiche juste au-dessus de l'obturateur *avant* la prise (« la
  photo sera enregistrée sans localisation »), et non après coup. Le fix déjà
  obtenu pendant la visée est réutilisé au moment d'enregistrer la fleur, ce qui
  évite une seconde attente de localisation après le déclenchement.
- **Déclencheur au volume (TÂCHE 6.4).** Sur l'écran de capture, les touches de
  volume (haut ou bas) déclenchent la prise de photo, comme l'obturateur à
  l'écran. L'interception n'est active que lorsque l'aperçu caméra est visible :
  l'écran enregistre l'action auprès de `MainActivity` à l'affichage et la retire
  à la sortie, si bien que les touches de volume gardent partout ailleurs leur
  comportement système normal. Un maintien de touche ne déclenche qu'une seule
  capture (`repeatCount == 0`), et une capture déjà en cours est ignorée.
- **Grille de composition (TÂCHE 6.3).** Une nouvelle bascule **▦ Grille** sur
  l'écran de capture superpose une grille « règle des tiers » à l'aperçu caméra
  (deux traits verticaux et deux horizontaux en blanc translucide), pour aider au
  cadrage. Purement visuelle (overlay `Canvas`), éteinte par défaut, sans aucun
  effet sur la caméra ni sur la photo prise ; le dessin n'intercepte pas les
  gestes, donc le tap-to-focus et le pincement-zoom restent actifs.
- **Mise au point par tap (TÂCHE 6.2).** Un appui sur l'aperçu caméra fait la
  mise au point sur le point touché : les coordonnées vue sont converties via la
  fabrique de points de mesure de la `PreviewView` (transformation vue → capteur
  gérée nativement, zoom compris), puis `startFocusAndMetering` lance le cycle
  d'AF/mesure. Le tap **respecte le mode macro** : le mode `AF_MODE_MACRO` est
  ré-appliqué une fois la mise au point terminée pour ne pas quitter le macro. Le
  pincement-zoom natif reste actif (l'événement tactile n'est pas consommé), et
  le tap-to-focus intégré du contrôleur est désactivé au profit de cette version.
- **Flash & torche à la capture (TÂCHE 6.1).** L'écran caméra offre deux
  nouvelles bascules à côté du mode macro : **⚡ Flash** (déclenchement du flash
  à la prise via `imageCaptureFlashMode`) et **🔦 Torche** (éclairage LED
  continu pendant la visée via `enableTorch`). Les deux contrôles sont
  indépendants, éteints par défaut, et se dégradent proprement si l'appareil n'a
  pas de LED (best effort).
- **Profil d'ami — amis en commun & ancienneté (TÂCHE 5.7).** Nouvel écran
  « profil d'ami » ouvert d'un tap sur un ami de l'écran **Amis** ou sur le
  « Partagée par … » d'une carte du **feed**. Il affiche l'avatar et le nom, une
  ancienneté « **Amis depuis mai 2026** » (calculée sur `friendship.createdAt`),
  le nombre d'**amis en commun**, les **espèces communes** et les **fleurs de
  l'ami visibles par moi**. Côté serveur, un endpoint public **limité**
  `GET /users/:id/profile` (module `friend-profile`) ne renvoie **que ce qui
  m'est déjà accessible** (fleurs partagées avec moi ou diffusées au réseau) —
  jamais les stats privées de l'ami — et n'est ouvert qu'entre amis acceptés
  (404 anti-énumération sinon). Aucune évolution de schéma (lecture seule).
- **Onglet Profil complété — nb de badges + dernières fleurs (TÂCHE 5.1).**
  L'onglet ① Profil affiche désormais, en plus de l'avatar et des statistiques
  d'entraide, un **compteur de badges débloqués** (raccourci vers l'onglet
  Badges) et un **aperçu horizontal des dernières fleurs** capturées (un tap
  ouvre le détail). Les deux sont calculés **100 % localement** (device-first,
  disponibles hors-ligne) via une passerelle `profile/ProfileCollection.kt` :
  nombre de paliers de la table Room `badges` (recalcul idempotent au passage) et
  6 dernières fleurs actives (`FlowerDao.recentActive`). Complète la refonte en
  trois onglets sans toucher au flux de déconnexion.
- **Mon herbier — stats de collection (TÂCHE 5.6).** Nouvel écran « Mon herbier »
  accessible depuis l'onglet Profil : espèces distinctes, nombre de fleurs et
  regroupement par **familles botaniques** (cartes dépliables, tap sur une espèce
  rapprochée → sa fiche). Les compteurs d'en-tête et la liste des espèces sont
  calculés **localement** (device-first, toujours disponibles, y compris
  hors-ligne — nouvel agrégat `FlowerDao.speciesCounts()`) ; le regroupement par
  familles est calculé **côté serveur** (la famille est portée par l'espèce) via
  un nouvel endpoint `GET /species/herbier`, donc partiel hors-ligne : l'écran
  retombe alors sur la liste à plat des espèces avec un bandeau explicatif. Côté
  backend, `SpeciesService.herbierFor` agrège les fleurs de l'utilisateur (deux
  requêtes groupées, pas de N+1) et `SpeciesService.normalizeFamily` normalise les
  familles (casse Titre, fusion des variantes) sur le référentiel embarqué du seed
  `seed-species.sql` ; les espèces en texte libre non rapprochées tombent sous
  « Non classées ». Nouveaux `herbier/HerbierScreen.kt` + `HerbierViewModel.kt`,
  route dans `FloraNavHost.kt`.
- **Grille de badges — étoiles + progression (TÂCHE 5.5).** L'onglet Badges du
  Profil affiche désormais une **grille** de familles de badges (DA actée) :
  chaque carte porte une **rangée d'étoiles** grisées (un palier atteignable
  chacune) qui se remplissent en or au fur et à mesure, une progression
  chiffrée « 34 / 50 » vers le prochain palier, et grise entièrement la carte
  tant qu'aucun palier n'est atteint. Deux sections : **Collection** (calcul
  local, disponible hors-ligne) et **Entraide** (compteurs serveur, cartes
  grisées hors-ligne — device-first). Les saisons (4 + « Quatre saisons ») et
  l'outre-mer (une par région) sont regroupés en **familles à paliers**
  (« 3 / 4 », « 2 / 5 »). Nouveau composant partagé `ui/components/BadgeCard.kt`
  + palette d'états dans `ui/theme/Color.kt` (étoile « or » chaude, lisible en
  clair comme en sombre) ; catalogue d'affichage `badges/BadgeCatalog.kt` ;
  `profile/BadgesViewModel.kt` (fusion local + serveur) et `profile/BadgesTab.kt`
  (grille). `BadgeCalculator` expose une passe `progress()` donnant les
  numérateurs bruts (fleurs, espèces, saisons, régions, lieux) pour la
  progression exacte, et `BadgeRepository.currentProgress()` la relaie. Un palier
  fraîchement débloqué déclenche un **retour haptique** de célébration (cf. QOL
  6.15, à centraliser plus tard dans un `Haptics` partagé) et un liseré sur la
  carte concernée.
- **Badges « entraide » — calcul serveur (TÂCHE 5.4).** Nouveau module backend
  `badges/` (`badges.module.ts` + `badges.controller.ts` + `badges.service.ts`,
  sur le modèle du module `likes` : agrégation **en lecture**, aucune table
  dédiée, recalcul à la volée). `GET /me/badges` renvoie les compteurs
  collaboratifs de l'utilisateur courant en une passe (quelques `COUNT` ciblés
  lancés **en parallèle**, pas de N+1) : 🤝 amis acceptés · 🔍 propositions
  faites · 🎓 propositions acceptées · ❓ demandes d'identification ouvertes ·
  ✅ propositions acceptées en tant que propriétaire · 💬 commentaires ·
  👍 réactions données · ❤️ réactions reçues. Sources : `Friendship`
  (`status='accepted'`, demandeur ou destinataire), `SpeciesProposal`
  (`proposedBy` / `status='accepted'`, et jointure `flowers.owner_id` pour les
  propositions reçues), `Flower.needsIdentification`, `FlowerComment`,
  `FlowerLike` (données = `user_id` ; reçues = jointure `flowers.owner_id`, hors
  auto-réactions). Côté app : `network/api/BadgesApi.kt` +
  `network/dto/BadgeDtos.kt` (`EntraideBadgeCountsDto`, champs à défaut pour la
  compat) branchés sur le client authentifié partagé (`NetworkModule`). Le
  serveur ne renvoie que des **compteurs bruts** : le mapping en paliers et la
  fusion avec les badges « collection » locaux se feront dans l'onglet Badges
  (TÂCHE 5.5). Device-first : indisponibles hors-ligne, à afficher depuis une
  dernière valeur en cache ou grisés.
- **Badges « collection » — calcul local (TÂCHE 5.3).** Nouveau
  `badges/BadgeCalculator.kt` : dérive **100 % localement** les badges à partir
  des fleurs de l'appareil — 🌸 Première fleur · 📚 Herbier (10/50/100/250) ·
  🌿 Diversité (10/25/50 espèces) · 🌷☀️🍁❄️ Saisons + 🍂 Quatre saisons ·
  🧭 Explorateur (2/5/10/15/18 régions, via `RegionResolver`) · 🏝️ Outre-mer
  (un badge par région d'outre-mer visitée) · 📍 Lieux distincts (grille ~5 km :
  5/15/30/50/100). Persistance en **table Room `badges`** (`data/BadgeEntity.kt`
  + `data/BadgeDao.kt`, une ligne par palier `(badgeId, tier)` — migration
  v14 → v15) ; agrégats ajoutés à `data/FlowerDao.kt` (`COUNT`,
  `COUNT(DISTINCT espèce)` avec repli sur le nom libre, coordonnées + dates —
  toujours `deletedAt IS NULL`). `data/BadgeRepository.kt` orchestre le recalcul
  et l'état « vu » : à la **première exécution** (base potentiellement déjà
  remplie), tous les paliers acquis sont marqués `seen = true` en masse (pas de
  pluie de célébrations, comme l'onboarding 1.4) ; les déblocages suivants sont
  à célébrer. Choix documentés : saisons codées **hémisphère nord** (fuseau
  Europe/Paris), grille 5 km avec correction de la longitude par
  `cos(latitude)`, dégradation device-first si les assets régions manquent.
- **Mapping GPS → région hors-ligne (TÂCHE 5.2).** Nouveau
  `geo/RegionResolver.kt` : les polygones des **18 régions françaises** (13 de
  métropole + 5 d'outre-mer) sont embarqués dans `assets/regions-fr.geojson`
  (source [france-geojson](https://github.com/gregoiredavid/france-geojson),
  simplifiée ; outre-mer réduit par Douglas-Peucker — ~125 Ko) et le test
  d'appartenance se fait **100 % localement** par *ray-casting* (rejet préalable
  par boîte englobante). `Polygon`, `MultiPolygon` et anneaux intérieurs (trous)
  sont gérés ; l'ordre GeoJSON `[longitude, latitude]` est respecté. Une position
  hors France renvoie `null` (les fleurs sans GPS ne sont pas résolues, pour ne
  pas fausser les futurs paliers). Débloque le calcul local des badges
  « Explorateur » / « Outre-mer » (TÂCHE 5.3).
- **Refonte Profil / Réglages en 3 onglets (TÂCHE 5.1).** L'écran Profil se
  divise désormais en trois onglets : **① Profil** (avatar, identité,
  statistiques d'entraide, emplacements des futurs herbier/badges), **② Badges**
  (grille à venir — TÂCHES 5.3–5.5) et **③ Configuration** (synchronisation,
  sauvegarde locale, sécurité, déconnexion, confidentialité, suppression de
  compte — le contenu préexistant, inchangé). Le flux de déconnexion
  (`LocalSessionDataCleaner`, qui ne purge plus les fleurs) n'est pas modifié.
- **Avatar / photo de profil (TÂCHE 5.1).** Depuis l'onglet Profil, l'utilisateur
  choisit une image via le sélecteur média système ; elle est réencodée en WebP
  côté serveur (nouveau `POST /users/me/avatar`, colonne `users.avatar_key`) et
  affichée via Coil (initiales par défaut en l'absence d'avatar). L'URL présignée
  est renvoyée par `GET /users/me` (et les autres réponses profil) et jamais
  figée. L'objet est purgé du stockage à la suppression de compte.
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

