-- Schéma de données FloraPin (NODE-27)
-- PostgreSQL + PostGIS. DDL de référence pour le POC ; en production, géré via
-- les migrations TypeORM (cf. backend/ARCHITECTURE.md).

-- Extension géospatiale (positions des fleurs).
CREATE EXTENSION IF NOT EXISTS postgis;
-- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- email insensible à la casse (colonne users.email)
CREATE EXTENSION IF NOT EXISTS citext;

-- =====================================================================
-- Utilisateurs
-- =====================================================================
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         CITEXT UNIQUE NOT NULL,          -- insensible à la casse
    password_hash TEXT        NOT NULL,            -- bcrypt/argon2 (cf. NODE-17)
    display_name  TEXT        NOT NULL,
    -- Vérification d'email opt-in, jamais bloquante (NODE-117).
    email_verified    BOOLEAN     NOT NULL DEFAULT false,
    email_verified_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Migration des bases existantes (NODE-117) : ces colonnes vivent dans le
-- CREATE TABLE ci-dessus. Sur une base créée AVANT NODE-117, le CREATE TABLE
-- IF NOT EXISTS est ignoré et ne les ajoute pas → ces ALTER les rattrapent.
-- (Sans ça, tout SELECT sur `users` — dont le login — échoue en 500.)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;
-- Avatar (TÂCHE 5.1) : clé de l'objet image WebP sur MinIO ; NULL = pas d'avatar.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_key TEXT;

-- =====================================================================
-- Refresh tokens (rotation — NODE-17)
-- =====================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,                     -- on ne stocke jamais le token en clair
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);

-- =====================================================================
-- Tokens de réinitialisation de mot de passe (NODE-116)
--   À usage unique (used_at) et durée limitée (expires_at). Seul le hash
--   est stocké, jamais le token en clair.
-- =====================================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pwd_reset_user ON password_reset_tokens(user_id);

-- =====================================================================
-- Tokens de vérification d'email (NODE-117) — opt-in, jamais bloquant.
-- =====================================================================
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_email_verif_user ON email_verification_tokens(user_id);

-- =====================================================================
-- Référentiel d'espèces (NODE-124)
--   Source structurée vers laquelle pointe flowers.species_id. Le texte libre
--   flowers.species est conservé : le rapprochement est best-effort, sans perte.
-- =====================================================================
CREATE TABLE IF NOT EXISTS species (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scientific_name TEXT NOT NULL UNIQUE,          -- binôme latin (clé naturelle)
    common_name     TEXT NOT NULL,                 -- nom commun FR
    family          TEXT NOT NULL,                 -- famille botanique (Rosaceae…)
    description     TEXT NOT NULL DEFAULT '',
    emoji           TEXT,                           -- repli visuel (optionnel)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Recherche / autocomplétion par nom scientifique (en complément de l'UNIQUE).
CREATE INDEX IF NOT EXISTS idx_species_scientific_name
    ON species(scientific_name);

-- =====================================================================
-- Fleurs
-- =====================================================================
CREATE TABLE IF NOT EXISTS flowers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Identifiant local stable envoyé par le client au push (sync — NODE-19).
    -- Rend POST /sync/flowers idempotent : un re-push retombe sur la fleur
    -- existante au lieu d'en créer un doublon (cf. albums.client_id).
    client_id   TEXT,
    image_key   TEXT NOT NULL,                     -- clé de l'objet (pleine rés.) dans MinIO
    thumbnail_key TEXT,                            -- miniature WebP (preview galerie/feed)
    -- Position WGS84 (SRID 4326). Nullable : la capture peut être sans GPS.
    location    geography(Point, 4326),
    accuracy_m  REAL,                              -- précision horizontale (mètres)
    taken_at    TIMESTAMPTZ NOT NULL,             -- date de la prise
    notes       TEXT NOT NULL DEFAULT '',
    species     TEXT,                              -- nom scientifique libre (NODE-26)
    -- Lien best-effort vers le référentiel (NODE-124). SET NULL : retirer une
    -- espèce du référentiel n'efface pas la fleur.
    species_id  UUID REFERENCES species(id) ON DELETE SET NULL,
    tags        TEXT[] NOT NULL DEFAULT '{}',      -- étiquettes libres
    visibility  TEXT NOT NULL DEFAULT 'private'    -- 'private' | 'friends'
                CHECK (visibility IN ('private', 'friends')),
    -- Demande d'identification collaborative (NODE-133).
    needs_identification BOOLEAN NOT NULL DEFAULT false,
    -- Dernière sollicitation des amis (ouverture + relances manuelles, TÂCHE 4.4).
    -- Anti-spam serveur : une relance est refusée sous le délai minimal.
    last_reminded_at TIMESTAMPTZ,
    -- Diffusion GPS au feed des amis (NODE-136) : masque le GPS si false quand
    -- visibility = 'friends', comme l'option includeGps des partages ciblés.
    feed_include_gps BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ                        -- soft-delete (sync — NODE-19)
);
-- Migration des bases existantes : colonnes de `flowers` définies dans le
-- CREATE TABLE ci-dessus mais ajoutées au fil des nœuds. Sur une base déjà
-- créée, le CREATE TABLE IF NOT EXISTS ne les ajoute pas → ALTER de rattrapage.
-- IMPORTANT : ces ALTER précèdent les CREATE INDEX qui portent sur ces colonnes
-- (sinon, sur une vieille base, l'index échouerait « column ... does not exist »).
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS species TEXT;                         -- NODE-26
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}';    -- NODE-26
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'private'; -- NODE-22/136
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS species_id UUID
    REFERENCES species(id) ON DELETE SET NULL;                                     -- NODE-124
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS needs_identification BOOLEAN
    NOT NULL DEFAULT false;                                                        -- NODE-133
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS last_reminded_at TIMESTAMPTZ;         -- TÂCHE 4.4
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS feed_include_gps BOOLEAN
    NOT NULL DEFAULT true;                                                         -- NODE-136
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS thumbnail_key TEXT;                   -- preview WebP
ALTER TABLE flowers ADD COLUMN IF NOT EXISTS client_id TEXT;                        -- idempotence sync push (NODE-19)

-- Index (après les ALTER : toutes les colonnes ciblées existent désormais).
-- Requêtes géo (ST_DWithin, bbox) : index GiST sur la position.
CREATE INDEX IF NOT EXISTS idx_flowers_location ON flowers USING GIST (location);
-- Listing par propriétaire + sync incrémentale.
CREATE INDEX IF NOT EXISTS idx_flowers_owner       ON flowers(owner_id);
CREATE INDEX IF NOT EXISTS idx_flowers_updated_at  ON flowers(updated_at);
-- Recherche par espèce / étiquette (NODE-26).
CREATE INDEX IF NOT EXISTS idx_flowers_species     ON flowers(species);
CREATE INDEX IF NOT EXISTS idx_flowers_species_id  ON flowers(species_id);   -- jointure référentiel (NODE-124)
CREATE INDEX IF NOT EXISTS idx_flowers_tags        ON flowers USING GIN (tags);
-- Résolution du feed broadcast : fleurs des amis visibles 'friends' (NODE-136).
CREATE INDEX IF NOT EXISTS idx_flowers_feed ON flowers(owner_id)
    WHERE visibility = 'friends';
-- Pagination keyset descendante du feed (TÂCHE 1.2) : le tri/curseur porte sur
-- le couple (created_at, id). Index composite DESC pour servir le keyset et la
-- limite sans tri en mémoire, aussi bien pour le broadcast que pour le scope 'all'.
CREATE INDEX IF NOT EXISTS idx_flowers_owner_created
    ON flowers(owner_id, created_at DESC, id DESC);
-- Idempotence du push (owner, client_id) : un même localId ne crée qu'une fleur
-- par utilisateur. Index partiel : les fleurs sans client_id (API standard) sont
-- ignorées. Sert aussi de lookup pour le dédoublonnage au re-push.
CREATE UNIQUE INDEX IF NOT EXISTS idx_flowers_owner_client
    ON flowers(owner_id, client_id) WHERE client_id IS NOT NULL;

-- Rapprochement best-effort du texte libre `species` vers le référentiel
-- (NODE-124). Sans perte : `species` (texte) reste la source quand aucun match.
UPDATE flowers f
   SET species_id = s.id
  FROM species s
 WHERE f.species_id IS NULL
   AND f.species IS NOT NULL
   AND lower(btrim(f.species)) = lower(s.scientific_name);

-- =====================================================================
-- Photos d'une fleur (NODE-104 : plusieurs photos par fleur)
--   Une fleur a 1..n photos ordonnées ; exactement une est la couverture.
--   `flowers.image_key` est conservé le temps de la transition (= couverture).
-- =====================================================================
CREATE TABLE IF NOT EXISTS flower_photos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    image_key   TEXT NOT NULL,                     -- clé de l'objet (pleine rés.) dans MinIO
    thumbnail_key TEXT,                            -- miniature WebP (preview)
    position    INTEGER NOT NULL DEFAULT 0,        -- ordre d'affichage
    is_cover    BOOLEAN NOT NULL DEFAULT false,    -- photo de couverture
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE flower_photos ADD COLUMN IF NOT EXISTS thumbnail_key TEXT;  -- preview WebP
CREATE INDEX IF NOT EXISTS idx_flower_photos_flower ON flower_photos(flower_id);
-- Au plus une couverture par fleur.
CREATE UNIQUE INDEX IF NOT EXISTS idx_flower_photos_cover
    ON flower_photos(flower_id) WHERE is_cover;

-- Backfill : chaque fleur existante devient sa propre photo de couverture.
INSERT INTO flower_photos (flower_id, image_key, position, is_cover)
SELECT id, image_key, 0, true FROM flowers
ON CONFLICT DO NOTHING;

-- =====================================================================
-- Albums de fleurs (NODE-98)
--   Regroupement nommé de fleurs appartenant à un utilisateur.
-- =====================================================================
CREATE TABLE IF NOT EXISTS albums (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    -- Identifiant stable généré par le client à la création (anti-doublon).
    -- Rend la création idempotente : un re-push (réponse perdue / crash après le
    -- POST) retombe sur l'album existant au lieu d'en créer un second.
    client_id   UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Idempotent pour les bases déjà créées (ALTER ADD COLUMN IF NOT EXISTS).
ALTER TABLE albums ADD COLUMN IF NOT EXISTS client_id UUID;
ALTER TABLE albums ADD COLUMN IF NOT EXISTS cover_flower_id UUID
    REFERENCES flowers(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_albums_owner ON albums(owner_id);
-- Unicité (owner, client_id) : garantit qu'un même clientId ne crée qu'un album
-- par utilisateur. Index partiel : les anciens albums (client_id NULL) sont ignorés.
CREATE UNIQUE INDEX IF NOT EXISTS idx_albums_owner_client
    ON albums(owner_id, client_id) WHERE client_id IS NOT NULL;

-- Appartenance fleur ↔ album (n..n).
CREATE TABLE IF NOT EXISTS flower_albums (
    album_id    UUID NOT NULL REFERENCES albums(id)  ON DELETE CASCADE,
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    PRIMARY KEY (album_id, flower_id)
);
CREATE INDEX IF NOT EXISTS idx_flower_albums_flower ON flower_albums(flower_id);

-- =====================================================================
-- Groupes collaboratifs (TÂCHE 7.1) — « albums collaboratifs = groupes »
--   Un groupe est l'unité de collaboration autour d'un ou plusieurs albums.
--   Créer un album collaboratif crée le groupe ; d'autres albums peuvent y être
--   rattachés. Découplé du partage réseau (`shares`) : l'appartenance vit dans
--   `group_members`, pas dans les amitiés.
-- =====================================================================
CREATE TABLE IF NOT EXISTS groups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    -- Idempotence de création (owner, client_id), sur le modèle des albums.
    client_id   UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_groups_owner ON groups(owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_groups_owner_client
    ON groups(owner_id, client_id) WHERE client_id IS NOT NULL;

-- Appartenance utilisateur ↔ groupe. role='owner'|'member',
-- status='pending'|'accepted' (invitation en attente / acceptée).
CREATE TABLE IF NOT EXISTS group_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    role        TEXT NOT NULL DEFAULT 'member'
                CHECK (role IN ('owner', 'member')),
    status      TEXT NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'accepted')),
    invited_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user  ON group_members(user_id);

-- Rattachement d'un album à un groupe + régime de droits.
--   group_id NULL       => album solo (privé au propriétaire, comportement historique).
--   permission_mode     => 'open' (tout membre édite) | 'restricted' (au cas par cas).
-- Idempotent pour les bases déjà créées (ADD COLUMN IF NOT EXISTS).
ALTER TABLE albums ADD COLUMN IF NOT EXISTS group_id UUID
    REFERENCES groups(id) ON DELETE SET NULL;
ALTER TABLE albums ADD COLUMN IF NOT EXISTS permission_mode TEXT NOT NULL DEFAULT 'open';
ALTER TABLE albums DROP CONSTRAINT IF EXISTS albums_permission_mode_check;
ALTER TABLE albums ADD CONSTRAINT albums_permission_mode_check
    CHECK (permission_mode IN ('open', 'restricted'));
CREATE INDEX IF NOT EXISTS idx_albums_group ON albums(group_id);

-- Droits « au cas par cas » d'un membre sur un album (mode 'restricted').
CREATE TABLE IF NOT EXISTS album_permissions (
    album_id    UUID NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    can_edit    BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (album_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_album_permissions_user ON album_permissions(user_id);

-- =====================================================================
-- Amitiés (NODE-20)
-- =====================================================================
CREATE TABLE IF NOT EXISTS friendships (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      TEXT NOT NULL DEFAULT 'pending'    -- 'pending'|'accepted'|'blocked'
                CHECK (status IN ('pending', 'accepted', 'blocked')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (requester_id <> addressee_id),
    UNIQUE (requester_id, addressee_id)
);
CREATE INDEX IF NOT EXISTS idx_friendships_addressee ON friendships(addressee_id);

-- =====================================================================
-- Partages configurables (NODE-22)
--   scope = 'all'    : toutes les fleurs du propriétaire
--   scope = 'flower' : une fleur précise (flower_id)
--   scope = 'album'  : les fleurs d'un album précis (album_id) — NODE-101
--   include_gps      : false => coordonnées masquées (protection des spots)
-- =====================================================================
CREATE TABLE IF NOT EXISTS shares (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- NULL pour audience='all_friends' (partage à tout le réseau).
    shared_with UUID REFERENCES users(id) ON DELETE CASCADE,
    audience    TEXT NOT NULL DEFAULT 'friend'
                CHECK (audience IN ('friend', 'all_friends')),
    scope       TEXT NOT NULL DEFAULT 'all'
                CHECK (scope IN ('all', 'flower', 'album')),
    flower_id   UUID REFERENCES flowers(id) ON DELETE CASCADE,
    album_id    UUID REFERENCES albums(id)  ON DELETE CASCADE,
    include_gps BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- cohérence scope/flower_id et scope/album_id
    CHECK ((scope = 'flower') = (flower_id IS NOT NULL)),
    CHECK ((scope = 'album')  = (album_id  IS NOT NULL)),
    -- destinataire précis ssi audience='friend'
    CHECK ((audience = 'friend') = (shared_with IS NOT NULL))
);
-- Migration des bases existantes (NODE-101) : partage par album. `album_id` ne
-- vit que dans le CREATE TABLE ci-dessus → absent des bases créées avant NODE-101.
-- Sans ça, toute lecture de `shares` (feed, demandes d'identification) plante en
-- 500 (« column Share.album_id does not exist »).
ALTER TABLE shares ADD COLUMN IF NOT EXISTS album_id UUID
    REFERENCES albums(id) ON DELETE CASCADE;
-- Élargit la contrainte de périmètre pour autoriser scope='album' (idempotent).
ALTER TABLE shares DROP CONSTRAINT IF EXISTS shares_scope_check;
ALTER TABLE shares ADD CONSTRAINT shares_scope_check
    CHECK (scope IN ('all', 'flower', 'album'));

-- Partage à tout le réseau d'amis, présents ET futurs (audience='all_friends').
-- `shared_with` devient optionnel (NULL pour ce mode) et une nouvelle colonne
-- `audience` distingue le partage ciblé du partage réseau. Idempotent.
ALTER TABLE shares ADD COLUMN IF NOT EXISTS audience TEXT NOT NULL DEFAULT 'friend';
ALTER TABLE shares DROP CONSTRAINT IF EXISTS shares_audience_check;
ALTER TABLE shares ADD CONSTRAINT shares_audience_check
    CHECK (audience IN ('friend', 'all_friends'));
ALTER TABLE shares ALTER COLUMN shared_with DROP NOT NULL;
ALTER TABLE shares DROP CONSTRAINT IF EXISTS shares_recipient_check;
ALTER TABLE shares ADD CONSTRAINT shares_recipient_check
    CHECK ((audience = 'friend') = (shared_with IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_shares_owner ON shares(owner_id);
CREATE INDEX IF NOT EXISTS idx_shares_user  ON shares(shared_with);

-- =====================================================================
-- Propositions d'espèce collaboratives (NODE-31)
-- =====================================================================
CREATE TABLE IF NOT EXISTS species_proposals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    proposed_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    species     TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'accepted')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_species_proposals_flower ON species_proposals(flower_id);
-- « Merci 🌸 » en un tap (TÂCHE 4.3) : le propriétaire remercie l'auteur d'une
-- proposition. Horodatage nullable — sa présence rend le merci idempotent (un
-- seul merci par proposition). Idempotent pour les bases déjà créées.
ALTER TABLE species_proposals ADD COLUMN IF NOT EXISTS thanked_at TIMESTAMPTZ;

-- =====================================================================
-- Cœurs sur les fleurs (NODE-139)
-- =====================================================================
CREATE TABLE IF NOT EXISTS flower_likes (
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Type de réaction (TÂCHE 3.5) ; 'heart' = cœur historique (NODE-139) et
    -- défaut d'un POST /like sans corps (anciennes apps). Une seule réaction par
    -- (fleur, utilisateur) : changer d'emoji met à jour cette colonne.
    reaction    TEXT NOT NULL DEFAULT 'heart',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (flower_id, user_id)            -- une réaction par (fleur, utilisateur)
);
-- Réactions enrichies (TÂCHE 3.5) : ajout de la colonne `reaction` aux bases déjà
-- créées (le CREATE TABLE IF NOT EXISTS ne la pose pas). Les cœurs existants
-- prennent la valeur par défaut 'heart' — rétro-compatibilité assurée. Idempotent.
ALTER TABLE flower_likes ADD COLUMN IF NOT EXISTS reaction TEXT NOT NULL DEFAULT 'heart';
ALTER TABLE flower_likes DROP CONSTRAINT IF EXISTS flower_likes_reaction_check;
ALTER TABLE flower_likes ADD CONSTRAINT flower_likes_reaction_check
    CHECK (reaction IN ('heart', 'love', 'blossom', 'rose', 'daisy', 'lavender', 'magnify', 'thumbsup'));
-- Comptage des réactions par fleur + « ma réaction » (NODE-139 / TÂCHE 3.5).
CREATE INDEX IF NOT EXISTS idx_flower_likes_flower ON flower_likes(flower_id);

-- =====================================================================
-- Commentaires sur les fleurs (fil de discussion)
-- =====================================================================
CREATE TABLE IF NOT EXISTS flower_comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    authored_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Édition d'un commentaire par son auteur (NULL si jamais modifié).
ALTER TABLE flower_comments ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
-- Réponse citée : commentaire racine auquel celui-ci répond (fil à un seul
-- niveau ; NULL pour un commentaire de premier niveau). Aplati côté serveur.
ALTER TABLE flower_comments
    ADD COLUMN IF NOT EXISTS reply_to_id UUID
    REFERENCES flower_comments(id) ON DELETE CASCADE;
-- Listing chronologique des commentaires d'une fleur.
CREATE INDEX IF NOT EXISTS idx_flower_comments_flower ON flower_comments(flower_id);
-- Récupération des réponses d'un commentaire racine.
CREATE INDEX IF NOT EXISTS idx_flower_comments_reply_to ON flower_comments(reply_to_id);

-- =====================================================================
-- Notifications in-app (NODE-23)
-- =====================================================================
CREATE TABLE IF NOT EXISTS notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        TEXT NOT NULL,             -- friend_request | friend_accepted | flower_shared | flower_liked | flower_commented | comment_mention | species_* | identification_requested
    data        JSONB NOT NULL DEFAULT '{}',
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_unread
    ON notifications(user_id) WHERE read_at IS NULL;

-- =====================================================================
-- Jetons d'appareil pour le push FCM/APNs (NODE-57)
-- =====================================================================
CREATE TABLE IF NOT EXISTS device_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL UNIQUE,             -- jeton FCM/APNs
    platform    TEXT NOT NULL                     -- 'android' | 'ios' | 'web'
                CHECK (platform IN ('android', 'ios', 'web')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_device_tokens_user ON device_tokens(user_id);

-- =====================================================================
-- Notes
-- =====================================================================
-- * Visibilité d'une fleur pour l'utilisateur U :
--     - owner_id = U, OU
--     - visibility = 'friends' ET amitié 'accepted' entre U et owner, OU
--     - existe flower_shares(flower_id, U).
--   (les requêtes effectives sont décrites dans backend/docs/API.md)
