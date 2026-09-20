-- One free pack is awarded once for each complete catalog version of a collection.
-- The version is a SHA-256 hash of the sorted card ids in that collection. When
-- cards are added, the hash changes and the expanded collection can be rewarded
-- again after the player owns every card in the new version.
CREATE TABLE collection_completion_rewards (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme_id            TEXT NOT NULL,
    catalog_version     TEXT NOT NULL,
    total_cards         INTEGER NOT NULL CHECK (total_cards > 0),
    rewarded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, theme_id, catalog_version)
);

CREATE INDEX idx_collection_completion_rewards_user
    ON collection_completion_rewards(user_id);

