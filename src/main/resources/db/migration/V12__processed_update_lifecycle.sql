-- Normal Telegram updates are claimed while processing and marked complete only
-- after their handler returns. A stale PROCESSING claim can be retried after a
-- worker crash; completed rows remain permanent idempotency records.

ALTER TABLE processed_telegram_updates
    ADD COLUMN status TEXT NOT NULL DEFAULT 'PROCESSED';

ALTER TABLE processed_telegram_updates
    ADD COLUMN claimed_at TIMESTAMPTZ;

UPDATE processed_telegram_updates
SET claimed_at = processed_at
WHERE claimed_at IS NULL;

ALTER TABLE processed_telegram_updates
    ALTER COLUMN claimed_at SET DEFAULT NOW();

ALTER TABLE processed_telegram_updates
    ALTER COLUMN claimed_at SET NOT NULL;

ALTER TABLE processed_telegram_updates
    ALTER COLUMN processed_at DROP NOT NULL;

ALTER TABLE processed_telegram_updates
    ADD CONSTRAINT processed_telegram_updates_status_check
        CHECK (status IN ('PROCESSING', 'PROCESSED'));

CREATE INDEX idx_processed_telegram_updates_status_claimed
    ON processed_telegram_updates(status, claimed_at);
