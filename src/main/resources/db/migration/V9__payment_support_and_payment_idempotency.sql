-- RiverKing-style payment support requests with an explicit admin decision.
CREATE TABLE payment_support_requests (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payment_id      BIGINT NOT NULL UNIQUE REFERENCES payments(id) ON DELETE CASCADE,
    reason          TEXT NOT NULL,
    status          TEXT NOT NULL,
    admin_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_support_user ON payment_support_requests(user_id, created_at DESC);
CREATE INDEX idx_payment_support_status ON payment_support_requests(status, created_at);

-- A Telegram charge may grant or revoke its packs only once. NULL payment ids
-- (starter/craft/opened rows) remain unrestricted by SQL UNIQUE semantics.
ALTER TABLE pack_ledger
    ADD CONSTRAINT uq_pack_ledger_source_payment UNIQUE (source, payment_id);
