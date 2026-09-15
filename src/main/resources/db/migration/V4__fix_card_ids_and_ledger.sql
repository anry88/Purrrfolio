-- Fix card_id types (JSON catalog is the authority, TEXT ids like 'sleepy')
-- and allow negative pack_ledger entries for pack consumption.
-- Replaces BIGINT FKs introduced in V3 which were never seeded.

ALTER TABLE IF EXISTS user_cards DROP CONSTRAINT IF EXISTS user_cards_card_id_fkey;
ALTER TABLE IF EXISTS random_trade_pool DROP CONSTRAINT IF EXISTS random_trade_pool_card_id_fkey;
ALTER TABLE IF EXISTS market_listings DROP CONSTRAINT IF EXISTS market_listings_card_id_fkey;

ALTER TABLE IF EXISTS user_cards ALTER COLUMN card_id TYPE TEXT USING card_id::text;
ALTER TABLE IF EXISTS random_trade_pool ALTER COLUMN card_id TYPE TEXT USING card_id::text;
ALTER TABLE IF EXISTS market_listings ALTER COLUMN card_id TYPE TEXT USING card_id::text;

-- pack_ledger: consumption rows use negative quantity with source='opened'.
ALTER TABLE IF EXISTS pack_ledger DROP CONSTRAINT IF EXISTS pack_ledger_quantity_check;
ALTER TABLE IF EXISTS pack_ledger ADD CHECK (quantity <> 0);
