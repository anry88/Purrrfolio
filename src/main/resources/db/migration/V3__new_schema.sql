-- New schema based on PDF specification v1.3
-- Replaces the fish-era tables (V1) with the Stars model. Old tables are
-- dropped (test data only). Card ids stay TEXT: the JSON catalog
-- (catalog/cards.json, e.g. 'sleepy') remains the authority for the MVP.
--
-- NOTE: V3 was never successfully applied anywhere before this revision
-- (first prod deploy attempt failed on duplicate processed_telegram_updates),
-- so editing it in place is safe.

-- Old card-for-fish tables are dropped (test data only, owner confirmed).
DROP TABLE IF EXISTS trade_offers CASCADE;
DROP TABLE IF EXISTS market_listings CASCADE;
DROP TABLE IF EXISTS pack_openings CASCADE;
DROP TABLE IF EXISTS theme_completion_claims CASCADE;
DROP TABLE IF EXISTS player_cards CASCADE;
DROP TABLE IF EXISTS players CASCADE;

-- Rarities
CREATE TABLE rarities (
    id              BIGSERIAL PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    probability_weight INTEGER NOT NULL,
    frame_theme     TEXT,
    sort_order      INTEGER NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Collections
CREATE TABLE collections (
    id              BIGSERIAL PRIMARY KEY,
    code            TEXT NOT NULL UNIQUE,
    localized_name  JSONB NOT NULL,
    sort_order      INTEGER NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cards
CREATE TABLE cards (
    id              BIGSERIAL PRIMARY KEY,
    collection_id   BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    english_title   TEXT NOT NULL,
    rarity_id       BIGINT NOT NULL REFERENCES rarities(id),
    image_ref       TEXT NOT NULL,
    sort_order      INTEGER NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    special         BOOLEAN NOT NULL DEFAULT false,
    limited         BOOLEAN NOT NULL DEFAULT false,
    event           TEXT,
    source          TEXT,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (collection_id, english_title)
);

CREATE INDEX idx_cards_collection ON cards(collection_id);
CREATE INDEX idx_cards_rarity ON cards(rarity_id);
CREATE INDEX idx_cards_active ON cards(active) WHERE active = true;

-- Users
CREATE TABLE users (
    id                      BIGSERIAL PRIMARY KEY,
    telegram_user_id        BIGINT NOT NULL UNIQUE,
    language                TEXT NOT NULL DEFAULT 'en',
    last_free_pack_opened_at TIMESTAMPTZ,
    available_packs         INTEGER NOT NULL DEFAULT 0 CHECK (available_packs >= 0),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- User cards (TEXT card_id matches catalog/cards.json ids; no FK while
-- the JSON catalog is the authority)
CREATE TABLE user_cards (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id         TEXT NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    first_obtained  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, card_id)
);

CREATE INDEX idx_user_cards_user ON user_cards(user_id);
CREATE INDEX idx_user_cards_card ON user_cards(card_id);

-- Pack ledger
CREATE TABLE pack_ledger (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source          TEXT NOT NULL,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    stars_paid      INTEGER CHECK (stars_paid IS NULL OR stars_paid >= 0),
    payment_id      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pack_ledger_user ON pack_ledger(user_id);
CREATE INDEX idx_pack_ledger_payment ON pack_ledger(payment_id);

-- Random trade pool (TEXT card_id, see above)
CREATE TABLE random_trade_pool (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id         TEXT NOT NULL,
    status          TEXT NOT NULL,
    matched_trade_id BIGINT REFERENCES random_trade_pool(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    matched_at      TIMESTAMPTZ
);

CREATE INDEX idx_random_trade_pool_status ON random_trade_pool(status);
CREATE INDEX idx_random_trade_pool_matched ON random_trade_pool(matched_trade_id);

-- Market listings (TEXT card_id, see above)
CREATE TABLE market_listings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id         TEXT NOT NULL,
    status          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_market_listings_seller ON market_listings(seller_id);
CREATE INDEX idx_market_listings_status ON market_listings(status);
CREATE INDEX idx_market_listings_card ON market_listings(card_id);

-- Trade offers
CREATE TABLE trade_offers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_listing_id   UUID NOT NULL REFERENCES market_listings(id) ON DELETE CASCADE,
    offered_listing_id  UUID NOT NULL REFERENCES market_listings(id) ON DELETE CASCADE,
    status              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX idx_trade_offers_target ON trade_offers(target_listing_id);
CREATE INDEX idx_trade_offers_offered ON trade_offers(offered_listing_id);
CREATE INDEX idx_trade_offers_status ON trade_offers(status);

-- Payments
CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    telegram_payment_id  TEXT NOT NULL UNIQUE,
    stars               INTEGER NOT NULL CHECK (stars > 0),
    packs_granted       INTEGER NOT NULL CHECK (packs_granted > 0),
    status              TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user ON payments(user_id);
CREATE INDEX idx_payments_telegram ON payments(telegram_payment_id);
CREATE INDEX idx_payments_status ON payments(status);

-- Seed rarities
INSERT INTO rarities (code, probability_weight, frame_theme, sort_order) VALUES
('COMMON', 48, 'cream', 1),
('UNCOMMON', 25, 'green', 2),
('RARE', 15, 'blue', 3),
('EPIC', 7, 'purple', 4),
('MYTHIC', 3, 'magenta', 5),
('LEGENDARY', 2, 'gold', 6);

-- Processed Telegram updates (idempotency). V1 already created this table,
-- so keep existing rows.
CREATE TABLE IF NOT EXISTS processed_telegram_updates (
    update_id           BIGINT PRIMARY KEY,
    processed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_telegram_updates_at ON processed_telegram_updates(processed_at);

-- Seed starter collections (placeholder, will be expanded to 10+ collections)
INSERT INTO collections (code, localized_name, sort_order) VALUES
('cozy-home', '{"ru": "Уютный дом", "en": "Cozy Home"}', 1),
('professions', '{"ru": "Профессии", "en": "Professions"}', 2),
('weather', '{"ru": "Погода и сезоны", "en": "Weather & Seasons"}', 3),
('food', '{"ru": "Еда и напитки", "en": "Food & Drinks"}', 4),
('travel', '{"ru": "Путешествия", "en": "Travel"}', 5),
('space', '{"ru": "Космос", "en": "Space"}', 6),
('night-city', '{"ru": "Ночной город", "en": "Night City"}', 7),
('hobbies', '{"ru": "Хобби", "en": "Hobbies"}', 8);
