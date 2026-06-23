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
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         CITEXT UNIQUE NOT NULL,          -- insensible à la casse
    password_hash TEXT        NOT NULL,            -- bcrypt/argon2 (cf. NODE-17)
    display_name  TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- Refresh tokens (rotation — NODE-17)
-- =====================================================================
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,                     -- on ne stocke jamais le token en clair
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- =====================================================================
-- Tokens de réinitialisation de mot de passe (NODE-116)
--   À usage unique (used_at) et durée limitée (expires_at). Seul le hash
--   est stocké, jamais le token en clair.
-- =====================================================================
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pwd_reset_user ON password_reset_tokens(user_id);

-- =====================================================================
-- Fleurs
-- =====================================================================
CREATE TABLE flowers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_key   TEXT NOT NULL,                     -- clé de l'objet dans MinIO
    -- Position WGS84 (SRID 4326). Nullable : la capture peut être sans GPS.
    location    geography(Point, 4326),
    accuracy_m  REAL,                              -- précision horizontale (mètres)
    taken_at    TIMESTAMPTZ NOT NULL,             -- date de la prise
    notes       TEXT NOT NULL DEFAULT '',
    species     TEXT,                              -- nom scientifique (NODE-26)
    tags        TEXT[] NOT NULL DEFAULT '{}',      -- étiquettes libres
    visibility  TEXT NOT NULL DEFAULT 'private'    -- 'private' | 'friends'
                CHECK (visibility IN ('private', 'friends')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ                        -- soft-delete (sync — NODE-19)
);
-- Requêtes géo (ST_DWithin, bbox) : index GiST sur la position.
CREATE INDEX idx_flowers_location ON flowers USING GIST (location);
-- Listing par propriétaire + sync incrémentale.
CREATE INDEX idx_flowers_owner       ON flowers(owner_id);
CREATE INDEX idx_flowers_updated_at  ON flowers(updated_at);
-- Recherche par espèce / étiquette (NODE-26).
CREATE INDEX idx_flowers_species     ON flowers(species);
CREATE INDEX idx_flowers_tags        ON flowers USING GIN (tags);

-- =====================================================================
-- Photos d'une fleur (NODE-104 : plusieurs photos par fleur)
--   Une fleur a 1..n photos ordonnées ; exactement une est la couverture.
--   `flowers.image_key` est conservé le temps de la transition (= couverture).
-- =====================================================================
CREATE TABLE flower_photos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    image_key   TEXT NOT NULL,                     -- clé de l'objet dans MinIO
    position    INTEGER NOT NULL DEFAULT 0,        -- ordre d'affichage
    is_cover    BOOLEAN NOT NULL DEFAULT false,    -- photo de couverture
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_flower_photos_flower ON flower_photos(flower_id);
-- Au plus une couverture par fleur.
CREATE UNIQUE INDEX idx_flower_photos_cover
    ON flower_photos(flower_id) WHERE is_cover;

-- Backfill : chaque fleur existante devient sa propre photo de couverture.
INSERT INTO flower_photos (flower_id, image_key, position, is_cover)
SELECT id, image_key, 0, true FROM flowers
ON CONFLICT DO NOTHING;

-- =====================================================================
-- Albums de fleurs (NODE-98)
--   Regroupement nommé de fleurs appartenant à un utilisateur.
-- =====================================================================
CREATE TABLE albums (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_albums_owner ON albums(owner_id);

-- Appartenance fleur ↔ album (n..n).
CREATE TABLE flower_albums (
    album_id    UUID NOT NULL REFERENCES albums(id)  ON DELETE CASCADE,
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    PRIMARY KEY (album_id, flower_id)
);
CREATE INDEX idx_flower_albums_flower ON flower_albums(flower_id);

-- =====================================================================
-- Amitiés (NODE-20)
-- =====================================================================
CREATE TABLE friendships (
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
CREATE INDEX idx_friendships_addressee ON friendships(addressee_id);

-- =====================================================================
-- Partages configurables (NODE-22)
--   scope = 'all'    : toutes les fleurs du propriétaire
--   scope = 'flower' : une fleur précise (flower_id)
--   scope = 'album'  : les fleurs d'un album précis (album_id) — NODE-101
--   include_gps      : false => coordonnées masquées (protection des spots)
-- =====================================================================
CREATE TABLE shares (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shared_with UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope       TEXT NOT NULL DEFAULT 'all'
                CHECK (scope IN ('all', 'flower', 'album')),
    flower_id   UUID REFERENCES flowers(id) ON DELETE CASCADE,
    album_id    UUID REFERENCES albums(id)  ON DELETE CASCADE,
    include_gps BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- cohérence scope/flower_id et scope/album_id
    CHECK ((scope = 'flower') = (flower_id IS NOT NULL)),
    CHECK ((scope = 'album')  = (album_id  IS NOT NULL))
);
CREATE INDEX idx_shares_owner ON shares(owner_id);
CREATE INDEX idx_shares_user  ON shares(shared_with);

-- =====================================================================
-- Propositions d'espèce collaboratives (NODE-31)
-- =====================================================================
CREATE TABLE species_proposals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flower_id   UUID NOT NULL REFERENCES flowers(id) ON DELETE CASCADE,
    proposed_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    species     TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'accepted')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_species_proposals_flower ON species_proposals(flower_id);

-- =====================================================================
-- Notifications in-app (NODE-23)
-- =====================================================================
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        TEXT NOT NULL,             -- friend_request | friend_accepted | flower_shared
    data        JSONB NOT NULL DEFAULT '{}',
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_unread
    ON notifications(user_id) WHERE read_at IS NULL;

-- =====================================================================
-- Jetons d'appareil pour le push FCM/APNs (NODE-57)
-- =====================================================================
CREATE TABLE device_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL UNIQUE,             -- jeton FCM/APNs
    platform    TEXT NOT NULL                     -- 'android' | 'ios' | 'web'
                CHECK (platform IN ('android', 'ios', 'web')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user ON device_tokens(user_id);

-- =====================================================================
-- Notes
-- =====================================================================
-- * Visibilité d'une fleur pour l'utilisateur U :
--     - owner_id = U, OU
--     - visibility = 'friends' ET amitié 'accepted' entre U et owner, OU
--     - existe flower_shares(flower_id, U).
--   (les requêtes effectives sont décrites dans backend/docs/API.md)
