-- Backfill the 23h free-pack timer for pre-existing users:
-- their countdown starts at registration, so no instant extra free pack.
UPDATE users
SET last_free_pack_opened_at = created_at,
    updated_at = NOW()
WHERE last_free_pack_opened_at IS NULL;
