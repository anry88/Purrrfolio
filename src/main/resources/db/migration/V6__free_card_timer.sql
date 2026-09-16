-- Free single-card drops (one card every 7h, claimed via button).
-- No data is touched: new nullable column, NULL means "a free card is due".
ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS last_free_card_at TIMESTAMPTZ;
