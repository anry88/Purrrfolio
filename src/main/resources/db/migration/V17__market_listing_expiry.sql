-- Listings older than 7 days are returned to their owners by a daily job.
-- Backfill: if the listing time cannot be determined, treat it as now so
-- currently listed cards get a full 7-day window instead of expiring at once.
UPDATE market_listings
SET created_at = NOW(), updated_at = NOW()
WHERE created_at IS NULL;

-- Supports the expiry lookup (status + oldest first) without scanning the table.
CREATE INDEX IF NOT EXISTS idx_market_listings_expiry
    ON market_listings(status, created_at);
