-- Registration attribution for deep links (t.me/<bot>?start=<source>).
-- 'direct' = plain /start with no payload; otherwise a normalized
-- lowercase campaign code (a-z, 0-9, _, -; max 64 chars, else 'other').
-- No data is touched: new column defaults to 'direct'.
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS registration_source TEXT NOT NULL DEFAULT 'direct';

CREATE INDEX IF NOT EXISTS idx_users_registration_source_created
    ON users(registration_source, created_at DESC);
