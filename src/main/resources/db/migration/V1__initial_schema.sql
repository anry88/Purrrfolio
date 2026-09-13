CREATE TABLE players (
    id              BIGSERIAL PRIMARY KEY,
    telegram_id     BIGINT NOT NULL UNIQUE,
    username        TEXT,
    display_name    TEXT,
    fish_balance    INTEGER NOT NULL DEFAULT 0 CHECK (fish_balance >= 0),
    locale          TEXT NOT NULL DEFAULT 'ru',
    last_daily_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE player_cards (
    id              BIGSERIAL PRIMARY KEY,
    player_id       BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    card_id         TEXT NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    first_obtained  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (player_id, card_id)
);

CREATE TABLE theme_completion_claims (
    id              BIGSERIAL PRIMARY KEY,
    player_id       BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    theme_id        TEXT NOT NULL,
    claimed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (player_id, theme_id)
);

CREATE TABLE pack_openings (
    id              BIGSERIAL PRIMARY KEY,
    player_id       BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    pack_id         TEXT NOT NULL,
    spent_fish      INTEGER NOT NULL CHECK (spent_fish >= 0),
    rolled_card_ids TEXT[] NOT NULL,
    opened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE trade_offers (
    id                  UUID PRIMARY KEY,
    seller_player_id    BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    buyer_player_id     BIGINT REFERENCES players(id) ON DELETE SET NULL,
    offered_card_id     TEXT NOT NULL,
    requested_card_id   TEXT,
    requested_fish      INTEGER CHECK (requested_fish IS NULL OR requested_fish >= 0),
    status              TEXT NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE TABLE market_listings (
    id                  UUID PRIMARY KEY,
    seller_player_id    BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    card_id             TEXT NOT NULL,
    price_fish          INTEGER NOT NULL CHECK (price_fish > 0),
    status              TEXT NOT NULL,
    buyer_player_id     BIGINT REFERENCES players(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sold_at             TIMESTAMPTZ
);

CREATE TABLE processed_telegram_updates (
    update_id       BIGINT PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_player_cards_player_id ON player_cards(player_id);
CREATE INDEX idx_trade_offers_status ON trade_offers(status);
CREATE INDEX idx_market_listings_status ON market_listings(status);
