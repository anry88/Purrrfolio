-- One referral reward per newly registered player. The unique referred_user_id
-- makes the grant idempotent even if registration is retried concurrently.
CREATE TABLE referral_rewards (
    referred_user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    referrer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    packs_each INTEGER NOT NULL CHECK (packs_each > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (referred_user_id <> referrer_user_id)
);

CREATE INDEX idx_referral_rewards_referrer_created
    ON referral_rewards(referrer_user_id, created_at DESC);
