-- Persist the result of a pack-opening Telegram update in the same transaction
-- as its debit and inventory credits. A retried update can then deliver the
-- same cards again without consuming another pack.

CREATE TABLE pack_opening_receipts (
    update_id                  BIGINT PRIMARY KEY,
    user_id                    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_ids                   TEXT[] NOT NULL,
    new_card_ids               TEXT[] NOT NULL,
    completed_collection_ids  TEXT[] NOT NULL DEFAULT '{}',
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pack_opening_receipts_user
    ON pack_opening_receipts(user_id, created_at DESC);
